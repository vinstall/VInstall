package com.vinstall.alwiz.ui

import android.annotation.SuppressLint
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

@SuppressLint("ClickableViewAccessibility")
fun View.applyPressAnimation(pressedScale: Float = 0.96f) {
    setOnTouchListener { v, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> if (!ReduceMotion.isEnabled(v.context)) {
                v.animate()
                    .scaleX(pressedScale)
                    .scaleY(pressedScale)
                    .setDuration(ReduceMotion.scaled(v.context, 90L))
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (v.scaleX != 1f || v.scaleY != 1f) {
                v.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(ReduceMotion.scaled(v.context, 240L))
                    .setInterpolator(OvershootInterpolator(2.4f))
                    .start()
            }
        }
        false
    }
}

fun View.popIn(startScale: Float = 0.7f) {
    if (ReduceMotion.isEnabled(context)) return
    scaleX = startScale
    scaleY = startScale
    animate()
        .scaleX(1f)
        .scaleY(1f)
        .setDuration(ReduceMotion.scaled(context, 340L))
        .setInterpolator(OvershootInterpolator(2.6f))
        .start()
}

fun List<View>.staggerIn(
    offsetDp: Int = 12,
    stepMs: Long = 24L,
    durationMs: Long = 240L,
    maxAnimated: Int = 8
) {
    if (isEmpty() || ReduceMotion.isEnabled(first().context)) return
    val context = first().context
    filter { it.visibility == View.VISIBLE }
        .take(maxAnimated)
        .forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = offsetDp * view.resources.displayMetrics.density
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(ReduceMotion.scaled(context, index * stepMs))
                .setDuration(ReduceMotion.scaled(context, durationMs))
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

fun beginDelayedTransitionIfAllowed(root: ViewGroup) {
    if (ReduceMotion.isEnabled(root.context)) return
    val transition = AutoTransition().setDuration(ReduceMotion.scaled(root.context, 300L))
    TransitionManager.beginDelayedTransition(root, transition)
}
