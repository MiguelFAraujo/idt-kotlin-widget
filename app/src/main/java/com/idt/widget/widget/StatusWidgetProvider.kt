package com.idt.widget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.idt.widget.MainActivity
import com.idt.widget.R
import com.idt.widget.data.model.ServiceCheckResult
import com.idt.widget.widget.StatusData

class StatusWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
        // Ao adicionar/atualizar o widget, dispara um check real imediato
        WidgetScheduler.refreshNow(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> {
                // Tap no widget = check real, não apenas re-ler cache velho
                WidgetScheduler.refreshNow(context)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.idt.widget.ACTION_REFRESH"

        /** Grava resultados frescos no cache do widget e atualiza os widgets na tela. */
        fun syncCache(context: Context, results: List<ServiceCheckResult>) {
            StatusData.write(context, results, System.currentTimeMillis())
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(
                ComponentName(context, StatusWidgetProvider::class.java)
            )
            for (id in ids) updateWidget(context, appWidgetManager, id)
        }

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val data = StatusData.read(context)
            val views = RemoteViews(context.packageName, R.layout.status_widget).apply {
                setTextViewText(R.id.tvTitle, "IDT Lab")
                val statusText = if (data.results.isNotEmpty()) {
                    val up = data.results.count { it.ok }
                    val down = data.results.size - up
                    val online = data.results.filter { it.ok }
                        .take(3).joinToString("") { " • ${it.endpoint.name}" }
                    val offline = data.results.filterNot { it.ok }
                        .take(2).joinToString("") { " ⛔${it.endpoint.name}" }
                    "${up}/${data.results.size} online" +
                        online +
                        offline +
                        if (down > 2) " ⛔+${down - 2}" else ""
                } else {
                    "Sem endpoints configurados"
                }
                setTextViewText(R.id.tvStatus, statusText)

                val openApp = PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                setOnClickPendingIntent(R.id.widget_root, openApp)

                val refresh = PendingIntent.getBroadcast(
                    context, appWidgetId,
                    Intent(context, StatusWidgetProvider::class.java).setAction(ACTION_REFRESH),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                setOnClickPendingIntent(R.id.tvStatus, refresh)
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
