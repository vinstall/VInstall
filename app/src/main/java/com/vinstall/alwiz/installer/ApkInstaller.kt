package com.vinstall.alwiz.installer

import android.content.Context
import android.net.Uri
import com.vinstall.alwiz.util.DebugLog
import com.vinstall.alwiz.util.FileUtil
import java.io.File

object ApkInstaller {

    suspend fun install(
        context: Context,
        uri: Uri,
        onStep: (String) -> Unit
    ): Result<Unit> {
        return try {
            DebugLog.d("ApkInstaller", "install: $uri")
            onStep("Copying APK...")
            val cachedApk = FileUtil.extractToCache(context, uri, "install.apk")
            DebugLog.d("ApkInstaller", "Cached: ${cachedApk.absolutePath}")
            onStep("Installing...")
            SplitInstaller.installSplits(context, listOf(cachedApk))
        } catch (e: Exception) {
            DebugLog.e("ApkInstaller", "Error: ${e.message}")
            Result.failure(e)
        }
    }
}
