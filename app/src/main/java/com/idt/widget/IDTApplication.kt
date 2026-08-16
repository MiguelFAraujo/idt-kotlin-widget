package com.idt.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import com.idt.widget.di.AppContainer
import com.idt.widget.util.ConnectionSyncHelper
import com.idt.widget.util.NotificationHelper
import com.idt.widget.widget.StatusWidgetProvider
import com.idt.widget.widget.WidgetLiveService
import com.idt.widget.widget.WidgetScheduler

class IDTApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.ensureChannels(this)
        WidgetScheduler.schedule(this, intervalMinutes = 15)
        WidgetScheduler.scheduleUpdateChecks(this)
        // Re-sincroniza com o host configurado se já existir conexão: o widget só
        // verifica o endereço que o usuário forneceu — nunca um host adivinhado.
        if (container.configDataSource.current().connectionConfigured) {
            ConnectionSyncHelper.resyncAndRefresh(this)
        }
        // Serviço ao vivo: atualiza o widget por segundo enquanto o app está aberto.
        // O ticker para quando não há widget nem app em uso.
        WidgetLiveService.start(this)
    }
}
