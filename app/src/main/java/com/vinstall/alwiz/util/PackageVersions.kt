package com.vinstall.alwiz.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object PackageVersions {

    fun installed(context: Context, packageName: String): Pair<String, Long>? {
        if (packageName.isEmpty()) return null
        return try {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            Pair(info.versionName ?: "", code)
        } catch (_: Exception) {
            null
        }
    }
}
