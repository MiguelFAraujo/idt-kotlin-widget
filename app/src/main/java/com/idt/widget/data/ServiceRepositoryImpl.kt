package com.idt.widget.data

import android.content.Context
import com.idt.widget.data.local.ConfigDataSource
import com.idt.widget.data.model.ServiceCheckResult
import com.idt.widget.data.model.ServiceEndpoint
import com.idt.widget.data.remote.DiagnosticsTool
import com.idt.widget.data.remote.EndpointPolicy
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

/**
 * Repositório de serviços.
 *
 * Fonte única de verdade: o host que o usuário configurou ([EndpointPolicy.hostFromUrl]
 * sobre [ConfigDataSource] serverUrl). Nunca inventa estado:
 *  - sem host configurado -> lista vazia (nada é verificado contra endereço adivinhado)
 *  - endpoints persistidos de outro host -> descartados como obsoletos
 *  - host mudou -> [resyncWithConfiguredHost] faz um SCAN REAL de rede no novo host
 */
class ServiceRepositoryImpl(
    context: Context,
    private val config: ConfigDataSource,
) : ServiceRepository {

    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences("idt_endpoints", Context.MODE_PRIVATE)
    private val endpointsFlow = MutableStateFlow(loadEndpoints())

    override fun observeEndpoints(): Flow<List<ServiceEndpoint>> = endpointsFlow.asStateFlow()

    override suspend fun getEndpoints(): List<ServiceEndpoint> = withContext(Dispatchers.IO) {
        // Revalida contra o host corrente a cada leitura: configuração mudou => obsoleto vira vazio
        val host = configuredHost()
        if (host.isBlank()) emptyList() else EndpointPolicy.filterStale(endpointsFlow.value, host)
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
            defaultUser = cfg.serverUser,
            defaultPass = cfg.serverPass,
        )
        return checker.check(endpoint)
    }

    /**
     * Reescaneia o host configurado pelo usuário com conexões TCP REAIS e persiste
     * somente as portas que responderam. Retorna a quantidade de serviços encontrados.
     */
    override suspend fun resyncWithConfiguredHost(): Int = withContext(Dispatchers.IO) {
        val host = configuredHost()
        if (host.isBlank()) return@withContext 0
        val openPorts = DiagnosticsTool.scanPorts(host, ServiceCatalog.knownPorts, timeoutMs = 1200, concurrency = 64)
        val found = ServiceCatalog.endpointsForHost(host).filter { it.port in openPorts }
        persist(found)
        found.size
    }

    private fun configuredHost(): String {
        val cfg = config.current()
        return EndpointPolicy.hostFromUrl(cfg.serverUrl)
    }

    private fun persist(endpoints: List<ServiceEndpoint>) {
        val arr = JSONArray()
        endpoints.forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("name", e.name)
                    .put("host", e.host)
                    .put("port", e.port)
                    .put("enabled", e.enabled)
                    .put("requireAuth", e.requireAuth)
                    .put("authType", e.authType.name)
                    .put("username", e.username)
                    .put("password", e.password)
                    .put("bearerToken", e.bearerToken)
                    .put("xIdtToken", e.xIdtToken)
                    .put("useFingerprint", e.useFingerprint)
            )
        }
        prefs.edit()
            .putString("endpoints_json", arr.toString())
            .putString("synced_host", configuredHost())
            .apply()
        endpointsFlow.value = endpoints
    }

    private fun loadEndpoints(): List<ServiceEndpoint> {
        val raw = prefs.getString("endpoints_json", null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        val host = configuredHost()
        if (host.isBlank()) return emptyList()
        val parsed = try {
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
                    authType = ServiceEndpoint.AuthType.valueOf(o.optString("authType", "NONE")),
                    username = o.optString("username", ""),
                    password = o.optString("password", ""),
                    bearerToken = o.optString("bearerToken", ""),
                    xIdtToken = o.optString("xIdtToken", ""),
                    useFingerprint = o.optBoolean("useFingerprint", false),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
        return EndpointPolicy.filterStale(parsed, host)
    }

    companion object {
        private const val TAG = "ServiceRepository"
    }
}
