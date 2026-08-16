package com.idt.widget.ui.addendpoint

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.idt.widget.IDTApplication
import com.idt.widget.R
import com.idt.widget.data.model.ServiceEndpoint
import com.idt.widget.databinding.FragmentAddEndpointBinding
import com.idt.widget.ui.ViewModelFactory
import com.idt.widget.ui.endpoints.EndpointsViewModel
import java.util.UUID

class AddEndpointFragment : Fragment(R.layout.fragment_add_endpoint) {

    private val viewModel: EndpointsViewModel by viewModels {
        ViewModelFactory(requireActivity().application as IDTApplication)
    }

    private var _binding: FragmentAddEndpointBinding? = null
    private val binding get() = _binding!!

    private var selectedAuthType = ServiceEndpoint.AuthType.NONE

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAddEndpointBinding.bind(view)

        setupAuthTypeSpinner()
        binding.swRequireAuth.setOnCheckedChangeListener { _, isChecked ->
            binding.tilAuthType.visibility = if (isChecked) View.VISIBLE else View.GONE
            updateAuthFieldsVisibility()
        }

        binding.btnSave.setOnClickListener {
            val name = binding.tilName.editText?.text.toString().trim()
            val host = binding.tilHost.editText?.text.toString().trim()
            val port = binding.tilPort.editText?.text.toString().trim().toIntOrNull() ?: 80
            val requireAuth = binding.swRequireAuth.isChecked
            if (name.isNotBlank() && host.isNotBlank()) {
                val endpoint = ServiceEndpoint(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    host = host,
                    port = port,
                    enabled = true,
                    requireAuth = requireAuth,
                    authType = if (requireAuth) selectedAuthType else ServiceEndpoint.AuthType.NONE,
                    username = binding.tilUsername.editText?.text.toString().trim(),
                    password = binding.tilPassword.editText?.text.toString().trim(),
                    bearerToken = binding.tilBearerToken.editText?.text.toString().trim(),
                    xIdtToken = binding.tilXIdtToken.editText?.text.toString().trim(),
                    useFingerprint = binding.cbUseFingerprint.isChecked,
                )
                viewModel.addEndpoint(endpoint)
                findNavController().popBackStack()
            }
        }
        binding.btnCancel.setOnClickListener { findNavController().popBackStack() }
    }

    private fun setupAuthTypeSpinner() {
        val authTypes = arrayOf(
            ServiceEndpoint.AuthType.NONE,
            ServiceEndpoint.AuthType.BASIC,
            ServiceEndpoint.AuthType.BEARER,
            ServiceEndpoint.AuthType.WEBDAV,
            ServiceEndpoint.AuthType.CUSTOM,
        )
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, authTypes.map { it.name })
        val actAuthType = binding.actAuthType
        actAuthType.setAdapter(adapter)
        actAuthType.setOnItemClickListener { _, _, position, _ ->
            selectedAuthType = authTypes[position]
            updateAuthFieldsVisibility()
        }
        actAuthType.setText(authTypes[0].name, false)
    }

    private fun updateAuthFieldsVisibility() {
        val requireAuth = binding.swRequireAuth.isChecked
        val authType = selectedAuthType
        val showAuthFields = requireAuth && authType != ServiceEndpoint.AuthType.NONE

        binding.tilUsername.visibility = if (showAuthFields && authType in setOf(ServiceEndpoint.AuthType.BASIC, ServiceEndpoint.AuthType.WEBDAV)) View.VISIBLE else View.GONE
        binding.tilPassword.visibility = if (showAuthFields && authType in setOf(ServiceEndpoint.AuthType.BASIC, ServiceEndpoint.AuthType.WEBDAV)) View.VISIBLE else View.GONE
        binding.tilBearerToken.visibility = if (showAuthFields && authType == ServiceEndpoint.AuthType.BEARER) View.VISIBLE else View.GONE
        binding.tilXIdtToken.visibility = if (showAuthFields && authType == ServiceEndpoint.AuthType.CUSTOM) View.VISIBLE else View.GONE
        binding.cbUseFingerprint.visibility = if (showAuthFields && authType in setOf(ServiceEndpoint.AuthType.BASIC, ServiceEndpoint.AuthType.WEBDAV)) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
