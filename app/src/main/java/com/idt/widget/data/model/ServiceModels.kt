package com.idt.widget.data.model

data class ServiceEndpoint(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val enabled: Boolean = true,
    val requireAuth: Boolean = false,
    val authType: AuthType = AuthType.NONE,
    val username: String = "",
    val password: String = "",
    val bearerToken: String = "",
    val xIdtToken: String = "",
    val useFingerprint: Boolean = false,
) {
    enum class AuthType {
        NONE, BASIC, BEARER, WEBDAV, CUSTOM
    }
}

data class ServiceCheckResult(
    val endpoint: ServiceEndpoint,
    val ok: Boolean,
    val roundUsed: String,
    val latencyMs: Long,
    val message: String,
)
