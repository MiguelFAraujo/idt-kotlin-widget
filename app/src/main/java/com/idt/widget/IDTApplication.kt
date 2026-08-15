package com.idt.widget

import android.app.Application
import com.idt.widget.di.AppContainer
import com.idt.widget.util.NotificationHelper
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
    }
}
