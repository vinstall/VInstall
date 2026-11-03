package com.vinstall.alwiz.model

data class InstallHistoryEntry(
    val id: String,
    val timestamp: Long,
    val appLabel: String,
    val packageName: String,
    val versionName: String,
    val format: String,
    val fileSize: Long,
    val status: HistoryStatus,
    val detail: String,
    val installMode: String,
    val durationMs: Long,
    val sourcePackage: String? = null
)

enum class HistoryStatus { SUCCESS, FAILED, CANCELLED, BLOCKED }
