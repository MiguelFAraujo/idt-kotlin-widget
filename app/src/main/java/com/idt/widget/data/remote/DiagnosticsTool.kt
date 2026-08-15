package com.idt.widget.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Ferramentas de diagnóstico de rede para a aba Utilidades.
 * Todas executam em IO dispatcher e retornam resultados legíveis.
 */
object DiagnosticsTool {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .build()
    }

    /** Latência HTTP até um host:porta. Retorna ms ou null em falha. */
    suspend fun httpLatency(host: String, port: Int): Long? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("http://$host:$port/").build()
            val started = System.currentTimeMillis()
            client.newCall(request).execute().use { resp ->
                if (resp.code in 200..499) System.currentTimeMillis() - started else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** TCP probe simples (3s timeout). Retorna true se porta aceitou conexão. */
    suspend fun tcpProbe(host: String, port: Int, timeoutMs: Int = 3000): Boolean =
        withContext(Dispatchers.IO) {
            try {
                Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
                true
            } catch (e: IOException) {
                false
            } catch (e: Exception) {
                false
            }
        }

    /** Escaneia uma lista de portas em paralelo e retorna as abertas. */
    suspend fun scanPorts(host: String, ports: List<Int>): List<Int> =
        withContext(Dispatchers.IO) {
            ports.filter { tcpProbe(host, it, timeoutMs = 1500) }.sorted()
        }

    val COMMON_PORTS: List<Int> = listOf(
        21, 22, 53, 80, 443, 3306, 5432, 8080, 8443, 9000,
        9090, 11434, 19999, 3000, 3001, 3002, 5678, 8081, 8082, 8083,
        9443, 8000, 9001, 3030, 2586, 5001, 7575, 5055,
    )

    /** Resolve DNS de um host. Retorna o primeiro IP ou mensagem de erro. */
    suspend fun dnsLookup(host: String): String = withContext(Dispatchers.IO) {
        try {
            val addr = InetAddress.getByName(host)
            addr.hostAddress ?: "sem IP"
        } catch (e: UnknownHostException) {
            "host não encontrado"
        } catch (e: Exception) {
            "erro: ${e.message}"
        }
    }

    /** Identifica a rede local (IP + hostname) do dispositivo. */
    suspend fun localNetworkInfo(): Map<String, String> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, String>()
        try {
            val addr = InetAddress.getLocalHost()
            result["ip"] = addr.hostAddress ?: "desconhecido"
            result["hostname"] = addr.hostName ?: "desconhecido"
        } catch (e: Exception) {
            result["ip"] = "indisponível"
            result["hostname"] = "indisponível"
        }
        result
    }
}
