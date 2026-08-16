package com.idt.widget.domain.repository

import com.idt.widget.data.model.ServiceCheckResult
import com.idt.widget.data.model.ServiceEndpoint
import kotlinx.coroutines.flow.Flow

interface ServiceRepository {
    suspend fun getEndpoints(): List<ServiceEndpoint>
    suspend fun addEndpoint(endpoint: ServiceEndpoint)
    suspend fun updateEndpoint(endpoint: ServiceEndpoint)
    suspend fun deleteEndpoint(id: String)
    suspend fun checkService(endpoint: ServiceEndpoint): ServiceCheckResult
    fun observeEndpoints(): Flow<List<ServiceEndpoint>>
    suspend fun resyncWithConfiguredHost(): Int
}
