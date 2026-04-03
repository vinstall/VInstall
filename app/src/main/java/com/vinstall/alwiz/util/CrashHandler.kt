package com.vinstall.alwiz.util

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashHandler : Thread.UncaughtExceptionHandler {

    private const val CRASH_FILE = "crash_report.txt"
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private lateinit var appContext: Context
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    fun install(context: Context) {
        appContext = context.applicationContext
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val report = buildReport(thread, throwable)
            File(appContext.filesDir, CRASH_FILE).writeText(report, Charsets.UTF_8)
        } catch (_: Exception) {
        }
        defaultHandler?.uncaughtException(thread, throwable)
    }

    fun hasPendingCrash(context: Context): Boolean {
        return File(context.filesDir, CRASH_FILE).exists()
    }

    fun readAndClear(context: Context): String? {
        val file = File(context.filesDir, CRASH_FILE)
        if (!file.exists()) return null
        val content = file.readText(Charsets.UTF_8)
        file.delete()
        return content
    }

    fun showCrashDialogIfNeeded(activity: Activity) {
        val report = readAndClear(activity) ?: return

        val tv = TextView(activity).apply {
            text = report
            textSize = 11f
            setPadding(32, 24, 32, 24)
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
        }

        val scroll = ScrollView(activity).apply {
            addView(tv)
        }

        AlertDialog.Builder(activity)
            .setTitle("Crash Report")
            .setView(scroll)
            .setPositiveButton("Copy") { _, _ ->
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("VInstall Crash Report", report))
            }
            .setNegativeButton("Dismiss", null)
            .show()
    }

    private fun buildReport(thread: Thread, throwable: Throwable): String {
        val sb = StringBuilder()
        sb.appendLine("=== VInstall Crash Report ===")
        sb.appendLine("Time   : ${fmt.format(Date())}")
        sb.appendLine("Thread : ${thread.name} (id=${thread.id})")
        sb.appendLine("Device : ${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("ABI    : ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"}")
        sb.appendLine()
        sb.appendLine("--- Stack Trace ---")
        sb.appendLine(android.util.Log.getStackTraceString(throwable))

        var cause = throwable.cause
        var depth = 0
        while (cause != null && depth < 5) {
            sb.appendLine("--- Caused by ---")
            sb.appendLine(android.util.Log.getStackTraceString(cause))
            cause = cause.cause
            depth++
        }

        return sb.toString().trimEnd()
    }
}
