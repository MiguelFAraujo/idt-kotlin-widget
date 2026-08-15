package com.idt.widget.domain

import com.idt.widget.data.history.EndpointStats
import com.idt.widget.data.model.ServiceCheckResult
import com.idt.widget.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeHistoryRepository : HistoryRepository {
    private val flow = MutableStateFlow<Map<String, EndpointStats>>(emptyMap())

    override fun observe(): Flow<Map<String, EndpointStats>> = flow.asStateFlow()
    override suspend fun record(results: List<ServiceCheckResult>, now: Long) {
        // no-op em teste: histórico fake vazio
    }
    override suspend fun statsFor(endpointId: String): EndpointStats? = flow.value[endpointId]
    override suspend fun clear() {
        flow.value = emptyMap()
    }
}
