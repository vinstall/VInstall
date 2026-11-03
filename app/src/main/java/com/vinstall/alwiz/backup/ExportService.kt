package com.vinstall.alwiz.backup

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import com.vinstall.alwiz.R
import com.vinstall.alwiz.apkv.ApkvExporter
import com.vinstall.alwiz.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

class ExportService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var lastNotifyAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val packageName = intent?.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val outputDir = intent?.getStringExtra(EXTRA_OUTPUT_DIR).orEmpty()
        enterForeground(packageName)

        if (packageName.isEmpty() || outputDir.isEmpty()) {
            BackupController.publish(BackupState.Error(getString(R.string.app_not_found)))
            finish(startId)
            return START_NOT_STICKY
        }

        val password = BackupController.takePassword()
        scope.launch { runExport(startId, packageName, File(outputDir), password) }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        if (BackupController.isRunning()) {
            BackupController.publish(BackupState.Error(getString(R.string.export_interrupted)))
        }
        super.onDestroy()
    }

    private fun enterForeground(label: String) {
        val notification = NotificationHelper.buildExportProgress(this, label, "", 0f)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, NotificationHelper.NOTIF_ID_EXPORT, notification, type)
    }

    private suspend fun runExport(startId: Int, packageName: String, outputDir: File, password: String?) {
        var label = packageName
        try {
            val app = BackupManager.getApp(this, packageName)
            if (app == null) {
                reportFailure(label, getString(R.string.app_not_found))
                return
            }
            label = app.label.ifBlank { app.packageName }

            var step = ""
            var progress = 0f
            publish(label, step, progress, true)

            val result = ApkvExporter.export(
                context = this,
                appInfo = app,
                outputDir = outputDir,
                password = password,
                onProgress = { value ->
                    progress = value
                    publish(label, step, progress, false)
                },
                onStep = { value ->
                    step = value
                    publish(label, step, progress, true)
                }
            )

            if (result.isSuccess) {
                val path = result.getOrNull()?.absolutePath.orEmpty()
                BackupController.publish(BackupState.Done(path))
                NotificationHelper.postExportFinished(
                    this, label, true, getString(R.string.backup_success, path)
                )
            } else {
                reportFailure(label, result.exceptionOrNull()?.message ?: "Unknown error")
            }
        } catch (e: Exception) {
            reportFailure(label, e.message ?: e.javaClass.simpleName)
        } finally {
            finish(startId)
        }
    }

    private fun publish(label: String, step: String, progress: Float, force: Boolean) {
        BackupController.publish(BackupState.Running(step, progress, label))
        val now = SystemClock.elapsedRealtime()
        if (force || now - lastNotifyAt >= NOTIFY_INTERVAL_MS) {
            lastNotifyAt = now
            NotificationHelper.updateExportProgress(this, label, step, progress)
        }
    }

    private fun reportFailure(label: String, reason: String) {
        BackupController.publish(BackupState.Error(reason))
        NotificationHelper.postExportFinished(
            this, label, false, getString(R.string.backup_failed, reason)
        )
    }

    private fun finish(startId: Int) {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    companion object {
        const val EXTRA_PACKAGE = "extra_package"
        const val EXTRA_OUTPUT_DIR = "extra_output_dir"
        private const val NOTIFY_INTERVAL_MS = 500L
    }
}
