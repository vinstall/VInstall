package com.vinstall.alwiz.backup

sealed class BackupState {
    object Idle : BackupState()
    data class Running(val step: String) : BackupState()
    data class Done(val path: String) : BackupState()
    data class Error(val message: String) : BackupState()
}
