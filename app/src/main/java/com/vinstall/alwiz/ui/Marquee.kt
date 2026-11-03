package com.vinstall.alwiz.ui

import android.animation.ValueAnimator
import android.os.Build
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.animation.Interpolator
import android.widget.TextView
import com.vinstall.alwiz.R
import kotlin.math.abs

private const val SCROLL_DP_PER_SECOND = 40f
private const val HOLD_FRACTION = 0.2f
private const val START_DELAY_MS = 800L
private const val MIN_LEG_MS = 600L

private const val MODE_NONE = -1
private const val MODE_MULTILINE = 0
private const val MODE_FIT = 1
private const val MODE_STATIC = 2
private const val MODE_SCROLL = 3

private val holdInterpolator = Interpolator { t ->
    when {
        t <= HOLD_FRACTION -> 0f
        t >= 1f - HOLD_FRACTION -> 1f
        else -> (t - HOLD_FRACTION) / (1f - 2f * HOLD_FRACTION)
    }
}

fun TextView.enableMarquee(scroll: Boolean = true) {
    if (getTag(R.id.tag_bounce_marquee) != null) return
    if (id == android.R.id.message) return
    setTag(R.id.tag_bounce_marquee, BounceMarquee(this, scroll))
}

private class BounceMarquee(
    private val view: TextView,
    private val scroll: Boolean
) : View.OnLayoutChangeListener, View.OnAttachStateChangeListener, TextWatcher {

    private val baseGravity = view.gravity
    private val baseMaxLines = view.maxLines
    private val baseEllipsize = view.ellipsize
    private var mode = MODE_NONE
    private var distance = 0f
    private var animator: ValueAnimator? = null
    private var pending = false

    private val updateRunnable = Runnable {
        pending = false
        update()
    }

    init {
        view.addOnLayoutChangeListener(this)
        view.addOnAttachStateChangeListener(this)
        view.addTextChangedListener(this)
        view.maxLines = 1
        view.ellipsize = TextUtils.TruncateAt.END
        if (view.isAttachedToWindow) schedule()
    }

    private fun schedule() {
        if (pending) return
        pending = true
        view.post(updateRunnable)
    }

    private fun animationsAllowed(): Boolean {
        if (ReduceMotion.isEnabled(view.context)) return false
        return Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()
    }

    private fun update() {
        if (!view.isAttachedToWindow) return
        val text = view.text
        if (text.contains('\n')) {
            apply(MODE_MULTILINE, 0f)
            return
        }
        val available = view.width - view.compoundPaddingLeft - view.compoundPaddingRight
        if (available <= 0 || text.isEmpty()) {
            apply(MODE_FIT, 0f)
            return
        }
        val textWidth = view.paint.measureText(text, 0, text.length)
        val overflow = textWidth - available
        when {
            overflow <= 0.5f -> apply(MODE_FIT, 0f)
            scroll && animationsAllowed() -> apply(MODE_SCROLL, overflow)
            else -> apply(MODE_STATIC, 0f)
        }
    }

    private fun apply(newMode: Int, newDistance: Float) {
        if (newMode == mode && (newMode != MODE_SCROLL || abs(newDistance - distance) < 1f)) return
        val wasScroll = mode == MODE_SCROLL
        mode = newMode
        distance = newDistance
        stopAnimation()
        when (newMode) {
            MODE_MULTILINE -> {
                view.setHorizontallyScrolling(false)
                view.maxLines = baseMaxLines
                view.ellipsize = baseEllipsize
                view.gravity = baseGravity
            }

            MODE_SCROLL -> {
                if (!wasScroll) {
                    view.maxLines = 1
                    view.ellipsize = null
                    view.gravity = (baseGravity and Gravity.VERTICAL_GRAVITY_MASK) or Gravity.START
                    view.setHorizontallyScrolling(true)
                }
                startAnimation()
            }

            else -> {
                view.setHorizontallyScrolling(false)
                view.maxLines = 1
                view.ellipsize = TextUtils.TruncateAt.END
                view.gravity = baseGravity
            }
        }
        if (newMode != MODE_SCROLL) view.scrollTo(0, 0)
    }

    private fun startAnimation() {
        val pxPerSecond = SCROLL_DP_PER_SECOND * view.resources.displayMetrics.density
        val moveMs = distance / pxPerSecond * 1000f
        val legMs = (moveMs / (1f - 2f * HOLD_FRACTION)).toLong().coerceAtLeast(MIN_LEG_MS)
        view.scrollTo(0, 0)
        animator = ValueAnimator.ofFloat(0f, distance).apply {
            duration = ReduceMotion.scaled(view.context, legMs)
            startDelay = START_DELAY_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = holdInterpolator
            addUpdateListener { view.scrollTo((it.animatedValue as Float).toInt(), 0) }
            start()
        }
    }

    private fun stopAnimation() {
        animator?.cancel()
        animator = null
    }

    override fun onLayoutChange(
        v: View, left: Int, top: Int, right: Int, bottom: Int,
        oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
    ) {
        if (right - left != oldRight - oldLeft) schedule()
    }

    override fun onViewAttachedToWindow(v: View) {
        schedule()
    }

    override fun onViewDetachedFromWindow(v: View) {
        stopAnimation()
        view.removeCallbacks(updateRunnable)
        pending = false
        if (mode == MODE_SCROLL) view.scrollTo(0, 0)
        mode = MODE_NONE
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

    override fun afterTextChanged(s: Editable?) {
        schedule()
    }
}
