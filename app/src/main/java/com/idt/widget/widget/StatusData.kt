package com.idt.widget.widget

import android.content.Context
import com.idt.widget.data.model.ServiceCheckResult
import org.json.JSONObject

/**
 * Cache síncrono do último resultado de verificação, gravado pelo Worker
 * e lido pelo WidgetProvider (que não pode executar corrotinas no onUpdate).
 */
data class StatusData(
    val results: List<ServiceCheckResult> = emptyList(),
    val timestamp: Long = 0L,
) {
    companion object {
        private const val PREFS = "idt_widget_cache"
        private const val KEY_JSON = "last_status_json"

        fun read(context: Context): StatusData {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_JSON, null) ?: return StatusData()
            return try {
                val o = JSONObject(raw)
                val arr = o.getJSONArray("results")
                val results = (0 until arr.length()).map { i ->
                    val e = arr.getJSONObject(i)
                    val ep = e.getJSONObject("endpoint")
                    ServiceCheckResult(
                        endpoint = com.idt.widget.data.model.ServiceEndpoint(
                            id = ep.optString("id"),
                            name = ep.optString("name"),
                            host = ep.optString("host"),
                            port = ep.optInt("port"),
                            enabled = ep.optBoolean("enabled", true),
                            requireAuth = ep.optBoolean("requireAuth", false),
                        ),
                        ok = e.optBoolean("ok"),
                        roundUsed = e.optString("round"),
                        latencyMs = e.optLong("latency"),
                        message = e.optString("message"),
                    )
                }
                StatusData(results, o.optLong("ts"))
            } catch (e: Exception) {
                StatusData()
            }
        }

        fun write(context: Context, results: List<ServiceCheckResult>, ts: Long) {
            val arr = org.json.JSONArray()
            results.forEach { r ->
                val ep = JSONObject()
                    .put("id", r.endpoint.id)
                    .put("name", r.endpoint.name)
                    .put("host", r.endpoint.host)
                    .put("port", r.endpoint.port)
                    .put("enabled", r.endpoint.enabled)
                    .put("requireAuth", r.endpoint.requireAuth)
                arr.put(
                    JSONObject()
                        .put("endpoint", ep)
                        .put("ok", r.ok)
                        .put("round", r.roundUsed)
                        .put("latency", r.latencyMs)
                        .put("message", r.message)
                )
            }
            val o = JSONObject().put("results", arr).put("ts", ts)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_JSON, o.toString()).apply()
        }
    }
}
