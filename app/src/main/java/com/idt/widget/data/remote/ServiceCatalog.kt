package com.idt.widget.data.remote

import com.idt.widget.data.model.ServiceEndpoint

object ServiceCatalog {
    const val LAB_HOST = "100.104.13.42"

    val defaultServices: List<ServiceEndpoint> = listOf(
        ServiceEndpoint("omniroute", "OmniRoute", LAB_HOST, 20128),
        ServiceEndpoint("ollama", "Ollama", LAB_HOST, 11434),
        ServiceEndpoint("prometheus", "Prometheus", LAB_HOST, 9091),
        ServiceEndpoint("netdata", "Netdata", LAB_HOST, 19999),
        ServiceEndpoint("n8n", "n8n", LAB_HOST, 5678),
        ServiceEndpoint("ntfy", "ntfy", LAB_HOST, 2586),
        ServiceEndpoint("portainer", "Portainer", LAB_HOST, 9443, requireAuth = true, authType = ServiceEndpoint.AuthType.BASIC),
        ServiceEndpoint("filebrowser", "Filebrowser", LAB_HOST, 8083),
        ServiceEndpoint("nextcloud", "Nextcloud", LAB_HOST, 8081, requireAuth = true, authType = ServiceEndpoint.AuthType.WEBDAV),
        ServiceEndpoint("grafana", "Grafana", LAB_HOST, 3030),
        ServiceEndpoint("minio", "MinIO", LAB_HOST, 9001),
        ServiceEndpoint("paperless", "Paperless", LAB_HOST, 8000),
        ServiceEndpoint("gitea", "Gitea", LAB_HOST, 3001),
        ServiceEndpoint("homarr", "Homarr", LAB_HOST, 7575),
        ServiceEndpoint("dash", "Dashboard", LAB_HOST, 8100),
    )

    fun withHost(baseHost: String): List<ServiceEndpoint> =
        defaultServices.map { it.copy(host = baseHost) }
}
