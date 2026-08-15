package com.idt.widget.data.history

/**
 * Motor de histórico com janela deslizante (rolling window), 100% puro Kotlin
 * (sem dependência de Android) para ser testável em JVM.
 *
 * Mantém por endpoint uma lista cronológica de amostras [HistorySample] e
 * deriva estatísticas: uptime %, latência média, série para sparkline e
 * estado de transição (caiu/voltou).
 */
data class HistorySample(
    val ts: Long,
    val ok: Boolean,
    val latencyMs: Long,
)

data class EndpointStats(
    val endpointId: String,
    val samples: List<HistorySample>,
    val uptimePercent: Double,
    val avgLatencyMs: Double,
    val lastOk: Boolean,
    val transitionedDown: Boolean,
    val transitionedUp: Boolean,
) {
    val latencySeriesMs: List<Long> get() = samples.map { it.latencyMs }
    val okSeries: List<Boolean> get() = samples.map { it.ok }
}

class HistoryStore(
    private val maxSamplesPerEndpoint: Int = 300,
) {

    private val map = mutableMapOf<String, MutableList<HistorySample>>()

    /** Adiciona uma amostra, trima a janela e retorna as estatísticas resultantes. */
    fun addSample(endpointId: String, ok: Boolean, latencyMs: Long, now: Long): EndpointStats {
        val list = map.getOrPut(endpointId) { mutableListOf() }
        list.add(HistorySample(ts = now, ok = ok, latencyMs = latencyMs))
        trim(list)
        return statsOf(endpointId, list)
    }

    fun addSample(endpointId: String, ok: Boolean, latencyMs: Long): EndpointStats =
        addSample(endpointId, ok, latencyMs, System.currentTimeMillis())

    fun historyFor(endpointId: String): EndpointStats =
        statsOf(endpointId, map[endpointId] ?: emptyList())

    fun allHistories(): List<EndpointStats> =
        map.map { (id, list) -> statsOf(id, list) }

    fun clear() = map.clear()

    fun size() = map.size

    fun isEmpty() = map.isEmpty()

    fun toJson(): String {
        val sb = StringBuilder("{")
        var first = true
        for ((id, list) in map) {
            if (!first) sb.append(",")
            first = false
            sb.append("\"").append(escape(id)).append("\":")
            sb.append("[")
            list.forEachIndexed { i, s ->
                if (i > 0) sb.append(",")
                sb.append("[").append(s.ts).append(",")
                    .append(if (s.ok) 1 else 0).append(",")
                    .append(s.latencyMs).append("]")
            }
            sb.append("]")
        }
        return sb.append("}").toString()
    }

    fun fromJson(raw: String) {
        map.clear()
        val arrPattern = Regex("\"([^\"]*)\"\\s*:\\s*\\[")
        val nodePattern = Regex("\\[(-?\\d+),([01]),(-?\\d+)\\]")
        val keys = arrPattern.findAll(raw).toList()
        val nodes = nodePattern.findAll(raw).toList()
        if (keys.isEmpty()) return
        var nodeIdx = 0
        for ((idx, k) in keys.withIndex()) {
            val id = k.groupValues[1]
            val list = mutableListOf<HistorySample>()
            val isLast = idx == keys.size - 1
            // coleta até o próximo chave ou fim do JSON
            val end = if (isLast) raw.length else keys[idx + 1].range.first
            while (nodeIdx < nodes.size && nodes[nodeIdx].range.first < end) {
                val g = nodes[nodeIdx].groupValues
                list.add(HistorySample(ts = g[1].toLong(), ok = g[2] == "1", latencyMs = g[3].toLong()))
                nodeIdx++
            }
            trim(list)
            if (list.isNotEmpty()) map[id] = list
        }
    }

    private fun trim(list: MutableList<HistorySample>) {
        while (list.size > maxSamplesPerEndpoint) list.removeAt(0)
    }

    private fun statsOf(endpointId: String, list: List<HistorySample>): EndpointStats {
        val okCount = list.count { it.ok }
        val uptime = if (list.isEmpty()) 0.0 else okCount * 100.0 / list.size
        val okLatency = list.filter { it.ok }.map { it.latencyMs }
        val avg = if (okLatency.isEmpty()) 0.0 else okLatency.average()
        val lastOk = list.lastOrNull()?.ok ?: false
        val transitionedDown = list.size >= 2 && list[list.size - 2].ok && !lastOk
        val transitionedUp = list.size >= 2 && !list[list.size - 2].ok && lastOk
        return EndpointStats(
            endpointId = endpointId,
            samples = list,
            uptimePercent = uptime,
            avgLatencyMs = avg,
            lastOk = lastOk,
            transitionedDown = transitionedDown,
            transitionedUp = transitionedUp,
        )
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")
}
