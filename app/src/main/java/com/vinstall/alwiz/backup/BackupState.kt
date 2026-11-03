package com.vinstall.alwiz.backup

sealed class BackupState {
    object Idle : BackupState()
    data class Running(
        val step: String,
        val progress: Float = 0f,
        val label: String = ""
    ) : BackupState() {
        val percent: Int get() = (progress * 100).toInt().coerceIn(0, 100)
    }
    data class Done(val path: String) : BackupState()
    data class Error(val message: String) : BackupState()
}
