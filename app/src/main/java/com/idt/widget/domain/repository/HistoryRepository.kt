package com.idt.widget.domain.repository

import com.idt.widget.data.history.EndpointStats
import com.idt.widget.data.model.ServiceCheckResult
import kotlinx.coroutines.flow.Flow

/** Histórico de verificações: uptime, latência e sparkline por endpoint. */
interface HistoryRepository {
    fun observe(): Flow<Map<String, EndpointStats>>
    suspend fun record(results: List<ServiceCheckResult>, now: Long)
    suspend fun statsFor(endpointId: String): EndpointStats?
    suspend fun clear()
}
