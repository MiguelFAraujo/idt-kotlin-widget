package com.idt.widget.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.idt.widget.R
import com.idt.widget.data.history.EndpointStats
import com.idt.widget.databinding.ItemServiceCardBinding

data class ServiceCardItem(
    val name: String,
    val host: String,
    val port: Int,
    val ok: Boolean,
    val latencyMs: Long,
    val roundUsed: String,
    val message: String,
    val stats: EndpointStats?,
)

class ServiceCardAdapter : ListAdapter<ServiceCardItem, ServiceCardAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemServiceCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(private val binding: ItemServiceCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ServiceCardItem) {
            binding.tvName.text = item.name
            binding.tvEndpoint.text = "${item.host}:${item.port}"

            val dotColor = ContextCompat.getColor(
                binding.root.context,
                if (item.ok) R.color.status_online else R.color.status_offline
            )
            binding.statusDot.background.setTint(dotColor)

            binding.tvStatus.text = if (item.ok) "online" else "offline"
            binding.tvStatus.setTextColor(dotColor)

            binding.tvLatency.text = if (item.latencyMs >= 0) "${item.latencyMs}ms" else "—"
            binding.tvLatency.text = if (item.ok) "${item.latencyMs}ms" else (item.message)

            val stats = item.stats
            if (stats != null) {
                binding.uptimeStripSmall.setOkSeries(stats.okSeries)
            } else {
                binding.uptimeStripSmall.setOkSeries(emptyList())
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ServiceCardItem>() {
            override fun areItemsTheSame(a: ServiceCardItem, b: ServiceCardItem) =
                a.host == b.host && a.port == b.port && a.name == b.name
            override fun areContentsTheSame(a: ServiceCardItem, b: ServiceCardItem) = a == b
        }
    }
}
