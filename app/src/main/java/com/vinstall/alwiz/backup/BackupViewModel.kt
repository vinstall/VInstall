package com.vinstall.alwiz.backup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vinstall.alwiz.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class BackupViewModel(app: Application) : AndroidViewModel(app) {

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState: StateFlow<BackupState> = _backupState

    fun loadApps(includeSystem: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = BackupManager.listInstalledApps(getApplication(), includeSystem)
            _apps.value = list
        }
    }

    fun backup(app: AppInfo, outputDir: File, password: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = ApkvExporter.export(
                context = getApplication(),
                appInfo = app,
                outputDir = outputDir,
                password = password
            ) { step ->
                _backupState.value = BackupState.Running(step)
            }
            _backupState.value = if (result.isSuccess) {
                BackupState.Done(result.getOrNull()?.absolutePath ?: "")
            } else {
                BackupState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
            }
        }
    }
}
