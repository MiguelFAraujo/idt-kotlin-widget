package com.idt.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.*
import java.util.concurrent.TimeUnit

class StatusWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        scheduleRefresh(context)
        val views = buildViews(context, "Atualizando...")
        appWidgetIds.forEach { id ->
            appWidgetManager.partiallyUpdateAppWidget(id, views)
        }
    }

    override fun onEnabled(context: Context) {
        scheduleRefresh(context)
    }

    override fun onDisabled(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(REFRESH_WORK)
    }

    fun scheduleRefresh(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<StatusWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            REFRESH_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    companion object {
        const val REFRESH_WORK = "idt_status_refresh"
    }

    private fun buildViews(context: Context, statusText: String): RemoteViews {
        return RemoteViews(context.packageName, R.layout.status_widget).apply {
            setTextViewText(R.id.tvStatus, statusText)
        }
    }
}
