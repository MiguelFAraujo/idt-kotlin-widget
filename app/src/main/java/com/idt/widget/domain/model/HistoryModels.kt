package com.idt.widget.domain.model

/** A single observed service check, independent of its storage adapter. */
data class HistorySample(
    val ts: Long,
    val ok: Boolean,
    val latencyMs: Long,
)

/** History statistics shared by the repository contract and its consumers. */
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
