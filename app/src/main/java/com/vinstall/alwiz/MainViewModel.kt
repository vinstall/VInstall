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
import com.vinstall.alwiz.util.InstallEvents
import com.vinstall.alwiz.util.MetadataReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)
    val state: StateFlow<InstallState> = _state

    private val _availableSplits = MutableStateFlow<List<String>>(emptyList())
    val availableSplits: StateFlow<List<String>> = _availableSplits

    private val _selectedSplits = MutableStateFlow<List<String>>(emptyList())
    val selectedSplits: StateFlow<List<String>> = _selectedSplits

    private val installQueue = ArrayDeque<Uri>()
    private var isProcessingQueue = false
    private var currentInstallJob: Job? = null

    private var fileLoadingJob: Job? = null

    fun canExportCurrentFile(): Boolean {
        val current = _state.value as? InstallState.FileSelected ?: return false
        return !current.isEncryptedApkv
    }

    fun onFileSelected(uri: Uri) {
        val context = getApplication<Application>()

        // FIX #1: Cancel loading sebelumnya sebelum mulai yang baru
        fileLoadingJob?.cancel()

        fileLoadingJob = viewModelScope.launch(Dispatchers.IO) {
            _state.value = InstallState.FileLoading

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
                appLabel = meta.appLabel,
                appIcon = meta.appIcon,
                hash = hash
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

    fun loadSplitsIfNeeded() {
        val current = _state.value as? InstallState.FileSelected ?: return
        if (!current.hasSplits || _availableSplits.value.isNotEmpty()) return
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val splits = when (current.format) {
                PackageFormat.APKS -> ApksInstaller.listSplits(context, current.uri)
                PackageFormat.APKM -> ApkmInstaller.listSplits(context, current.uri)
                PackageFormat.XAPK -> XapkInstaller.listSplits(context, current.uri)
                PackageFormat.ZIP -> ZipApkInstaller.listSplits(context, current.uri)
                PackageFormat.APKV -> ApkvInstaller.listSplits(context, current.uri, current.apkvPassword)
                else -> emptyList()
            }
            _availableSplits.value = splits
            _selectedSplits.value = splits
        }
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
        InstallEvents.reset()
        val previous = _state.value
        if (previous is InstallState.Installing || previous is InstallState.Analyzing) {
            _state.value = InstallState.Cancelled("Installation cancelled by user.")
            DebugLog.i("MainViewModel", "Install cancelled by user")
        }
    }

    fun install() {
        val current = _state.value as? InstallState.FileSelected ?: return
        val context = getApplication<Application>()
        val splits = _selectedSplits.value.takeIf { it.isNotEmpty() }
        val mode = AppSettings.getInstallMode(context)
        DebugLog.i("MainViewModel", "Starting installation mode=$mode format=${current.format}")

        currentInstallJob = viewModelScope.launch(Dispatchers.IO) {
            InstallEvents.reset()
            _state.value = InstallState.Analyzing

            val result = when (current.format) {
                PackageFormat.APK -> ApkInstaller.install(context, current.uri) { step ->
                    _state.value = InstallState.Installing(step)
                }
                PackageFormat.XAPK -> XapkInstaller.install(context, current.uri, { step ->
                    _state.value = InstallState.Installing(step)
                }, splits)
                PackageFormat.APKS -> ApksInstaller.install(context, current.uri, { step ->
                    _state.value = InstallState.Installing(step)
                }, splits)
                PackageFormat.APKM -> ApkmInstaller.install(context, current.uri, { step ->
                    _state.value = InstallState.Installing(step)
                }, splits)
                PackageFormat.ZIP -> ZipApkInstaller.install(context, current.uri, { step ->
                    _state.value = InstallState.Installing(step)
                }, splits)
                PackageFormat.APKV -> ApkvInstaller.install(
                    context, current.uri, current.apkvPassword, { step ->
                        _state.value = InstallState.Installing(step)
                    }, splits
                )
                PackageFormat.UNKNOWN -> {
                    _state.value = InstallState.Error("File format not supported")
                    return@launch
                }
            }

            if (result.isFailure) {
                val msg = result.exceptionOrNull()?.message ?: "Unknown error"
                DebugLog.e("MainViewModel", "Install failed: $msg")
                when (msg) {
                    "DRM protected" ->
                        _state.value = InstallState.Error("This APKM file is DRM-protected and cannot be installed.")
                    ApkvInstaller.ERROR_WRONG_PASSWORD ->
                        _state.value = InstallState.Error("Incorrect password.")
                    ApkvInstaller.ERROR_PASSWORD_REQUIRED ->
                        _state.value = InstallState.Error("Password is required for this file.")
                    else ->
                        _state.value = InstallState.Error(msg)
                }
                return@launch
            }

            DebugLog.d("MainViewModel", "Waiting for installation result...")
            val installResult = withContext(Dispatchers.IO) {
                InstallEvents.awaitResult(timeoutMs = 120_000L)
            }

            if (installResult == null) {
                DebugLog.e("MainViewModel", "Timeout waiting for install result")
                _state.value = InstallState.Error(
                    "Installation timed out. The system did not respond. " +
                    "If you cancelled the install dialog, please try again."
                )
                if (AppSettings.isClearCacheAfterInstall(context)) FileUtil.clearCache(context)
                return@launch
            }

            DebugLog.i("MainViewModel", "Install result: $installResult")
            _state.value = when (installResult) {
                is InstallEvents.Result.Success -> InstallState.Success
                is InstallEvents.Result.Failure -> {
                    val msg = installResult.message ?: "Install failed"
                    if (msg.contains("cancelled", ignoreCase = true) ||
                        msg.contains("aborted", ignoreCase = true)) {
                        InstallState.Cancelled(msg)
                    } else {
                        InstallState.Error(msg)
                    }
                }
                else -> InstallState.Error("Unknown install result")
            }
            if (AppSettings.isClearCacheAfterInstall(context)) FileUtil.clearCache(context)
        }
    }

    fun enqueueFiles(uris: List<Uri>) {
        installQueue.addAll(uris)
        if (!isProcessingQueue) processNextInQueue()
    }

    private fun processNextInQueue() {
        if (installQueue.isEmpty()) {
            isProcessingQueue = false
            return
        }
        isProcessingQueue = true
        val next = installQueue.removeFirst()
        onFileSelected(next)

        viewModelScope.launch {
            _state.collect { s ->
                if (s is InstallState.FileSelected || s is InstallState.Error) {
                    if (installQueue.isNotEmpty()) processNextInQueue()
                    else isProcessingQueue = false
                    return@collect
                }
            }
        }
    }

    fun reset() {
        fileLoadingJob?.cancel()
        fileLoadingJob = null
        currentInstallJob?.cancel()
        currentInstallJob = null
        FileUtil.clearCache(getApplication<Application>())
        _availableSplits.value = emptyList()
        _selectedSplits.value = emptyList()
        _state.value = InstallState.Idle
    }
}
