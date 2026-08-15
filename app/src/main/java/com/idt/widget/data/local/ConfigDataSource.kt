package com.idt.widget.data.local

import android.content.Context
import android.content.SharedPreferences
import com.idt.widget.data.model.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ConfigDataSource(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("idt_config", Context.MODE_PRIVATE)

    private val configFlow = MutableStateFlow(load())

    fun observeConfig(): Flow<AppConfig> = configFlow.asStateFlow()

    suspend fun getConfig(): AppConfig = withContext(Dispatchers.IO) { configFlow.value }

    suspend fun saveConfig(config: AppConfig) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString("server_url", config.serverUrl)
            .putString("server_user", config.serverUser)
            .putString("server_pass", config.serverPass)
            .putString("server_path", config.serverPath)
            .putBoolean("use_webdav", config.useWebDav)
            .putBoolean("auto_discover", config.autoDiscover)
            .putBoolean("auto_refresh", config.autoRefresh)
            .putLong("refresh_interval", config.refreshIntervalSeconds)
            .putBoolean("show_notifications", config.showNotifications)
            .putBoolean("compact_view", config.compactView)
            .apply()
        configFlow.value = config
    }

    fun updateLocal(f: (AppConfig) -> AppConfig) {
        configFlow.value = f(configFlow.value)
    }

    private fun load(): AppConfig = AppConfig(
        serverUrl = prefs.getString("server_url", "http://192.168.1.9") ?: "http://192.168.1.9",
        serverUser = prefs.getString("server_user", "") ?: "",
        serverPass = prefs.getString("server_pass", "") ?: "",
        serverPath = prefs.getString("server_path", "/") ?: "/",
        useWebDav = prefs.getBoolean("use_webdav", false),
        autoDiscover = prefs.getBoolean("auto_discover", true),
        autoRefresh = prefs.getBoolean("auto_refresh", true),
        refreshIntervalSeconds = prefs.getLong("refresh_interval", 10),
        showNotifications = prefs.getBoolean("show_notifications", false),
        compactView = prefs.getBoolean("compact_view", false),
    )
}
