package com.vinstall.alwiz.history

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vinstall.alwiz.model.HistoryStatus
import com.vinstall.alwiz.model.InstallHistoryEntry
import com.vinstall.alwiz.settings.AppSettings
import com.vinstall.alwiz.timeline.AppTimelineManager
import java.io.File
import java.util.UUID

object InstallHistoryManager {

    private const val FILE_NAME = "install_history.json"
    private val gson = Gson()

    private fun historyFile(context: Context): File =
        File(context.filesDir, FILE_NAME)

    fun getAll(context: Context): List<InstallHistoryEntry> {
        val file = historyFile(context)
        if (!file.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<InstallHistoryEntry>>() {}.type
            gson.fromJson(file.readText(), type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun add(context: Context, entry: InstallHistoryEntry) {
        val current = getAll(context).toMutableList()
        current.add(0, entry)
        save(context, current)
        try {
            AppTimelineManager.onHistoryEntry(context, entry)
        } catch (_: Exception) {
        }
    }

    fun recordExternal(
        context: Context,
        sourcePackage: String?,
        appLabel: String,
        packageName: String,
        versionName: String,
        format: String,
        fileSize: Long,
        status: HistoryStatus,
        detail: String,
        durationMs: Long
    ) {
        add(
            context,
            InstallHistoryEntry(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                appLabel = appLabel,
                packageName = packageName,
                versionName = versionName,
                format = format,
                fileSize = fileSize,
                status = status,
                detail = detail,
                installMode = AppSettings.getInstallMode(context).name,
                durationMs = durationMs,
                sourcePackage = sourcePackage
            )
        )
    }

    @Synchronized
    fun remove(context: Context, id: String) {
        val updated = getAll(context).filter { it.id != id }
        save(context, updated)
    }

    fun clear(context: Context) {
        historyFile(context).delete()
    }

    fun count(context: Context): Int = getAll(context).size

    private fun save(context: Context, entries: List<InstallHistoryEntry>) {
        historyFile(context).writeText(gson.toJson(entries))
    }
}
