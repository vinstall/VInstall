package com.vinstall.alwiz.ui

import android.content.Context
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import com.vinstall.alwiz.settings.AppSettings
import kotlin.math.abs
import kotlin.math.ln

object ReduceMotion {

    const val SYSTEM = "system"
    val KEYS = arrayOf(SYSTEM, "0", "0.5", "1", "2", "3")
    val TIERS = floatArrayOf(0.5f, 1f, 2f, 3f)

    private const val LAYOUT_STAGGER_BASE_MS = 260L

    fun speed(context: Context): Float {
        val key = AppSettings.getReduceMotion(context)
        if (key == SYSTEM) return if (systemRemovesAnimations(context)) 0f else 1f
        return key.toFloatOrNull()?.coerceAtLeast(0f) ?: 1f
    }

    fun isEnabled(context: Context): Boolean = speed(context) == 0f

    fun scaled(context: Context, baseMs: Long): Long {
        val speed = speed(context)
        if (speed <= 0f) return 0L
        return (baseMs / speed).toLong().coerceAtLeast(1L)
    }

    fun tier(context: Context): Float {
        val speed = speed(context)
        if (speed <= 0f) return 1f
        return TIERS.minByOrNull { abs(ln(it / speed)) } ?: 1f
    }

    fun label(key: String): String {
        val value = key.toFloatOrNull() ?: return key
        return if (value % 1f == 0f) "${value.toInt()}x" else "${value}x"
    }

    private fun systemRemovesAnimations(context: Context): Boolean = try {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    } catch (_: Exception) {
        false
    }

    fun applyLayoutAnimations(view: View, context: Context) {
        if (view !is ViewGroup) return
        val controller = view.layoutAnimation
        if (controller != null) {
            val duration = scaled(context, LAYOUT_STAGGER_BASE_MS)
            if (duration == 0L) {
                view.layoutAnimation = null
            } else {
                controller.animation?.duration = duration
            }
        }
        for (i in 0 until view.childCount) applyLayoutAnimations(view.getChildAt(i), context)
    }
}
