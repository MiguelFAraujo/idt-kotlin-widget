package com.idt.widget.data.remote

import com.idt.widget.data.model.ServiceEndpoint

object ServiceCatalog {
    val defaultServices: List<ServiceEndpoint> = listOf(
        ServiceEndpoint("omniroute", "OmniRoute", "127.0.0.1", 20128),
        ServiceEndpoint("ollama", "Ollama", "127.0.0.1", 11434),
        ServiceEndpoint("prometheus", "Prometheus", "127.0.0.1", 9091),
        ServiceEndpoint("netdata", "Netdata", "127.0.0.1", 19999),
        ServiceEndpoint("n8n", "n8n", "127.0.0.1", 5678),
        ServiceEndpoint("ntfy", "ntfy", "127.0.0.1", 2586),
        ServiceEndpoint("portainer", "Portainer", "127.0.0.1", 9443),
        ServiceEndpoint("filebrowser", "Filebrowser", "127.0.0.1", 8083),
        ServiceEndpoint("nextcloud", "Nextcloud", "127.0.0.1", 8081),
        ServiceEndpoint("grafana", "Grafana", "127.0.0.1", 3030),
        ServiceEndpoint("minio", "MinIO", "127.0.0.1", 9001),
        ServiceEndpoint("paperless", "Paperless", "127.0.0.1", 8000),
        ServiceEndpoint("gitea", "Gitea", "127.0.0.1", 3000),
        ServiceEndpoint("homarr", "Homarr", "127.0.0.1", 7575),
        ServiceEndpoint("dash", "Dashboard", "127.0.0.1", 8100),
    )

    fun withHost(baseHost: String): List<ServiceEndpoint> =
        defaultServices.map { it.copy(host = baseHost) }
}
