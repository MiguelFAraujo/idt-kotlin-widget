package com.idt.widget.data.history

import android.content.Context
import com.idt.widget.data.model.ServiceCheckResult
import com.idt.widget.domain.repository.HistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Persistência do histórico em SharedPreferences (JSON compacto).
 * Guarda até [HistoryStore.maxSamplesPerEndpoint] amostras por endpoint.
 */
class PersistentHistoryRepository(context: Context) : HistoryRepository {

    private val prefs = context.getSharedPreferences("idt_history", Context.MODE_PRIVATE)
    private val store = HistoryStore(maxSamplesPerEndpoint = 300)
    private val flow = MutableStateFlow<Map<String, EndpointStats>>(emptyMap())

    init {
        val raw = prefs.getString(KEY_HISTORY, null)
        if (!raw.isNullOrBlank()) {
            runCatching { store.fromJson(raw) }
        }
        flow.value = store.allHistories().associateBy { it.endpointId }
    }

    override fun observe(): Flow<Map<String, EndpointStats>> = flow.asStateFlow()

    override suspend fun record(results: List<ServiceCheckResult>, now: Long) {
        withContext(Dispatchers.IO) {
            results.forEach { r ->
                store.addSample(r.endpoint.id, r.ok, r.latencyMs, now)
            }
            persist()
            flow.value = store.allHistories().associateBy { it.endpointId }
        }
    }

    override suspend fun statsFor(endpointId: String): EndpointStats? =
        withContext(Dispatchers.IO) { store.historyFor(endpointId) }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            store.clear()
            persist()
            flow.value = emptyMap()
        }
    }

    private fun persist() {
        prefs.edit().putString(KEY_HISTORY, store.toJson()).apply()
    }

    companion object {
        private const val KEY_HISTORY = "history_json"
    }
}
