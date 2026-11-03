package com.vinstall.alwiz.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.vinstall.alwiz.R
import com.vinstall.alwiz.settings.AppSettings

object NotificationHelper {

    private const val CHANNEL_ID = "vinstall_install"
    private const val CHANNEL_ID_QUIET = "vinstall_install_quiet"
    private const val CHANNEL_ID_EXPORT = "vinstall_export"
    private const val NOTIF_ID_INSTALLING = 1001
    const val NOTIF_ID_SILENT = 1002
    const val NOTIF_ID_EXPORT = 1003
    private var nextNotificationId = 2000

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notify_channel_install),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        if (manager.getNotificationChannel(CHANNEL_ID_QUIET) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_QUIET,
                    context.getString(R.string.notify_channel_quiet),
                    NotificationManager.IMPORTANCE_MIN
                )
            )
        }
        if (manager.getNotificationChannel(CHANNEL_ID_EXPORT) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_EXPORT,
                    context.getString(R.string.notify_channel_export),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    fun postInstalling(context: Context, appLabel: String, step: String) {
        if (AppSettings.isHideNotification(context)) return
        val label = appLabel.ifBlank { context.getString(R.string.app_name) }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.notify_installing_title, label))
            .setContentText(step)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID_INSTALLING, notification)
    }

    fun updateProgress(context: Context, appLabel: String, progress: Float) {
        if (AppSettings.isHideNotification(context)) return
        val label = appLabel.ifBlank { context.getString(R.string.app_name) }
        val percent = (progress * 100).toInt().coerceIn(0, 100)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.notify_installing_title, label))
            .setContentText("$percent%")
            .setOngoing(true)
            .setProgress(100, percent, false)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID_INSTALLING, notification)
    }

    fun updateInstallingStep(context: Context, appLabel: String, step: String) {
        postInstalling(context, appLabel, step)
    }

    fun cancelInstalling(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIF_ID_INSTALLING)
    }

    fun postInstallCancelled(context: Context, appLabel: String, reason: String) {
        if (AppSettings.isHideNotification(context)) return
        val label = appLabel.ifBlank { context.getString(R.string.app_name) }
        val body = reason.ifBlank { context.getString(R.string.notify_install_cancelled_body) }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.notify_install_cancelled_title, label))
            .setContentText(body)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID_INSTALLING, notification)
    }

    fun postInstallSuccess(context: Context, packageName: String, appLabel: String) {
        cancelInstalling(context)
        if (AppSettings.isHideNotification(context)) return
        val label = appLabel.ifBlank { packageName }.ifBlank { context.getString(R.string.app_name) }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.notify_install_success_title))
            .setContentText(context.getString(R.string.notify_install_success_body, label))
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(nextNotificationId++, notification)
    }

    fun postInstallFailure(context: Context, appLabel: String, reason: String) {
        cancelInstalling(context)
        if (AppSettings.isHideNotification(context)) return
        val label = appLabel.ifBlank { context.getString(R.string.app_name) }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.notify_install_failed_title, label))
            .setContentText(reason)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(nextNotificationId++, notification)
    }

    fun buildSilentProgress(context: Context, appLabel: String, step: String, progress: Float? = null): Notification {
        val label = appLabel.ifBlank { context.getString(R.string.app_name) }
        val hidden = AppSettings.isHideNotification(context)
        val builder = NotificationCompat.Builder(context, if (hidden) CHANNEL_ID_QUIET else CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.notify_installing_title, label))
            .setContentText(step)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(if (hidden) NotificationCompat.PRIORITY_MIN else NotificationCompat.PRIORITY_LOW)
        if (progress == null) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(100, (progress * 100).toInt().coerceIn(0, 100), false)
        }
        return builder.build()
    }

    fun updateSilentProgress(context: Context, appLabel: String, step: String, progress: Float? = null) {
        if (AppSettings.isHideNotification(context)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID_SILENT, buildSilentProgress(context, appLabel, step, progress))
    }

    private fun openAppIntent(context: Context): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun buildExportProgress(context: Context, appLabel: String, step: String, progress: Float): Notification {
        val label = appLabel.ifBlank { context.getString(R.string.app_name) }
        val percent = (progress * 100).toInt().coerceIn(0, 100)
        val text = if (step.isBlank()) "$percent%" else "$percent% · $step"
        return NotificationCompat.Builder(context, CHANNEL_ID_EXPORT)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(context.getString(R.string.notify_export_running_title, label))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent(context))
            .setProgress(100, percent, false)
            .build()
    }

    fun updateExportProgress(context: Context, appLabel: String, step: String, progress: Float) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID_EXPORT, buildExportProgress(context, appLabel, step, progress))
    }

    fun postExportFinished(context: Context, appLabel: String, success: Boolean, body: String) {
        val label = appLabel.ifBlank { context.getString(R.string.app_name) }
        val title = context.getString(
            if (success) R.string.notify_export_done_title else R.string.notify_export_failed_title
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_EXPORT)
            .setSmallIcon(if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle("$title · $label")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(nextNotificationId++, notification)
    }
}
