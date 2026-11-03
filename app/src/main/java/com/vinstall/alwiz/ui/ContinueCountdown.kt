package com.vinstall.alwiz.ui

import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.widget.Button
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors

class ContinueCountdown(
    seconds: Int,
    private val onTick: (remaining: Int) -> Unit,
    private val onFinish: () -> Unit
) {

    private val handler = Handler(Looper.getMainLooper())
    private var remaining = seconds

    private val tick = object : Runnable {
        override fun run() {
            remaining -= 1
            if (remaining > 0) {
                onTick(remaining)
                handler.postDelayed(this, 1000L)
            } else {
                onFinish()
            }
        }
    }

    fun start() {
        onTick(remaining)
        handler.postDelayed(tick, 1000L)
    }

    fun cancel() {
        handler.removeCallbacks(tick)
    }
}

fun Button.applyDangerStyle() {
    val error = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorError)
    val onError = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnError)
    val onSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
    val states = arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf())
    backgroundTintList = ColorStateList(
        states,
        intArrayOf(ColorUtils.setAlphaComponent(onSurface, 31), error)
    )
    setTextColor(
        ColorStateList(
            states,
            intArrayOf(ColorUtils.setAlphaComponent(onSurface, 97), onError)
        )
    )
}
