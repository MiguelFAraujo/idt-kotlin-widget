package com.idt.widget.ui.connection

import android.os.Bundle
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
import com.idt.widget.IDTApplication
import com.idt.widget.R
import com.idt.widget.data.remote.DiscoveredService
import com.idt.widget.databinding.FragmentConnectionBinding
import com.idt.widget.ui.ViewModelFactory
import com.idt.widget.util.BiometricHelper
import kotlinx.coroutines.launch

class ConnectionFragment : Fragment(R.layout.fragment_connection) {

    private val viewModel: ConnectionViewModel by viewModels {
        ViewModelFactory(requireActivity().application as IDTApplication)
    }

    private var _binding: FragmentConnectionBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentConnectionBinding.bind(view)

        binding.swUseWebDav.setOnCheckedChangeListener { _, isChecked ->
            binding.tilWebDavPath.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (isChecked) binding.etWebDavPath.setText("/")
        }

        binding.btnTestConnection.setOnClickListener {
            val url = binding.etServerUrl.text.toString().trim()
            viewModel.setServerUrl(url)
            viewModel.testConnection()
        }

        binding.btnDiscoverServices.setOnClickListener {
            viewModel.discoverServices()
        }

        binding.btnSkip.setOnClickListener {
            goToDashboard()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: ConnectionUiState) {
        when (state) {
            is ConnectionUiState.Idle -> {
                binding.btnTestConnection.isEnabled = true
                binding.btnTestConnection.text = getString(R.string.connection_test)
                binding.btnDiscoverServices.isEnabled = false
                binding.tvConnectionStatus.visibility = View.GONE
            }
            is ConnectionUiState.Testing -> {
                binding.btnTestConnection.isEnabled = false
                binding.btnTestConnection.text = getString(R.string.connection_testing)
                binding.btnDiscoverServices.isEnabled = false
                binding.tvConnectionStatus.visibility = View.GONE
            }
            is ConnectionUiState.TestSuccess -> {
                binding.btnTestConnection.isEnabled = true
                binding.btnTestConnection.text = getString(R.string.connection_test)
                binding.btnDiscoverServices.isEnabled = true
                binding.tvConnectionStatus.visibility = View.VISIBLE
                binding.tvConnectionStatus.text = getString(R.string.connection_success)
                binding.tvConnectionStatus.setTextColor(requireContext().getColor(android.R.color.holo_green_dark))
            }
            is ConnectionUiState.TestError -> {
                binding.btnTestConnection.isEnabled = true
                binding.btnTestConnection.text = getString(R.string.connection_test)
                binding.btnDiscoverServices.isEnabled = false
                binding.tvConnectionStatus.visibility = View.VISIBLE
                binding.tvConnectionStatus.text = getString(R.string.connection_error, state.error)
                binding.tvConnectionStatus.setTextColor(requireContext().getColor(android.R.color.holo_red_dark))
            }
            is ConnectionUiState.Discovering -> {
                binding.btnTestConnection.isEnabled = false
                binding.btnDiscoverServices.isEnabled = false
                binding.btnDiscoverServices.text = getString(R.string.connection_discovering)
                binding.tvConnectionStatus.visibility = View.VISIBLE
                binding.tvConnectionStatus.text = getString(R.string.connection_discovering)
                binding.tvConnectionStatus.setTextColor(requireContext().getColor(android.R.color.holo_orange_dark))
            }
            is ConnectionUiState.Discovered -> {
                binding.btnTestConnection.isEnabled = true
                binding.btnDiscoverServices.isEnabled = true
                binding.btnDiscoverServices.text = getString(R.string.connection_discover)
                binding.tvConnectionStatus.visibility = View.VISIBLE
                binding.tvConnectionStatus.text = getString(R.string.connection_discovered, state.services.size)
                binding.tvConnectionStatus.setTextColor(requireContext().getColor(android.R.color.holo_green_dark))
                // Auto-save and navigate
                saveAndNavigate(state.serverUrl, state.services)
            }
            is ConnectionUiState.DiscoverError -> {
                binding.btnTestConnection.isEnabled = true
                binding.btnDiscoverServices.isEnabled = true
                binding.btnDiscoverServices.text = getString(R.string.connection_discover)
                binding.tvConnectionStatus.visibility = View.VISIBLE
                binding.tvConnectionStatus.text = getString(R.string.connection_error, state.error)
                binding.tvConnectionStatus.setTextColor(requireContext().getColor(android.R.color.holo_red_dark))
            }
        }
    }

    private fun saveAndNavigate(serverUrl: String, services: List<DiscoveredService>) {
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val useWebDav = binding.swUseWebDav.isChecked
        val webDavPath = binding.etWebDavPath.text.toString().trim().ifBlank { "/" }
        val useFingerprint = binding.swUseFingerprint.isChecked

        if (useFingerprint && BiometricHelper.isBiometricAvailable(requireContext())) {
            val biometricHelper = BiometricHelper(requireActivity())
            biometricHelper.authenticateWithCallback(
                reason = "Confirme sua identidade para salvar a configuração",
                onSuccess = {
                    viewLifecycleOwner.lifecycleScope.launch {
                        viewModel.saveConfiguration(serverUrl, username, password, useWebDav, webDavPath, useFingerprint)
                        // Save discovered services to repository
                        saveDiscoveredServices(services, username, password, useWebDav)
                        Toast.makeText(requireContext(), "Configuração salva! Redirecionando...", Toast.LENGTH_SHORT).show()
                        goToDashboard()
                    }
                },
                onError = { error ->
                    Toast.makeText(requireContext(), "Biometria necessária: $error", Toast.LENGTH_LONG).show()
                }
            )
        } else {
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.saveConfiguration(serverUrl, username, password, useWebDav, webDavPath, useFingerprint)
                saveDiscoveredServices(services, username, password, useWebDav)
                Toast.makeText(requireContext(), "Configuração salva! Redirecionando...", Toast.LENGTH_SHORT).show()
                goToDashboard()
            }
        }
    }

    private fun saveDiscoveredServices(services: List<DiscoveredService>, username: String, password: String, useWebDav: Boolean) {
        val repository = (requireActivity().application as IDTApplication).container.serviceRepository
        viewLifecycleOwner.lifecycleScope.launch {
            val existing = repository.getEndpoints()
            val already = existing.map { it.host.trim() to it.port }.toSet()
            services.forEach { service ->
                val endpoint = service.toEndpoint().copy(
                    username = if (service.requiresAuth) username else "",
                    password = if (service.requiresAuth) password else "",
                    authType = if (service.requiresAuth && useWebDav) com.idt.widget.data.model.ServiceEndpoint.AuthType.WEBDAV 
                        else if (service.requiresAuth) com.idt.widget.data.model.ServiceEndpoint.AuthType.BASIC
                        else com.idt.widget.data.model.ServiceEndpoint.AuthType.NONE,
                )
                if ((endpoint.host.trim() to endpoint.port) !in already) {
                    repository.addEndpoint(endpoint)
                }
            }
        }
    }

    private fun goToDashboard() {
        findNavController().navigate(R.id.action_connection_to_dashboard)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}