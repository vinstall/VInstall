package com.vinstall.alwiz.timeline

enum class TimelineType { BASELINE, INSTALL, UPDATE, DOWNGRADE, REINSTALL, UNINSTALL, ATTEMPT }

enum class TimelineOutcome { SUCCESS, FAILED, CANCELLED, BLOCKED }

data class TimelineNode(
    val id: String,
    val parentId: String?,
    val packageName: String,
    val appLabel: String,
    val type: TimelineType,
    val outcome: TimelineOutcome,
    val timestamp: Long,
    val versionName: String,
    val versionCode: Long,
    val prevVersionName: String?,
    val prevVersionCode: Long?,
    val installMode: String?,
    val source: String?,
    val installer: String?,
    val format: String?,
    val fileSize: Long,
    val signature: String?,
    val signatureChanged: Boolean,
    val minSdk: Int,
    val targetSdk: Int,
    val permissionsAdded: List<String>?,
    val permissionsRemoved: List<String>?,
    val keptData: Boolean?,
    val forced: Boolean,
    val external: Boolean,
    val detail: String?
)

data class SnapshotEntry(
    val versionName: String?,
    val versionCode: Long,
    val firstInstall: Long,
    val lastUpdate: Long
)

class InstalledState(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val firstInstall: Long,
    val lastUpdate: Long,
    val signers: Set<String>,
    val permissions: List<String>,
    val minSdk: Int,
    val targetSdk: Int,
    val installer: String?
) {
    fun toSnapshot() = SnapshotEntry(versionName, versionCode, firstInstall, lastUpdate)
}

class PackageProbe(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val installed: InstalledState?
)

class StagedInstall(
    val probe: PackageProbe,
    val mode: String,
    val stagedAt: Long
) {
    @Volatile var forced: Boolean = false
    @Volatile var keptData: Boolean? = null
}

data class TimelineSummary(
    val packageName: String,
    val label: String,
    val count: Int,
    val lastTimestamp: Long,
    val lastType: TimelineType,
    val currentVersion: String
)
