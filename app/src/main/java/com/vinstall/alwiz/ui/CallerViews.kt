package com.vinstall.alwiz.ui

import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import com.google.android.material.color.MaterialColors
import com.vinstall.alwiz.R
import com.vinstall.alwiz.model.CallerInfo

fun bindCallerRow(root: View, caller: CallerInfo) {
    val icon = root.findViewById<ImageView>(R.id.image_caller_icon)
    val badge = root.findViewById<ImageView>(R.id.image_caller_badge)
    val label = root.findViewById<TextView>(R.id.text_caller_label)
    val pkg = root.findViewById<TextView>(R.id.text_caller_package)

    if (caller.icon != null) {
        icon.setImageDrawable(caller.icon)
    } else {
        icon.setImageResource(android.R.drawable.sym_def_app_icon)
    }
    label.text = caller.label
    pkg.text = caller.packageName
    pkg.isVisible = caller.packageName.isNotEmpty()

    val context = root.context
    if (caller.trusted) {
        badge.setImageResource(R.drawable.ic_verified)
        badge.contentDescription = context.getString(R.string.caller_badge_verified)
        val tint = MaterialColors.getColor(root, androidx.appcompat.R.attr.colorPrimary)
        ImageViewCompat.setImageTintList(badge, ColorStateList.valueOf(tint))
    } else {
        badge.setImageResource(R.drawable.ic_warning)
        badge.contentDescription = context.getString(R.string.caller_badge_untrusted)
        val tint = statusWarn(context)
        ImageViewCompat.setImageTintList(badge, ColorStateList.valueOf(tint))
    }
}
