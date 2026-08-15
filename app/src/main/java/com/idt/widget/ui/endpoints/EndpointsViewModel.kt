package com.idt.widget.ui.endpoints

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.idt.widget.data.local.ConfigDataSource
import com.idt.widget.data.model.ServiceEndpoint
import com.idt.widget.domain.repository.ServiceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EndpointsViewModel(
    private val repository: ServiceRepository,
    private val config: ConfigDataSource,
) : ViewModel() {

    val endpoints: StateFlow<List<ServiceEndpoint>> = repository.observeEndpoints()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addEndpoint(endpoint: ServiceEndpoint) {
        viewModelScope.launch { repository.addEndpoint(endpoint) }
    }

    fun updateEndpoint(endpoint: ServiceEndpoint) {
        viewModelScope.launch { repository.updateEndpoint(endpoint) }
    }

    fun deleteEndpoint(id: String) {
        viewModelScope.launch { repository.deleteEndpoint(id) }
    }
}
