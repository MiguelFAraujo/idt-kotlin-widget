package com.idt.widget.ui.scan

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.idt.widget.R
import com.idt.widget.databinding.ItemScanPortBinding

data class ScanPortItem(
    val port: Int,
    val name: String,
    val open: Boolean,
    val selected: Boolean,
)

class ScanPortAdapter(
    private val onToggle: (ScanPortItem) -> Unit,
) : ListAdapter<ScanPortItem, ScanPortAdapter.ViewHolder>(Diff) {

    class ViewHolder(val binding: ItemScanPortBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemScanPortBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val ctx = holder.binding.root.context
        holder.binding.tvPortName.text = item.name
        holder.binding.tvHostPort.text = "porta ${item.port}"
        holder.binding.tvStatus.text = if (item.open) "ABERTA" else "OFF"
        holder.binding.tvStatus.setBackgroundColor(
            ContextCompat.getColor(
                ctx,
                if (item.open) R.color.status_online else R.color.status_offline
            )
        )
        holder.binding.cbSelect.setOnCheckedChangeListener(null)
        holder.binding.cbSelect.isChecked = item.selected
        holder.binding.cbSelect.setOnCheckedChangeListener { _, checked ->
            onToggle(item.copy(selected = checked))
        }
    }

    companion object {
        private val Diff = object : DiffUtil.ItemCallback<ScanPortItem>() {
            override fun areItemsTheSame(a: ScanPortItem, b: ScanPortItem) = a.port == b.port
            override fun areContentsTheSame(a: ScanPortItem, b: ScanPortItem) = a == b
        }
    }
}
