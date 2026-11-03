package com.vinstall.alwiz.history

import android.content.Context
import com.vinstall.alwiz.R
import com.vinstall.alwiz.model.InstallHistoryEntry

private val uuidRegex = Regex("^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$")
private val packageExtensions = setOf("apk", "xapk", "apks", "apkm", "apkv", "zip")

fun InstallHistoryEntry.displayTitle(context: Context): String {
    val label = appLabel.trim()
    val extension = label.substringAfterLast('.', "").lowercase()
    val base = if (extension in packageExtensions) label.substringBeforeLast('.') else label
    return when {
        base.isNotBlank() && !uuidRegex.matches(base) -> base
        packageName.isNotEmpty() -> packageName
        else -> context.getString(R.string.caller_unknown)
    }
}

fun InstallHistoryEntry.displaySubtitle(): String =
    packageName.ifEmpty { detail.lineSequence().firstOrNull().orEmpty().trim() }
