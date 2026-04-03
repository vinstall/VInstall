package com.vinstall.alwiz.util

import android.content.Context
import com.vinstall.alwiz.settings.AppSettings
import com.vinstall.alwiz.settings.InstallMode
import com.vinstall.alwiz.shizuku.ShizukuHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UninstallHelper {

    suspend fun uninstall(context: Context, packageName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            DebugLog.i("Uninstall", "Starting uninstall for $packageName")
            when (AppSettings.getInstallMode(context)) {
                InstallMode.ROOT -> uninstallViaRoot(packageName)
                InstallMode.SHIZUKU -> {
                    if (ShizukuHelper.isAvailable() && ShizukuHelper.isGranted())
                        uninstallViaShizuku(packageName)
                    else
                        Result.failure(Exception("Shizuku is not active or has not been granted permission"))
                }
                InstallMode.NORMAL -> Result.failure(Exception("NORMAL_MODE"))
            }
        }

    private fun uninstallViaRoot(packageName: String): Result<Unit> = try {
        DebugLog.d("Uninstall", "Via root: pm uninstall $packageName")
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "pm uninstall $packageName"))
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

    private fun uninstallViaShizuku(packageName: String): Result<Unit> = try {
        DebugLog.d("Uninstall", "Via Shizuku: pm uninstall $packageName")
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
            arrayOf("pm", "uninstall", packageName),
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
