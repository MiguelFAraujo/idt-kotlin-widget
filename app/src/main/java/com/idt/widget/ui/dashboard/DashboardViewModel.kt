package com.idt.widget.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idt.widget.data.history.EndpointStats
import com.idt.widget.data.model.ServiceCheckResult
import com.idt.widget.domain.repository.HistoryRepository
import com.idt.widget.domain.repository.ServiceRepository
import com.idt.widget.update.UpdateInfo
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class DashboardUiState(
    val isLoading: Boolean = false,
    val overallOk: Int = 0,
    val overallTotal: Int = 0,
    val results: List<ServiceCheckResult> = emptyList(),
    val cards: List<ServiceCardItem> = emptyList(),
    val overallUptime: Float = 0f,
    val avgLatencySeries: List<Long> = emptyList(),
    val lastUpdate: Long? = null,
    val error: String? = null,
    val updateAvailable: UpdateInfo? = null,
)

class DashboardViewModel(
    private val repository: ServiceRepository,
    private val history: HistoryRepository,
    private val checkTimeoutMs: Long = 20_000L,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState = _uiState.asStateFlow()

    private var lastStats: Map<String, EndpointStats> = emptyMap()

    init {
        viewModelScope.launch {
            history.observe().collect { stats ->
                lastStats = stats
                _uiState.value = _uiState.value.copy(
                    cards = buildCards(_uiState.value.results, stats),
                )
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val endpoints = repository.getEndpoints().filter { it.enabled }
                // Timeout global: nunca deixa o dashboard preso em "Verificando..."
                val results = withTimeout(checkTimeoutMs) {
                    endpoints
                        .map { async { repository.checkService(it) } }
                        .awaitAll()
                }
                history.record(results, System.currentTimeMillis())
                val ok = results.count { it.ok }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    overallOk = ok,
                    overallTotal = results.size,
                    results = results,
                    cards = buildCards(results, lastStats),
                    overallUptime = if (results.isEmpty()) 0f else ok.toFloat() / results.size,
                    avgLatencySeries = buildAvgLatency(results, lastStats),
                    lastUpdate = System.currentTimeMillis(),
                )
            } catch (e: TimeoutCancellationException) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Tempo esgotado na verificação dos serviços",
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Erro ao verificar serviços",
                )
            }
        }
    }

    fun onUpdateChecked(update: UpdateInfo?) {
        _uiState.value = _uiState.value.copy(updateAvailable = update)
    }

    private fun buildCards(
        results: List<ServiceCheckResult>,
        stats: Map<String, EndpointStats>,
    ): List<ServiceCardItem> = results.map { r ->
        ServiceCardItem(
            name = r.endpoint.name,
            host = r.endpoint.host,
            port = r.endpoint.port,
            ok = r.ok,
            latencyMs = r.latencyMs,
            roundUsed = r.roundUsed,
            message = r.message,
            stats = stats[r.endpoint.id],
        )
    }

    /** Série de latência média por rodada de verificação (1 ponto por chamada). */
    private fun buildAvgLatency(
        results: List<ServiceCheckResult>,
        stats: Map<String, EndpointStats>,
    ): List<Long> {
        val online = results.filter { it.ok }.map { it.latencyMs }
        if (online.isEmpty()) return emptyList()
        val avg = online.sum() / online.size
        return buildList {
            add(avg)
            // aproveita amostras históricas do primeiro endpoint online como contexto
            results.filter { it.ok }.firstOrNull()?.let { first ->
                stats[first.endpoint.id]?.let { s ->
                    if (s.samples.size > 1) addAll(s.samples.map { it.latencyMs })
                }
            }
        }.take(60)
    }
}
