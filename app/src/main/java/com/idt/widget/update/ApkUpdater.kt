package com.idt.widget.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
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
 * Atualizador completo: baixa o APK da URL do manifesto e instala via
 * PackageInstaller (Android instala sobre a versão atual preservando dados).
 */
class ApkUpdater(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val downloadDir: File
        get() = File(context.cacheDir, "apk-downloads").apply { mkdirs() }

    /** Baixa o APK. Retorna o arquivo baixado ou lança [IOException]. */
    suspend fun download(apkUrl: String): File = withContext(Dispatchers.IO) {
        downloadDir.listFiles()?.forEach { it.delete() }
        val target = File(downloadDir, "update-${BuildConfig.VERSION_CODE + 1}.apk")
        val request = Request.Builder().url(apkUrl).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} ao baixar APK")
            resp.body?.byteStream()?.use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            } ?: throw IOException("Corpo vazio no download")
        }
        target
    }

    /**
     * Instala o APK via PackageInstaller. Retorna true se iniciou o fluxo.
     * Pode exigir confirmação do usuário na tela de instalação do sistema.
     */
    suspend fun install(apkFile: File, versionName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Fallback simples: FileProvider + ACTION_VIEW para a tela de instalação
            val uri: Uri = FileProvider.getUriForFile(
                context, "${BuildConfig.APPLICATION_ID}.fileprovider", apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Instala via PackageInstaller API (mais confiável). */
    suspend fun installViaPackageInstaller(apkFile: File, versionName: String): Boolean =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return@withContext false
            try {
                val pm = context.packageManager
                val installer = pm.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                params.setAppPackageName(BuildConfig.APPLICATION_ID)

                val sessionId = installer.createSession(params)
                val session = installer.openSession(sessionId)
                apkFile.inputStream().use { input ->
                    session.openWrite("update.apk", 0, apkFile.length()).use { out ->
                        input.copyTo(out, DEFAULT_CHUNK)
                    }
                }
                session.commit(InstallResultReceiver.resultPendingIntent(context, versionName, ok = true).intentSender)
                session.close()
                true
            } catch (e: Exception) {
                false
            }
        }

    companion object {
        private const val DEFAULT_CHUNK = 8192
    }
}
