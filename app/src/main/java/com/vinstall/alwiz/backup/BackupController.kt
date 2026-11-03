package com.vinstall.alwiz.backup

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.vinstall.alwiz.model.AppInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

object BackupController {

    private val _state = MutableStateFlow<BackupState>(BackupState.Idle)
    val state: StateFlow<BackupState> = _state

    @Volatile
    private var pendingPassword: String? = null

    fun isRunning(): Boolean = _state.value is BackupState.Running

    fun start(context: Context, app: AppInfo, outputDir: File, password: String?): Boolean {
        if (isRunning()) return false
        val appContext = context.applicationContext
        pendingPassword = password
        _state.value = BackupState.Running("", 0f, app.label.ifBlank { app.packageName })
        val intent = Intent(appContext, ExportService::class.java)
            .putExtra(ExportService.EXTRA_PACKAGE, app.packageName)
            .putExtra(ExportService.EXTRA_OUTPUT_DIR, outputDir.absolutePath)
        return try {
            ContextCompat.startForegroundService(appContext, intent)
            true
        } catch (e: Exception) {
            pendingPassword = null
            _state.value = BackupState.Error(e.message ?: e.javaClass.simpleName)
            false
        }
    }

    fun takePassword(): String? {
        val password = pendingPassword
        pendingPassword = null
        return password
    }

    fun publish(state: BackupState) {
        _state.value = state
    }

    fun acknowledgeFinished() {
        val current = _state.value
        if (current is BackupState.Done || current is BackupState.Error) {
            _state.value = BackupState.Idle
        }
    }
}
