package com.vinstall.alwiz.history

import com.vinstall.alwiz.ui.StatusIcon
import com.vinstall.alwiz.ui.showStatusIcon
import com.vinstall.alwiz.ui.statusBlocked
import com.vinstall.alwiz.ui.statusOk
import com.vinstall.alwiz.ui.statusWarn
import com.vinstall.alwiz.util.VersionLabel
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.vinstall.alwiz.R
import com.vinstall.alwiz.databinding.ItemHistoryBinding
import com.vinstall.alwiz.model.HistoryStatus
import com.vinstall.alwiz.model.InstallHistoryEntry
import com.vinstall.alwiz.ui.applyPressAnimation
import com.vinstall.alwiz.util.CallerResolver
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

        val subtitle = entry.displaySubtitle()
        holder.binding.textAppLabel.text = entry.displayTitle(ctx)
        holder.binding.textPackageName.text = subtitle
        holder.binding.textPackageName.isVisible = subtitle.isNotEmpty()
        holder.binding.textDate.text = dateFormat.format(Date(entry.timestamp))

        val versionText = buildString {
            if (entry.versionName.isNotEmpty()) append("${VersionLabel.format(entry.versionName)}  ·  ")
            append(entry.format)
            entry.sourcePackage?.takeIf { it.isNotEmpty() }?.let { source ->
                append("  ·  ")
                append(ctx.getString(R.string.history_via, CallerResolver.labelOf(ctx, source)))
            }
        }
        holder.binding.textVersionFormat.text = versionText

        val statusLabel: String
        val color: Int
        val icon: StatusIcon
        when (entry.status) {
            HistoryStatus.SUCCESS -> {
                statusLabel = ctx.getString(R.string.history_status_success)
                color = statusOk(ctx)
                icon = StatusIcon.SUCCESS
            }
            HistoryStatus.FAILED -> {
                statusLabel = ctx.getString(R.string.history_status_failed)
                color = MaterialColors.getColor(holder.binding.root, androidx.appcompat.R.attr.colorError)
                icon = StatusIcon.ERROR
            }
            HistoryStatus.CANCELLED -> {
                statusLabel = ctx.getString(R.string.history_status_cancelled)
                color = statusWarn(ctx)
                icon = StatusIcon.CANCELLED
            }
            HistoryStatus.BLOCKED -> {
                statusLabel = ctx.getString(R.string.history_status_blocked)
                color = statusBlocked(ctx)
                icon = StatusIcon.BLOCKED
            }
        }

        holder.binding.textStatus.text = statusLabel
        holder.binding.textStatus.setTextColor(color)
        holder.binding.textStatus.showStatusIcon(icon, color)
        holder.binding.statusIndicator.setBackgroundColor(color)

        holder.binding.root.setOnClickListener { onItemClick(entry) }
        holder.binding.btnDelete.contentDescription =
            ctx.getString(R.string.history_delete_item_desc, entry.displayTitle(ctx))
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
