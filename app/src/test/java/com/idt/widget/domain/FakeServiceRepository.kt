package com.idt.widget.domain

import com.idt.widget.data.model.ServiceCheckResult
import com.idt.widget.data.model.ServiceEndpoint
import com.idt.widget.data.remote.ServiceCatalog
import com.idt.widget.domain.repository.ServiceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeServiceRepository : ServiceRepository {
    private val endpoints = MutableStateFlow(ServiceCatalog.defaultServices.take(3))

    override fun observeEndpoints(): Flow<List<ServiceEndpoint>> = endpoints.asStateFlow()
    override suspend fun getEndpoints(): List<ServiceEndpoint> = endpoints.value
    override suspend fun addEndpoint(endpoint: ServiceEndpoint) {
        endpoints.value = endpoints.value + endpoint
    }
    override suspend fun updateEndpoint(endpoint: ServiceEndpoint) {
        endpoints.value = endpoints.value.map { if (it.id == endpoint.id) endpoint else it }
    }
    override suspend fun deleteEndpoint(id: String) {
        endpoints.value = endpoints.value.filterNot { it.id == id }
    }
    override suspend fun checkService(endpoint: ServiceEndpoint): ServiceCheckResult =
        ServiceCheckResult(endpoint, ok = endpoint.port != 1, roundUsed = "TCP", latencyMs = 5, message = "ok")
}
