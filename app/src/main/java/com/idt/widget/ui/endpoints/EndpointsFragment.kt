package com.idt.widget.ui.endpoints

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.idt.widget.IDTApplication
import com.idt.widget.R
import com.idt.widget.databinding.FragmentEndpointsBinding
import com.idt.widget.ui.ViewModelFactory
import kotlinx.coroutines.launch

class EndpointsFragment : Fragment(R.layout.fragment_endpoints) {

    private val viewModel: EndpointsViewModel by viewModels {
        ViewModelFactory(requireActivity().application as IDTApplication)
    }

    private var _binding: FragmentEndpointsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: EndpointAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentEndpointsBinding.bind(view)

        adapter = EndpointAdapter { endpoint ->
            viewModel.updateEndpoint(endpoint.copy(enabled = !endpoint.enabled))
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.fabAdd.setOnClickListener {
            findNavController().navigate(R.id.action_endpoints_to_add)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.endpoints.collect { list -> adapter.submitList(list) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
