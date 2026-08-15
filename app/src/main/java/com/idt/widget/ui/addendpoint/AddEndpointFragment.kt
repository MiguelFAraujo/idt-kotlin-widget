package com.idt.widget.ui.addendpoint

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAddEndpointBinding.bind(view)

        binding.btnSave.setOnClickListener {
            val name = binding.tilName.editText?.text.toString().trim()
            val host = binding.tilHost.editText?.text.toString().trim()
            val port = binding.tilPort.editText?.text.toString().trim().toIntOrNull() ?: 80
            if (name.isNotBlank() && host.isNotBlank()) {
                viewModel.addEndpoint(
                    ServiceEndpoint(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        host = host,
                        port = port,
                        enabled = true,
                        requireAuth = false,
                    )
                )
                findNavController().popBackStack()
            }
        }
        binding.btnCancel.setOnClickListener { findNavController().popBackStack() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
