package com.idt.widget.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.idt.widget.R

/**
 * Recebe o resultado do commit do PackageInstaller e notifica o usuário.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val ok = intent.getBooleanExtra(EXTRA_OK, false)
        val version = intent.getStringExtra(EXTRA_VERSION) ?: ""
        if (ok) {
            notify(context, "✅ Atualização para $version instalada", "O app será atualizado. Reabra quando concluir.")
        } else {
            notify(context, "❌ Falha ao instalar $version", "A instalação não foi concluída. Tente novamente.")
        }
    }

    private fun notify(context: Context, title: String, text: String) {
        val notif = NotificationCompat.Builder(context, CHANNEL_UPDATE)
            .setSmallIcon(R.drawable.ic_info)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(3000, notif)
    }

    companion object {
        const val CHANNEL_UPDATE = "idt_updates"
        const val ACTION = "com.idt.widget.INSTALL_RESULT"
        const val EXTRA_OK = "ok"
        const val EXTRA_VERSION = "version"

        fun resultPendingIntent(context: Context, version: String, ok: Boolean): PendingIntent {
            val intent = Intent(context, InstallResultReceiver::class.java)
                .setAction(ACTION)
                .putExtra(EXTRA_OK, ok)
                .putExtra(EXTRA_VERSION, version)
            return PendingIntent.getBroadcast(
                context,
                if (ok) 100 else 101,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
