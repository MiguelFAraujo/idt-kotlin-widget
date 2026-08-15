package com.idt.widget.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idt.widget.data.remote.DiagnosticsTool
import com.idt.widget.domain.repository.ServiceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScanUiState(
    val scanning: Boolean = false,
    val host: String = "",
    val ports: List<ScanPortItem> = emptyList(),
    val selectedCount: Int = 0,
    val error: String? = null,
)

class ScanViewModel(
    private val repository: ServiceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState = _uiState.asStateFlow()

    private var selection = mutableMapOf<Int, Boolean>()

    fun setHost(host: String) {
        _uiState.value = _uiState.value.copy(host = host)
    }

    fun scan() {
        val host = _uiState.value.host.trim()
        if (host.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "Informe o endereço (ex: seu Tailscale)")
            return
        }
        _uiState.value = _uiState.value.copy(scanning = true, error = null)
        viewModelScope.launch {
            try {
                val open = DiagnosticsTool.scanPorts(host, DiagnosticsTool.COMMON_PORTS)
                selection = open.associateWith { true }.toMutableMap()
                val items = DiagnosticsTool.COMMON_PORTS.map { p ->
                    ScanPortItem(
                        port = p,
                        name = DiagnosticsTool.portName(p),
                        open = open.contains(p),
                        selected = open.contains(p),
                    )
                }
                _uiState.value = _uiState.value.copy(
                    scanning = false,
                    ports = items,
                    selectedCount = items.count { it.selected },
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    scanning = false,
                    error = "Falha ao escanear: ${e.message}",
                )
            }
        }
    }

    fun toggle(item: ScanPortItem) {
        selection[item.port] = item.selected
        val items = _uiState.value.ports.map { if (it.port == item.port) item else it }
        _uiState.value = _uiState.value.copy(
            ports = items,
            selectedCount = items.count { it.selected },
        )
    }

    /** Cria endpoints para as portas selecionadas; retorna quantos foram adicionados. */
    fun addSelected(): Int {
        val host = _uiState.value.host.trim()
        val selected = _uiState.value.ports.filter { it.selected }
        if (host.isEmpty() || selected.isEmpty()) return 0
        viewModelScope.launch {
            val existing = repository.getEndpoints()
            val already = existing.map { it.host.trim() to it.port }.toSet()
            selected
                .filter { (host to it.port) !in already }
                .forEach { item ->
                    repository.addEndpoint(
                        com.idt.widget.data.model.ServiceEndpoint(
                            id = java.util.UUID.randomUUID().toString(),
                            name = item.name,
                            host = host,
                            port = item.port,
                            enabled = true,
                            requireAuth = false,
                        )
                    )
                }
        }
        return selected.size
    }
}
