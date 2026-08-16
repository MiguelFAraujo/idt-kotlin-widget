package com.idt.widget.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.idt.widget.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Recebe o toque em "Baixar agora" na notificação de update e executa
 * download + instalação em background, mostrando progresso na notificação.
 */
class UpdateNowReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val apkUrl = intent.getStringExtra(EXTRA_URL) ?: return
        val version = intent.getStringExtra(EXTRA_VERSION) ?: ""
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        scope.launch {
            try {
                updateNotification(appContext, 0)
                val updater = ApkUpdater(appContext)
                val result = updater.downloadAndInstall(apkUrl)
                updateNotification(appContext, 100)
                notifyResult(appContext, version, result.success)
            } catch (e: Exception) {
                notifyResult(appContext, version, ok = false)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun updateNotification(context: Context, progress: Int) {
        val notif = NotificationCompat.Builder(context, "idt_update_progress")
            .setSmallIcon(R.drawable.ic_info)
            .setContentTitle("Baixando atualização...")
            .setContentText("$progress%")
            .setProgress(100, progress, progress == 0)
            .setOngoing(progress < 100)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        NotificationManagerCompat.from(context).notify(4001, notif)
    }

    private fun notifyResult(context: Context, version: String, ok: Boolean) {
        val notif = NotificationCompat.Builder(context, "idt_update_progress")
            .setSmallIcon(R.drawable.ic_info)
            .setContentTitle(if (ok) "Atualização para $version pronta" else "Falha na atualização")
            .setContentText(
                if (ok) "Confirmado pela tela de instalação do sistema."
                else "Não foi possível instalar. Confira a conexão e tente pelo painel."
            )
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(4002, notif)
    }

    companion object {
        const val ACTION = "com.idt.widget.UPDATE_NOW"
        const val EXTRA_URL = "url"
        const val EXTRA_VERSION = "version"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun pendingIntent(context: Context, apkUrl: String, version: String): PendingIntent {
            val intent = Intent(context, UpdateNowReceiver::class.java)
                .setAction(ACTION)
                .putExtra(EXTRA_URL, apkUrl)
                .putExtra(EXTRA_VERSION, version)
            return PendingIntent.getBroadcast(
                context, 200, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /** Dispara o download+instalação imediatamente (mesmo fluxo da notificação). */
        fun trigger(context: Context, apkUrl: String, version: String) {
            val intent = Intent(context, UpdateNowReceiver::class.java)
                .setAction(ACTION)
                .putExtra(EXTRA_URL, apkUrl)
                .putExtra(EXTRA_VERSION, version)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            context.sendBroadcast(intent)
        }
    }
}