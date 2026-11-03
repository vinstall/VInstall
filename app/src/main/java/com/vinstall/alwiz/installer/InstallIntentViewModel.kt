package com.vinstall.alwiz.installer

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vinstall.alwiz.installer.ApkmInstaller
import com.vinstall.alwiz.installer.ApkvInstaller
import com.vinstall.alwiz.installer.ApksInstaller
import com.vinstall.alwiz.R
import com.vinstall.alwiz.history.InstallHistoryManager
import com.vinstall.alwiz.model.HistoryStatus
import com.vinstall.alwiz.model.InstallState
import com.vinstall.alwiz.model.PackageFormat
import com.vinstall.alwiz.settings.AppSettings
import com.vinstall.alwiz.settings.InstallMode
import com.vinstall.alwiz.ui.ConfirmationBottomSheet
import com.vinstall.alwiz.util.DebugLog
import com.vinstall.alwiz.util.FileUtil
import com.vinstall.alwiz.util.MetadataReader
import com.vinstall.alwiz.util.NotificationHelper
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InstallIntentViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)
    val state: StateFlow<InstallState> = _state

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sourcePackage: String? = null
    private var lastFileState: InstallState.FileSelected? = null
    private var fallbackName: String = ""
    private var installStartedAt: Long = 0L
    private var historyRecorded = false

    fun recordCancelled(detail: String? = null) {
        val context = getApplication<Application>()
        record(HistoryStatus.CANCELLED, detail ?: context.getString(R.string.history_cancelled_by_user))
    }

    fun recordBlocked(detail: String) {
        record(HistoryStatus.BLOCKED, detail)
    }

    private fun record(status: HistoryStatus, detail: String) {
        if (historyRecorded) return
        historyRecorded = true
        val context = getApplication<Application>()
        val fileState = lastFileState
        val source = sourcePackage
        val startedAt = installStartedAt
        ioScope.launch {
            try {
                InstallHistoryManager.recordExternal(
                    context = context,
                    sourcePackage = source,
                    appLabel = fileState?.let { it.appLabel.ifBlank { it.name } } ?: fallbackName,
                    packageName = fileState?.packageName ?: "",
                    versionName = fileState?.versionName ?: "",
                    format = fileState?.format?.label ?: PackageFormat.fromFileName(fallbackName).label,
                    fileSize = fileState?.size ?: 0L,
                    status = status,
                    detail = detail,
                    durationMs = if (startedAt > 0L) System.currentTimeMillis() - startedAt else 0L
                )
            } catch (e: Exception) {
                DebugLog.e("InstallIntentVM", "Could not record history: ${e.message}")
            }
        }
    }

    private fun stageExternalFile(context: Application, uri: Uri): Pair<Uri, String> {
        val name = try {
            FileUtil.getFileName(context, uri)
        } catch (e: Exception) {
            uri.lastPathSegment ?: "package.apk"
        }.substringAfterLast('/').ifBlank { "package.apk" }

        val dir = File(context.cacheDir, "ext_stage_${UUID.randomUUID()}").also { it.mkdirs() }
        val target = File(dir, name)
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open $uri")
        input.use { src ->
            target.outputStream().buffered(FileUtil.BUFFER_SIZE).use { dst ->
                src.copyTo(dst, FileUtil.BUFFER_SIZE)
            }
        }
        if (target.length() == 0L) throw IllegalStateException("File is empty")
        return Pair(Uri.fromFile(target), name)
    }

    fun loadPackageInfo(sourceUri: Uri, source: String? = null) {
        val context = getApplication<Application>()
        sourcePackage = source
        fallbackName = sourceUri.lastPathSegment?.substringAfterLast('/') ?: ""
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = InstallState.FileLoading
            try {
                val (uri, name) = try {
                    stageExternalFile(context, sourceUri)
                } catch (e: Exception) {
                    DebugLog.e("InstallIntentVM", "Cannot read source file: ${e.message}")
                    val message = "Cannot read the file. Access from the source app may have expired, please try again from that app."
                    record(HistoryStatus.FAILED, message)
                    _state.value = InstallState.Error(message)
                    return@launch
                }
                val format = PackageFormat.fromFileName(name)
                DebugLog.i("InstallIntentVM", "External file staged: $name format=$format")

                if (format == PackageFormat.APKV && ApkvInstaller.isEncrypted(context, uri)) {
                    val header = ApkvInstaller.readHeader(context, uri)
                    _state.value = InstallState.PasswordRequired(
                        uri = uri,
                        fileName = name,
                        packageName = header?.packageName ?: "",
                        versionName = header?.versionName ?: "",
                        label = header?.label ?: ""
                    )
                    return@launch
                }

                val meta = when (format) {
                    PackageFormat.APK -> MetadataReader.readFromApk(context, uri, name)
                    PackageFormat.XAPK -> MetadataReader.readFromXapk(context, uri)
                    PackageFormat.APKM -> MetadataReader.readFromApkm(context, uri)
                    PackageFormat.APKV -> MetadataReader.readFromApkv(context, uri)
                    PackageFormat.APKS, PackageFormat.ZIP -> MetadataReader.readFromApks(context, uri)
                    else -> MetadataReader.AppMeta()
                }

                if (format == PackageFormat.APK && meta.packageName.isEmpty()) {
                    DebugLog.e("InstallIntentVM", "APK metadata unreadable, aborting before confirmation")
                    fallbackName = name
                    record(HistoryStatus.FAILED, "Invalid or unreadable APK file.")
                    _state.value = InstallState.Error("Invalid or unreadable APK file.")
                    return@launch
                }

                val selected = InstallState.FileSelected(
                    uri = uri,
                    name = name,
                    size = FileUtil.getFileSize(context, uri).takeIf { it > 0 }
                        ?: File(uri.path ?: "").length(),
                    format = format,
                    packageName = meta.packageName,
                    versionName = meta.versionName,
                    versionCode = meta.versionCode,
                    appLabel = meta.appLabel,
                    appIcon = meta.appIcon,
                    minSdk = meta.minSdk,
                    targetSdk = meta.targetSdk
                )
                lastFileState = selected
                _state.value = selected
            } catch (e: Exception) {
                DebugLog.e("InstallIntentVM", "loadPackageInfo failed: ${e.message}")
                record(HistoryStatus.FAILED, e.message ?: "Failed to read package")
                _state.value = InstallState.Error(e.message ?: "Failed to read package")
            }
        }
    }

    fun install() {
        val context = getApplication<Application>()
        val fileState = _state.value as? InstallState.FileSelected ?: return
        val uri = fileState.uri
        DebugLog.i("InstallIntentVM", "User confirmed install: ${fileState.packageName}")
        installStartedAt = System.currentTimeMillis()
        viewModelScope.launch(Dispatchers.IO) {
            InstallHelper.reset()
            _state.value = InstallState.Analyzing
            val result = when (fileState.format) {
                PackageFormat.APK -> ApkInstaller.install(context, uri,
                    onStep = { step -> _state.value = InstallState.Installing(step) },
                    onProgress = { progress ->
                        val step = (_state.value as? InstallState.Installing)?.step ?: ""
                        _state.value = InstallState.Installing(step, progress)
                    }
                )
                PackageFormat.XAPK -> XapkInstaller.install(context, uri,
                    onStep = { step -> _state.value = InstallState.Installing(step) },
                    selectedSplits = null,
                    onProgress = { progress ->
                        val step = (_state.value as? InstallState.Installing)?.step ?: ""
                        _state.value = InstallState.Installing(step, progress)
                    }
                )
                PackageFormat.APKS -> ApksInstaller.install(context, uri,
                    onStep = { step -> _state.value = InstallState.Installing(step) },
                    selectedSplits = null,
                    onProgress = { progress ->
                        val step = (_state.value as? InstallState.Installing)?.step ?: ""
                        _state.value = InstallState.Installing(step, progress)
                    }
                )
                PackageFormat.APKM -> ApkmInstaller.install(context, uri,
                    onStep = { step -> _state.value = InstallState.Installing(step) },
                    selectedSplits = null,
                    onProgress = { progress ->
                        val step = (_state.value as? InstallState.Installing)?.step ?: ""
                        _state.value = InstallState.Installing(step, progress)
                    }
                )
                PackageFormat.APKV -> ApkvInstaller.install(context, uri, fileState.apkvPassword,
                    onStep = { step -> _state.value = InstallState.Installing(step) },
                    onProgress = { progress ->
                        val step = (_state.value as? InstallState.Installing)?.step ?: ""
                        _state.value = InstallState.Installing(step, progress)
                    }
                )
                PackageFormat.ZIP -> ZipApkInstaller.install(context, uri,
                    onStep = { step -> _state.value = InstallState.Installing(step) },
                    selectedSplits = null,
                    onProgress = { progress ->
                        val step = (_state.value as? InstallState.Installing)?.step ?: ""
                        _state.value = InstallState.Installing(step, progress)
                    }
                )
                else -> {
                    record(HistoryStatus.FAILED, "Unsupported format from file manager")
                    _state.value = InstallState.Error("Unsupported format from file manager")
                    return@launch
                }
            }

            if (result.isFailure) {
                val failure = result.exceptionOrNull()?.message ?: "Install failed"
                record(HistoryStatus.FAILED, failure)
                _state.value = InstallState.Error(failure)
                autoClearCache()
                return@launch
            }

            val installResult = withContext(Dispatchers.IO) {
                InstallHelper.awaitResult(timeoutMs = 120_000L)
            }

            autoClearCache()

            val finalState = when {
                installResult == null -> InstallState.Error("Installation timed out")
                installResult is InstallHelper.Result.Success -> {
                    if (AppSettings.getInstallMode(context) != InstallMode.NORMAL) {
                        NotificationHelper.postInstallSuccess(context, fileState.packageName, fileState.appLabel)
                    }
                    InstallState.Success(fileState.packageName)
                }
                installResult is InstallHelper.Result.Failure -> {
                    val msg = installResult.message ?: "Install failed"
                    if (msg.contains("cancelled", ignoreCase = true) || msg.contains("aborted", ignoreCase = true)) {
                        InstallState.Cancelled(msg)
                    } else {
                        InstallState.Error(msg)
                    }
                }
                else -> InstallState.Error("Unknown result")
            }
            when (finalState) {
                is InstallState.Success -> record(HistoryStatus.SUCCESS, "")
                is InstallState.Cancelled -> record(HistoryStatus.CANCELLED, finalState.reason)
                is InstallState.Error -> record(HistoryStatus.FAILED, finalState.message)
                else -> {}
            }
            _state.value = finalState
        }
    }

    private fun autoClearCache() {
        val context = getApplication<Application>()
        if (AppSettings.isAutoClearCache(context)) FileUtil.clearCache(context)
    }

    override fun onCleared() {
        super.onCleared()
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { autoClearCache() }
    }

    private fun getInstalledVersionInfo(packageName: String): Pair<String, Long>? {
        return try {
            val pm = getApplication<Application>().packageManager
            val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pi.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pi.versionCode.toLong()
            }
            Pair(pi.versionName ?: "", code)
        } catch (_: Exception) {
            null
        }
    }

    fun submitApkvPassword(uri: Uri, password: String) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = InstallState.FileLoading
            val valid = ApkvInstaller.verifyPassword(context, uri, password)
            if (!valid) {
                _state.value = InstallState.Error(ApkvInstaller.ERROR_WRONG_PASSWORD)
                return@launch
            }
            val name = FileUtil.getFileName(context, uri)
            val meta = MetadataReader.readFromApkv(context, uri, password)
            val installed = if (meta.packageName.isNotEmpty()) getInstalledVersionInfo(meta.packageName) else null
            val selected = InstallState.FileSelected(
                uri = uri,
                name = name,
                size = FileUtil.getFileSize(context, uri),
                format = PackageFormat.APKV,
                packageName = meta.packageName,
                versionName = meta.versionName,
                versionCode = meta.versionCode,
                appLabel = meta.appLabel,
                appIcon = meta.appIcon,
                isEncryptedApkv = true,
                apkvPassword = password
            )
            lastFileState = selected
            _state.value = selected
        }
    }
}
