package com.idt.widget.di

import android.content.Context
import com.idt.widget.data.ServiceRepositoryImpl
import com.idt.widget.data.history.PersistentHistoryRepository
import com.idt.widget.data.local.ConfigDataSource
import com.idt.widget.domain.repository.HistoryRepository
import com.idt.widget.domain.repository.ServiceRepository

class AppContainer(context: Context) {
    val configDataSource: ConfigDataSource = ConfigDataSource(context.applicationContext)
    val historyRepository: HistoryRepository =
        PersistentHistoryRepository(context.applicationContext)
    val serviceRepository: ServiceRepository =
        ServiceRepositoryImpl(context.applicationContext, configDataSource)
}
