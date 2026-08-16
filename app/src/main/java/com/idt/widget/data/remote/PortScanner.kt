package com.idt.widget.data.remote

/** Estratégia de escaneamento de portas, injetável para testes. */
interface PortScanner {
    suspend fun scanPorts(
        host: String,
        ports: List<Int>,
        timeoutMs: Int,
        concurrency: Int,
        onProgress: (done: Int, total: Int) -> Unit,
    ): List<Int>

    val commonPorts: List<Int>
    val fullScanPorts: List<Int>
}

object DiagnosticsPortScanner : PortScanner {
    override val commonPorts: List<Int> = DiagnosticsTool.COMMON_PORTS
    override val fullScanPorts: List<Int> = DiagnosticsTool.FULL_SCAN_PORTS

    override suspend fun scanPorts(
        host: String,
        ports: List<Int>,
        timeoutMs: Int,
        concurrency: Int,
        onProgress: (done: Int, total: Int) -> Unit,
    ): List<Int> = DiagnosticsTool.scanPorts(host, ports, timeoutMs, concurrency, onProgress)
}
