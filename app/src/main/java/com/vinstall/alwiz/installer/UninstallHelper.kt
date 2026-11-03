package com.vinstall.alwiz.installer

import android.content.Context
import com.vinstall.alwiz.settings.AppSettings
import com.vinstall.alwiz.settings.InstallMode
import com.vinstall.alwiz.shizuku.ShizukuHelper
import com.vinstall.alwiz.timeline.AppTimelineManager
import com.vinstall.alwiz.util.DebugLog
import com.vinstall.alwiz.util.ShellSafe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UninstallHelper {

    fun uninstallWith(mode: InstallMode, packageName: String, keepData: Boolean): Result<Unit> =
        when (mode) {
            InstallMode.ROOT -> uninstallViaRoot(packageName, keepData)
            InstallMode.SHIZUKU -> uninstallViaShizuku(packageName, keepData)
            InstallMode.NORMAL -> Result.failure(Exception("NORMAL_MODE"))
        }

    suspend fun uninstall(context: Context, packageName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            DebugLog.i("Uninstall", "Starting uninstall for $packageName")
            val mode = AppSettings.getInstallMode(context)
            val before = AppTimelineManager.readInstalled(context, packageName)
            val result = when (mode) {
                InstallMode.ROOT -> uninstallViaRoot(packageName, false)
                InstallMode.SHIZUKU -> {
                    if (ShizukuHelper.isAvailable() && ShizukuHelper.isGranted()) {
                        if (ShizukuHelper.isNewProcessAvailable()) {
                            uninstallViaShizuku(packageName, false)
                        } else {
                            Result.failure(Exception("Shizuku version on this device does not support shell execution"))
                        }
                    } else {
                        Result.failure(Exception("Shizuku is not active or has not been granted permission"))
                    }
                }
                InstallMode.NORMAL -> Result.failure(Exception("NORMAL_MODE"))
            }
            if (result.isSuccess) {
                try {
                    AppTimelineManager.recordUninstall(context, packageName, mode.name, before, false)
                } catch (e: Exception) {
                    DebugLog.e("Uninstall", "Timeline record failed: ${e.message}")
                }
            }
            result
        }

    private fun uninstallViaRoot(packageName: String, keepData: Boolean): Result<Unit> = try {
        val flag = if (keepData) "-k " else ""
        DebugLog.d("Uninstall", "Via root: pm uninstall $flag$packageName")
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "pm uninstall $flag${ShellSafe.quote(packageName)}"))
        val output = process.inputStream.bufferedReader().readText().trim()
        val err = process.errorStream.bufferedReader().readText().trim()
        process.waitFor()
        DebugLog.d("Uninstall", "Root output: $output | err: $err")
        if (output.contains("Success", ignoreCase = true))
            Result.success(Unit)
        else
            Result.failure(Exception("Root uninstall failed: ${output.ifBlank { err }}"))
    } catch (e: Exception) {
        DebugLog.e("Uninstall", "Root exception: ${e.message}")
        Result.failure(e)
    }

    private fun uninstallViaShizuku(packageName: String, keepData: Boolean): Result<Unit> = try {
        DebugLog.d("Uninstall", "Via Shizuku: pm uninstall keepData=$keepData $packageName")
        val clazz = Class.forName("rikka.shizuku.Shizuku")
        val method = clazz.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true
        val process = method.invoke(
            null,
            if (keepData) arrayOf("pm", "uninstall", "-k", packageName) else arrayOf("pm", "uninstall", packageName),
            null,
            null
        ) as rikka.shizuku.ShizukuRemoteProcess
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        process.waitFor()
        process.destroy()
        DebugLog.d("Uninstall", "Shizuku output: $output")
        if (output.contains("Success", ignoreCase = true))
            Result.success(Unit)
        else
            Result.failure(Exception("Shizuku uninstall failed: $output"))
    } catch (e: Exception) {
        DebugLog.e("Uninstall", "Shizuku exception: ${e.message}")
        Result.failure(e)
    }
}
