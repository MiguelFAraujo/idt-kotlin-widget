package com.idt.widget.data.remote

/** Estratégia de escaneamento de portas, injetável para testes. */
interface PortScanner {
    suspend fun scanPorts(host: String, ports: List<Int>): List<Int>
    val commonPorts: List<Int>
}

object DiagnosticsPortScanner : PortScanner {
    override val commonPorts: List<Int> = DiagnosticsTool.COMMON_PORTS
    override suspend fun scanPorts(host: String, ports: List<Int>): List<Int> =
        DiagnosticsTool.scanPorts(host, ports)
}
