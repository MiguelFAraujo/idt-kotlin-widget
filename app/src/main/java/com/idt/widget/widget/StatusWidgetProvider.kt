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
        // Widget ao vivo: serviço de primeiro plano mantém atualização por segundo
        WidgetLiveService.start(context)
        // Ao adicionar/atualizar o widget, dispara um check real imediato
        WidgetScheduler.refreshNow(context)
        // Garante o trabalho periódico mesmo se o processo reiniciar
        WidgetScheduler.schedule(context, intervalMinutes = 15)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> {
                // Tap no widget = check real + mantém ticker ao vivo
                WidgetLiveService.start(context)
                WidgetScheduler.refreshNow(context)
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetLiveService.start(context)
        WidgetScheduler.schedule(context, intervalMinutes = 15)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetLiveService.stop(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        // Se não restarem widgets, para o serviço ao vivo
        val manager = AppWidgetManager.getInstance(context)
        val remaining = manager.getAppWidgetIds(
            ComponentName(context, StatusWidgetProvider::class.java)
        )
        if (remaining.isEmpty()) {
            WidgetLiveService.stop(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.idt.widget.ACTION_REFRESH"

        /** Formata o texto do widget. Lógica pura, testável em JVM. */
        fun statusText(results: List<ServiceCheckResult>): String {
            if (results.isEmpty()) return "Sem endpoints configurados"
            val up = results.count { it.ok }
            val down = results.size - up
            val online = results.filter { it.ok }
                .take(3).joinToString("") { " • ${it.endpoint.name}" }
            val offline = results.filterNot { it.ok }
                .take(2).joinToString("") { " ⛔${it.endpoint.name}" }
            return "${up}/${results.size} online" +
                online +
                offline +
                if (down > 2) " ⛔+${down - 2}" else ""
        }

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
                setTextViewText(R.id.tvStatus, if (data.results.isEmpty()) "Carregando..." else statusText(data.results))

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
