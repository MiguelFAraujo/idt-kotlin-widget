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
import com.idt.widget.R
import com.idt.widget.update.UpdateNowReceiver

object NotificationHelper {
    const val CHANNEL_UPDATE = "idt_updates"
    const val NOTIF_UPDATE = 1001
    private const val CHANNEL_ID = CHANNEL_UPDATE

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_UPDATE,
                    "Atualizações do IDT Status",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "Avisa quando existe uma nova versão do app" }
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    "idt_update_progress",
                    "Progresso da atualização",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "Progresso do download e instalação" }
            )
        }
    }

    fun notifyUpdate(context: Context, versionName: String, apkUrl: String, changelog: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val pendingUpdateNow = UpdateNowReceiver.pendingIntent(context, apkUrl, versionName)

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_info)
            .setContentTitle("Nova versão $versionName")
            .setContentText("Uma atualização do IDT Status está disponível. Toque em Baixar agora.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                (if (changelog.isNotBlank()) "Novidades:\n$changelog\n\n" else "") +
                    "Toque em \"Baixar agora\" para instalar automaticamente."
            ))
            .addAction(R.drawable.ic_info, "Baixar agora", pendingUpdateNow)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_UPDATE, notif)
    }
}
