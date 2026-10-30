package com.vinstall.alwiz.backup

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
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
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0)
        }

        val result = packages
            .mapNotNull { toAppInfo(pm, it, includeSystem, detailed = false) }
            .sortedBy { it.label.lowercase() }

        DebugLog.d("BackupManager", "Found ${result.size} applications")
        result
    }

    suspend fun getApp(context: Context, packageName: String): AppInfo? =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            try {
                val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(
                        packageName,
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
                }
                toAppInfo(pm, info, includeSystem = true, detailed = true)
            } catch (_: Exception) {
                null
            }
        }

    private fun toAppInfo(
        pm: PackageManager,
        pkgInfo: PackageInfo,
        includeSystem: Boolean,
        detailed: Boolean
    ): AppInfo? {
        return try {
            val appInfo = pkgInfo.applicationInfo ?: return null
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (!includeSystem && isSystem) return null

            val splitDirs = appInfo.splitSourceDirs
            val isSplit = !splitDirs.isNullOrEmpty()

            val apkSize = try {
                var total = File(appInfo.sourceDir).takeIf { it.exists() }?.length() ?: 0L
                splitDirs?.forEach { path ->
                    val f = File(path)
                    if (f.exists()) total += f.length()
                }
                total
            } catch (_: Exception) {
                0L
            }

            AppInfo(
                packageName = pkgInfo.packageName,
                label = pm.getApplicationLabel(appInfo).toString(),
                versionName = pkgInfo.versionName ?: "?",
                versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkgInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pkgInfo.versionCode.toLong()
                },
                minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) appInfo.minSdkVersion else 0,
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
                icon = if (detailed) {
                    try { pm.getApplicationIcon(appInfo) } catch (_: Exception) { null }
                } else null,
                requestedPermissions = if (detailed) {
                    pkgInfo.requestedPermissions?.toList() ?: emptyList()
                } else emptyList()
            )
        } catch (_: Exception) {
            null
        }
    }
}
