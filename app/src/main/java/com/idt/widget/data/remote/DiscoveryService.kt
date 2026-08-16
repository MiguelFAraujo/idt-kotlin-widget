package com.idt.widget.data.remote

import com.idt.widget.data.model.ServiceEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Auto-discovery service that connects to a known server (Nextcloud, etc.)
 * and discovers available services using WebDAV or known endpoints.
 */
class DiscoveryService(
    private val serverUrl: String,
    private val username: String,
    private val password: String,
    private val webDavPath: String = "/",
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5000, TimeUnit.MILLISECONDS)
        .readTimeout(10000, TimeUnit.MILLISECONDS)
        .build()

    private val baseUrl: String
        get() {
            val url = serverUrl.trim()
            return if (url.endsWith("/")) url.substring(0, url.length - 1) else url
        }

    /** Discover services via WebDAV (Nextcloud, etc.) */
    suspend fun discoverViaWebDav(): List<DiscoveredService> = withContext(Dispatchers.IO) {
        val url = "$baseUrl$webDavPath"
        val request = Request.Builder()
            .url(url)
            .method("PROPFIND", null)
            .addHeader("Authorization", Credentials.basic(username, password))
            .addHeader("Depth", "1")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                parseWebDavResponse(body)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Discover known IDT-Lab services by checking common ports */
    suspend fun discoverKnownServices(host: String): List<DiscoveredService> = withContext(Dispatchers.IO) {
        val knownServices = mapOf(
            20128 to "OmniRoute",
            11434 to "Ollama",
            9091 to "Prometheus",
            19999 to "Netdata",
            5678 to "n8n",
            2586 to "ntfy",
            9443 to "Portainer",
            8083 to "Filebrowser",
            8081 to "Nextcloud",
            3030 to "Grafana",
            9001 to "MinIO",
            8000 to "Paperless",
            3001 to "Gitea",
            7575 to "Homarr",
            8100 to "Dashboard",
        )

        val results = mutableListOf<DiscoveredService>()
        for ((port, name) in knownServices) {
            if (checkPort(host, port)) {
                val scheme = if (port == 9443) "https" else "http"
                results.add(DiscoveredService(
                    name = name,
                    host = host,
                    port = port,
                    url = "$scheme://$host:$port",
                    requiresAuth = port in setOf(9443, 8081), // Portainer, Nextcloud
                ))
            }
        }
        results
    }

    private fun checkPort(host: String, port: Int): Boolean {
        try {
            val socket = java.net.Socket()
            socket.connect(java.net.InetSocketAddress(host, port), 2000)
            socket.close()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    private fun parseWebDavResponse(xml: String): List<DiscoveredService> {
        // Simple XML parsing for WebDAV PROPFIND response
        // In a real implementation, use a proper XML parser
        val results = mutableListOf<DiscoveredService>()
        // This is a placeholder - WebDAV parsing is complex
        // For now, return empty and rely on discoverKnownServices
        return results
    }
}

data class DiscoveredService(
    val name: String,
    val host: String,
    val port: Int,
    val url: String,
    val requiresAuth: Boolean = false,
) {
    fun toEndpoint(): ServiceEndpoint {
        return ServiceEndpoint(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            host = host,
            port = port,
            enabled = true,
            requireAuth = requiresAuth,
            authType = if (requiresAuth) ServiceEndpoint.AuthType.WEBDAV else ServiceEndpoint.AuthType.NONE,
            username = "",
            password = "",
        )
    }
}