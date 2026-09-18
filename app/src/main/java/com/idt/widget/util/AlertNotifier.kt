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

/**
 * Alertas autônomos consolidados: independente de quantos serviços caírem,
 * emite uma ÚNICA notificação listando todos os fora do ar (e uma única
 * notificação quando todos voltam). Evita spam de uma notificação por porta.
 */
object AlertNotifier {
    const val CHANNEL_ALERTS = "idt_alerts"

    /** ID fixo: a notificação de alerta é sempre a mesma e é substituída. */
    const val NOTIF_ALERT = 2001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ALERTS) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ALERTS,
                        "Alertas de serviço",
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply { description = "Notifica quando um ou mais serviços caem ou voltam" }
                )
            }
        }
    }

    fun onCheckComplete(context: Context, results: List<ServiceCheckResult>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        // Respeita a opção "Mostrar notificações" das Configurações
        val enabled = context.getSharedPreferences("idt_config", Context.MODE_PRIVATE)
            .getBoolean("show_notifications", false)
        if (!enabled) return

        val down = results.filter { !it.ok }.sortedBy { it.endpoint.name }
        val downKey = down.joinToString(",") { it.endpoint.id }

        val prefs = context.getSharedPreferences("idt_alerts", Context.MODE_PRIVATE)
        val prevKey = prefs.getString("down_key", "") ?: ""

        // Nada mudou desde o último ciclo: não re-notifica
        if (downKey == prevKey) return
        prefs.edit().putString("down_key", downKey).apply()

        if (down.isEmpty()) {
            if (prevKey.isNotEmpty()) notifyAllUp(context, results)
        } else {
            notifyDownList(context, down)
        }
    }

    /** Uma única notificação consolidada com todos os serviços fora do ar. */
    private fun notifyDownList(context: Context, down: List<ServiceCheckResult>) {
        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val count = down.size
        val title = if (count == 1) "⚠ 1 serviço fora do ar" else "⚠ $count serviços fora do ar"
        val body = down.joinToString("\n") { r ->
            "• ${r.endpoint.name} (${r.endpoint.host}:${r.endpoint.port}) — ${r.message}"
        }
        val notif = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_info)
            .setContentTitle(title)
            .setContentText(if (count == 1) down[0].endpoint.name else "Toque para ver os serviços afetados")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ALERT, notif)
    }

    /** Uma única notificação quando todos os serviços voltam. */
    private fun notifyAllUp(context: Context, results: List<ServiceCheckResult>) {
        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val total = results.count { it.ok }
        val notif = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_info)
            .setContentTitle("✅ Todos os serviços operando")
            .setContentText("$total/${results.size} serviços online — tudo normal")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ALERT, notif)
    }
}