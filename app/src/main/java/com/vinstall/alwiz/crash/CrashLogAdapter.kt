package com.vinstall.alwiz.crash

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.vinstall.alwiz.R
import com.vinstall.alwiz.databinding.ItemCrashBinding
import com.vinstall.alwiz.model.CrashEntry
import com.vinstall.alwiz.ui.applyPressAnimation

class CrashLogAdapter(
    private val entries: MutableList<CrashEntry>,
    private val onItemClick: (CrashEntry) -> Unit,
    private val onDeleteClick: (CrashEntry) -> Unit
) : RecyclerView.Adapter<CrashLogAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemCrashBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCrashBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        binding.root.applyPressAnimation(0.98f)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        val b = holder.binding

        b.textException.text = entry.exceptionType
        b.textMessage.text = entry.exceptionMessage
        b.textMessage.isVisible = entry.exceptionMessage.isNotEmpty()
        b.textTime.text = entry.time
        b.textThread.text = entry.thread
        b.statusIndicator.setBackgroundColor(
            MaterialColors.getColor(b.root, androidx.appcompat.R.attr.colorError)
        )

        b.root.setOnClickListener { onItemClick(entry) }
        b.btnDelete.contentDescription =
            b.root.context.getString(R.string.crash_log_delete_item_desc, entry.exceptionType)
        b.btnDelete.setOnClickListener { onDeleteClick(entry) }
    }

    override fun getItemCount(): Int = entries.size

    fun remove(entry: CrashEntry): Boolean {
        val index = entries.indexOfFirst { it.report == entry.report }
        if (index < 0) return false
        entries.removeAt(index)
        notifyItemRemoved(index)
        return true
    }
}
