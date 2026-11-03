package com.vinstall.alwiz.timeline

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vinstall.alwiz.model.HistoryStatus
import com.vinstall.alwiz.model.InstallHistoryEntry
import com.vinstall.alwiz.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object AppTimelineManager {

    private const val NODES_FILE = "app_timeline.json"
    private const val SNAPSHOT_FILE = "app_timeline_snapshot.json"
    private const val MAX_NODES_PER_APP = 150
    private const val STAGE_TTL_MS = 30 * 60 * 1000L
    private const val TIME_TOLERANCE_MS = 1_000L
    private const val INSTALL_UPDATE_GAP_MS = 5_000L

    private val gson = Gson()
    private val lock = Any()
    private val staged = ConcurrentHashMap<String, StagedInstall>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nodesType = object : TypeToken<LinkedHashMap<String, MutableList<TimelineNode>>>() {}.type
    private val snapshotType = object : TypeToken<LinkedHashMap<String, SnapshotEntry>>() {}.type

    fun syncAsync(context: Context) {
        val app = context.applicationContext
        scope.launch {
            try {
                syncSnapshots(app)
            } catch (e: Exception) {
                DebugLog.e("Timeline", "Sync failed: ${e.message}")
            }
        }
    }

    fun probeAndStage(context: Context, files: List<File>, mode: String): PackageProbe? {
        return try {
            val pm = context.packageManager
            val base = files.firstOrNull { it.name.equals("base.apk", ignoreCase = true) }
                ?: files.firstOrNull { archiveInfo(pm, it) != null }
                ?: return null
            val info = archiveInfo(pm, base) ?: return null
            val packageName = info.packageName ?: return null
            val probe = PackageProbe(
                packageName = packageName,
                label = archiveLabel(pm, info, base),
                versionName = info.versionName.orEmpty(),
                versionCode = versionCodeOf(info),
                installed = readInstalled(context, packageName)
            )
            val now = System.currentTimeMillis()
            staged.filterValues { now - it.stagedAt >= STAGE_TTL_MS }.keys.forEach { staged.remove(it) }
            staged[packageName] = StagedInstall(probe, mode, now)
            probe
        } catch (e: Exception) {
            DebugLog.e("Timeline", "Probe failed: ${e.message}")
            null
        }
    }

    fun markAdvanced(packageName: String, forced: Boolean, keptData: Boolean?) {
        staged[packageName]?.let {
            it.forced = forced
            it.keptData = keptData
        }
    }

    fun onHistoryEntry(context: Context, entry: InstallHistoryEntry) {
        val packageName = entry.packageName
        if (packageName.isEmpty()) return
        val stage = staged.remove(packageName)
            ?.takeIf { System.currentTimeMillis() - it.stagedAt < STAGE_TTL_MS }
        synchronized(lock) {
            val all = loadNodes(context)
            val chain = all.getOrPut(packageName) { mutableListOf() }
            val snapshot = loadSnapshot(context)
            val now = readInstalled(context, packageName)
            if (entry.status == HistoryStatus.SUCCESS) {
                addInstallNode(chain, entry, stage, now, snapshot[packageName])
                if (now != null) {
                    snapshot[packageName] = now.toSnapshot()
                    saveSnapshot(context, snapshot)
                }
            } else {
                addAttemptNode(chain, entry, stage, now)
            }
            prune(chain)
            saveNodes(context, all)
        }
    }

    fun recordUninstall(
        context: Context,
        packageName: String,
        mode: String,
        before: InstalledState?,
        keptData: Boolean
    ) {
        synchronized(lock) {
            val snapshot = loadSnapshot(context)
            val all = loadNodes(context)
            val chain = all.getOrPut(packageName) { mutableListOf() }
            val snap = snapshot[packageName]
            seedBaselineIfEmpty(chain, packageName, before, snap, System.currentTimeMillis() - 1)
            val head = lifecycleHead(chain)
            if (head?.type != TimelineType.UNINSTALL) {
                chain += TimelineNode(
                    id = UUID.randomUUID().toString(),
                    parentId = head?.id,
                    packageName = packageName,
                    appLabel = before?.label ?: head?.appLabel ?: packageName,
                    type = TimelineType.UNINSTALL,
                    outcome = TimelineOutcome.SUCCESS,
                    timestamp = System.currentTimeMillis(),
                    versionName = before?.versionName ?: head?.versionName ?: snap?.versionName.orEmpty(),
                    versionCode = before?.versionCode ?: head?.versionCode ?: snap?.versionCode ?: 0L,
                    prevVersionName = null,
                    prevVersionCode = null,
                    installMode = mode,
                    source = null,
                    installer = null,
                    format = null,
                    fileSize = 0L,
                    signature = null,
                    signatureChanged = false,
                    minSdk = 0,
                    targetSdk = 0,
                    permissionsAdded = null,
                    permissionsRemoved = null,
                    keptData = keptData,
                    forced = false,
                    external = false,
                    detail = null
                )
            }
            prune(chain)
            saveNodes(context, all)
            if (snapshot.remove(packageName) != null) saveSnapshot(context, snapshot)
        }
    }

    fun syncSnapshots(context: Context) {
        synchronized(lock) {
            val file = File(context.filesDir, SNAPSHOT_FILE)
            val current = currentSnapshot(context)
            if (!file.exists()) {
                saveSnapshot(context, current)
                return
            }
            val previous = loadSnapshot(context)
            val all = loadNodes(context)
            val now = System.currentTimeMillis()
            val inFlight = staged.filterValues { now - it.stagedAt < STAGE_TTL_MS }.keys
            var changed = false

            for (packageName in current.keys.toList()) {
                val prev = previous[packageName]
                if (packageName in inFlight) {
                    if (prev != null) current[packageName] = prev else current.remove(packageName)
                    continue
                }
                val fresh = current.getValue(packageName)
                val type = when {
                    prev == null -> TimelineType.INSTALL
                    fresh.versionCode > prev.versionCode -> TimelineType.UPDATE
                    fresh.versionCode < prev.versionCode -> TimelineType.DOWNGRADE
                    fresh.lastUpdate - prev.lastUpdate > TIME_TOLERANCE_MS -> TimelineType.REINSTALL
                    else -> null
                } ?: continue
                if (addExternalNode(context, all, packageName, prev, type)) changed = true
            }

            for ((packageName, prev) in previous) {
                if (packageName in current || packageName in inFlight) continue
                addExternalUninstall(all, packageName, prev)
                changed = true
            }

            saveSnapshot(context, current)
            if (changed) saveNodes(context, all)
        }
    }

    fun ensureBaseline(context: Context, packageName: String) {
        synchronized(lock) {
            val all = loadNodes(context)
            val chain = all.getOrPut(packageName) { mutableListOf() }
            if (chain.isNotEmpty()) return
            val state = readInstalled(context, packageName) ?: return
            chain += baselineNode(packageName, state.label, state.versionName, state.versionCode, state.lastUpdate, state)
            saveNodes(context, all)
        }
    }

    fun nodesFor(context: Context, packageName: String): List<TimelineNode> =
        synchronized(lock) { loadNodes(context)[packageName]?.toList().orEmpty() }

    fun summaries(context: Context): List<TimelineSummary> = synchronized(lock) {
        loadNodes(context).mapNotNull { (packageName, list) ->
            if (list.isEmpty()) return@mapNotNull null
            val head = lifecycleHead(list)
            TimelineSummary(
                packageName = packageName,
                label = list.lastOrNull { it.appLabel.isNotBlank() }?.appLabel ?: packageName,
                count = list.size,
                lastTimestamp = list.maxOf { it.timestamp },
                lastType = list.last().type,
                currentVersion = head?.takeIf { it.type != TimelineType.UNINSTALL }?.versionName.orEmpty()
            )
        }.sortedByDescending { it.lastTimestamp }
    }

    fun clearAll(context: Context) {
        synchronized(lock) { File(context.filesDir, NODES_FILE).delete() }
    }

    fun readInstalled(context: Context, packageName: String): InstalledState? {
        if (packageName.isEmpty()) return null
        val pm = context.packageManager
        val flags = PackageManager.GET_PERMISSIONS or signatureFlag()
        val info = try {
            packageInfo(pm, packageName, flags)
        } catch (_: Exception) {
            return null
        }
        val applicationInfo = info.applicationInfo
        val label = try {
            applicationInfo?.let { pm.getApplicationLabel(it).toString() }
        } catch (_: Exception) {
            null
        }
        return InstalledState(
            packageName = info.packageName,
            label = label?.takeIf { it.isNotBlank() } ?: info.packageName,
            versionName = info.versionName.orEmpty(),
            versionCode = versionCodeOf(info),
            firstInstall = info.firstInstallTime,
            lastUpdate = info.lastUpdateTime,
            signers = signersOf(info),
            permissions = info.requestedPermissions?.toList().orEmpty().sorted(),
            minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) applicationInfo?.minSdkVersion ?: 0 else 0,
            targetSdk = applicationInfo?.targetSdkVersion ?: 0,
            installer = installerOf(pm, packageName)
        )
    }

    private fun addInstallNode(
        chain: MutableList<TimelineNode>,
        entry: InstallHistoryEntry,
        stage: StagedInstall?,
        now: InstalledState?,
        snap: SnapshotEntry?
    ) {
        val packageName = entry.packageName
        val prev = stage?.probe?.installed
        val prevName = prev?.versionName ?: snap?.versionName
        val prevCode = prev?.versionCode ?: snap?.versionCode
        val label = now?.label ?: stage?.probe?.label ?: entry.appLabel.ifBlank { packageName }

        if (chain.isEmpty() && prevCode != null) {
            chain += baselineNode(
                packageName, label, prevName.orEmpty(), prevCode,
                prev?.lastUpdate ?: snap?.lastUpdate ?: (entry.timestamp - 1), prev
            )
        }

        val newCode = now?.versionCode ?: stage?.probe?.versionCode ?: 0L
        val newName = now?.versionName ?: stage?.probe?.versionName ?: entry.versionName
        val type = when {
            stage != null && stage.probe.installed == null -> TimelineType.INSTALL
            prevCode != null -> when {
                newCode > prevCode -> TimelineType.UPDATE
                newCode < prevCode -> TimelineType.DOWNGRADE
                else -> TimelineType.REINSTALL
            }
            now != null && now.lastUpdate - now.firstInstall > INSTALL_UPDATE_GAP_MS -> TimelineType.UPDATE
            else -> TimelineType.INSTALL
        }

        val oldSigners = prev?.signers.orEmpty()
        val newSigners = now?.signers.orEmpty()
        val added = if (prev != null && now != null) (now.permissions - prev.permissions.toSet()) else null
        val removed = if (prev != null && now != null) (prev.permissions - now.permissions.toSet()) else null

        chain += TimelineNode(
            id = UUID.randomUUID().toString(),
            parentId = parentFor(chain, type, newCode),
            packageName = packageName,
            appLabel = label,
            type = type,
            outcome = TimelineOutcome.SUCCESS,
            timestamp = entry.timestamp,
            versionName = newName,
            versionCode = newCode,
            prevVersionName = prevName,
            prevVersionCode = prevCode,
            installMode = entry.installMode,
            source = entry.sourcePackage,
            installer = now?.installer,
            format = entry.format,
            fileSize = entry.fileSize,
            signature = newSigners.takeIf { it.isNotEmpty() }?.sorted()?.joinToString(","),
            signatureChanged = oldSigners.isNotEmpty() && newSigners.isNotEmpty() && oldSigners != newSigners,
            minSdk = now?.minSdk ?: 0,
            targetSdk = now?.targetSdk ?: 0,
            permissionsAdded = added?.takeIf { it.isNotEmpty() },
            permissionsRemoved = removed?.takeIf { it.isNotEmpty() },
            keptData = stage?.keptData,
            forced = stage?.forced == true,
            external = false,
            detail = entry.detail.takeIf { it.isNotBlank() }
        )
    }

    private fun addAttemptNode(
        chain: MutableList<TimelineNode>,
        entry: InstallHistoryEntry,
        stage: StagedInstall?,
        now: InstalledState?
    ) {
        val packageName = entry.packageName
        if (chain.isEmpty()) {
            val current = now ?: stage?.probe?.installed
            if (current != null) {
                chain += baselineNode(
                    packageName, current.label, current.versionName, current.versionCode, current.lastUpdate, current
                )
            }
        }
        val head = lifecycleHead(chain)
        val outcome = when (entry.status) {
            HistoryStatus.CANCELLED -> TimelineOutcome.CANCELLED
            HistoryStatus.BLOCKED -> TimelineOutcome.BLOCKED
            else -> TimelineOutcome.FAILED
        }
        chain += TimelineNode(
            id = UUID.randomUUID().toString(),
            parentId = head?.id,
            packageName = packageName,
            appLabel = stage?.probe?.label ?: now?.label ?: entry.appLabel.ifBlank { packageName },
            type = TimelineType.ATTEMPT,
            outcome = outcome,
            timestamp = entry.timestamp,
            versionName = stage?.probe?.versionName ?: entry.versionName,
            versionCode = stage?.probe?.versionCode ?: 0L,
            prevVersionName = head?.versionName,
            prevVersionCode = head?.versionCode,
            installMode = entry.installMode,
            source = entry.sourcePackage,
            installer = null,
            format = entry.format,
            fileSize = entry.fileSize,
            signature = null,
            signatureChanged = false,
            minSdk = 0,
            targetSdk = 0,
            permissionsAdded = null,
            permissionsRemoved = null,
            keptData = stage?.keptData,
            forced = stage?.forced == true,
            external = false,
            detail = entry.detail.takeIf { it.isNotBlank() }
        )
    }

    private fun addExternalNode(
        context: Context,
        all: LinkedHashMap<String, MutableList<TimelineNode>>,
        packageName: String,
        prev: SnapshotEntry?,
        type: TimelineType
    ): Boolean {
        val state = readInstalled(context, packageName) ?: return false
        val chain = all.getOrPut(packageName) { mutableListOf() }
        if (chain.isEmpty() && prev != null) {
            chain += baselineNode(
                packageName, state.label, prev.versionName.orEmpty(), prev.versionCode, prev.lastUpdate, null
            )
        }
        val head = lifecycleHead(chain)
        val signature = state.signers.takeIf { it.isNotEmpty() }?.sorted()?.joinToString(",")
        chain += TimelineNode(
            id = UUID.randomUUID().toString(),
            parentId = parentFor(chain, type, state.versionCode),
            packageName = packageName,
            appLabel = state.label,
            type = type,
            outcome = TimelineOutcome.SUCCESS,
            timestamp = state.lastUpdate.takeIf { it > 0 } ?: System.currentTimeMillis(),
            versionName = state.versionName,
            versionCode = state.versionCode,
            prevVersionName = prev?.versionName ?: head?.versionName,
            prevVersionCode = prev?.versionCode ?: head?.versionCode,
            installMode = null,
            source = null,
            installer = state.installer,
            format = null,
            fileSize = 0L,
            signature = signature,
            signatureChanged = head?.signature != null && signature != null && head.signature != signature,
            minSdk = state.minSdk,
            targetSdk = state.targetSdk,
            permissionsAdded = null,
            permissionsRemoved = null,
            keptData = null,
            forced = false,
            external = true,
            detail = null
        )
        prune(chain)
        return true
    }

    private fun addExternalUninstall(
        all: LinkedHashMap<String, MutableList<TimelineNode>>,
        packageName: String,
        prev: SnapshotEntry
    ) {
        val chain = all.getOrPut(packageName) { mutableListOf() }
        if (chain.isEmpty()) {
            chain += baselineNode(packageName, packageName, prev.versionName.orEmpty(), prev.versionCode, prev.lastUpdate, null)
        }
        val head = lifecycleHead(chain)
        if (head?.type == TimelineType.UNINSTALL) return
        chain += TimelineNode(
            id = UUID.randomUUID().toString(),
            parentId = head?.id,
            packageName = packageName,
            appLabel = chain.lastOrNull { it.appLabel.isNotBlank() }?.appLabel ?: packageName,
            type = TimelineType.UNINSTALL,
            outcome = TimelineOutcome.SUCCESS,
            timestamp = System.currentTimeMillis(),
            versionName = head?.versionName ?: prev.versionName.orEmpty(),
            versionCode = head?.versionCode ?: prev.versionCode,
            prevVersionName = null,
            prevVersionCode = null,
            installMode = null,
            source = null,
            installer = null,
            format = null,
            fileSize = 0L,
            signature = null,
            signatureChanged = false,
            minSdk = 0,
            targetSdk = 0,
            permissionsAdded = null,
            permissionsRemoved = null,
            keptData = null,
            forced = false,
            external = true,
            detail = null
        )
        prune(chain)
    }

    private fun seedBaselineIfEmpty(
        chain: MutableList<TimelineNode>,
        packageName: String,
        before: InstalledState?,
        snap: SnapshotEntry?,
        timestamp: Long
    ) {
        if (chain.isNotEmpty()) return
        when {
            before != null -> chain += baselineNode(
                packageName, before.label, before.versionName, before.versionCode, before.lastUpdate, before
            )
            snap != null -> chain += baselineNode(
                packageName, packageName, snap.versionName.orEmpty(), snap.versionCode,
                snap.lastUpdate.takeIf { it > 0 } ?: timestamp, null
            )
        }
    }

    private fun baselineNode(
        packageName: String,
        label: String,
        versionName: String,
        versionCode: Long,
        timestamp: Long,
        state: InstalledState?
    ) = TimelineNode(
        id = UUID.randomUUID().toString(),
        parentId = null,
        packageName = packageName,
        appLabel = label,
        type = TimelineType.BASELINE,
        outcome = TimelineOutcome.SUCCESS,
        timestamp = timestamp,
        versionName = versionName,
        versionCode = versionCode,
        prevVersionName = null,
        prevVersionCode = null,
        installMode = null,
        source = null,
        installer = state?.installer,
        format = null,
        fileSize = 0L,
        signature = state?.signers?.takeIf { it.isNotEmpty() }?.sorted()?.joinToString(","),
        signatureChanged = false,
        minSdk = state?.minSdk ?: 0,
        targetSdk = state?.targetSdk ?: 0,
        permissionsAdded = null,
        permissionsRemoved = null,
        keptData = null,
        forced = false,
        external = false,
        detail = null
    )

    private fun lifecycleHead(chain: List<TimelineNode>): TimelineNode? =
        chain.lastOrNull { it.type != TimelineType.ATTEMPT }

    private fun parentFor(chain: List<TimelineNode>, type: TimelineType, newCode: Long): String? {
        val head = lifecycleHead(chain)
        if (type != TimelineType.DOWNGRADE) return head?.id
        val rewind = chain.lastOrNull {
            it !== head &&
                it.type != TimelineType.ATTEMPT &&
                it.type != TimelineType.UNINSTALL &&
                it.versionCode == newCode
        }
        return (rewind ?: head)?.id
    }

    private fun prune(chain: MutableList<TimelineNode>) {
        while (chain.size > MAX_NODES_PER_APP) {
            val victim = chain.firstOrNull { it.type == TimelineType.ATTEMPT }
                ?: chain.firstOrNull { it.type != TimelineType.BASELINE }
                ?: chain.first()
            for (i in chain.indices) {
                if (chain[i].parentId == victim.id) chain[i] = chain[i].copy(parentId = victim.parentId)
            }
            chain.remove(victim)
        }
    }

    private fun loadNodes(context: Context): LinkedHashMap<String, MutableList<TimelineNode>> {
        val file = File(context.filesDir, NODES_FILE)
        if (!file.exists()) return LinkedHashMap()
        return try {
            gson.fromJson<LinkedHashMap<String, MutableList<TimelineNode>>>(file.readText(), nodesType)
                ?: LinkedHashMap()
        } catch (_: Exception) {
            LinkedHashMap()
        }
    }

    private fun saveNodes(context: Context, map: LinkedHashMap<String, MutableList<TimelineNode>>) {
        File(context.filesDir, NODES_FILE).writeText(gson.toJson(map))
    }

    private fun loadSnapshot(context: Context): LinkedHashMap<String, SnapshotEntry> {
        val file = File(context.filesDir, SNAPSHOT_FILE)
        if (!file.exists()) return LinkedHashMap()
        return try {
            gson.fromJson<LinkedHashMap<String, SnapshotEntry>>(file.readText(), snapshotType) ?: LinkedHashMap()
        } catch (_: Exception) {
            LinkedHashMap()
        }
    }

    private fun saveSnapshot(context: Context, map: LinkedHashMap<String, SnapshotEntry>) {
        File(context.filesDir, SNAPSHOT_FILE).writeText(gson.toJson(map))
    }

    @Suppress("DEPRECATION")
    private fun currentSnapshot(context: Context): LinkedHashMap<String, SnapshotEntry> {
        val pm = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            pm.getInstalledPackages(0)
        }
        val result = LinkedHashMap<String, SnapshotEntry>()
        for (info in packages) {
            val flags = info.applicationInfo?.flags ?: 0
            val isSystem = flags and ApplicationInfo.FLAG_SYSTEM != 0
            val isUpdated = flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
            if (isSystem && !isUpdated) continue
            result[info.packageName] = SnapshotEntry(
                info.versionName, versionCodeOf(info), info.firstInstallTime, info.lastUpdateTime
            )
        }
        return result
    }

    @Suppress("DEPRECATION")
    private fun signatureFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
        else PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun packageInfo(pm: PackageManager, packageName: String, flags: Int): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getPackageInfo(packageName, flags)
        }

    @Suppress("DEPRECATION")
    private fun archiveInfo(pm: PackageManager, file: File): PackageInfo? {
        val flags = PackageManager.GET_PERMISSIONS
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getPackageArchiveInfo(file.absolutePath, flags)
        }
    }

    private fun archiveLabel(pm: PackageManager, info: PackageInfo, file: File): String {
        val applicationInfo = info.applicationInfo ?: return info.packageName
        return try {
            applicationInfo.sourceDir = file.absolutePath
            applicationInfo.publicSourceDir = file.absolutePath
            pm.getApplicationLabel(applicationInfo).toString().ifBlank { info.packageName }
        } catch (_: Exception) {
            info.packageName
        }
    }

    @Suppress("DEPRECATION")
    private fun versionCodeOf(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun installerOf(pm: PackageManager, packageName: String): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(packageName).installingPackageName
        } else {
            pm.getInstallerPackageName(packageName)
        }
    } catch (_: Exception) {
        null
    }

    @Suppress("DEPRECATION")
    private fun signersOf(info: PackageInfo): Set<String> {
        val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        }
        return signatures?.map { digest(it) }?.toSet().orEmpty()
    }

    private fun digest(signature: Signature): String =
        MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
