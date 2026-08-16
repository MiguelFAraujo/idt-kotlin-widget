package com.idt.widget.ui.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idt.widget.data.local.ConfigDataSource
import com.idt.widget.data.remote.DiscoveredService
import com.idt.widget.data.remote.DiscoveryService
import com.idt.widget.data.remote.DiagnosticsTool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ConnectionUiState {
    data class Idle(val serverUrl: String = "") : ConnectionUiState
    data class Testing(val serverUrl: String) : ConnectionUiState
    data class TestSuccess(val serverUrl: String) : ConnectionUiState
    data class TestError(val serverUrl: String, val error: String) : ConnectionUiState
    data class Discovering(val serverUrl: String) : ConnectionUiState
    data class Discovered(val serverUrl: String, val services: List<DiscoveredService>) : ConnectionUiState
    data class DiscoverError(val serverUrl: String, val error: String) : ConnectionUiState
}

class ConnectionViewModel(
    private val configDataSource: ConfigDataSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ConnectionUiState>(ConnectionUiState.Idle())
    val uiState = _uiState.asStateFlow()

    fun setServerUrl(url: String) {
        _uiState.value = when (val current = _uiState.value) {
            is ConnectionUiState.Idle -> current.copy(serverUrl = url)
            is ConnectionUiState.TestSuccess -> current.copy(serverUrl = url)
            is ConnectionUiState.TestError -> current.copy(serverUrl = url)
            is ConnectionUiState.Discovered -> current.copy(serverUrl = url)
            is ConnectionUiState.DiscoverError -> current.copy(serverUrl = url)
            else -> current
        }
    }

    fun setUsername(username: String) {
        // Store in config later
    }

    fun setPassword(password: String) {
        // Store in config later
    }

    fun setUseWebDav(useWebDav: Boolean) {
        // Update UI visibility handled in fragment
    }

    fun setWebDavPath(path: String) {
        // Store in config later
    }

    fun setUseFingerprint(useFingerprint: Boolean) {
        // Store in config later
    }

    fun testConnection() {
        val url = (_uiState.value as? ConnectionUiState.Idle)?.serverUrl
            ?: (_uiState.value as? ConnectionUiState.TestSuccess)?.serverUrl
            ?: (_uiState.value as? ConnectionUiState.TestError)?.serverUrl
            ?: (_uiState.value as? ConnectionUiState.Discovered)?.serverUrl
            ?: (_uiState.value as? ConnectionUiState.DiscoverError)?.serverUrl
            ?: return

        if (url.isBlank()) {
            _uiState.value = ConnectionUiState.TestError("", "Informe o endereço do servidor")
            return
        }

        _uiState.value = ConnectionUiState.Testing(url)

        viewModelScope.launch {
            try {
                // Extract host from URL
                val host = extractHost(url)
                if (host.isNullOrBlank()) {
                    _uiState.value = ConnectionUiState.TestError(url, "URL inválida")
                    return@launch
                }

                // Test TCP connection to common ports
                val ports = listOf(80, 443, 8081, 8083, 9001, 11434, 20128)
                var connected = false
                for (port in ports) {
                    if (DiagnosticsTool.tcpProbe(host, port, 2000)) {
                        connected = true
                        break
                    }
                }

                if (connected) {
                    _uiState.value = ConnectionUiState.TestSuccess(url)
                } else {
                    _uiState.value = ConnectionUiState.TestError(url, "Nenhuma porta conhecida respondendo. Verifique o endereço e tente novamente.")
                }
            } catch (e: Exception) {
                _uiState.value = ConnectionUiState.TestError(url, "Erro ao testar: ${e.message}")
            }
        }
    }

    fun discoverServices() {
        val url = (_uiState.value as? ConnectionUiState.TestSuccess)?.serverUrl ?: return

        _uiState.value = ConnectionUiState.Discovering(url)

        viewModelScope.launch {
            try {
                val host = extractHost(url)
                if (host.isNullOrBlank()) {
                    _uiState.value = ConnectionUiState.DiscoverError(url, "URL inválida")
                    return@launch
                }

                // Get credentials from config
                val config = configDataSource.getConfig()
                
                // Discover known services
                val discoveryService = DiscoveryService(
                    serverUrl = url,
                    username = config.serverUser,
                    password = config.serverPass,
                    webDavPath = config.serverPath,
                )
                
                val knownServices = discoveryService.discoverKnownServices(host)
                
                // Also try WebDAV discovery if configured
                var allServices = knownServices
                if (config.useWebDav && config.serverUser.isNotEmpty()) {
                    val webDavServices = discoveryService.discoverViaWebDav()
                    allServices += webDavServices
                }

                _uiState.value = ConnectionUiState.Discovered(url, allServices)
            } catch (e: Exception) {
                _uiState.value = ConnectionUiState.DiscoverError(url, "Erro ao descobrir: ${e.message}")
            }
        }
    }

    suspend fun saveConfiguration(serverUrl: String, username: String, password: String, useWebDav: Boolean, webDavPath: String, useFingerprint: Boolean) {
        val config = configDataSource.getConfig()
        configDataSource.saveConfig(config.copy(
            serverUrl = serverUrl,
            serverUser = username,
            serverPass = password,
            serverPath = webDavPath,
            useWebDav = useWebDav,
            connectionConfigured = true,
            useFingerprint = useFingerprint,
        ))
        // TODO: Save fingerprint preference
    }

    private fun extractHost(url: String): String? {
        return try {
            val u = java.net.URL(url.trim())
            u.host
        } catch (e: Exception) {
            // Try parsing as host:port
            val cleanUrl = url.trim()
            if (cleanUrl.contains("://")) {
                cleanUrl.substringAfter("://").substringBefore(":").substringBefore("/")
            } else {
                cleanUrl.substringBefore(":").substringBefore("/")
            }
        }
    }
}