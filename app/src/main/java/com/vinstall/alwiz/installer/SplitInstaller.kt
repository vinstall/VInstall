package com.vinstall.alwiz.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.vinstall.alwiz.receiver.InstallResultReceiver
import com.vinstall.alwiz.settings.AppSettings
import com.vinstall.alwiz.settings.InstallMode
import com.vinstall.alwiz.shizuku.ShizukuHelper
import com.vinstall.alwiz.util.DebugLog
import com.vinstall.alwiz.util.ShellSafe
import com.vinstall.alwiz.root.RootHelper
import com.vinstall.alwiz.timeline.AppTimelineManager
import com.vinstall.alwiz.timeline.PackageProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class InstallException(val code: String?, message: String) : Exception(message)

object SplitInstaller {

    private enum class Backend { ROOT, SHIZUKU }

    private const val ERR_DOWNGRADE = "INSTALL_FAILED_VERSION_DOWNGRADE"
    private const val ERR_INCOMPATIBLE = "INSTALL_FAILED_UPDATE_INCOMPATIBLE"

    private fun validateBaseApk(context: Context, apkFiles: List<File>): String? {
        @Suppress("DEPRECATION")
        fun parse(f: File) = context.packageManager.getPackageArchiveInfo(f.absolutePath, 0)

        val base = apkFiles.firstOrNull { it.name.equals("base.apk", ignoreCase = true) }
            ?: apkFiles.firstOrNull { parse(it) != null }
            ?: apkFiles.first()
        val pkgInfo = parse(base)
            ?: return "Invalid or corrupt package: cannot parse ${base.name}"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val minSdk = pkgInfo.applicationInfo?.minSdkVersion ?: 0
            if (minSdk > Build.VERSION.SDK_INT) {
                return "Incompatible package: requires API level $minSdk, device is ${Build.VERSION.SDK_INT}"
            }
        }
        return null
    }

    suspend fun installSplits(
        context: Context,
        apkFiles: List<File>,
        selectedSplits: List<String>? = null,
        onProgress: ((Float) -> Unit)? = null
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
        val allowIncompatible = AppSettings.isAllowIncompatibleApi(context) && mode != InstallMode.NORMAL
        if (!allowIncompatible) {
            val validationError = validateBaseApk(context, filesToInstall)
            if (validationError != null) {
                return@withContext Result.failure(Exception(validationError))
            }
        }

        val silentOnly = coroutineContext[SilentOnly] != null
        DebugLog.i("SplitInstaller", "Install mode: $mode, silentOnly=$silentOnly, files: ${filesToInstall.map { it.name }}")
        val probe = AppTimelineManager.probeAndStage(context, filesToInstall, mode.name)

        return@withContext when (mode) {
            InstallMode.ROOT -> {
                if (RootHelper.isRooted()) {
                    installPrivileged(Backend.ROOT, context, filesToInstall, allowIncompatible, onProgress, probe, !silentOnly)
                } else if (silentOnly) {
                    Result.failure<Unit>(Exception("Root access is not available"))
                } else {
                    DebugLog.e("SplitInstaller", "ROOT mode selected but device is not rooted, falling back to session")
                    installViaSession(context, filesToInstall, onProgress)
                }
            }
            InstallMode.SHIZUKU -> {
                val liveGranted = ShizukuHelper.isAvailable() && ShizukuHelper.isGranted()
                val storedGranted = ShizukuHelper.isAvailable() && AppSettings.isShizukuPermissionGranted(context)

                if (liveGranted || storedGranted) {
                    if (ShizukuHelper.isNewProcessAvailable()) {
                        val result = installPrivileged(Backend.SHIZUKU, context, filesToInstall, allowIncompatible, onProgress, probe, !silentOnly)
                        if (result.isFailure && !silentOnly && result.exceptionOrNull() !is InstallException) {
                            AppSettings.setShizukuPermissionGranted(context, false)
                            DebugLog.e("SplitInstaller", "Shizuku install failed, falling back to session")
                            installViaSession(context, filesToInstall, onProgress)
                        } else {
                            result
                        }
                    } else if (silentOnly) {
                        Result.failure<Unit>(Exception("Shizuku is not supported on this device"))
                    } else {
                        DebugLog.e("SplitInstaller", "Shizuku newProcess not available, falling back to session")
                        installViaSession(context, filesToInstall, onProgress)
                    }
                } else if (silentOnly) {
                    Result.failure<Unit>(Exception("Shizuku is not running or VInstall has no permission"))
                } else {
                    DebugLog.e("SplitInstaller", "SHIZUKU mode selected but Shizuku is not active/permitted, falling back to session")
                    installViaSession(context, filesToInstall, onProgress)
                }
            }
            InstallMode.NORMAL ->
                if (silentOnly) {
                    Result.failure<Unit>(Exception("Silent install needs Root or Shizuku mode"))
                } else {
                    installViaSession(context, filesToInstall, onProgress)
                }
        }
    }

    private suspend fun runInstall(
        backend: Backend,
        context: Context,
        files: List<File>,
        allowIncompatible: Boolean,
        onProgress: ((Float) -> Unit)?,
        flags: List<String>,
        emit: Boolean
    ): Result<Unit> = when (backend) {
        Backend.ROOT -> installViaRoot(files, allowIncompatible, onProgress, flags, emit)
        Backend.SHIZUKU -> installViaShizuku(context, files, allowIncompatible, onProgress, flags, emit)
    }

    private suspend fun installPrivileged(
        backend: Backend,
        context: Context,
        files: List<File>,
        allowIncompatible: Boolean,
        onProgress: ((Float) -> Unit)?,
        probe: PackageProbe?,
        advancedAllowed: Boolean
    ): Result<Unit> {
        val installed = probe?.installed
        val downgradeOn = advancedAllowed && probe != null && installed != null &&
            probe.versionCode < installed.versionCode && AppSettings.isAllowDowngrade(context)
        val forceOn = advancedAllowed && probe != null && installed != null &&
            AppSettings.isForceInstallMismatch(context)

        if (probe == null || (!downgradeOn && !forceOn)) {
            return runInstall(backend, context, files, allowIncompatible, onProgress, emptyList(), true)
        }

        val packageName = probe.packageName
        val keepData = AppSettings.isDowngradeKeepData(context)

        if (downgradeOn && !keepData) {
            DebugLog.i("SplitInstaller", "Downgrade without keeping data: $packageName")
            return replaceByUninstall(
                backend, context, files, allowIncompatible, onProgress,
                packageName, keepData = false, forceOn = forceOn, forcedMismatch = false
            )
        }

        val first = runInstall(
            backend, context, files, allowIncompatible, onProgress,
            if (downgradeOn) listOf("-d") else emptyList(), true
        )
        if (first.isSuccess) {
            if (downgradeOn) AppTimelineManager.markAdvanced(packageName, forced = false, keptData = true)
            return first
        }

        val code = (first.exceptionOrNull() as? InstallException)?.code
        return when {
            downgradeOn && keepData && code == ERR_DOWNGRADE -> {
                DebugLog.i("SplitInstaller", "Direct downgrade rejected, retrying via keep-data uninstall: $packageName")
                replaceByUninstall(
                    backend, context, files, allowIncompatible, onProgress,
                    packageName, keepData = true, forceOn = forceOn, forcedMismatch = false
                )
            }
            forceOn && code == ERR_INCOMPATIBLE -> {
                val keepForced = AppSettings.isForceKeepData(context)
                DebugLog.i("SplitInstaller", "Signature mismatch, forcing install (keepData=$keepForced): $packageName")
                replaceByUninstall(
                    backend, context, files, allowIncompatible, onProgress,
                    packageName, keepData = keepForced, forceOn = true, forcedMismatch = true
                )
            }
            else -> first
        }
    }

    private suspend fun replaceByUninstall(
        backend: Backend,
        context: Context,
        files: List<File>,
        allowIncompatible: Boolean,
        onProgress: ((Float) -> Unit)?,
        packageName: String,
        keepData: Boolean,
        forceOn: Boolean,
        forcedMismatch: Boolean
    ): Result<Unit> {
        val stash = stashInstalled(context, packageName)
            ?: return Result.failure(
                InstallException(null, "Cannot back up the installed version for rollback. Nothing was changed.")
            )
        val mode = if (backend == Backend.ROOT) InstallMode.ROOT else InstallMode.SHIZUKU
        try {
            val removed = UninstallHelper.uninstallWith(mode, packageName, keepData)
            if (removed.isFailure) {
                return Result.failure(
                    InstallException(null, "Could not remove the installed version: ${removed.exceptionOrNull()?.message}")
                )
            }

            var kept = keepData
            var forced = forcedMismatch
            var result = runInstall(backend, context, files, allowIncompatible, onProgress, emptyList(), true)

            val retryCode = (result.exceptionOrNull() as? InstallException)?.code
            val eraseFallback = forceOn && !AppSettings.isForceKeepData(context)
            if (result.isFailure && keepData && eraseFallback && retryCode == ERR_INCOMPATIBLE) {
                DebugLog.i("SplitInstaller", "Kept data blocks signature change, erasing data: $packageName")
                UninstallHelper.uninstallWith(mode, packageName, false)
                kept = false
                forced = true
                result = runInstall(backend, context, files, allowIncompatible, onProgress, emptyList(), true)
            }

            if (result.isFailure) {
                val restored = runInstall(backend, context, stash, allowIncompatible, null, emptyList(), false)
                val note = if (restored.isSuccess) {
                    " The original version was restored."
                } else {
                    " Restoring the original version also failed."
                }
                val cause = result.exceptionOrNull()
                val keepBlocked = keepData && forcedMismatch && retryCode == ERR_INCOMPATIBLE
                val hint = if (keepBlocked) " Android blocked keeping the data across a signature change on this device." else ""
                return Result.failure(
                    InstallException((cause as? InstallException)?.code, (cause?.message ?: "Install failed") + hint + note)
                )
            }

            AppTimelineManager.markAdvanced(packageName, forced = forced, keptData = kept)
            return result
        } finally {
            stash.firstOrNull()?.parentFile?.deleteRecursively()
        }
    }

    private fun stashInstalled(context: Context, packageName: String): List<File>? = try {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        val sources = listOf(info.sourceDir) + info.splitSourceDirs.orEmpty().toList()
        val dir = File(context.cacheDir, "rollback_${System.nanoTime()}").also { it.mkdirs() }
        sources.map { path -> File(path).copyTo(File(dir, File(path).name), overwrite = true) }
    } catch (e: Exception) {
        DebugLog.e("SplitInstaller", "Stash failed: ${e.message}")
        null
    }

    private suspend fun installViaSession(
        context: Context,
        apkFiles: List<File>,
        onProgress: ((Float) -> Unit)? = null
    ): Result<Unit> =
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
                val totalBytes = apkFiles.sumOf { it.length() }.coerceAtLeast(1L)
                var writtenBytes = 0L
                val buffer = ByteArray(65536)

                for (apk in apkFiles) {
                    DebugLog.d("SplitInstaller", "Writing ${apk.name} (${apk.length()} bytes)")
                    session.openWrite(apk.name, 0, apk.length()).use { out ->
                        apk.inputStream().use { input ->
                            var n: Int
                            while (input.read(buffer).also { n = it } >= 0) {
                                out.write(buffer, 0, n)
                                writtenBytes += n
                                val progress = (writtenBytes.toFloat() / totalBytes) * 0.9f
                                session.setStagingProgress(progress)
                                onProgress?.invoke(progress)
                            }
                        }
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

                val sessionCallback = object : PackageInstaller.SessionCallback() {
                    override fun onCreated(sid: Int) {}
                    override fun onBadgingChanged(sid: Int) {}
                    override fun onActiveChanged(sid: Int, active: Boolean) {}
                    override fun onProgressChanged(sid: Int, progress: Float) {
                        if (sid == sessionId) onProgress?.invoke(0.9f + progress * 0.1f)
                    }
                    override fun onFinished(sid: Int, success: Boolean) {
                        if (sid == sessionId) {
                            installer.unregisterSessionCallback(this)
                        }
                    }
                }
                installer.registerSessionCallback(sessionCallback, Handler(Looper.getMainLooper()))

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

    private suspend fun installViaShizuku(
        context: Context,
        apkFiles: List<File>,
        allowIncompatible: Boolean,
        onProgress: ((Float) -> Unit)? = null,
        flags: List<String> = emptyList(),
        emit: Boolean = true
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val totalSize = apkFiles.sumOf { it.length() }
                DebugLog.d("SplitInstaller", "installViaShizuku totalSize=$totalSize flags=$flags")

                val createOutput = shizukuExec("pm", "install-create", *sdkBlockFlags(allowIncompatible), *flags.toTypedArray(), "-S", "$totalSize")
                DebugLog.d("SplitInstaller", "Shizuku create: $createOutput")

                val sessionId = Regex("\\[(\\d+)]").find(createOutput)?.groupValues?.get(1)
                    ?: return@withContext Result.failure(Exception("Shizuku: failed to create install session: $createOutput"))

                apkFiles.forEachIndexed { index, apk ->
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
                    onProgress?.invoke(((index + 1).toFloat() / apkFiles.size) * 0.9f)
                }

                onProgress?.invoke(0.95f)
                val commitOutput = shizukuExec("pm", "install-commit", sessionId)
                DebugLog.d("SplitInstaller", "Shizuku commit: $commitOutput")

                if (commitOutput.contains("Success", ignoreCase = true)) {
                    if (emit) com.vinstall.alwiz.installer.InstallHelper.emit(com.vinstall.alwiz.installer.InstallHelper.Result.Success)
                    Result.success(Unit)
                } else {
                    val friendlyMessage = parseInstallError(commitOutput)
                    Result.failure(InstallException(extractErrorCode(commitOutput), friendlyMessage))
                }
            } catch (e: Exception) {
                DebugLog.e("SplitInstaller", "Shizuku exception: ${e.message}")
                Result.failure(e)
            }
        }

    private suspend fun installViaRoot(
        apkFiles: List<File>,
        allowIncompatible: Boolean,
        onProgress: ((Float) -> Unit)? = null,
        flags: List<String> = emptyList(),
        emit: Boolean = true
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val totalSize = apkFiles.sumOf { it.length() }
                DebugLog.d("SplitInstaller", "installViaRoot totalSize=$totalSize flags=$flags")

                val createFlags = (sdkBlockFlags(allowIncompatible).toList() + flags).joinToString(" ")
                val createOutput = Runtime.getRuntime()
                    .exec(arrayOf("su", "-c", "pm install-create $createFlags -S $totalSize"))
                    .let { process ->
                        val out = process.inputStream.bufferedReader().readText()
                        process.waitFor()
                        out
                    }

                val sessionId = Regex("\\[(\\d+)]").find(createOutput)?.groupValues?.get(1)
                    ?: return@withContext Result.failure(Exception("Root: failed to create install session"))

                apkFiles.forEachIndexed { index, apk ->
                    val command = "pm install-write -S ${apk.length()} $sessionId " +
                        "${ShellSafe.quote(apk.name)} ${ShellSafe.quote(apk.absolutePath)}"
                    Runtime.getRuntime().exec(arrayOf("su", "-c", command))
                        .let { process -> process.waitFor() }
                    onProgress?.invoke(((index + 1).toFloat() / apkFiles.size) * 0.9f)
                }

                onProgress?.invoke(0.95f)
                val commitOutput = Runtime.getRuntime()
                    .exec(arrayOf("su", "-c", "pm install-commit $sessionId"))
                    .let { process ->
                        val out = process.inputStream.bufferedReader().readText()
                        process.waitFor()
                        out
                    }

                DebugLog.d("SplitInstaller", "Root commit: $commitOutput")

                if (commitOutput.contains("Success", ignoreCase = true)) {
                    if (emit) com.vinstall.alwiz.installer.InstallHelper.emit(com.vinstall.alwiz.installer.InstallHelper.Result.Success)
                    Result.success(Unit)
                } else {
                    val friendlyMessage = parseInstallError(commitOutput)
                    Result.failure(InstallException(extractErrorCode(commitOutput), friendlyMessage))
                }
            } catch (e: Exception) {
                DebugLog.e("SplitInstaller", "Root exception: ${e.message}")
                Result.failure(e)
            }
        }

    private fun sdkBlockFlags(allowIncompatible: Boolean): Array<String> =
        if (allowIncompatible && Build.VERSION.SDK_INT >= 34) arrayOf("--bypass-low-target-sdk-block") else emptyArray()

    private fun extractErrorCode(output: String): String? =
        Regex("INSTALL_[A-Z_]+").find(output)?.value

    private fun parseInstallError(output: String): String {
        return when {
            output.contains("INSTALL_FAILED_ALREADY_EXISTS") -> "App already installed with a different signature. Uninstall the existing version first."
            output.contains("INSTALL_FAILED_INVALID_APK") -> "Invalid APK: the package file is malformed or could not be written to the install session."
            output.contains("INSTALL_FAILED_CONFLICTING_PROVIDER") -> "Conflict: another app uses the same content provider authority."
            output.contains("INSTALL_FAILED_INSUFFICIENT_STORAGE") -> "Not enough storage space to install this package."
            output.contains("INSTALL_FAILED_VERSION_DOWNGRADE") -> "Cannot install an older version over a newer one. Root or Shizuku users can enable Settings > Experimental > Allow downgrade."
            output.contains("INSTALL_FAILED_PERMISSION_MODEL_DOWNGRADE") -> "Permission model downgrade is not allowed."
            output.contains("INSTALL_PARSE_FAILED_NO_CERTIFICATES") -> "APK is not signed. A valid signature is required."
            output.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE") -> "Signature mismatch: uninstall the existing app first. Root or Shizuku users can enable Settings > Experimental > Force install on signature mismatch."
            output.contains("INSTALL_FAILED_DEXOPT") -> "APK could not be optimized (dexopt). The ABI may be incompatible with this device."
            output.contains("INSTALL_FAILED_CPU_ABI_INCOMPATIBLE") -> "Incompatible APK: this package does not support your device's CPU architecture."
            output.contains("INSTALL_FAILED_NO_MATCHING_ABIS") -> "Incompatible APK: no native libraries match your device's ABI."
            output.contains("INSTALL_FAILED_TEST_ONLY") -> "This is a test-only APK and cannot be installed normally."
            output.contains("INSTALL_FAILED_OLD_SDK") -> "Incompatible package: it requires a newer Android version than this device runs."
            output.contains("INSTALL_FAILED_DEPRECATED_SDK_VERSION") -> "This app targets an Android API that is too old for this device. Enable \"Install apps with incompatible API\" in Settings > Experimental to try anyway."
            output.contains("INSTALL_FAILED_ABORTED") -> "Installation was cancelled."
            else -> "Install failed: $output"
        }
    }
}
