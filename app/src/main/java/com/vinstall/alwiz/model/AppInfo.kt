package com.vinstall.alwiz.model

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val sourceDir: String,
    val splitSourceDirs: Array<String>?,
    val dataDir: String,
    val installTimeMs: Long,
    val updateTimeMs: Long,
    val sizeBytes: Long,
    val isSystemApp: Boolean,
    val isDebuggable: Boolean,
    val isSplitApp: Boolean,
    val icon: Drawable?,
    val requestedPermissions: List<String>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AppInfo) return false
        return packageName == other.packageName
    }

    override fun hashCode(): Int = packageName.hashCode()
}
