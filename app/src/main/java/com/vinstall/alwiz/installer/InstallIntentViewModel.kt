package com.vinstall.alwiz.installer

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vinstall.alwiz.installer.ApkmInstaller
import com.vinstall.alwiz.installer.ApkvInstaller
import com.vinstall.alwiz.installer.ApksInstaller
import com.vinstall.alwiz.model.InstallState
import com.vinstall.alwiz.model.PackageFormat
import com.vinstall.alwiz.settings.AppSettings
import com.vinstall.alwiz.settings.InstallMode
import com.vinstall.alwiz.ui.ConfirmationBottomSheet
import com.vinstall.alwiz.util.DebugLog
import com.vinstall.alwiz.util.FileUtil
import com.vinstall.alwiz.util.MetadataReader
import com.vinstall.alwiz.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InstallIntentViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)
    val state: StateFlow<InstallState> = _state

    fun loadPackageInfo(uri: Uri): MutableStateFlow<ConfirmationBottomSheet.AppInstallInfo?> {
        val infoFlow = MutableStateFlow<ConfirmationBottomSheet.AppInstallInfo?>(null)
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = InstallState.FileLoading
            try {
                val name = FileUtil.getFileName(context, uri)
                val format = PackageFormat.fromFileName(name)

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
                val installed = if (meta.packageName.isNotEmpty()) {
                    getInstalledVersionInfo(meta.packageName)
                } else null

                infoFlow.value = ConfirmationBottomSheet.AppInstallInfo(
                    icon = meta.appIcon,
                    appLabel = meta.appLabel,
                    packageName = meta.packageName,
                    versionName = meta.versionName,
                    versionCode = meta.versionCode,
                    installedVersionName = installed?.first,
                    installedVersionCode = installed?.second,
                    minSdk = meta.minSdk,
                    targetSdk = meta.targetSdk
                )

                _state.value = InstallState.FileSelected(
                    uri = uri,
                    name = name,
                    size = FileUtil.getFileSize(context, uri),
                    format = format,
                    packageName = meta.packageName,
                    versionName = meta.versionName,
                    versionCode = meta.versionCode,
                    appLabel = meta.appLabel,
                    appIcon = meta.appIcon,
                    minSdk = meta.minSdk,
                    targetSdk = meta.targetSdk
                )
            } catch (e: Exception) {
                DebugLog.e("InstallIntentVM", "loadPackageInfo failed: ${e.message}")
                _state.value = InstallState.Error(e.message ?: "Failed to read package")
            }
        }
        return infoFlow
    }

    fun install(uri: Uri) {
        val context = getApplication<Application>()
        val fileState = _state.value as? InstallState.FileSelected ?: return
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
                    _state.value = InstallState.Error("Unsupported format from file manager")
                    return@launch
                }
            }

            if (result.isFailure) {
                _state.value = InstallState.Error(result.exceptionOrNull()?.message ?: "Install failed")
                autoClearCache()
                return@launch
            }

            val installResult = withContext(Dispatchers.IO) {
                InstallHelper.awaitResult(timeoutMs = 120_000L)
            }

            autoClearCache()

            _state.value = when {
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
            _state.value = InstallState.FileSelected(
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
        }
    }
}
