package com.vinstall.alwiz.util

import com.vinstall.alwiz.R
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.fragment.app.FragmentActivity
import com.vinstall.alwiz.crash.CrashLogActivity
import com.vinstall.alwiz.model.CrashEntry
import com.vinstall.alwiz.settings.DialogHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashHandler : Thread.UncaughtExceptionHandler {

    private const val CRASH_PENDING_FILE = "crash_report.txt"

    private const val CRASH_LOG_FILE = "crash_log_persistent.txt"

    private const val ENTRY_SEPARATOR = "\n\n============================================================\n"

    private const val MAX_LOG_ENTRIES = 50

    private const val STACK_TRACE_HEADER = "--- Stack Trace ---"

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

    fun showCrashDialogIfNeeded(activity: FragmentActivity) {
        readAndClearPending(activity) ?: return

        DialogHelper.showConfirmation(
            activity = activity,
            title = activity.getString(R.string.crash_detected_title),
            message = activity.getString(R.string.crash_detected_message),
            positiveLabel = activity.getString(R.string.crash_view_report),
            negativeLabel = activity.getString(R.string.dismiss),
            onConfirm = {
                activity.startActivity(Intent(activity, CrashLogActivity::class.java))
            }
        )
    }

    @Synchronized
    private fun readRawEntries(context: Context): List<String> {
        val file = File(context.filesDir, CRASH_LOG_FILE)
        if (!file.exists()) return emptyList()
        return file.readText(Charsets.UTF_8)
            .split(ENTRY_SEPARATOR)
            .filter { it.isNotBlank() }
    }

    @Synchronized
    private fun writeRawEntries(context: Context, entries: List<String>) {
        val file = File(context.filesDir, CRASH_LOG_FILE)
        if (entries.isEmpty()) {
            file.delete()
        } else {
            file.writeText(entries.joinToString(ENTRY_SEPARATOR), Charsets.UTF_8)
        }
    }

    @Synchronized
    private fun appendToPersistentLog(context: Context, report: String) {
        val entries = readRawEntries(context).toMutableList()
        entries.add(report)
        writeRawEntries(context, entries.takeLast(MAX_LOG_ENTRIES))
    }

    fun hasCrashLog(context: Context): Boolean {
        val f = File(context.filesDir, CRASH_LOG_FILE)
        return f.exists() && f.length() > 0
    }

    fun crashLogEntryCount(context: Context): Int = readRawEntries(context).size

    fun readEntries(context: Context): List<CrashEntry> =
        readRawEntries(context).map { parseEntry(it) }.asReversed()

    @Synchronized
    fun removeEntry(context: Context, entry: CrashEntry): Boolean {
        val entries = readRawEntries(context).toMutableList()
        val index = entries.indexOf(entry.report)
        if (index < 0) return false
        entries.removeAt(index)
        writeRawEntries(context, entries)
        return true
    }

    fun clearCrashLog(context: Context) {
        writeRawEntries(context, emptyList())
    }

    private fun parseEntry(report: String): CrashEntry {
        fun field(prefix: String): String =
            report.lineSequence()
                .firstOrNull { it.startsWith(prefix) }
                ?.substringAfter(':')
                ?.trim()
                .orEmpty()

        val exceptionLine = report.lineSequence()
            .dropWhile { it.trim() != STACK_TRACE_HEADER }
            .drop(1)
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
            .trim()

        val type = exceptionLine.substringBefore(':').trim().substringAfterLast('.')
        val message = if (exceptionLine.contains(':')) exceptionLine.substringAfter(':').trim() else ""

        return CrashEntry(
            time = field("Time").substringBefore('.'),
            thread = field("Thread"),
            device = field("Device"),
            exceptionType = type.ifEmpty { "Unknown" },
            exceptionMessage = message,
            report = report
        )
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
        sb.appendLine(STACK_TRACE_HEADER)
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
