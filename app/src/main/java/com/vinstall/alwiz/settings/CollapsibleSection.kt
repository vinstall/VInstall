package com.vinstall.alwiz.settings

import android.app.Activity
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.vinstall.alwiz.R
import com.vinstall.alwiz.ui.ReduceMotion
import com.vinstall.alwiz.ui.setChildRevealed

fun Activity.setupCollapsibleSection(
    title: TextView,
    key: String,
    defaultOpen: Boolean,
    helpDescription: String? = null,
    onHelp: (() -> Unit)? = null
) {
    val column = title.parent as? LinearLayout ?: return
    val index = column.indexOfChild(title)
    val density = resources.displayMetrics.density
    fun dp(value: Int): Int = (value * density).toInt()

    fun resolveBackground(attr: Int): Int {
        val value = TypedValue()
        theme.resolveAttribute(attr, value, true)
        return value.resourceId
    }

    val titleBottomMargin = (title.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0

    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    val moved = ArrayList<View>()
    for (i in index + 1 until column.childCount) moved.add(column.getChildAt(i))
    moved.forEach {
        column.removeView(it)
        content.addView(it)
    }
    column.removeView(title)

    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(40)
        isClickable = true
        isFocusable = true
        setBackgroundResource(resolveBackground(android.R.attr.selectableItemBackground))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = titleBottomMargin }
    }

    title.layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
    header.addView(title)

    if (onHelp != null) {
        val help = ImageView(this).apply {
            setImageResource(R.drawable.ic_help)
            val touch = dp(40)
            val pad = ((touch - title.textSize) / 2f).toInt().coerceAtLeast(0)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(pad, pad, pad, pad)
            contentDescription = helpDescription
            isClickable = true
            isFocusable = true
            setBackgroundResource(resolveBackground(android.R.attr.selectableItemBackgroundBorderless))
            layoutParams = LinearLayout.LayoutParams(touch, touch)
            setOnClickListener { onHelp() }
        }
        header.addView(help)
    }

    header.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) })

    val arrow = ImageView(this).apply {
        setImageResource(R.drawable.ic_chevron_right)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(4) }
    }
    header.addView(arrow)

    var open = AppSettings.isSectionOpen(this, key, defaultOpen)

    fun describe() {
        ViewCompat.setStateDescription(
            header,
            getString(if (open) R.string.section_expanded else R.string.section_collapsed)
        )
    }

    header.contentDescription = title.text
    describe()
    arrow.rotation = if (open) 90f else 0f
    content.visibility = if (open) View.VISIBLE else View.GONE

    header.setOnClickListener {
        open = !open
        AppSettings.setSectionOpen(this, key, open)
        describe()
        val target = if (open) 90f else 0f
        if (ReduceMotion.isEnabled(this)) {
            arrow.rotation = target
        } else {
            arrow.animate()
                .rotation(target)
                .setDuration(ReduceMotion.scaled(this, 200L))
                .start()
        }
        content.setChildRevealed(open, true)
    }

    column.addView(header, index)
    column.addView(content, index + 1)
}
