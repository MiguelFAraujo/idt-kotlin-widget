package com.idt.widget.widget

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WidgetScheduler {
    private const val WORK_WIDGET = "widget_refresh"
    private const val WORK_WIDGET_NOW = "widget_refresh_now"
    private const val WORK_UPDATE = "update_check"

    fun schedule(context: Context, intervalMinutes: Long = 15) {
        val interval = intervalMinutes.coerceAtLeast(15) // WorkManager mínimo
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(interval, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_WIDGET,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /** Dispara um check real imediato (usado ao adicionar o widget, tocar nele ou sincronizar). */
    fun refreshNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_WIDGET_NOW,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun scheduleUpdateChecks(context: Context) {
        // Verifica atualizações uma vez por dia
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_UPDATE,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
