package com.idt.widget

import android.content.Context
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket

class StatusWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val client = OkHttpClient()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val services = listOf(
                Service("OmniRoute", "127.0.0.1", 20128, "http"),
                Service("Ollama", "127.0.0.1", 11434, "http"),
                Service("Prometheus", "127.0.0.1", 9091, "http"),
                Service("Netdata", "127.0.0.1", 19999, "http"),
                Service("n8n", "127.0.0.1", 5678, "http"),
                Service("ntfy", "127.0.0.1", 2586, "http")
            )

            val results = services.map { it.check() }
            val overall = if (results.all { r -> r.ok }) "OK" else "DEGRADADO"
            val text = results.joinToString("\n") { r -> "${r.name}: ${if (r.ok) "OK" else "DOWN"}" }

            val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
            val views = buildViews(applicationContext, "$overall\n$text")
            appWidgetManager.partiallyUpdateAppWidget(
                AppWidgetManager.INVALID_APPWIDGET_ID,
                views
            )

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun buildViews(context: Context, statusText: String): RemoteViews {
        return RemoteViews(context.packageName, R.layout.status_widget).apply {
            setTextViewText(R.id.tvStatus, statusText)
        }
    }

    data class Service(val name: String, val host: String, val port: Int, val scheme: String) {
        data class CheckResult(val name: String, val ok: Boolean)
        fun check(): CheckResult {
            return try {
                Socket().use { it.connect(InetSocketAddress(host, port), 1500) }
                CheckResult(name, true)
            } catch (e: Exception) {
                CheckResult(name, false)
            }
        }
    }
}
