package com.vinstall.alwiz.timeline

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.vinstall.alwiz.R
import com.vinstall.alwiz.databinding.ItemTimelineNodeBinding
import com.vinstall.alwiz.ui.applyPressAnimation
import com.vinstall.alwiz.ui.showStatusIcon
import com.vinstall.alwiz.util.CallerResolver
import com.vinstall.alwiz.util.VersionLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TimelineTreeAdapter(
    private val onItemClick: (TimelineNode) -> Unit
) : RecyclerView.Adapter<TimelineTreeAdapter.ViewHolder>() {

    private var rows: List<TimelineRow> = emptyList()
    private var maxDepth = 0
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    inner class ViewHolder(val binding: ItemTimelineNodeBinding) : RecyclerView.ViewHolder(binding.root)

    fun submit(newRows: List<TimelineRow>) {
        rows = newRows
        maxDepth = newRows.maxOfOrNull { it.depth } ?: 0
        notifyDataSetChanged()
    }

    fun currentPosition(): Int = rows.indexOfFirst { it.isCurrent }.takeIf { it >= 0 } ?: (rows.size - 1)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTimelineNodeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        binding.cardNode.applyPressAnimation(0.98f)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val row = rows[position]
        val node = row.node
        val b = holder.binding
        val ctx = b.root.context
        val color = TimelineStyle.color(b.root, node)

        b.guide.bind(row, maxDepth, color, TimelineStyle.lineColor(b.root))

        val version = node.versionName.takeIf { it.isNotBlank() }?.let { VersionLabel.format(it) }
        b.textTitle.text = listOfNotNull(TimelineStyle.typeLabel(ctx, node.type), version)
            .joinToString(ctx.getString(R.string.separator_dot))

        val showOutcome = node.type == TimelineType.ATTEMPT || node.outcome != TimelineOutcome.SUCCESS
        b.textOutcome.isVisible = showOutcome
        b.textOutcome.text = TimelineStyle.outcomeLabel(ctx, node.outcome)
        b.textOutcome.setTextColor(color)
        b.textOutcome.showStatusIcon(TimelineStyle.outcomeIcon(node.outcome), color)

        val subtitle = buildList {
            add(dateFormat.format(Date(node.timestamp)))
            node.installMode?.takeIf { it.isNotBlank() }?.let { add(it) }
            node.source?.takeIf { it.isNotBlank() }?.let {
                add(ctx.getString(R.string.history_via, CallerResolver.labelOf(ctx, it)))
            }
        }.joinToString(ctx.getString(R.string.separator_dot))
        b.textSubtitle.text = subtitle

        val tags = buildList {
            if (row.isCurrent) add(ctx.getString(R.string.timeline_tag_current))
            if (node.external) add(ctx.getString(R.string.timeline_tag_external))
            if (node.forced) add(ctx.getString(R.string.timeline_tag_forced))
            when (node.keptData) {
                true -> add(ctx.getString(R.string.timeline_tag_data_kept))
                false -> if (node.type != TimelineType.ATTEMPT) add(ctx.getString(R.string.timeline_tag_data_erased))
                null -> {}
            }
            if (node.signatureChanged) add(ctx.getString(R.string.timeline_tag_signature))
            node.permissionsAdded?.takeIf { it.isNotEmpty() }
                ?.let { add(ctx.getString(R.string.timeline_tag_perm_added, it.size)) }
            node.permissionsRemoved?.takeIf { it.isNotEmpty() }
                ?.let { add(ctx.getString(R.string.timeline_tag_perm_removed, it.size)) }
        }
        b.textTags.isVisible = tags.isNotEmpty()
        b.textTags.text = tags.joinToString(ctx.getString(R.string.separator_dot))

        b.cardNode.setOnClickListener { onItemClick(node) }
    }

    override fun getItemCount(): Int = rows.size
}
