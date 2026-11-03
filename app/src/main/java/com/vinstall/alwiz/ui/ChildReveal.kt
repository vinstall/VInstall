package com.vinstall.alwiz.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import com.vinstall.alwiz.R

private const val REVEAL_SHOW_MS = 260L
private const val REVEAL_HIDE_MS = 200L

fun View.setChildRevealed(show: Boolean, animate: Boolean) {
    (getTag(R.id.tag_child_reveal_anim) as? Animator)?.cancel()
    setTag(R.id.tag_child_reveal_anim, null)

    val lp = layoutParams
    val parentView = parent as? ViewGroup
    val alreadyThere = if (show) visibility == View.VISIBLE else visibility == View.GONE
    if (alreadyThere && lp.height == ViewGroup.LayoutParams.WRAP_CONTENT && alpha == 1f) return

    val canAnimate = animate && !ReduceMotion.isEnabled(context) && parentView != null && parentView.width > 0 && isAttachedToWindow
    if (!canAnimate) {
        visibility = if (show) View.VISIBLE else View.GONE
        alpha = 1f
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
        layoutParams = lp
        return
    }

    val from: Int
    val to: Int
    if (show) {
        val margins = lp as? ViewGroup.MarginLayoutParams
        val width = parentView!!.width - parentView.paddingLeft - parentView.paddingRight -
            (margins?.leftMargin ?: 0) - (margins?.rightMargin ?: 0)
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
        measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        from = if (visibility == View.VISIBLE) height else 0
        to = measuredHeight
        if (visibility != View.VISIBLE) alpha = 0f
        lp.height = from
        layoutParams = lp
        visibility = View.VISIBLE
    } else {
        from = height
        to = 0
    }

    val startAlpha = alpha
    val endAlpha = if (show) 1f else 0f
    val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = ReduceMotion.scaled(context, if (show) REVEAL_SHOW_MS else REVEAL_HIDE_MS)
        interpolator = if (show) DecelerateInterpolator(1.6f) else AccelerateInterpolator(1.2f)
        addUpdateListener { va ->
            val f = va.animatedValue as Float
            val l = layoutParams
            l.height = (from + (to - from) * f).toInt()
            layoutParams = l
            alpha = startAlpha + (endAlpha - startAlpha) * f
        }
        addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (getTag(R.id.tag_child_reveal_anim) === animation) {
                    setTag(R.id.tag_child_reveal_anim, null)
                }
                if (cancelled) return
                val l = layoutParams
                l.height = ViewGroup.LayoutParams.WRAP_CONTENT
                layoutParams = l
                alpha = 1f
                if (!show) visibility = View.GONE
            }
        })
    }
    setTag(R.id.tag_child_reveal_anim, animator)
    animator.start()
}
