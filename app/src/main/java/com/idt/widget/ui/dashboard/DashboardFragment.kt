package com.idt.widget.ui.dashboard

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import com.idt.widget.data.model.UpdateInfo
import com.idt.widget.data.remote.UpdateChecker
import com.idt.widget.databinding.FragmentDashboardBinding
import com.idt.widget.ui.ViewModelFactory
import com.idt.widget.update.ApkUpdater
import com.idt.widget.widget.WidgetScheduler
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

    private val adapter = ServiceCardAdapter()

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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val update = UpdateChecker().check()
            viewModel.onUpdateChecked(update)
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

        renderUpdateBanner(state.updateAvailable)
    }

    private fun renderUpdateBanner(update: UpdateInfo?) {
        val hasUpdate = update != null &&
            update.isNewerThan(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        binding.cardUpdate.visibility = if (hasUpdate) View.VISIBLE else View.GONE
        if (hasUpdate && update != null) {
            binding.tvUpdateText.text = "Nova versão ${update.versionName} disponível"
            binding.btnUpdate.setOnClickListener { downloadAndInstall(update.apkUrl, update.versionName) }
        }
    }

    private fun downloadAndInstall(apkUrl: String, version: String) {
        binding.btnUpdate.isEnabled = false
        binding.tvUpdateText.text = "Baixando $version..."
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val updater = ApkUpdater(requireContext())
                val apk = updater.download(apkUrl)
                binding.tvUpdateText.text = "Instalando $version..."
                val ok = updater.install(apk, version)
                if (!ok) {
                    binding.tvUpdateText.text = "Nova versão $version — abra manualmente"
                    binding.btnUpdate.isEnabled = true
                    Toast.makeText(requireContext(), "Falha na instalação automática", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                binding.tvUpdateText.text = "Nova versão $version disponível"
                binding.btnUpdate.isEnabled = true
                Toast.makeText(requireContext(), "Erro ao baixar: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
