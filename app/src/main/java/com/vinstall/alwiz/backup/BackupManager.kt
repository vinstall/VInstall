package com.vinstall.alwiz.backup

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.vinstall.alwiz.model.AppInfo
import com.vinstall.alwiz.util.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object BackupManager {

    suspend fun listInstalledApps(
        context: Context,
        includeSystem: Boolean = false
    ): List<AppInfo> = withContext(Dispatchers.IO) {
        DebugLog.d("BackupManager", "listInstalledApps includeSystem=$includeSystem")
        val pm = context.packageManager

        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        }

        val result = packages.mapNotNull { pkgInfo ->
            try {
                val appInfo = pkgInfo.applicationInfo ?: return@mapNotNull null
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (!includeSystem && isSystem) return@mapNotNull null

                val splitDirs = appInfo.splitSourceDirs
                val isSplit = !splitDirs.isNullOrEmpty()

                val apkSize = try {
                    val base = File(appInfo.sourceDir)
                    var total = if (base.exists()) base.length() else 0L
                    splitDirs?.forEach { sp ->
                        val f = File(sp)
                        if (f.exists()) total += f.length()
                    }
                    total
                } catch (_: Exception) { 0L }

                AppInfo(
                    packageName = pkgInfo.packageName,
                    label = pm.getApplicationLabel(appInfo).toString(),
                    versionName = pkgInfo.versionName ?: "?",
                    versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                        pkgInfo.longVersionCode else pkgInfo.versionCode.toLong(),
                    minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                        appInfo.minSdkVersion else 0,
                    targetSdk = appInfo.targetSdkVersion,
                    sourceDir = appInfo.sourceDir,
                    splitSourceDirs = splitDirs,
                    dataDir = appInfo.dataDir,
                    installTimeMs = pkgInfo.firstInstallTime,
                    updateTimeMs = pkgInfo.lastUpdateTime,
                    sizeBytes = apkSize,
                    isSystemApp = isSystem,
                    isDebuggable = (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
                    isSplitApp = isSplit,
                    icon = try { pm.getApplicationIcon(appInfo) } catch (_: Exception) { null },
                    requestedPermissions = pkgInfo.requestedPermissions?.toList() ?: emptyList()
                )
            } catch (_: Exception) {
                null
            }
        }.sortedBy { it.label.lowercase() }

        DebugLog.d("BackupManager", "Found ${result.size} applications")
        result
    }
}
