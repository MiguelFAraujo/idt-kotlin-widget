package com.idt.widget.data

import android.content.Context
import com.idt.widget.data.local.ConfigDataSource
import com.idt.widget.data.model.ServiceCheckResult
import com.idt.widget.data.model.ServiceEndpoint
import com.idt.widget.data.remote.ServiceCatalog
import com.idt.widget.data.remote.ServiceChecker
import com.idt.widget.domain.repository.ServiceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class ServiceRepositoryImpl(
    context: Context,
    private val config: ConfigDataSource,
) : ServiceRepository {

    private val prefs = context.getSharedPreferences("idt_endpoints", Context.MODE_PRIVATE)
    private val endpointsFlow = MutableStateFlow(loadEndpoints())

    override fun observeEndpoints(): Flow<List<ServiceEndpoint>> = endpointsFlow.asStateFlow()

    override suspend fun getEndpoints(): List<ServiceEndpoint> = withContext(Dispatchers.IO) {
        endpointsFlow.value
    }

    override suspend fun addEndpoint(endpoint: ServiceEndpoint) {
        val next = endpointsFlow.value + endpoint
        persist(next)
    }

    override suspend fun updateEndpoint(endpoint: ServiceEndpoint) {
        val next = endpointsFlow.value.map { if (it.id == endpoint.id) endpoint else it }
        persist(next)
    }

    override suspend fun deleteEndpoint(id: String) {
        val next = endpointsFlow.value.filterNot { it.id == id }
        persist(next)
    }

    override suspend fun checkService(endpoint: ServiceEndpoint): ServiceCheckResult {
        val cfg = config.getConfig()
        val checker = ServiceChecker(
            serverUser = cfg.serverUser,
            serverPass = cfg.serverPass,
        )
        return checker.check(endpoint)
    }

    private fun persist(endpoints: List<ServiceEndpoint>) {
        val arr = org.json.JSONArray()
        endpoints.forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("name", e.name)
                    .put("host", e.host)
                    .put("port", e.port)
                    .put("enabled", e.enabled)
                    .put("requireAuth", e.requireAuth)
            )
        }
        prefs.edit().putString("endpoints_json", arr.toString()).apply()
        endpointsFlow.value = endpoints
    }

    private fun loadEndpoints(): List<ServiceEndpoint> {
        val raw = prefs.getString("endpoints_json", null)
        if (raw.isNullOrBlank()) return ServiceCatalog.defaultServices
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ServiceEndpoint(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    host = o.optString("host"),
                    port = o.optInt("port"),
                    enabled = o.optBoolean("enabled", true),
                    requireAuth = o.optBoolean("requireAuth", false),
                )
            }
        } catch (e: Exception) {
            ServiceCatalog.defaultServices
        }
    }

    companion object {
        private const val TAG = "ServiceRepository"
    }
}
