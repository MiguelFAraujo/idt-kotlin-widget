package com.idt.widget.data.remote

import com.idt.widget.data.model.ServiceCheckResult
import com.idt.widget.data.model.ServiceEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Motor de verificação de serviços.
 *
 * Regra principal: porta TCP aberta = serviço online (uptime).
 * O probe HTTP só qualifica a resposta; qualquer status HTTP (2xx-5xx)
 * significa que o serviço está de pé e respondendo.
 *
 * Subprocesso 1 — TCP probe: conecta no host:porta (timeout 3s).
 * Subprocesso 2 — Detecta TLS no socket (Portainer 9443 etc.) e faz GET /:
 *   qualquer status 2xx-5xx = online. Se a porta não fala HTTP, "porta aberta (sem HTTP)".
 * Subprocesso 3 — Auth probe: 5 rodadas em cascata até conseguir:
 *   R1 anônimo · R2 Basic user/pass · R3 Bearer token · R4 WebDAV PROPFIND Basic · R5 custom header X-IDT-Token
 */
class ServiceChecker(
    private val defaultUser: String = "",
    private val defaultPass: String = "",
    private val defaultBearerToken: String = "",
    private val defaultXIdtToken: String = "",
) {
    private val tcpTimeoutMs = 3000
    private val httpTimeoutMs = 3000

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(httpTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(httpTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .build()
    }

    suspend fun check(endpoint: ServiceEndpoint): ServiceCheckResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val tcpOk = tcpProbe(endpoint.host, endpoint.port)
        if (!tcpOk) {
            return@withContext ServiceCheckResult(
                endpoint, ok = false,
                roundUsed = "TCP", latencyMs = System.currentTimeMillis() - started,
                message = "porta fechada / sem resposta",
            )
        }

        val isTls = detectTls(endpoint.host, endpoint.port)
        val httpResult = httpProbe(endpoint, isTls)

        val message = when {
            httpResult != null -> "${if (isTls) "HTTPS" else "HTTP"} ${httpResult.code}"
            else -> "porta aberta (sem HTTP)"
        }
        ServiceCheckResult(
            endpoint, ok = true,
            roundUsed = if (httpResult == null) "TCP" else httpResult.round,
            latencyMs = System.currentTimeMillis() - started,
            message = message,
        )
    }

    private data class HttpOutcome(val code: Int, val round: String)

    private fun tcpProbe(host: String, port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(host, port), tcpTimeoutMs) }
        true
    } catch (e: IOException) {
        false
    } catch (e: Exception) {
        false
    }

    /** Primeiro byte do handshake TLS é 0x16 (ClientHello/ServerHello). */
    private fun detectTls(host: String, port: Int): Boolean = try {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, port), tcpTimeoutMs)
            s.soTimeout = 2000
            val b = s.getInputStream().read()
            b == 0x16
        }
    } catch (e: Exception) {
        false
    }

    private fun httpProbe(endpoint: ServiceEndpoint, tls: Boolean): HttpOutcome? {
        val scheme = if (tls) "https" else "http"
        val base = "$scheme://${endpoint.host}:${endpoint.port}"

        val anon = runRound(base, endpoint, "R1", headersOf(endpoint, "R1"))
        if (anon != null) return anon

        if (endpoint.requireAuth) {
            for (round in listOf("R2", "R3", "R4", "R5")) {
                val out = runRound(base, endpoint, round, headersOf(endpoint, round))
                if (out != null) return out
            }
        }
        return null
    }

    private fun headersOf(endpoint: ServiceEndpoint, round: String): Map<String, String> = when (round) {
        "R2" -> if (endpoint.username.isNotEmpty() && endpoint.authType == ServiceEndpoint.AuthType.BASIC)
            mapOf("Authorization" to Credentials.basic(endpoint.username, endpoint.password))
            else if (defaultUser.isNotEmpty())
                mapOf("Authorization" to Credentials.basic(defaultUser, defaultPass))
            else emptyMap()
        "R3" -> if (endpoint.bearerToken.isNotEmpty() && endpoint.authType == ServiceEndpoint.AuthType.BEARER)
            mapOf("Authorization" to "Bearer ${endpoint.bearerToken}")
            else if (defaultBearerToken.isNotEmpty())
                mapOf("Authorization" to "Bearer $defaultBearerToken")
            else emptyMap()
        "R4" -> if (endpoint.username.isNotEmpty() && endpoint.authType == ServiceEndpoint.AuthType.WEBDAV) {
            mapOf(
                "Authorization" to Credentials.basic(endpoint.username, endpoint.password),
                "Depth" to "0",
            )
        } else if (defaultUser.isNotEmpty()) {
            mapOf(
                "Authorization" to Credentials.basic(defaultUser, defaultPass),
                "Depth" to "0",
            )
        } else emptyMap()
        "R5" -> if (endpoint.xIdtToken.isNotEmpty() && endpoint.authType == ServiceEndpoint.AuthType.CUSTOM)
            mapOf("X-IDT-Token" to endpoint.xIdtToken)
            else if (defaultXIdtToken.isNotEmpty())
                mapOf("X-IDT-Token" to defaultXIdtToken)
            else emptyMap()
        else -> emptyMap()
    }

    private fun runRound(base: String, endpoint: ServiceEndpoint, round: String, headers: Map<String, String>): HttpOutcome? {
        val request = try {
            val method = if (round == "R4") "PROPFIND" else "GET"
            Request.Builder()
                .url("$base/")
                .method(method, null)
                .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
                .build()
        } catch (e: Exception) {
            return null
        }
        return try {
            client.newCall(request).execute().use { resp ->
                HttpOutcome(resp.code, round)
            }
        } catch (e: Exception) {
            null
        }
    }
}
