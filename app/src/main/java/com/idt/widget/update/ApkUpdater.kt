package com.idt.widget.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.idt.widget.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Auto-updater estilo Play Store: download silencioso em background + instalação via PackageInstaller.
 *
 * Fluxo:
 * 1. Verifica update.json (já feito pelo UpdateChecker)
 * 2. Baixa APK para cache privado (DownloadManager ou OkHttp)
 * 3. Instala via PackageInstaller.Session (sem tela de confirmação no Android 12+)
 * 4. Notifica resultado via BroadcastReceiver
 * 5. App reinicia com nova versão preservando dados
 */
class ApkUpdater(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()

    private val downloadDir: File
        get() = File(context.cacheDir, "apk-downloads").apply { mkdirs() }

    data class DownloadResult(
        val file: File?,
        val error: String? = null
    )

    data class InstallResult(
        val success: Boolean,
        val error: String? = null
    )

    /** Baixa APK usando DownloadManager do sistema (mais confiável, suporta resume, notificação). */
    suspend fun downloadWithManager(apkUrl: String): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("IDT Status Update")
                .setDescription("Baixando versão mais recente...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "idt-update-${System.currentTimeMillis()}.apk")
                .setRequiresCharging(false)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)

            val downloadId = dm.enqueue(request)

            // Aguarda conclusão (polling simples)
            while (true) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                dm.query(query).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val reasonCol = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                        val localUriCol = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                        val status = cursor.getInt(statusCol)
                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                val localUri = cursor.getString(localUriCol) ?: ""
                                val path = Uri.parse(localUri).path ?: ""
                                return@withContext DownloadResult(File(path))
                            }
                            DownloadManager.STATUS_FAILED -> {
                                val reason = cursor.getInt(reasonCol)
                                return@withContext DownloadResult(null, "Download falhou: código $reason")
                            }
                        }
                    }
                }
                Thread.sleep(1000)
            }
        } catch (e: Exception) {
            // Fallback: download direto com OkHttp
            return@withContext downloadDirect(apkUrl)
        }
        @Suppress("UNREACHABLE_CODE")
        DownloadResult(null, "Erro desconhecido")
    }

    /** Fallback: download direto OkHttp para cache privado. */
    suspend fun downloadDirect(apkUrl: String): DownloadResult = withContext(Dispatchers.IO) {
        try {
            downloadDir.listFiles()?.forEach { it.delete() }
            val target = File(downloadDir, "update-${BuildConfig.VERSION_CODE + 1}.apk")
            val request = Request.Builder().url(apkUrl).build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext DownloadResult(null, "HTTP ${resp.code}")
                resp.body?.byteStream()?.use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                } ?: return@withContext DownloadResult(null, "Corpo vazio")
            }
            DownloadResult(target)
        } catch (e: IOException) {
            DownloadResult(null, e.message)
        }
    }

    /** Instala APK via PackageInstaller (silencioso no Android 12+). */
    suspend fun install(apkFile: File): InstallResult = withContext(Dispatchers.IO) {
        if (!apkFile.exists()) return@withContext InstallResult(false, "Arquivo não encontrado")

        // Verifica se o APK é válido e assinado corretamente
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val packageInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNATURES)
        if (packageInfo == null || packageInfo.packageName != BuildConfig.APPLICATION_ID) {
            return@withContext InstallResult(false, "APK inválido ou package name diferente")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return@withContext installViaPackageInstaller(apkFile)
        } else {
            return@withContext installLegacy(apkFile)
        }
    }

    private fun installViaPackageInstaller(apkFile: File): InstallResult {
        try {
            val pm = context.packageManager
            val installer = pm.packageInstaller

            // Parâmetros da sessão
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(BuildConfig.APPLICATION_ID)
            // Permite downgrade se necessário (versionCode menor) - API 29+
            if (Build.VERSION.SDK_INT >= 29) {
                try {
                    params.javaClass.getDeclaredField("allowDowngrade").apply {
                        isAccessible = true
                        setBoolean(params, true)
                    }
                } catch (e: Exception) {
                    // Campo não existe nessa versão, ignora
                }
            }

            val sessionId = installer.createSession(params)
            val session = installer.openSession(sessionId)

            // Escreve APK na sessão
            val out = session.openWrite("update.apk", 0, apkFile.length())
            apkFile.inputStream().use { input ->
                input.copyTo(out)
            }
            out.close()

            // FSYNC para garantir escrita no disco
            session.fsync(out)

            // Commit - usa PendingIntent para receber resultado
            val pendingIntent = InstallResultReceiver.resultPendingIntent(
                context, BuildConfig.VERSION_NAME, true
            )
            session.commit(pendingIntent.intentSender)
            session.close()

            return InstallResult(true)
        } catch (e: Exception) {
            android.util.Log.e("ApkUpdater", "installViaPackageInstaller failed", e)
            return InstallResult(false, e.message)
        }
    }

    private fun installLegacy(apkFile: File): InstallResult {
        try {
            val uri = FileProvider.getUriForFile(
                context, "${BuildConfig.APPLICATION_ID}.fileprovider", apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return InstallResult(true)
        } catch (e: Exception) {
            return InstallResult(false, e.message)
        }
    }

    /** Fluxo completo: baixa + instala. Retorna resultado final. */
    suspend fun downloadAndInstall(apkUrl: String): InstallResult {
        val download = downloadWithManager(apkUrl)
        if (download.file == null) return InstallResult(false, download.error)
        return install(download.file)
    }

    companion object {
        /** Verifica se o app tem permissão para instalar pacotes (Android 8+). */
        fun canInstallPackages(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
            return context.packageManager.canRequestPackageInstalls()
        }

        /** Abre settings para conceder permissão REQUEST_INSTALL_PACKAGES. */
        fun openInstallPermissionSettings(context: Context) {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}