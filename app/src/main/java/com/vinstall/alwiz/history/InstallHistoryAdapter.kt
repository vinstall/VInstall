package com.vinstall.alwiz.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.vinstall.alwiz.R
import com.vinstall.alwiz.databinding.ItemHistoryBinding
import com.vinstall.alwiz.model.HistoryStatus
import com.vinstall.alwiz.model.InstallHistoryEntry
import com.vinstall.alwiz.ui.applyPressAnimation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InstallHistoryAdapter(
    private val entries: MutableList<InstallHistoryEntry>,
    private val onItemClick: (InstallHistoryEntry) -> Unit,
    private val onDeleteClick: (InstallHistoryEntry) -> Unit
) : RecyclerView.Adapter<InstallHistoryAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    inner class ViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        binding.root.applyPressAnimation(0.98f)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        val ctx = holder.binding.root.context

        holder.binding.textAppLabel.text = entry.appLabel.ifEmpty { entry.packageName }
        holder.binding.textPackageName.text = entry.packageName.ifEmpty { entry.format }
        holder.binding.textDate.text = dateFormat.format(Date(entry.timestamp))

        val versionText = buildString {
            if (entry.versionName.isNotEmpty()) append("v${entry.versionName}  ·  ")
            append(entry.format)
        }
        holder.binding.textVersionFormat.text = versionText

        val statusLabel: String
        val color: Int
        when (entry.status) {
            HistoryStatus.SUCCESS -> {
                statusLabel = ctx.getString(R.string.history_status_success)
                color = ContextCompat.getColor(ctx, R.color.status_ok)
            }
            HistoryStatus.FAILED -> {
                statusLabel = ctx.getString(R.string.history_status_failed)
                color = MaterialColors.getColor(holder.binding.root, androidx.appcompat.R.attr.colorError)
            }
            HistoryStatus.CANCELLED -> {
                statusLabel = ctx.getString(R.string.history_status_cancelled)
                color = ContextCompat.getColor(ctx, R.color.status_warn)
            }
        }

        holder.binding.textStatus.text = statusLabel
        holder.binding.textStatus.setTextColor(color)
        holder.binding.statusIndicator.setBackgroundColor(color)

        holder.binding.root.setOnClickListener { onItemClick(entry) }
        holder.binding.btnDelete.setOnClickListener { onDeleteClick(entry) }
    }

    override fun getItemCount(): Int = entries.size

    fun remove(entry: InstallHistoryEntry): Boolean {
        val index = entries.indexOfFirst { it.id == entry.id }
        if (index < 0) return false
        entries.removeAt(index)
        notifyItemRemoved(index)
        return true
    }
}
