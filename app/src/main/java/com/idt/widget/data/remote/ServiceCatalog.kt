package com.idt.widget.data.remote

import com.idt.widget.data.model.ServiceEndpoint

/**
 * Catálogo dos serviços conhecidos do IDT-Lab (porta + nome).
 *
 * IMPORTANTE: este catálogo NÃO define host. O host vem SEMPRE do que o usuário
 * configurar ([EndpointPolicy]). [LAB_HOST] é apenas um preenchimento padrão de
 * campo na UI de scan, nunca a fonte dos checks de status.
 */
object ServiceCatalog {

    /** Padrão de preenchimento da UI de scan (NÃO é usado nos checks de status). */
    const val LAB_HOST = "100.104.13.42"

    private data class KnownService(
        val id: String,
        val name: String,
        val port: Int,
        val requireAuth: Boolean = false,
        val authType: ServiceEndpoint.AuthType = ServiceEndpoint.AuthType.NONE,
    )

    private val known: List<KnownService> = listOf(
        KnownService("omniroute", "OmniRoute", 20128),
        KnownService("ollama", "Ollama", 11434),
        KnownService("prometheus", "Prometheus", 9091),
        KnownService("netdata", "Netdata", 19999),
        KnownService("n8n", "n8n", 5678),
        KnownService("ntfy", "ntfy", 2586),
        KnownService("portainer", "Portainer", 9443, requireAuth = true, authType = ServiceEndpoint.AuthType.BASIC),
        KnownService("filebrowser", "Filebrowser", 8083),
        KnownService("nextcloud", "Nextcloud", 8081, requireAuth = true, authType = ServiceEndpoint.AuthType.WEBDAV),
        KnownService("grafana", "Grafana", 3030),
        KnownService("minio", "MinIO", 9001),
        KnownService("paperless", "Paperless", 8000),
        KnownService("gitea", "Gitea", 3001),
        KnownService("homarr", "Homarr", 7575),
        KnownService("dash", "Dashboard", 8100),
    )

    /** Portas conhecidas, alvo do scan real no host configurado pelo usuário. */
    val knownPorts: List<Int> get() = known.map { it.port }

    /** Monta os endpoints a partir do HOST FORNECIDO PELO USUÁRIO. */
    fun endpointsForHost(host: String): List<ServiceEndpoint> =
        known.map { svc ->
            ServiceEndpoint(
                id = svc.id,
                name = svc.name,
                host = host,
                port = svc.port,
                requireAuth = svc.requireAuth,
                authType = svc.authType,
            )
        }

    /** Compatibilidade: catálogo no host padrão de UI. Não usado pelos checks de status. */
    val defaultServices: List<ServiceEndpoint> get() = endpointsForHost(LAB_HOST)

    fun withHost(baseHost: String): List<ServiceEndpoint> = endpointsForHost(baseHost)
}
