package com.vinstall.alwiz

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vinstall.alwiz.installer.ApkInstaller
import com.vinstall.alwiz.installer.ApkmInstaller
import com.vinstall.alwiz.installer.ApksInstaller
import com.vinstall.alwiz.installer.ApkvInstaller
import com.vinstall.alwiz.installer.XapkInstaller
import com.vinstall.alwiz.installer.ZipApkInstaller
import com.vinstall.alwiz.model.InstallState
import com.vinstall.alwiz.model.PackageFormat
import com.vinstall.alwiz.settings.AppSettings
import com.vinstall.alwiz.util.DebugLog
import com.vinstall.alwiz.util.FileUtil
import com.vinstall.alwiz.installer.InstallHelper
import com.vinstall.alwiz.settings.InstallMode
import com.vinstall.alwiz.util.MetadataReader
import com.vinstall.alwiz.util.NotificationHelper
import com.vinstall.alwiz.history.InstallHistoryManager
import com.vinstall.alwiz.model.HistoryStatus
import com.vinstall.alwiz.model.InstallHistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class BatchProgress(val current: Int, val total: Int, val label: String)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)
    val state: StateFlow<InstallState> = _state

    private val _availableSplits = MutableStateFlow<List<String>>(emptyList())
    val availableSplits: StateFlow<List<String>> = _availableSplits

    private val _selectedSplits = MutableStateFlow<List<String>>(emptyList())
    val selectedSplits: StateFlow<List<String>> = _selectedSplits

    private val _batchProgress = MutableStateFlow<BatchProgress?>(null)
    val batchProgress: StateFlow<BatchProgress?> = _batchProgress

    private val _queueItems = MutableStateFlow<List<QueueItem>>(emptyList())
    val queueItems: StateFlow<List<QueueItem>> = _queueItems

    private val _queueActive = MutableStateFlow(false)
    val queueActive: StateFlow<Boolean> = _queueActive

    private val _currentFile = MutableStateFlow<InstallState.FileSelected?>(null)
    val currentFile: StateFlow<InstallState.FileSelected?> = _currentFile

    init {
        viewModelScope.launch {
            _state.collect { s ->
                when (s) {
                    is InstallState.FileSelected -> _currentFile.value = s
                    is InstallState.FileLoading,
                    is InstallState.PasswordRequired,
                    is InstallState.Idle -> _currentFile.value = null
                    else -> {}
                }
            }
        }
    }

    private var isProcessingQueue = false
    private var currentInstallJob: Job? = null
    private var fileLoadingJob: Job? = null
    private var queueMetadataJob: Job? = null
    private var installStartTime: Long = 0L

    private fun autoClearCache() {
        val context = getApplication<Application>()
        if (AppSettings.isAutoClearCache(context)) FileUtil.clearCache(context)
    }

    override fun onCleared() {
        super.onCleared()
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { autoClearCache() }
    }

    fun canExportCurrentFile(): Boolean {
        val current = _state.value as? InstallState.FileSelected ?: return false
        return !current.isEncryptedApkv
    }

    fun onFileSelected(uri: Uri) {
        val context = getApplication<Application>()
        queueMetadataJob?.cancel()
        queueMetadataJob = null
        _queueItems.value = emptyList()
        _queueActive.value = false
        fileLoadingJob?.cancel()
        fileLoadingJob = viewModelScope.launch(Dispatchers.IO) {
            _state.value = InstallState.FileLoading
            autoClearCache()
            val name = FileUtil.getFileName(context, uri)
            val format = PackageFormat.fromFileName(name)
            DebugLog.i("MainViewModel", "File selected: $name format=$format")

            if (format == PackageFormat.APKV) {
                handleApkvSelected(uri, name)
                return@launch
            }

            val size = FileUtil.getFileSize(context, uri)
            val hasSplits = format == PackageFormat.APKS
                || format == PackageFormat.APKM
                || format == PackageFormat.XAPK
                || format == PackageFormat.ZIP

            val meta = when (format) {
                PackageFormat.APK -> MetadataReader.readFromApk(context, uri, name)
                PackageFormat.XAPK -> MetadataReader.readFromXapk(context, uri)
                PackageFormat.APKM -> MetadataReader.readFromApkm(context, uri)
                PackageFormat.APKS, PackageFormat.ZIP -> MetadataReader.readFromApks(context, uri)
                else -> MetadataReader.AppMeta()
            }

            val hash = FileUtil.computeHash(context, uri)
            DebugLog.i("MainViewModel", "SHA-256: $hash")

            _availableSplits.value = emptyList()
            _selectedSplits.value = emptyList()
            _state.value = InstallState.FileSelected(
                uri = uri,
                name = name,
                size = size,
                format = format,
                hasSplits = hasSplits,
                packageName = meta.packageName,
                versionName = meta.versionName,
                versionCode = meta.versionCode,
                appLabel = meta.appLabel,
                appIcon = meta.appIcon,
                hash = hash,
                minSdk = meta.minSdk,
                targetSdk = meta.targetSdk
            )
        }
    }

    private suspend fun handleApkvSelected(uri: Uri, name: String) {
        val context = getApplication<Application>()
        val encrypted = ApkvInstaller.isEncrypted(context, uri)
        if (encrypted) {
            val header = ApkvInstaller.readHeader(context, uri)
            _state.value = InstallState.PasswordRequired(
                uri = uri,
                fileName = name,
                packageName = header?.packageName ?: "",
                versionName = header?.versionName ?: "",
                label = header?.label ?: ""
            )
            return
        }
        resolveApkvFileSelected(uri, name, password = null)
    }

    fun submitApkvPassword(password: String) {
        val pending = _state.value as? InstallState.PasswordRequired ?: return
        val context = getApplication<Application>()
        fileLoadingJob?.cancel()
        fileLoadingJob = viewModelScope.launch(Dispatchers.IO) {
            _state.value = InstallState.FileLoading
            val valid = ApkvInstaller.verifyPassword(context, pending.uri, password)
            if (!valid) {
                _state.value = InstallState.Error(ApkvInstaller.ERROR_WRONG_PASSWORD)
                return@launch
            }
            resolveApkvFileSelected(pending.uri, pending.fileName, password)
        }
    }

    private suspend fun resolveApkvFileSelected(uri: Uri, name: String, password: String?) {
        val context = getApplication<Application>()
        val encrypted = password != null
        val size = FileUtil.getFileSize(context, uri)
        val manifest = ApkvInstaller.readManifest(context, uri, password)
        val splits = manifest?.splits ?: emptyList()
        val hash = FileUtil.computeHash(context, uri)
        val iconBytes = ApkvInstaller.readIcon(context, uri, password)
        val appIcon = if (iconBytes != null) {
            android.graphics.BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.size)
        } else {
            MetadataReader.readFromApkv(context, uri, password).appIcon
        }
        _availableSplits.value = splits
        _selectedSplits.value = splits
        _state.value = InstallState.FileSelected(
            uri = uri,
            name = name,
            size = size,
            format = PackageFormat.APKV,
            splits = splits,
            hasSplits = manifest?.isSplit == true,
            packageName = manifest?.packageName ?: "",
            versionName = manifest?.versionName ?: "",
            appLabel = manifest?.label ?: "",
            appIcon = appIcon,
            hash = hash,
            isEncryptedApkv = encrypted,
            apkvPassword = password
        )
    }

    suspend fun ensureSplitsLoaded() {
        val current = (_state.value as? InstallState.FileSelected) ?: _currentFile.value ?: return
        if (!current.hasSplits || _availableSplits.value.isNotEmpty()) return
        val context = getApplication<Application>()
        val splits = withContext(Dispatchers.IO) {
            when (current.format) {
                PackageFormat.APKS -> ApksInstaller.listSplits(context, current.uri)
                PackageFormat.APKM -> ApkmInstaller.listSplits(context, current.uri)
                PackageFormat.XAPK -> XapkInstaller.listSplits(context, current.uri)
                PackageFormat.ZIP -> ZipApkInstaller.listSplits(context, current.uri)
                PackageFormat.APKV -> ApkvInstaller.listSplits(context, current.uri, current.apkvPassword)
                else -> emptyList()
            }
        }
        _availableSplits.value = splits
        _selectedSplits.value = splits
    }

    fun toggleSplit(splitName: String, selected: Boolean) {
        val current = _selectedSplits.value.toMutableList()
        if (selected) { if (!current.contains(splitName)) current.add(splitName) }
        else current.remove(splitName)
        _selectedSplits.value = current
    }

    fun selectAllSplits() { _selectedSplits.value = _availableSplits.value.toList() }
    fun deselectAllSplits() { _selectedSplits.value = emptyList() }

    fun cancelInstall() {
        currentInstallJob?.cancel()
        currentInstallJob = null
        isProcessingQueue = false
        _batchProgress.value = null
        InstallHelper.reset()
        val previous = _state.value
        if (previous is InstallState.Installing || previous is InstallState.Analyzing) {
            val context = getApplication<Application>()
            NotificationHelper.postInstallCancelled(context, "", "Installation cancelled by user.")
            _state.value = InstallState.Cancelled("Installation cancelled by user.")
            DebugLog.i("MainViewModel", "Install cancelled by user")
            viewModelScope.launch(Dispatchers.IO) { autoClearCache() }
        }
    }

    fun install() {
        val active = _state.value
        if (active is InstallState.Installing || active is InstallState.Analyzing) return
        val current = (active as? InstallState.FileSelected) ?: _currentFile.value ?: return
        val splits = _selectedSplits.value.takeIf { it.isNotEmpty() }
        currentInstallJob = viewModelScope.launch(Dispatchers.IO) {
            performInstall(current, splits)
        }
    }

    fun buildQueueItems(uris: List<Uri>) {
        queueMetadataJob?.cancel()
        _queueItems.value = emptyList()
        _queueActive.value = true
        queueMetadataJob = viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            autoClearCache()

            val placeholders = uris.map { uri ->
                val name = FileUtil.getFileName(context, uri)
                val format = PackageFormat.fromFileName(name)
                val label = if (format != PackageFormat.UNKNOWN) {
                    format.label
                } else {
                    name.substringAfterLast('.', "").uppercase()
                }
                QueueItem(
                    uri = uri,
                    displayName = name,
                    formatLabel = label,
                    fileSize = FileUtil.getFileSize(context, uri)
                )
            }
            _queueItems.value = placeholders

            val items = placeholders.toMutableList()

            for (i in items.indices) {
                val placeholder = items[i]
                try {
                    val name = placeholder.displayName
                    val format = PackageFormat.fromFileName(name)

                    if (format == PackageFormat.APKV && ApkvInstaller.isEncrypted(context, placeholder.uri)) {
                        val header = ApkvInstaller.readHeader(context, placeholder.uri)
                        items[i] = placeholder.copy(
                            appLabel = header?.label ?: "",
                            packageName = header?.packageName ?: "",
                            versionName = header?.versionName ?: "",
                            isEncryptedApkv = true
                        )
                        _queueItems.value = items.toList()
                        continue
                    }

                    val meta = when (format) {
                        PackageFormat.APK  -> MetadataReader.readFromApk(context, placeholder.uri, name)
                        PackageFormat.XAPK -> MetadataReader.readFromXapk(context, placeholder.uri)
                        PackageFormat.APKM -> MetadataReader.readFromApkm(context, placeholder.uri)
                        PackageFormat.APKS,
                        PackageFormat.ZIP  -> MetadataReader.readFromApks(context, placeholder.uri)
                        PackageFormat.APKV -> MetadataReader.readFromApkv(context, placeholder.uri)
                        else               -> MetadataReader.AppMeta()
                    }
                    items[i] = placeholder.copy(
                        appLabel    = meta.appLabel,
                        packageName = meta.packageName,
                        versionName = meta.versionName,
                        versionCode = meta.versionCode,
                        appIcon     = meta.appIcon
                    )
                    _queueItems.value = items.toList()
                } catch (e: Exception) {
                    DebugLog.e("MainViewModel", "buildQueueItems meta failed for ${placeholder.displayName}: ${e.message}")
                }
            }

            for (i in items.indices) {
                try {
                    val itemUri = items[i].uri
                    val hash = FileUtil.computeHash(context, itemUri)
                    items[i] = items[i].copy(sha256 = hash)
                    val latest = _queueItems.value.toMutableList()
                    val latestIdx = latest.indexOfFirst { it.uri == itemUri }
                    if (latestIdx >= 0) {
                        latest[latestIdx] = latest[latestIdx].copy(sha256 = hash)
                        _queueItems.value = latest
                    }
                } catch (e: Exception) {
                    DebugLog.e("MainViewModel", "buildQueueItems hash failed for ${items[i].displayName}: ${e.message}")
                }
            }
        }
    }

    fun enqueueFiles(queueItems: List<QueueItem>) {
        if (queueItems.isEmpty()) return
        currentInstallJob?.cancel()
        currentInstallJob = viewModelScope.launch(Dispatchers.IO) {
            isProcessingQueue = true
            try {
                val total = queueItems.size
                for ((index, item) in queueItems.withIndex()) {
                    _batchProgress.value = BatchProgress(index + 1, total, "")
                    val fileState = loadFileMetadata(item)
                    if (fileState == null) {
                        DebugLog.i("MainViewModel", "Skipping unreadable file at batch index $index")
                        continue
                    }
                    _batchProgress.value = BatchProgress(
                        index + 1, total,
                        fileState.appLabel.ifEmpty { fileState.name }
                    )
                    performInstall(fileState, fileState.splits.takeIf { it.isNotEmpty() })
                    if (index < total - 1) delay(300)
                }
            } finally {
                isProcessingQueue = false
                _batchProgress.value = null
                _queueItems.value = emptyList()
                _queueActive.value = false
                autoClearCache()
            }
            _state.value = InstallState.Idle
        }
    }

    fun submitBatchApkvPassword(uri: Uri, password: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val valid = ApkvInstaller.verifyPassword(context, uri, password)
            if (valid) {
                val iconBytes = ApkvInstaller.readIcon(context, uri, password)
                val appIcon = if (iconBytes != null) {
                    android.graphics.BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.size)
                } else {
                    MetadataReader.readFromApkv(context, uri, password).appIcon
                }
                val current = _queueItems.value.toMutableList()
                val idx = current.indexOfFirst { it.uri == uri }
                if (idx >= 0) {
                    current[idx] = current[idx].copy(
                        apkvPassword = password,
                        isEncryptedApkv = false,
                        appIcon = appIcon ?: current[idx].appIcon
                    )
                    _queueItems.value = current
                }
            }
            withContext(Dispatchers.Main) { onResult(valid) }
        }
    }

    private suspend fun loadFileMetadata(item: QueueItem): InstallState.FileSelected? {
        val uri = item.uri
        val context = getApplication<Application>()
        return try {
            val name = item.displayName
            val format = PackageFormat.fromFileName(name)
            if (format == PackageFormat.UNKNOWN) return null

            if (format == PackageFormat.APKV) {
                if (item.isEncryptedApkv) {
                    DebugLog.i("MainViewModel", "Skipping encrypted APKV without password in batch: $name")
                    return null
                }
                val password = item.apkvPassword
                val manifest = ApkvInstaller.readManifest(context, uri, password)
                val splits = manifest?.splits ?: emptyList()
                val iconBytes = ApkvInstaller.readIcon(context, uri, password)
                val appIcon = if (iconBytes != null)
                    android.graphics.BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.size)
                else null
                return InstallState.FileSelected(
                    uri = uri, name = name,
                    size = FileUtil.getFileSize(context, uri),
                    format = PackageFormat.APKV,
                    splits = splits,
                    hasSplits = manifest?.isSplit == true,
                    packageName = manifest?.packageName ?: "",
                    versionName = manifest?.versionName ?: "",
                    appLabel = manifest?.label ?: "",
                    appIcon = appIcon,
                    hash = "",
                    apkvPassword = password
                )
            }

            val size = FileUtil.getFileSize(context, uri)
            val hasSplits = format == PackageFormat.APKS || format == PackageFormat.APKM
                || format == PackageFormat.XAPK || format == PackageFormat.ZIP
            val meta = when (format) {
                PackageFormat.APK -> MetadataReader.readFromApk(context, uri, name)
                PackageFormat.XAPK -> MetadataReader.readFromXapk(context, uri)
                PackageFormat.APKM -> MetadataReader.readFromApkm(context, uri)
                PackageFormat.APKS, PackageFormat.ZIP -> MetadataReader.readFromApks(context, uri)
                else -> MetadataReader.AppMeta()
            }
            InstallState.FileSelected(
                uri = uri, name = name, size = size, format = format,
                hasSplits = hasSplits, packageName = meta.packageName,
                versionName = meta.versionName, versionCode = meta.versionCode,
                appLabel = meta.appLabel, appIcon = meta.appIcon, hash = ""
            )
        } catch (e: Exception) {
            DebugLog.e("MainViewModel", "loadFileMetadata failed: ${e.message}")
            null
        }
    }

    private suspend fun performInstall(fileState: InstallState.FileSelected, splits: List<String>? = null) {
        val context = getApplication<Application>()
        InstallHelper.reset()
        installStartTime = System.currentTimeMillis()
        _state.value = InstallState.Analyzing

        val result = when (fileState.format) {
            PackageFormat.APK -> ApkInstaller.install(context, fileState.uri,
                onStep = { step ->
                    _state.value = InstallState.Installing(step)
                    NotificationHelper.postInstalling(context, fileState.appLabel, step)
                },
                onProgress = { progress ->
                    val step = (_state.value as? InstallState.Installing)?.step ?: ""
                    _state.value = InstallState.Installing(step, progress)
                    NotificationHelper.updateProgress(context, fileState.appLabel, progress)
                }
            )
            PackageFormat.XAPK -> XapkInstaller.install(context, fileState.uri,
                onStep = { step ->
                    _state.value = InstallState.Installing(step)
                    NotificationHelper.postInstalling(context, fileState.appLabel, step)
                },
                selectedSplits = splits,
                onProgress = { progress ->
                    val step = (_state.value as? InstallState.Installing)?.step ?: ""
                    _state.value = InstallState.Installing(step, progress)
                    NotificationHelper.updateProgress(context, fileState.appLabel, progress)
                }
            )
            PackageFormat.APKS -> ApksInstaller.install(context, fileState.uri,
                onStep = { step ->
                    _state.value = InstallState.Installing(step)
                    NotificationHelper.postInstalling(context, fileState.appLabel, step)
                },
                selectedSplits = splits,
                onProgress = { progress ->
                    val step = (_state.value as? InstallState.Installing)?.step ?: ""
                    _state.value = InstallState.Installing(step, progress)
                    NotificationHelper.updateProgress(context, fileState.appLabel, progress)
                }
            )
            PackageFormat.APKM -> ApkmInstaller.install(context, fileState.uri,
                onStep = { step ->
                    _state.value = InstallState.Installing(step)
                    NotificationHelper.postInstalling(context, fileState.appLabel, step)
                },
                selectedSplits = splits,
                onProgress = { progress ->
                    val step = (_state.value as? InstallState.Installing)?.step ?: ""
                    _state.value = InstallState.Installing(step, progress)
                    NotificationHelper.updateProgress(context, fileState.appLabel, progress)
                }
            )
            PackageFormat.ZIP -> ZipApkInstaller.install(context, fileState.uri,
                onStep = { step ->
                    _state.value = InstallState.Installing(step)
                    NotificationHelper.postInstalling(context, fileState.appLabel, step)
                },
                selectedSplits = splits,
                onProgress = { progress ->
                    val step = (_state.value as? InstallState.Installing)?.step ?: ""
                    _state.value = InstallState.Installing(step, progress)
                    NotificationHelper.updateProgress(context, fileState.appLabel, progress)
                }
            )
            PackageFormat.APKV -> ApkvInstaller.install(
                context, fileState.uri, fileState.apkvPassword,
                onStep = { step ->
                    _state.value = InstallState.Installing(step)
                    NotificationHelper.postInstalling(context, fileState.appLabel, step)
                },
                selectedSplits = splits,
                onProgress = { progress ->
                    val step = (_state.value as? InstallState.Installing)?.step ?: ""
                    _state.value = InstallState.Installing(step, progress)
                    NotificationHelper.updateProgress(context, fileState.appLabel, progress)
                }
            )
            PackageFormat.UNKNOWN -> {
                _state.value = InstallState.Error("File format not supported")
                return
            }
        }

        if (result.isFailure) {
            val msg = result.exceptionOrNull()?.message ?: "Unknown error"
            DebugLog.e("MainViewModel", "Install failed: $msg")
            NotificationHelper.cancelInstalling(context)
            when (msg) {
                ApkvInstaller.ERROR_WRONG_PASSWORD ->
                    _state.value = InstallState.Error("Incorrect password.")
                ApkvInstaller.ERROR_PASSWORD_REQUIRED ->
                    _state.value = InstallState.Error("Password is required for this file.")
                else ->
                    _state.value = InstallState.Error(msg)
            }
            recordHistory(context, fileState, HistoryStatus.FAILED, msg)
            autoClearCache()
            return
        }

        DebugLog.d("MainViewModel", "Waiting for installation result...")
        val installResult = withContext(Dispatchers.IO) {
            InstallHelper.awaitResult(timeoutMs = 120_000L)
        }

        if (installResult == null) {
            DebugLog.e("MainViewModel", "Timeout waiting for install result")
            _state.value = InstallState.Error(
                "Installation timed out. The system did not respond. " +
                "If you cancelled the install dialog, please try again."
            )
            recordHistory(context, fileState, HistoryStatus.FAILED, "Installation timed out")
            autoClearCache()
            return
        }

        DebugLog.i("MainViewModel", "Install result: $installResult")
        val packageName = fileState.packageName
        val appLabel = fileState.appLabel
        _state.value = when (installResult) {
            is InstallHelper.Result.Success -> {
                NotificationHelper.postInstallSuccess(context, packageName, appLabel)
                recordHistory(context, fileState, HistoryStatus.SUCCESS, "")
                InstallState.Success(packageName)
            }
            is InstallHelper.Result.Failure -> {
                val msg = installResult.message ?: "Install failed"
                if (msg.contains("cancelled", ignoreCase = true) ||
                    msg.contains("aborted", ignoreCase = true)) {
                    NotificationHelper.postInstallCancelled(context, appLabel, "Installation cancelled by user.")
                    recordHistory(context, fileState, HistoryStatus.CANCELLED, msg)
                    InstallState.Cancelled(msg)
                } else {
                    NotificationHelper.postInstallFailure(context, appLabel, msg)
                    recordHistory(context, fileState, HistoryStatus.FAILED, msg)
                    InstallState.Error(msg)
                }
            }
            else -> {
                NotificationHelper.cancelInstalling(context)
                recordHistory(context, fileState, HistoryStatus.FAILED, "Unknown install result")
                InstallState.Error("Unknown install result")
            }
        }
        autoClearCache()
    }

    private fun recordHistory(
        context: android.app.Application,
        fileState: InstallState.FileSelected,
        status: HistoryStatus,
        detail: String
    ) {
        val mode = AppSettings.getInstallMode(context)
        val entry = InstallHistoryEntry(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            appLabel = fileState.appLabel,
            packageName = fileState.packageName,
            versionName = fileState.versionName,
            format = fileState.format.label,
            fileSize = fileState.size,
            status = status,
            detail = detail,
            installMode = mode.name,
            durationMs = System.currentTimeMillis() - installStartTime
        )
        InstallHistoryManager.add(context, entry)
    }

    fun reset() {
        queueMetadataJob?.cancel()
        queueMetadataJob = null
        _queueItems.value = emptyList()
        _queueActive.value = false
        fileLoadingJob?.cancel()
        fileLoadingJob = null
        currentInstallJob?.cancel()
        currentInstallJob = null
        isProcessingQueue = false
        _batchProgress.value = null
        autoClearCache()
        _availableSplits.value = emptyList()
        _selectedSplits.value = emptyList()
        _state.value = InstallState.Idle
    }
}
