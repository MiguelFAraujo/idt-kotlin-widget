package com.idt.widget.data.remote

import com.idt.widget.data.model.ServiceEndpoint

/**
 * Política de sincronização dos endpoints com o endereço configurado pelo usuário.
 *
 * Fonte única de verdade: o host derivado de `AppConfig.serverUrl`. Nada de host
 * hardcoded, nada de cache inventado — se o host configurado não bate com o host
 * dos endpoints persistidos, os endpoints são tratados como obsoletos.
 */
object EndpointPolicy {

    /** Extrai o host de uma URL ("http://100.104.13.42:8081/x" -> "100.104.13.42"). */
    fun hostFromUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return ""
        return try {
            val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
            java.net.URI(withScheme).host?.trim() ?: ""
        } catch (e: Exception) {
            trimmed.substringBefore(":").substringBefore("/").trim()
        }
    }

    /** Diz se o host configurado difere do host já escaneado/persistido. */
    fun needsResync(configuredHost: String, syncedHost: String): Boolean {
        val a = configuredHost.trim().lowercase()
        val b = syncedHost.trim().lowercase()
        if (a.isEmpty()) return false
        return a != b
    }

    /**
     * Descarta endpoints que não pertencem ao host configurado.
     * Nunca devolve o que não corresponde ao que o usuário forneceu.
     */
    fun filterStale(endpoints: List<ServiceEndpoint>, configuredHost: String): List<ServiceEndpoint> {
        if (configuredHost.isBlank()) return emptyList()
        val host = configuredHost.trim().lowercase()
        return endpoints.filter { it.host.trim().lowercase() == host }
    }
}
