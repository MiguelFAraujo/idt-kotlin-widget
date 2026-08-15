package com.idt.widget.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.idt.widget.R

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
        }
    }

    fun notifyUpdate(context: Context, versionName: String, apkUrl: String, changelog: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val open = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingOpen = PendingIntent.getActivity(
            context, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_info)
            .setContentTitle("Nova versão $versionName")
            .setContentText("Uma atualização do IDT Status está disponível. Toque para baixar.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                (if (changelog.isNotBlank()) "Novidades:\n$changelog\n\n" else "") +
                    "Toque para baixar o novo APK."
            ))
            .setContentIntent(pendingOpen)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_UPDATE, notif)
    }
}
