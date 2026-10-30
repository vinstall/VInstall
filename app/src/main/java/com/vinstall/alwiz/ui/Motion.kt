package com.vinstall.alwiz.ui

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

@SuppressLint("ClickableViewAccessibility")
fun View.applyPressAnimation(pressedScale: Float = 0.96f) {
    setOnTouchListener { v, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> v.animate()
                .scaleX(pressedScale)
                .scaleY(pressedScale)
                .setDuration(90L)
                .setInterpolator(DecelerateInterpolator())
                .start()

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(240L)
                .setInterpolator(OvershootInterpolator(2.4f))
                .start()
        }
        false
    }
}

fun View.popIn(startScale: Float = 0.7f) {
    scaleX = startScale
    scaleY = startScale
    animate()
        .scaleX(1f)
        .scaleY(1f)
        .setDuration(340L)
        .setInterpolator(OvershootInterpolator(2.6f))
        .start()
}

fun List<View>.staggerIn(
    offsetDp: Int = 12,
    stepMs: Long = 24L,
    durationMs: Long = 240L,
    maxAnimated: Int = 8
) {
    filter { it.visibility == View.VISIBLE }
        .take(maxAnimated)
        .forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = offsetDp * view.resources.displayMetrics.density
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(index * stepMs)
                .setDuration(durationMs)
                .setInterpolator(DecelerateInterpolator(1.6f))
                .start()
        }
}

fun ViewGroup.staggerChildren(
    offsetDp: Int = 12,
    stepMs: Long = 24L,
    durationMs: Long = 240L,
    maxAnimated: Int = 8
) {
    (0 until childCount)
        .map { getChildAt(it) }
        .staggerIn(offsetDp, stepMs, durationMs, maxAnimated)
}
