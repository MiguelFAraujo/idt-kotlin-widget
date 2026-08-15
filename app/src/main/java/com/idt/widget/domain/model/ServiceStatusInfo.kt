package com.idt.widget.domain.model

enum class ServiceStatus { ONLINE, OFFLINE, UNKNOWN }

data class ServiceStatusInfo(
    val name: String,
    val host: String,
    val port: Int,
    val status: ServiceStatus,
    val latencyMs: Long? = null,
    val message: String? = null,
)
