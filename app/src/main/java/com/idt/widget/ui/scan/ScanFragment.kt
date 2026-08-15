package com.idt.widget.ui.scan

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.idt.widget.IDTApplication
import com.idt.widget.R
import com.idt.widget.databinding.FragmentScanBinding
import com.idt.widget.ui.ViewModelFactory
import kotlinx.coroutines.launch

class ScanFragment : Fragment(R.layout.fragment_scan) {

    private val viewModel: ScanViewModel by viewModels {
        ViewModelFactory(requireActivity().application as IDTApplication)
    }

    private var _binding: FragmentScanBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ScanPortAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentScanBinding.bind(view)

        adapter = ScanPortAdapter { item -> viewModel.toggle(item) }
        binding.recyclerPorts.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerPorts.adapter = adapter

        binding.btnScan.setOnClickListener {
            viewModel.setHost(binding.etHost.text?.toString() ?: "")
            viewModel.scan()
        }

        binding.btnAddSelected.setOnClickListener {
            val added = viewModel.addSelected()
            Toast.makeText(requireContext(), "$added endpoints adicionados", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: ScanUiState) {
        binding.btnScan.isEnabled = !state.scanning
        binding.btnScan.text = if (state.scanning) "Escaneando..." else getString(R.string.scan_btn)
        binding.btnAddSelected.isEnabled = state.selectedCount > 0
        binding.btnAddSelected.text =
            getString(R.string.scan_add_selected, state.selectedCount)
        binding.tvSummary.text = when {
            state.error != null -> state.error
            state.scanning -> "Escaneando ${state.host}..."
            state.ports.isEmpty() -> getString(R.string.scan_summary_idle)
            else -> getString(
                R.string.scan_summary_done,
                state.host,
                state.ports.count { it.open },
                state.ports.size,
            )
        }
        adapter.submitList(state.ports)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
