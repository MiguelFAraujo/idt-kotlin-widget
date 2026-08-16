package com.idt.widget.widget

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.idt.widget.IDTApplication
import com.idt.widget.MainActivity
import com.idt.widget.R
import com.idt.widget.util.AlertNotifier
import com.idt.widget.util.NetworkSpeedMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Serviço de primeiro plano que mantém o widget vivo: a cada segundo relê o
 * cache e atualiza o texto (ticker de velocidade + tempo desde o último check),
 * e a cada [CHECK_INTERVAL_MS] executa um check real nos endpoints.
 *
 * Necessário porque o WorkManager tem mínimo de 15 minutos para trabalho
 * periódico — insuficiente para um widget "ao vivo".
 */
class WidgetLiveService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickerJob: Job? = null
    private var checkJob: Job? = null

    private var lastCheckAt: Long = System.currentTimeMillis()
    private var lastResults: List<com.idt.widget.data.model.ServiceCheckResult> = emptyList()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AlertNotifier.ensureChannel(this)
        startForeground(NOTIF_ID, buildNotification())
        lastResults = StatusData.read(this).results
        startTicker()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun startTicker() {
        tickerJob?.cancel()
        checkJob?.cancel()

        val manager = AppWidgetManager.getInstance(this)

        // Tick de 1s: atualiza tempo desde o último check + velocidade de rede
        tickerJob = scope.launch {
            while (isActive) {
                // Re-lê os widgets a cada tick para incluir widgets adicionados depois
                val ids = manager.getAppWidgetIds(
                    ComponentName(this@WidgetLiveService, StatusWidgetProvider::class.java)
                )
                val since = System.currentTimeMillis() - lastCheckAt
                val speed = NetworkSpeedMonitor.sample()
                ids.forEach { id ->
                    val views = baseViews(id)
                    views.setTextViewText(
                        R.id.tvTicker,
                        "⏱ há ${since / 1000}s  ⬇ ${NetworkSpeedMonitor.format(speed.rxBytesPerSec)}  ⬆ ${NetworkSpeedMonitor.format(speed.txBytesPerSec)}"
                    )
                    manager.updateAppWidget(id, views)
                }
                delay(1000)
            }
        }

        // Check real periódico (cada 15s) com atualização suave do status
        checkJob = scope.launch {
            while (isActive) {
                val app = applicationContext as IDTApplication
                val endpoints = app.container.serviceRepository.getEndpoints().filter { it.enabled }
                if (endpoints.isNotEmpty()) {
                    val results = endpoints.map { async { app.container.serviceRepository.checkService(it) } }.awaitAll()
                    lastResults = results
                    lastCheckAt = System.currentTimeMillis()
                    StatusData.write(this@WidgetLiveService, results, lastCheckAt)
                    AlertNotifier.ensureChannel(this@WidgetLiveService)
                    AlertNotifier.onCheckComplete(this@WidgetLiveService, results)
                    updateStatusViews(results)
                }
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    private fun baseViews(widgetId: Int): RemoteViews =
        RemoteViews(packageName, R.layout.status_widget).apply {
            setTextViewText(R.id.tvTitle, "IDT Lab · ${lastResults.count { it.ok }}/${lastResults.size}")

            val openApp = PendingIntent.getActivity(
                this@WidgetLiveService, 0,
                Intent(this@WidgetLiveService, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            setOnClickPendingIntent(R.id.widget_root, openApp)
        }

    private fun updateStatusViews(results: List<com.idt.widget.data.model.ServiceCheckResult>) {
        val manager = AppWidgetManager.getInstance(this)
        val ids = manager.getAppWidgetIds(
            ComponentName(this, StatusWidgetProvider::class.java)
        )
        val text = StatusWidgetProvider.statusText(results)
        ids.forEach { id ->
            val views = baseViews(id)
            views.setTextViewText(R.id.tvStatus, text)
            manager.updateAppWidget(id, views)
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, AlertNotifier.CHANNEL_ALERTS)
            .setContentTitle("IDT Widget ao vivo")
            .setContentText("Atualizando status em tempo real")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        tickerJob?.cancel()
        checkJob?.cancel()
        scope.cancel()
    }

    companion object {
        const val NOTIF_ID = 42
        const val ACTION_STOP = "com.idt.widget.WIDGET_LIVE_STOP"
        const val CHECK_INTERVAL_MS = 15_000L

        fun start(context: Context) {
            try {
                val intent = Intent(context, WidgetLiveService::class.java)
                context.startForegroundService(intent)
            } catch (e: Exception) {
                // Em Android 12+ o background pode negar (widget adicionado pela launcher):
                // mesmo assim garante um check real imediato via WorkManager.
                try {
                    WidgetScheduler.refreshNow(context)
                } catch (_: Exception) {
                }
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, WidgetLiveService::class.java).setAction(ACTION_STOP)
                context.startService(intent)
            } catch (e: Exception) {
            }
        }
    }
}
