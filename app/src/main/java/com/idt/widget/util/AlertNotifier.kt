package com.idt.widget.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.idt.widget.MainActivity
import com.idt.widget.R
import com.idt.widget.data.model.ServiceCheckResult
import java.util.concurrent.ConcurrentHashMap

/**
 * Alertas autônomos de mudança de status: notifica quando um serviço
 * cai (transitionedDown) ou volta (transitionedUp), com deduplicação
 * por endpoint para não renotificar em cada ciclo de refresh.
 */
object AlertNotifier {
    const val CHANNEL_ALERTS = "idt_alerts"
    const val NOTIF_BASE = 2000

    private val notifiedStates = ConcurrentHashMap<String, Boolean>()

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ALERTS) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ALERTS,
                        "Alertas de serviço",
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply { description = "Notifica quando um serviço cai ou volta" }
                )
            }
        }
    }

    fun onCheckComplete(context: Context, results: List<ServiceCheckResult>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val prefs = context.getSharedPreferences("idt_alerts", Context.MODE_PRIVATE)

        // Respeita a opção "Mostrar notificações" das Configurações
        val enabled = context.getSharedPreferences("idt_config", Context.MODE_PRIVATE)
            .getBoolean("show_notifications", false)
        if (!enabled) return

        // Estado anterior por endpoint (persistido)
        results.forEach { r ->
            val prev = prefs.getBoolean("state_${r.endpoint.id}", true)
            val now = r.ok
            if (prev && !now) {
                // caiu
                notify(context, r, down = true)
            } else if (!prev && now) {
                // voltou
                notify(context, r, down = false)
            }
            prefs.edit().putBoolean("state_${r.endpoint.id}", now).apply()
        }
    }

    private fun notify(context: Context, result: ServiceCheckResult, down: Boolean) {
        val openApp = PendingIntent.getActivity(
            context, result.endpoint.id.hashCode(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_info)
            .setContentTitle(
                if (down) "⚠ ${result.endpoint.name} caiu"
                else "✅ ${result.endpoint.name} voltou"
            )
            .setContentText(
                if (down) "${result.endpoint.host}:${result.endpoint.port} sem resposta (${result.message})"
                else "Serviço operando novamente (${result.latencyMs}ms)"
            )
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        NotificationManagerCompat.from(context)
            .notify(NOTIF_BASE + result.endpoint.id.hashCode() % 1000, notif)
    }
}
