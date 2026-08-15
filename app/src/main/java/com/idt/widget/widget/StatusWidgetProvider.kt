package com.idt.widget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.idt.widget.IDTApplication
import com.idt.widget.MainActivity
import com.idt.widget.R
import com.idt.widget.widget.StatusData

class StatusWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val ids = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, StatusWidgetProvider::class.java)
                )
                for (id in ids) updateWidget(context, appWidgetManager, id)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.idt.widget.ACTION_REFRESH"

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val data = StatusData.read(context)
            val views = RemoteViews(context.packageName, R.layout.status_widget).apply {
                setTextViewText(R.id.tvTitle, "IDT Lab")
                val statusText = if (data.results.isNotEmpty()) {
                    val up = data.results.count { it.ok }
                    "${up}/${data.results.size} online" + data.results.filter { it.ok }
                        .take(4).joinToString("") { " • ${it.endpoint.name}" }
                } else {
                    "Verificando..."
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
