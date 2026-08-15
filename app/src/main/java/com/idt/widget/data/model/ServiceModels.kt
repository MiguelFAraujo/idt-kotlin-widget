package com.idt.widget.data.model

data class ServiceEndpoint(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val enabled: Boolean = true,
    val requireAuth: Boolean = false,
)

data class ServiceCheckResult(
    val endpoint: ServiceEndpoint,
    val ok: Boolean,
    val roundUsed: String,
    val latencyMs: Long,
    val message: String,
)
