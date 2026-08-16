package com.idt.widget.data.remote

import com.idt.widget.data.model.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Busca o manifest de versão (update.json) e decide se existe atualização.
 * URL configurável — default: GitHub Releases (raw) do repositório do projeto.
 */
class UpdateChecker(
    private val updateUrl: String = DEFAULT_UPDATE_URL,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(updateUrl).build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                parse(body)
            }
        } catch (e: Exception) {
            null
        }
    }
    /** Converte o corpo do manifest em [UpdateInfo]. Lógica pura, testável em JVM. */
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
