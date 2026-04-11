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

    private const val CRASH_PENDING_FILE = "crash_report.txt"

    private const val CRASH_LOG_FILE = "crash_log_persistent.txt"

    private const val ENTRY_SEPARATOR = "\n\n============================================================\n"

    private const val MAX_LOG_ENTRIES = 50

    private const val UI_TAIL_ENTRIES = 10

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
            File(appContext.filesDir, CRASH_PENDING_FILE).writeText(report, Charsets.UTF_8)
            appendToPersistentLog(appContext, report)
        } catch (_: Exception) {}
        defaultHandler?.uncaughtException(thread, throwable)
    }

    fun hasPendingCrash(context: Context): Boolean =
        File(context.filesDir, CRASH_PENDING_FILE).exists()

    private fun readAndClearPending(context: Context): String? {
        val file = File(context.filesDir, CRASH_PENDING_FILE)
        if (!file.exists()) return null
        val content = file.readText(Charsets.UTF_8)
        file.delete()
        return content
    }

    fun showCrashDialogIfNeeded(activity: Activity) {
        val report = readAndClearPending(activity) ?: return

        AlertDialog.Builder(activity)
            .setTitle("Crash Detected")
            .setMessage("VInstall crashed on the previous session. The report has been saved to Settings → Crash Reports.")
            .setPositiveButton("View Report") { _, _ ->
                showReportDialog(activity, report)
            }
            .setNegativeButton("Dismiss", null)
            .show()
    }

    private fun showReportDialog(activity: Activity, report: String) {
        val tv = TextView(activity).apply {
            text = report
            textSize = 11f
            setPadding(32, 24, 32, 24)
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val scroll = ScrollView(activity).apply { addView(tv) }

        AlertDialog.Builder(activity)
            .setTitle("Crash Report")
            .setView(scroll)
            .setPositiveButton("Copy") { _, _ ->
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("VInstall Crash Report", report))
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun appendToPersistentLog(context: Context, report: String) {
        val logFile = File(context.filesDir, CRASH_LOG_FILE)

        val entries: MutableList<String> = if (logFile.exists()) {
            logFile.readText(Charsets.UTF_8)
                .split(ENTRY_SEPARATOR)
                .filter { it.isNotBlank() }
                .toMutableList()
        } else {
            mutableListOf()
        }

        entries.add(report)

        val trimmed = if (entries.size > MAX_LOG_ENTRIES) {
            entries.takeLast(MAX_LOG_ENTRIES)
        } else {
            entries
        }

        logFile.writeText(trimmed.joinToString(ENTRY_SEPARATOR), Charsets.UTF_8)
    }

    fun hasCrashLog(context: Context): Boolean {
        val f = File(context.filesDir, CRASH_LOG_FILE)
        return f.exists() && f.length() > 0
    }

    fun readCrashLogTail(context: Context, maxEntries: Int = UI_TAIL_ENTRIES): String? {
        val file = File(context.filesDir, CRASH_LOG_FILE)
        if (!file.exists()) return null

        val entries = file.readText(Charsets.UTF_8)
            .split(ENTRY_SEPARATOR)
            .filter { it.isNotBlank() }

        if (entries.isEmpty()) return null

        val tail = entries.takeLast(maxEntries)
        val header = if (entries.size > maxEntries) {
            "[ Showing ${tail.size} of ${entries.size} entries — oldest entries trimmed ]\n\n"
        } else {
            ""
        }

        return header + tail.joinToString(ENTRY_SEPARATOR)
    }

    fun crashLogEntryCount(context: Context): Int {
        val file = File(context.filesDir, CRASH_LOG_FILE)
        if (!file.exists()) return 0
        return file.readText(Charsets.UTF_8)
            .split(ENTRY_SEPARATOR)
            .count { it.isNotBlank() }
    }

    fun clearCrashLog(context: Context) {
        File(context.filesDir, CRASH_LOG_FILE).delete()
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
