package com.idt.widget.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.idt.widget.IDTApplication
import com.idt.widget.util.AlertNotifier
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class WidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as IDTApplication
            val endpoints = app.container.serviceRepository.getEndpoints().filter { it.enabled }
            val results = coroutineScope {
                endpoints.map { async { app.container.serviceRepository.checkService(it) } }.awaitAll()
            }
            StatusData.write(applicationContext, results, System.currentTimeMillis())

            // Alerta autônomo de mudança de status
            AlertNotifier.ensureChannel(applicationContext)
            AlertNotifier.onCheckComplete(applicationContext, results)

            val manager = AppWidgetManager.getInstance(applicationContext)
            val ids = manager.getAppWidgetIds(
                ComponentName(applicationContext, StatusWidgetProvider::class.java)
            )
            ids.forEach { id ->
                StatusWidgetProvider.updateWidget(applicationContext, manager, id)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
