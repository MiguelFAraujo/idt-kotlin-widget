package com.idt.widget.ui.diagnostics

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.idt.widget.IDTApplication
import com.idt.widget.R
import com.idt.widget.data.model.ServiceCheckResult
import com.idt.widget.data.remote.DiagnosticsTool
import com.idt.widget.databinding.FragmentDiagnosticsBinding
import com.idt.widget.ui.ViewModelFactory
import kotlinx.coroutines.launch

class DiagnosticsFragment : Fragment(R.layout.fragment_diagnostics) {

    private val viewModel: DiagnosticsViewModel by viewModels {
        ViewModelFactory(requireActivity().application as IDTApplication)
    }

    private var _binding: FragmentDiagnosticsBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDiagnosticsBinding.bind(view)

        binding.btnScan.setOnClickListener { runScan() }
        binding.btnDns.setOnClickListener { runDns() }
        binding.btnPing.setOnClickListener { runPing() }
        binding.btnExportConfig.setOnClickListener { exportConfig() }
        binding.btnShareReport.setOnClickListener { shareReport() }
        binding.btnClearHistory.setOnClickListener { clearHistory() }
    }

    private fun host(): String =
        binding.etHost.text?.toString()?.trim()?.ifEmpty { "192.168.1.9" } ?: "192.168.1.9"

    private fun runScan() {
        val h = host()
        binding.tvResult.text = "Escaneando $h..."
        lifecycleScope.launch {
            val open = DiagnosticsTool.scanPorts(h, DiagnosticsTool.COMMON_PORTS)
            binding.tvResult.text = buildString {
                appendLine("Portas abertas em $h:")
                if (open.isEmpty()) append("Nenhuma porta comum aberta.")
                else open.forEach { p -> appendLine("  • $p ${portName(p)}") }
            }
        }
    }

    private fun runDns() {
        val h = host()
        binding.tvResult.text = "Resolvendo DNS de $h..."
        lifecycleScope.launch {
            binding.tvResult.text = "DNS de $h → ${DiagnosticsTool.dnsLookup(h)}"
        }
    }

    private fun runPing() {
        val h = host()
        binding.tvResult.text = "Testando latência em $h..."
        lifecycleScope.launch {
            val ms = DiagnosticsTool.httpLatency(h, 80)
            binding.tvResult.text = if (ms != null) "Latência HTTP $h:80 → ${ms}ms"
            else "Sem resposta HTTP em $h:80"
        }
    }

    private fun exportConfig() {
        val app = requireActivity().application as IDTApplication
        lifecycleScope.launch {
            val endpoints = app.container.serviceRepository.getEndpoints()
            val json = org.json.JSONArray().apply {
                endpoints.forEach { e ->
                    put(
                        org.json.JSONObject()
                            .put("id", e.id)
                            .put("name", e.name)
                            .put("host", e.host)
                            .put("port", e.port)
                            .put("enabled", e.enabled)
                            .put("requireAuth", e.requireAuth)
                    )
                }
            }.toString()
            shareText("idt-endpoints.json", json)
        }
    }

    private fun shareReport() {
        lifecycleScope.launch {
            val app = requireActivity().application as IDTApplication
            val endpoints = app.container.serviceRepository.getEndpoints()
            val results = endpoints.filter { it.enabled }
                .map { app.container.serviceRepository.checkService(it) }
            val report = buildString {
                appendLine("IDT Status — Relatório de monitoramento")
                appendLine("Gerado: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}")
                appendLine()
                val ok = results.count { it.ok }
                appendLine("Online: $ok/${results.size}")
                appendLine()
                results.forEach { r -> appendLine(line(r)) }
            }
            shareText("idt-status-report.txt", report)
        }
    }

    private fun line(r: ServiceCheckResult): String =
        "${if (r.ok) "●" else "○"} ${r.endpoint.name} (${r.endpoint.host}:${r.endpoint.port}) " +
            "- ${if (r.ok) "online" else "offline"} ${r.latencyMs}ms · ${r.roundUsed} · ${r.message}"

    private fun shareText(filename: String, content: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, filename)
            putExtra(Intent.EXTRA_TEXT, content)
        }
        startActivity(Intent.createChooser(send, "Compartilhar"))
    }

    private fun clearHistory() {
        lifecycleScope.launch {
            (requireActivity().application as IDTApplication)
                .container.historyRepository.clear()
            Toast.makeText(requireContext(), "Histórico limpo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun portName(p: Int): String = when (p) {
        21 -> "(FTP)"
        22 -> "(SSH)"
        53 -> "(DNS)"
        80 -> "(HTTP)"
        443 -> "(HTTPS)"
        3306 -> "(MySQL)"
        5432 -> "(PostgreSQL)"
        8080 -> "(HTTP-alt)"
        9090 -> "(Prometheus)"
        11434 -> "(Ollama)"
        19999 -> "(Netdata)"
        else -> ""
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
