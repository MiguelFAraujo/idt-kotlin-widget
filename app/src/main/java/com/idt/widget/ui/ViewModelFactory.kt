package com.idt.widget.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.idt.widget.IDTApplication
import com.idt.widget.ui.dashboard.DashboardViewModel
import com.idt.widget.ui.diagnostics.DiagnosticsViewModel
import com.idt.widget.ui.endpoints.EndpointsViewModel

class ViewModelFactory(
    private val application: IDTApplication,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        return when {
            modelClass.isAssignableFrom(DashboardViewModel::class.java) ->
                DashboardViewModel(
                    application.container.serviceRepository,
                    application.container.historyRepository,
                ) as T
            modelClass.isAssignableFrom(EndpointsViewModel::class.java) ->
                EndpointsViewModel(application.container.serviceRepository, application.container.configDataSource) as T
            modelClass.isAssignableFrom(DiagnosticsViewModel::class.java) ->
                DiagnosticsViewModel() as T
            else -> throw IllegalArgumentException("ViewModel desconhecido: ${modelClass.name}")
        }
    }
}
