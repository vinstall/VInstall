package com.vinstall.alwiz.timeline

import com.vinstall.alwiz.ui.StatusIcon
import com.vinstall.alwiz.ui.statusBlocked
import com.vinstall.alwiz.ui.statusOk
import com.vinstall.alwiz.ui.statusWarn
import android.content.Context
import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import com.vinstall.alwiz.R

object TimelineStyle {

    fun typeLabel(context: Context, type: TimelineType): String = context.getString(
        when (type) {
            TimelineType.BASELINE -> R.string.timeline_type_baseline
            TimelineType.INSTALL -> R.string.timeline_type_install
            TimelineType.UPDATE -> R.string.timeline_type_update
            TimelineType.DOWNGRADE -> R.string.timeline_type_downgrade
            TimelineType.REINSTALL -> R.string.timeline_type_reinstall
            TimelineType.UNINSTALL -> R.string.timeline_type_uninstall
            TimelineType.ATTEMPT -> R.string.timeline_type_attempt
        }
    )

    fun outcomeLabel(context: Context, outcome: TimelineOutcome): String = context.getString(
        when (outcome) {
            TimelineOutcome.SUCCESS -> R.string.history_status_success
            TimelineOutcome.FAILED -> R.string.history_status_failed
            TimelineOutcome.CANCELLED -> R.string.history_status_cancelled
            TimelineOutcome.BLOCKED -> R.string.history_status_blocked
        }
    )

    fun outcomeIcon(outcome: TimelineOutcome): StatusIcon = when (outcome) {
        TimelineOutcome.SUCCESS -> StatusIcon.SUCCESS
        TimelineOutcome.FAILED -> StatusIcon.ERROR
        TimelineOutcome.CANCELLED -> StatusIcon.CANCELLED
        TimelineOutcome.BLOCKED -> StatusIcon.BLOCKED
    }

    fun color(view: View, node: TimelineNode): Int {
        val context = view.context
        val error = MaterialColors.getColor(view, androidx.appcompat.R.attr.colorError)
        return when {
            node.outcome == TimelineOutcome.FAILED -> error
            node.outcome == TimelineOutcome.CANCELLED -> statusWarn(context)
            node.outcome == TimelineOutcome.BLOCKED -> statusBlocked(context)
            node.type == TimelineType.INSTALL -> statusOk(context)
            node.type == TimelineType.UPDATE -> MaterialColors.getColor(view, androidx.appcompat.R.attr.colorPrimary)
            node.type == TimelineType.DOWNGRADE -> statusWarn(context)
            node.type == TimelineType.UNINSTALL -> error
            node.type == TimelineType.REINSTALL ->
                MaterialColors.getColor(view, com.google.android.material.R.attr.colorSecondary)
            else -> MaterialColors.getColor(view, com.google.android.material.R.attr.colorOutline)
        }
    }

    fun lineColor(view: View): Int =
        MaterialColors.getColor(view, com.google.android.material.R.attr.colorOutlineVariant)
}
