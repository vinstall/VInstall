package com.vinstall.alwiz.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.StringRes
import com.vinstall.alwiz.settings.AppSettings

private const val SYSTEM_LONG_MS = 3500L

private val mainHandler = Handler(Looper.getMainLooper())
private var activeToast: Toast? = null
private var pendingStep: Runnable? = null

fun showToast(context: Context, @StringRes resId: Int) {
    showToast(context, context.getString(resId))
}

fun showToast(context: Context, text: CharSequence) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
        mainHandler.post { showToast(context, text) }
        return
    }
    cancelActiveToast()
    presentToast(context, text, AppSettings.getToastSeconds(context) * 1000L)
}

private fun presentToast(context: Context, text: CharSequence, remainingMs: Long) {
    val toast = Toast.makeText(context, text, Toast.LENGTH_LONG)
    toast.show()
    activeToast = toast
    val slice = minOf(remainingMs, SYSTEM_LONG_MS)
    val left = remainingMs - slice
    val step = Runnable {
        toast.cancel()
        if (activeToast === toast) activeToast = null
        pendingStep = null
        if (left > 0L) presentToast(context, text, left)
    }
    pendingStep = step
    mainHandler.postDelayed(step, slice)
}

private fun cancelActiveToast() {
    pendingStep?.let { mainHandler.removeCallbacks(it) }
    pendingStep = null
    activeToast?.cancel()
    activeToast = null
}
