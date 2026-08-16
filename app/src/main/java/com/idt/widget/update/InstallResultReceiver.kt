package com.idt.widget.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.idt.widget.BuildConfig
import com.idt.widget.R

/**
 * Info de atualização disponível (para persistência e UI).
 */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val changelog: String,
    val timestamp: Long,
) {
    fun isNewerThan(currentName: String, currentCode: Int): Boolean =
        versionCode > currentCode && versionName != currentName
}

/**
 * Recebe resultado do commit do PackageInstaller e gerencia pós-instalação.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val ok = intent.getBooleanExtra(EXTRA_OK, false)
        val version = intent.getStringExtra(EXTRA_VERSION) ?: ""
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""

        android.util.Log.d("InstallResult", "status=$status ok=$ok version=$version msg=$message")

        when {
            ok && status == PackageInstaller.STATUS_SUCCESS -> {
                notify(context,
                    "✅ Atualização $version instalada",
                    "O app foi atualizado. Reabra para usar a nova versão."
                )
                clearUpdateAvailable(context)
            }
            status == PackageInstaller.STATUS_FAILURE -> {
                notify(context,
                    "❌ Falha ao instalar $version",
                    "Não foi possível instalar: $message. Tente novamente."
                )
            }
            status == PackageInstaller.STATUS_FAILURE_BLOCKED -> {
                notify(context,
                    "🔒 Instalação bloqueada",
                    "Permita instalação de fontes desconhecidas nas configurações."
                )
                openInstallPermissionSettings(context)
            }
            else -> {
                notify(context,
                    "⚠️ Status desconhecido ($status)",
                    message.ifBlank { "Verifique as notificações do sistema." }
                )
            }
        }
    }

    private fun notify(context: Context, title: String, text: String) {
        createChannel(context)
        val notif = NotificationCompat.Builder(context, CHANNEL_UPDATE)
            .setSmallIcon(R.drawable.ic_info)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SYSTEM)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notif)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = android.app.NotificationChannel(
                CHANNEL_UPDATE,
                "Atualizações IDT Status",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações de atualização automática do app"
            }
            context.getSystemService(android.app.NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun clearUpdateAvailable(context: Context) {
        val prefs = context.getSharedPreferences("idt_update", Context.MODE_PRIVATE)
        prefs.edit().remove("update_available").apply()
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(android.net.Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    companion object {
        const val CHANNEL_UPDATE = "idt_updates"
        const val ACTION = "com.idt.widget.INSTALL_RESULT"
        const val EXTRA_OK = "ok"
        const val EXTRA_VERSION = "version"
        private const val NOTIFICATION_ID = 3000

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

        /** Marca que há update disponível (usado para mostrar banner). */
        fun markUpdateAvailable(context: Context, info: UpdateInfo) {
            val prefs = context.getSharedPreferences("idt_update", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("update_available", true)
                .putString("update_version_name", info.versionName)
                .putInt("update_version_code", info.versionCode)
                .putString("update_apk_url", info.apkUrl)
                .putString("update_changelog", info.changelog)
                .putLong("update_timestamp", info.timestamp)
                .apply()
        }

        /** Verifica se há update marcado. */
        fun isUpdateAvailable(context: Context): Boolean {
            val prefs = context.getSharedPreferences("idt_update", Context.MODE_PRIVATE)
            return prefs.getBoolean("update_available", false)
        }

        /** Obtém info do update marcado. */
        fun getUpdateInfo(context: Context): UpdateInfo? {
            val prefs = context.getSharedPreferences("idt_update", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("update_available", false)) return null
            return UpdateInfo(
                versionName = prefs.getString("update_version_name", "") ?: "",
                versionCode = prefs.getInt("update_version_code", 0),
                apkUrl = prefs.getString("update_apk_url", "") ?: "",
                changelog = prefs.getString("update_changelog", "") ?: "",
                timestamp = prefs.getLong("update_timestamp", 0L),
            )
        }

        /** Limpa flag de update (após instalação bem-sucedida ou dismiss). */
        fun clearUpdateFlag(context: Context) {
            val prefs = context.getSharedPreferences("idt_update", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
        }
    }
}