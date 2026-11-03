package com.vinstall.alwiz.backup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.vinstall.alwiz.model.AppInfo
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class BackupViewModel(app: Application) : AndroidViewModel(app) {

    val backupState: StateFlow<BackupState> = BackupController.state

    init {
        BackupController.acknowledgeFinished()
    }

    fun isRunning(): Boolean = BackupController.isRunning()

    fun backup(app: AppInfo, outputDir: File, password: String? = null) {
        BackupController.start(getApplication(), app, outputDir, password)
    }
}
