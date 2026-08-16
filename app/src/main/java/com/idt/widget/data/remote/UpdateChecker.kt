package com.idt.widget.data.remote

import com.idt.widget.BuildConfig
import com.idt.widget.update.InstallResultReceiver
import com.idt.widget.update.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Busca manifesto de versão (update.json) e verifica se há atualização.
 * Integra com InstallResultReceiver para persistir info de update disponível.
 */
class UpdateChecker(
    private val updateUrl: String = DEFAULT_UPDATE_URL,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    suspend fun check(context: android.content.Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(updateUrl).build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                parseAndStore(context, body)
            }
        } catch (e: Exception) {
            android.util.Log.w("UpdateChecker", "Falha ao verificar update", e)
            null
        }
    }

    /** Parse do JSON + armazenamento se for versão mais nova. */
    private fun parseAndStore(context: android.content.Context, body: String): UpdateInfo? {
        return try {
            val o = JSONObject(body)
            val info = UpdateInfo(
                versionName = o.optString("versionName", ""),
                versionCode = o.optInt("versionCode", 0),
                apkUrl = o.optString("apkUrl", ""),
                changelog = o.optString("changelog", ""),
                timestamp = System.currentTimeMillis(),
            )

            // Só considera update se versionCode for maior
            if (info.isNewerThan(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)) {
                InstallResultReceiver.markUpdateAvailable(context, info)
                info
            } else {
                // Versão igual ou menor - limpa flag se existir
                InstallResultReceiver.clearUpdateFlag(context)
                null
            }
        } catch (e: Exception) {
            android.util.Log.w("UpdateChecker", "Erro ao parsear update.json", e)
            null
        }
    }

    /** Parse puro (para testes unitários). */
    fun parse(body: String): UpdateInfo? = try {
        val o = JSONObject(body)
        UpdateInfo(
            versionName = o.optString("versionName", ""),
            versionCode = o.optInt("versionCode", 0),
            apkUrl = o.optString("apkUrl", ""),
            changelog = o.optString("changelog", ""),
            timestamp = System.currentTimeMillis(),
        )
    } catch (e: Exception) {
        null
    }

    companion object {
        const val DEFAULT_UPDATE_URL =
            "https://raw.githubusercontent.com/MiguelFAraujo/idt-kotlin-widget/main/updates/update.json"
    }
}