package com.vinstall.alwiz.timeline

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.vinstall.alwiz.R
import com.vinstall.alwiz.appmanager.AppIconLoader
import com.vinstall.alwiz.databinding.ItemAppBinding
import com.vinstall.alwiz.ui.applyPressAnimation
import com.vinstall.alwiz.util.VersionLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TimelineAppAdapter(
    private val icons: AppIconLoader,
    private val onItemClick: (TimelineSummary) -> Unit
) : RecyclerView.Adapter<TimelineAppAdapter.ViewHolder>() {

    private var items: List<TimelineSummary> = emptyList()
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    inner class ViewHolder(val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root)

    fun submit(list: List<TimelineSummary>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        binding.root.applyPressAnimation(0.98f)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val b = holder.binding
        val ctx = b.root.context
        b.textAppName.text = item.label
        b.textPackageName.text = item.packageName
        val meta = buildList {
            if (item.currentVersion.isNotBlank()) add(VersionLabel.format(item.currentVersion))
            add(ctx.getString(R.string.timeline_events_count, item.count))
            add(
                ctx.getString(
                    R.string.timeline_last_event,
                    "${TimelineStyle.typeLabel(ctx, item.lastType)} ${dateFormat.format(Date(item.lastTimestamp))}"
                )
            )
        }
        b.textMeta.text = meta.joinToString(ctx.getString(R.string.separator_dot))
        icons.load(item.packageName, b.imageIcon)
        b.root.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = items.size
}
