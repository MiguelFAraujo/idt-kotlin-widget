package com.idt.widget.ui.endpoints

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.idt.widget.data.model.ServiceEndpoint
import com.idt.widget.databinding.ItemEndpointBinding

class EndpointAdapter(
    private val onToggle: (ServiceEndpoint) -> Unit,
) : ListAdapter<ServiceEndpoint, EndpointAdapter.ViewHolder>(Diff) {

    class ViewHolder(val binding: ItemEndpointBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEndpointBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val endpoint = getItem(position)
        holder.binding.tvName.text = endpoint.name
        holder.binding.tvHostPort.text = "${endpoint.host}:${endpoint.port}"
        holder.binding.swEnabled.isChecked = endpoint.enabled
        holder.binding.swEnabled.setOnCheckedChangeListener { _, _ -> onToggle(endpoint) }
    }

    companion object {
        private val Diff = object : DiffUtil.ItemCallback<ServiceEndpoint>() {
            override fun areItemsTheSame(a: ServiceEndpoint, b: ServiceEndpoint) = a.id == b.id
            override fun areContentsTheSame(a: ServiceEndpoint, b: ServiceEndpoint) = a == b
        }
    }
}
