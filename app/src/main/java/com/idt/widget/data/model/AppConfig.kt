package com.idt.widget.data.model

data class AppConfig(
    val serverUrl: String = "http://192.168.1.9",
    val serverUser: String = "",
    val serverPass: String = "",
    val serverPath: String = "/",
    val useWebDav: Boolean = false,
    val autoDiscover: Boolean = true,
    val autoRefresh: Boolean = true,
    val refreshIntervalSeconds: Long = 10,
    val showNotifications: Boolean = false,
    val compactView: Boolean = false,
    val connectionConfigured: Boolean = false,
    val useFingerprint: Boolean = false,
)
