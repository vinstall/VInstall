package com.vinstall.alwiz.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.vinstall.alwiz.settings.AppSettings

object Haptics {

    private val SUCCESS_PATTERN = longArrayOf(0L, 45L)
    private val FAILURE_PATTERN = longArrayOf(0L, 70L, 90L, 70L)

    fun success(context: Context) = play(context, SUCCESS_PATTERN)

    fun failure(context: Context) = play(context, FAILURE_PATTERN)

    private fun play(context: Context, pattern: LongArray) {
        if (!AppSettings.isHapticFeedback(context)) return
        val vibrator = vibratorOf(context) ?: return
        if (!vibrator.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        } catch (_: Exception) {
        }
    }

    private fun vibratorOf(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
}
