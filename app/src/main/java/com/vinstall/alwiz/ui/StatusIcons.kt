package com.vinstall.alwiz.ui

import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.vinstall.alwiz.R

enum class StatusIcon(@DrawableRes val res: Int) {
    SUCCESS(R.drawable.ic_status_success),
    ERROR(R.drawable.ic_status_error),
    CANCELLED(R.drawable.ic_status_cancelled),
    BLOCKED(R.drawable.ic_status_blocked)
}

fun TextView.showStatusIcon(icon: StatusIcon?, tint: Int) {
    if (icon == null) {
        setCompoundDrawablesRelative(null, null, null, null)
        return
    }
    val drawable = ContextCompat.getDrawable(context, icon.res)?.mutate() ?: return
    val wrapped = DrawableCompat.wrap(drawable)
    DrawableCompat.setTint(wrapped, tint)
    val size = (textSize * 1.15f).toInt()
    wrapped.setBounds(0, 0, size, size)
    compoundDrawablePadding = (6 * resources.displayMetrics.density).toInt()
    setCompoundDrawablesRelative(wrapped, null, null, null)
}
