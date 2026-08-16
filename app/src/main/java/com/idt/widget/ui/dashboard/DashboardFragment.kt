package com.idt.widget.ui.dashboard

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.idt.widget.BuildConfig
import com.idt.widget.IDTApplication
import com.idt.widget.MainActivity
import com.idt.widget.R
import com.idt.widget.data.remote.UpdateChecker
import com.idt.widget.databinding.DialogServiceDetailBinding
import com.idt.widget.databinding.FragmentDashboardBinding
import com.idt.widget.ui.ViewModelFactory
import com.idt.widget.update.ApkUpdater
import com.idt.widget.update.InstallResultReceiver
import com.idt.widget.update.UpdateInfo
import com.idt.widget.util.NetworkSpeedMonitor
import com.idt.widget.widget.StatusWidgetProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private val viewModel: DashboardViewModel by viewModels {
        ViewModelFactory(requireActivity().application as IDTApplication)
    }

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val adapter = ServiceCardAdapter { item -> showServiceDetail(item) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDashboardBinding.bind(view)

        binding.recyclerServices.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerServices.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        binding.btnSettings.setOnClickListener { findNavController().navigate(R.id.action_dashboard_to_settings) }
        binding.btnEndpoints.setOnClickListener { findNavController().navigate(R.id.action_dashboard_to_endpoints) }

        (requireActivity() as? MainActivity)?.onRefreshRequested = { viewModel.refresh() }
        Log.d("IDT_MAIN", "DashboardFragment.onViewCreated: callback registrado")

        // UI State from ViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }

        // Real-time auto-refresh based on config (loop contínuo, ver RefreshScheduler)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val configFlow = (requireActivity().application as IDTApplication)
                    .container.configDataSource.observeConfig()
                RefreshScheduler(
                    configFlow = configFlow,
                    scope = this,
                    onRefresh = { viewModel.refresh() },
                ).start()
            }
        }

        // Live network speed (ticker a cada segundo, resiliente a falhas)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    try {
                        val s = NetworkSpeedMonitor.sample()
                        binding.tvNetDown.text = "⬇ ${NetworkSpeedMonitor.format(s.rxBytesPerSec)}"
                        binding.tvNetUp.text = "⬆ ${NetworkSpeedMonitor.format(s.txBytesPerSec)}"
                    } catch (e: Exception) {
                        Log.w("IDT_MAIN", "Erro no ticker de rede", e)
                    }
                    delay(1000)
                }
            }
        }

        // Check for updates on start (once per session)
        checkForUpdates()
    }

    private fun checkForUpdates() {
        viewLifecycleOwner.lifecycleScope.launch {
            // Primeiro verifica se já tem update marcado (após reboot pós-instalação)
            val storedUpdate = InstallResultReceiver.getUpdateInfo(requireContext())
            if (storedUpdate != null) {
                viewModel.onUpdateChecked(storedUpdate)
                // Auto-inicia download/install se configurado
                val cfg = (requireActivity().application as IDTApplication).container.configDataSource.getConfig()
                if (cfg.autoRefresh) { // Reusa autoRefresh como "auto-update"
                    startAutoUpdate(storedUpdate)
                }
            } else {
                // Busca no servidor
                val update = UpdateChecker().check(requireContext())
                viewModel.onUpdateChecked(update)
                if (update != null) {
                    val cfg = (requireActivity().application as IDTApplication).container.configDataSource.getConfig()
                    if (cfg.autoRefresh) {
                        startAutoUpdate(update)
                    }
                }
            }
        }
    }

    private fun startAutoUpdate(update: UpdateInfo) {
        binding.cardUpdate.visibility = View.VISIBLE
        binding.btnUpdate.visibility = View.GONE
        binding.tvUpdateText.text = "Baixando ${update.versionName} automaticamente..."

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val updater = ApkUpdater(requireContext())

                // Verifica permissão de instalação
                if (!ApkUpdater.canInstallPackages(requireContext())) {
                    binding.tvUpdateText.text = "Permita instalação de fontes desconhecidas"
                    binding.btnUpdate.visibility = View.VISIBLE
                    binding.btnUpdate.text = "Abrir configurações"
                    binding.btnUpdate.setOnClickListener {
                        ApkUpdater.openInstallPermissionSettings(requireContext())
                    }
                    return@launch
                }

                val result = updater.downloadAndInstall(update.apkUrl)

                if (result.success) {
                    binding.tvUpdateText.text = "Instalando ${update.versionName}... Aguarde"
                    binding.btnUpdate.visibility = View.GONE
                    // InstallResultReceiver vai notificar e limpar flag
                } else {
                    binding.tvUpdateText.text = "Falha: ${result.error}"
                    binding.btnUpdate.visibility = View.VISIBLE
                    binding.btnUpdate.text = "Tentar novamente"
                    binding.btnUpdate.setOnClickListener {
                        binding.btnUpdate.visibility = View.GONE
                        binding.tvUpdateText.text = "Baixando ${update.versionName} automaticamente..."
                        startAutoUpdate(update)
                    }
                }
            } catch (e: Exception) {
                binding.tvUpdateText.text = "Erro: ${e.message}"
                binding.btnUpdate.visibility = View.VISIBLE
                binding.btnUpdate.text = "Tentar novamente"
                binding.btnUpdate.setOnClickListener {
                    binding.btnUpdate.visibility = View.GONE
                    binding.tvUpdateText.text = "Baixando ${update.versionName} automaticamente..."
                    startAutoUpdate(update)
                }
            }
        }
    }

    private fun render(state: DashboardUiState) {
        binding.swipeRefresh.isRefreshing = state.isLoading

        binding.tvOverallStatus.text = when {
            state.isLoading -> getString(R.string.checking)
            state.error != null -> "Erro"
            state.overallTotal == 0 -> "Sem serviços"
            state.overallOk == state.overallTotal -> getString(R.string.overall_ok)
            else -> getString(R.string.overall_partial, state.overallOk, state.overallTotal)
        }

        binding.tvOverallDetail.text = when {
            state.error != null -> state.error
            state.overallTotal == 0 -> "Configure seus endpoints"
            else -> "${state.overallOk} de ${state.overallTotal} serviços operacionais"
        }

        binding.tvLastUpdate.text = state.lastUpdate?.let {
            getString(R.string.updated_at, SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it)))
        } ?: getString(R.string.tap_to_refresh)

        binding.statusRing.setFraction(state.overallUptime, animate = !state.isLoading)

        val avgSeries = state.avgLatencySeries
        binding.latencyChart.setValues(avgSeries)

        val okSeries = state.cards.map { it.ok }.takeLast(40)
        binding.uptimeStrip.setOkSeries(okSeries)

        adapter.submitList(state.cards)

        if (!state.isLoading && state.error == null && state.results.isNotEmpty()) {
            StatusWidgetProvider.syncCache(requireContext(), state.results)
        }

        renderUpdateBanner(state.updateAvailable)
    }

    private fun renderUpdateBanner(update: UpdateInfo?) {
        val hasUpdate = update != null &&
            update.isNewerThan(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)

        binding.cardUpdate.visibility = if (hasUpdate) View.VISIBLE else View.GONE

        if (hasUpdate && binding.btnUpdate.visibility != View.GONE) {
            val u = update!!
            binding.tvUpdateText.text = "Nova versão ${u.versionName} disponível"
            binding.btnUpdate.setOnClickListener {
                binding.btnUpdate.visibility = View.GONE
                startAutoUpdate(u)
            }
        }
    }

    private fun showServiceDetail(item: ServiceCardItem) {
        val binding = DialogServiceDetailBinding.inflate(LayoutInflater.from(requireContext()))
        binding.tvEndpoint.text = getString(R.string.service_detail_endpoint, item.host, item.port)
        binding.tvStatus.text = if (item.ok) {
            getString(R.string.service_detail_status_online, item.latencyMs)
        } else {
            getString(R.string.service_detail_status_offline, item.message)
        }
        binding.tvRound.text = getString(R.string.service_detail_round, item.roundUsed)
        binding.uptimeStrip.setOkSeries(item.stats?.okSeries ?: emptyList())
        if (item.ok) binding.tvOpen.visibility = View.VISIBLE else binding.tvOpen.visibility = View.GONE
        binding.tvOpen.setOnClickListener {
            openServiceUrl(item)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.service_detail_title, item.name))
            .setView(binding.root)
            .setPositiveButton(R.string.service_detail_close, null)
            .show()
    }

    private fun openServiceUrl(item: ServiceCardItem) {
        try {
            val url = if (item.port == 443) "https://${item.host}" else "http://${item.host}:${item.port}"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}