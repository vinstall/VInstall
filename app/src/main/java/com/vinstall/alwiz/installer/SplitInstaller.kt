package com.vinstall.alwiz.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import com.vinstall.alwiz.receiver.InstallResultReceiver
import com.vinstall.alwiz.settings.AppSettings
import com.vinstall.alwiz.settings.InstallMode
import com.vinstall.alwiz.shizuku.ShizukuHelper
import com.vinstall.alwiz.util.DebugLog
import com.vinstall.alwiz.util.RootHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object SplitInstaller {

    suspend fun installSplits(
        context: Context,
        apkFiles: List<File>,
        selectedSplits: List<String>? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val filesToInstall = if (selectedSplits != null) {
            apkFiles.filter { f ->
                selectedSplits.any { s -> f.name.equals(s, ignoreCase = true) }
                    || f.name.equals("base.apk", ignoreCase = true)
            }
        } else {
            apkFiles
        }

        if (filesToInstall.isEmpty()) {
            return@withContext Result.failure(Exception("No APK files to install"))
        }

        val mode = AppSettings.getInstallMode(context)
        DebugLog.i("SplitInstaller", "Install mode: $mode, files: ${filesToInstall.map { it.name }}")

        return@withContext when (mode) {
            InstallMode.ROOT -> {
                if (RootHelper.isRooted()) {
                    installViaRoot(filesToInstall)
                } else {
                    DebugLog.e("SplitInstaller", "ROOT mode selected but device is not rooted, falling back to session")
                    installViaSession(context, filesToInstall)
                }
            }
            InstallMode.SHIZUKU -> {
                if (ShizukuHelper.isAvailable() && ShizukuHelper.isGranted()) {
                    installViaShizuku(context, filesToInstall)
                } else {
                    DebugLog.e("SplitInstaller", "SHIZUKU mode selected but Shizuku is not active/permitted, falling back to session")
                    installViaSession(context, filesToInstall)
                }
            }
            InstallMode.NORMAL -> installViaSession(context, filesToInstall)
        }
    }

    private suspend fun installViaSession(context: Context, apkFiles: List<File>): Result<Unit> =
        withContext(Dispatchers.IO) {
            DebugLog.d("SplitInstaller", "installViaSession: ${apkFiles.size} file(s)")
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                params.setInstallReason(PackageManager.INSTALL_REASON_USER)
            }

            val sessionId = installer.createSession(params)
            val session = installer.openSession(sessionId)

            try {
                for (apk in apkFiles) {
                    DebugLog.d("SplitInstaller", "Writing ${apk.name} (${apk.length()} bytes)")
                    session.openWrite(apk.name, 0, apk.length()).use { out ->
                        apk.inputStream().use { it.copyTo(out) }
                        session.fsync(out)
                    }
                }

                val intent = Intent(context, InstallResultReceiver::class.java).apply {
                    action = "com.vinstall.alwiz.INSTALL_STATUS"
                }
                val pi = PendingIntent.getBroadcast(
                    context, sessionId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                session.commit(pi.intentSender)
                session.close()
                DebugLog.d("SplitInstaller", "Session committed, id=$sessionId")
                Result.success(Unit)
            } catch (e: Exception) {
                session.abandon()
                DebugLog.e("SplitInstaller", "Session error: ${e.message}")
                Result.failure(e)
            }
        }

    private fun shizukuExec(vararg cmd: String): String {
        val clazz = Class.forName("rikka.shizuku.Shizuku")
        val method = clazz.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true
        val process = method.invoke(null, cmd, null, null) as rikka.shizuku.ShizukuRemoteProcess
        var stderr = ""
        val stderrThread = Thread {
            stderr = try { process.errorStream.bufferedReader().use { it.readText() } } catch (_: Exception) { "" }
        }
        stderrThread.start()
        val stdout = try { process.inputStream.bufferedReader().use { it.readText() } } catch (_: Exception) { "" }
        stderrThread.join()
        process.waitFor()
        process.destroy()
        return (stdout + stderr).trim()
    }

    private fun shizukuExecWithInput(inputFile: File, vararg cmd: String): String {
        val clazz = Class.forName("rikka.shizuku.Shizuku")
        val method = clazz.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true
        val process = method.invoke(null, cmd, null, null) as rikka.shizuku.ShizukuRemoteProcess

        val writeThread = Thread {
            try {
                process.outputStream.use { os ->
                    inputFile.inputStream().use { input -> input.copyTo(os) }
                }
            } catch (_: Exception) {}
        }
        writeThread.start()

        var stderr = ""
        val stderrThread = Thread {
            stderr = try { process.errorStream.bufferedReader().use { it.readText() } } catch (_: Exception) { "" }
        }
        stderrThread.start()

        val stdout = try { process.inputStream.bufferedReader().use { it.readText() } } catch (_: Exception) { "" }
        stderrThread.join()
        writeThread.join()
        process.waitFor()
        process.destroy()
        return (stdout + stderr).trim()
    }

    private suspend fun installViaShizuku(context: Context, apkFiles: List<File>): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val totalSize = apkFiles.sumOf { it.length() }
                DebugLog.d("SplitInstaller", "installViaShizuku totalSize=$totalSize")

                val createOutput = shizukuExec("pm", "install-create", "-S", "$totalSize")
                DebugLog.d("SplitInstaller", "Shizuku create: $createOutput")

                val sessionId = Regex("\\[(\\d+)]").find(createOutput)?.groupValues?.get(1)
                    ?: return@withContext Result.failure(Exception("Shizuku: failed to create install session: $createOutput"))

                for (apk in apkFiles) {
                    DebugLog.d("SplitInstaller", "Shizuku write: ${apk.name}")
                    val writeOutput = shizukuExecWithInput(
                        apk,
                        "pm", "install-write", "-S", "${apk.length()}", sessionId, apk.name
                    )
                    DebugLog.d("SplitInstaller", "Shizuku write result: $writeOutput")
                    if (!writeOutput.contains("Success", ignoreCase = true)) {
                        shizukuExec("pm", "install-abandon", sessionId)
                        return@withContext Result.failure(
                            Exception("Failed to write ${apk.name}: $writeOutput")
                        )
                    }
                }

                val commitOutput = shizukuExec("pm", "install-commit", sessionId)
                DebugLog.d("SplitInstaller", "Shizuku commit: $commitOutput")

                if (commitOutput.contains("Success", ignoreCase = true)) {
                    com.vinstall.alwiz.util.InstallEvents.emit(com.vinstall.alwiz.util.InstallEvents.Result.Success)
                    Result.success(Unit)
                } else {
                    val friendlyMessage = parseInstallError(commitOutput)
                    Result.failure(Exception(friendlyMessage))
                }
            } catch (e: Exception) {
                DebugLog.e("SplitInstaller", "Shizuku exception: ${e.message}")
                Result.failure(e)
            }
        }

    private suspend fun installViaRoot(apkFiles: List<File>): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val totalSize = apkFiles.sumOf { it.length() }
                DebugLog.d("SplitInstaller", "installViaRoot totalSize=$totalSize")

                val createOutput = Runtime.getRuntime()
                    .exec(arrayOf("su", "-c", "pm install-create -S $totalSize"))
                    .let { process ->
                        val out = process.inputStream.bufferedReader().readText()
                        process.waitFor()
                        out
                    }

                val sessionId = Regex("\\[(\\d+)]").find(createOutput)?.groupValues?.get(1)
                    ?: return@withContext Result.failure(Exception("Root: failed to create install session"))

                for (apk in apkFiles) {
                    Runtime.getRuntime().exec(
                        arrayOf("su", "-c", "pm install-write -S ${apk.length()} $sessionId ${apk.name} ${apk.absolutePath}")
                    ).let { process -> process.waitFor() }
                }

                val commitOutput = Runtime.getRuntime()
                    .exec(arrayOf("su", "-c", "pm install-commit $sessionId"))
                    .let { process ->
                        val out = process.inputStream.bufferedReader().readText()
                        process.waitFor()
                        out
                    }

                DebugLog.d("SplitInstaller", "Root commit: $commitOutput")

                if (commitOutput.contains("Success", ignoreCase = true)) {
                    com.vinstall.alwiz.util.InstallEvents.emit(com.vinstall.alwiz.util.InstallEvents.Result.Success)
                    Result.success(Unit)
                } else {
                    val friendlyMessage = parseInstallError(commitOutput)
                    Result.failure(Exception(friendlyMessage))
                }
            } catch (e: Exception) {
                DebugLog.e("SplitInstaller", "Root exception: ${e.message}")
                Result.failure(e)
            }
        }

    private fun parseInstallError(output: String): String {
        return when {
            output.contains("INSTALL_FAILED_ALREADY_EXISTS") -> "App already installed with a different signature. Uninstall the existing version first."
            output.contains("INSTALL_FAILED_INVALID_APK") -> "Invalid APK: the package file is malformed or could not be written to the install session."
            output.contains("INSTALL_FAILED_CONFLICTING_PROVIDER") -> "Conflict: another app uses the same content provider authority."
            output.contains("INSTALL_FAILED_INSUFFICIENT_STORAGE") -> "Not enough storage space to install this package."
            output.contains("INSTALL_FAILED_VERSION_DOWNGRADE") -> "Cannot install an older version over a newer one."
            output.contains("INSTALL_FAILED_PERMISSION_MODEL_DOWNGRADE") -> "Permission model downgrade is not allowed."
            output.contains("INSTALL_PARSE_FAILED_NO_CERTIFICATES") -> "APK is not signed. A valid signature is required."
            output.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE") -> "Signature mismatch: uninstall the existing app first."
            output.contains("INSTALL_FAILED_DEXOPT") -> "APK could not be optimized (dexopt). The ABI may be incompatible with this device."
            output.contains("INSTALL_FAILED_CPU_ABI_INCOMPATIBLE") -> "Incompatible APK: this package does not support your device's CPU architecture."
            output.contains("INSTALL_FAILED_NO_MATCHING_ABIS") -> "Incompatible APK: no native libraries match your device's ABI."
            output.contains("INSTALL_FAILED_TEST_ONLY") -> "This is a test-only APK and cannot be installed normally."
            output.contains("INSTALL_FAILED_ABORTED") -> "Installation was cancelled."
            else -> "Install failed: $output"
        }
    }
}
