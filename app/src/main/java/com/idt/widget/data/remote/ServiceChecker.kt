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
 * Motor de verificação de serviços com 3 subprocessos e 5 rodadas de autenticação.
 *
 * Subprocesso 1 — TCP probe: conecta no host:porta (timeout 3s).
 * Subprocesso 2 — HTTP probe: GET / aceita 200-399 (timeout 3s).
 * Subprocesso 3 — Auth probe: 5 rodadas em cascata até conseguir:
 *   R1 anônimo · R2 Basic user/pass · R3 Bearer token · R4 WebDAV PROPFIND Basic · R5 custom header X-IDT-Token
 */
class ServiceChecker(
    private val serverUser: String,
    private val serverPass: String,
    private val bearerToken: String = "",
    private val xIdtToken: String = "",
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

        val httpResult = httpProbe(endpoint)
        return@withContext when (httpResult) {
            null -> ServiceCheckResult(
                endpoint, ok = true,
                roundUsed = "TCP", latencyMs = System.currentTimeMillis() - started,
                message = "porta aberta (sem HTTP)",
            )
            else -> ServiceCheckResult(
                endpoint, ok = httpResult.ok,
                roundUsed = httpResult.round,
                latencyMs = System.currentTimeMillis() - started,
                message = httpResult.message,
            )
        }
    }

    private data class HttpOutcome(val ok: Boolean, val round: String, val message: String)

    private fun tcpProbe(host: String, port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(host, port), tcpTimeoutMs) }
        true
    } catch (e: IOException) {
        false
    } catch (e: Exception) {
        false
    }

    private fun httpProbe(endpoint: ServiceEndpoint): HttpOutcome? {
        val base = "http://${endpoint.host}:${endpoint.port}"
        // Subprocesso 2 — HTTP simples
        val anon = runRound(base, endpoint, "R1", headersOf("R1"))
        if (anon != null) return anon

        if (endpoint.requireAuth) {
            // Subprocesso 3 — 5 rodadas de autenticação em cascata
            for (round in listOf("R2", "R3", "R4", "R5")) {
                val out = runRound(base, endpoint, round, headersOf(round))
                if (out != null) return out
            }
        }
        return null
    }

    private fun headersOf(round: String): Map<String, String> = when (round) {
        "R2" -> if (serverUser.isNotEmpty()) mapOf("Authorization" to Credentials.basic(serverUser, serverPass)) else emptyMap()
        "R3" -> if (bearerToken.isNotEmpty()) mapOf("Authorization" to "Bearer $bearerToken") else emptyMap()
        "R4" -> if (serverUser.isNotEmpty()) {
            mapOf(
                "Authorization" to Credentials.basic(serverUser, serverPass),
                "Depth" to "0",
            )
        } else emptyMap()
        "R5" -> if (xIdtToken.isNotEmpty()) mapOf("X-IDT-Token" to xIdtToken) else emptyMap()
        else -> emptyMap()
    }

    private fun runRound(base: String, endpoint: ServiceEndpoint, round: String, headers: Map<String, String>): HttpOutcome? {
        if (headers.isEmpty()) return null
        val request = try {
            val method = if (round == "R4") "PROPFIND" else "GET"
            Request.Builder()
                .url("$base/")
                .method(method, null)
                .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
                .build()
        } catch (e: Exception) {
            return HttpOutcome(false, round, "URL inválida: ${e.message}")
        }
        return try {
            client.newCall(request).execute().use { resp ->
                val code = resp.code
                when {
                    code in 200..399 -> HttpOutcome(true, round, "HTTP $code")
                    code in 400..499 -> HttpOutcome(false, round, "HTTP $code")
                    else -> HttpOutcome(false, round, "HTTP $code")
                }
            }
        } catch (e: IOException) {
            HttpOutcome(false, round, "sem HTTP: ${e.message ?: "timeout"}")
        } catch (e: Exception) {
            HttpOutcome(false, round, "erro: ${e.message}")
        }
    }
}