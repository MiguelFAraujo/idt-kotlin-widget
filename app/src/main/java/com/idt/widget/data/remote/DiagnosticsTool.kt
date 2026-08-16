package com.idt.widget.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

    /**
     * Escaneia portas em paralelo (chunks) e retorna as abertas, com progresso
     * reportado por [onProgress] (contador de portas testadas). Cada probe tem
     * timeout curto (1.2s) para não travar o scan inteiro.
     */
    suspend fun scanPorts(
        host: String,
        ports: List<Int>,
        timeoutMs: Int = 1200,
        concurrency: Int = 48,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<Int> = withContext(Dispatchers.IO) {
        val open = java.util.Collections.synchronizedList(mutableListOf<Int>())
        val done = java.util.concurrent.atomic.AtomicInteger(0)
        val chunks = ports.chunked(concurrency)
        for (chunk in chunks) {
            coroutineScope {
                chunk.map { p ->
                    async {
                        if (tcpProbe(host, p, timeoutMs)) {
                            synchronized(open) { open.add(p) }
                        }
                        onProgress(done.incrementAndGet(), ports.size)
                    }
                }.awaitAll()
            }
        }
        open.sorted()
    }

    /** Faixa completa de scan: portas conhecidas (1-1024) + portas comuns de aplicações. */
    val FULL_SCAN_PORTS: List<Int> by lazy {
        (1..1024).toList() + COMMON_PORTS.filter { it > 1024 }
    }

    val COMMON_PORTS: List<Int> = listOf(
        21, 22, 53, 80, 443, 3306, 5432, 8080, 8443, 9000,
        9090, 11434, 19999, 3000, 3001, 3002, 5678, 8081, 8082, 8083,
        9443, 8000, 9001, 3030, 2586, 5001, 7575, 5055,
    )

    private val PORT_NAMES: Map<Int, String> = mapOf(
        21 to "FTP",
        22 to "SSH",
        53 to "DNS",
        80 to "HTTP",
        443 to "HTTPS",
        3306 to "MySQL",
        5432 to "PostgreSQL",
        8080 to "HTTP-alt",
        8443 to "HTTPS-alt",
        9000 to "MinIO",
        9090 to "Prometheus",
        11434 to "Ollama",
        19999 to "Netdata",
        3000 to "Gitea",
        3001 to "Uptime Kuma",
        3002 to "Homepage",
        5678 to "n8n",
        8081 to "Nextcloud",
        8082 to "Dozzle",
        8083 to "Filebrowser",
        9443 to "Portainer",
        8000 to "Paperless",
        9001 to "MinIO-Console",
        3030 to "Grafana",
        2586 to "ntfy",
        5001 to "Dockge",
        7575 to "Homarr",
        5055 to "Jellyseerr",
    )

    /** Nome amigável de uma porta conhecida, ou "Porta X". */
    fun portName(port: Int): String = PORT_NAMES[port] ?: "Porta $port"

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
