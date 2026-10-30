package com.vinstall.alwiz.ui

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

fun View.applySystemBarInsets() {
    val baseLeft = paddingLeft
    val baseTop = paddingTop
    val baseRight = paddingRight
    val baseBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val types = WindowInsetsCompat.Type.systemBars() or
            WindowInsetsCompat.Type.displayCutout() or
            WindowInsetsCompat.Type.ime()
        val bars = insets.getInsets(types)
        v.setPadding(
            baseLeft + bars.left,
            baseTop + bars.top,
            baseRight + bars.right,
            baseBottom + bars.bottom
        )
        WindowInsetsCompat.CONSUMED
    }
    ViewCompat.requestApplyInsets(this)
}
