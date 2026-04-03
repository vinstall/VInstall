package com.vinstall.alwiz.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.vinstall.alwiz.util.DebugLog
import com.vinstall.alwiz.util.InstallEvents

class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        DebugLog.d("InstallReceiver", "status=$status msg=$message")

        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                DebugLog.i("InstallReceiver", "Install SUCCESS")
                InstallEvents.emit(InstallEvents.Result.Success)
            }
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                DebugLog.d("InstallReceiver", "Pending user action")
                val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirmIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                confirmIntent?.let { context.startActivity(it) }
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                DebugLog.i("InstallReceiver", "Install cancelled by user")
                InstallEvents.emit(InstallEvents.Result.Failure("Installation was cancelled"))
            }
            PackageInstaller.STATUS_FAILURE_BLOCKED -> {
                DebugLog.e("InstallReceiver", "Install blocked by the system")
                InstallEvents.emit(InstallEvents.Result.Failure("Installation blocked by the system (check Play Protect or device policy)"))
            }
            PackageInstaller.STATUS_FAILURE_CONFLICT -> {
                DebugLog.e("InstallReceiver", "Install conflict (signature mismatch or version downgrade)")
                InstallEvents.emit(InstallEvents.Result.Failure("Conflict: signature mismatch or version downgrade. Uninstall the existing app first."))
            }
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> {
                DebugLog.e("InstallReceiver", "Install incompatible (ABI or SDK mismatch)")
                InstallEvents.emit(InstallEvents.Result.Failure("Incompatible APK: ABI or Android version mismatch"))
            }
            PackageInstaller.STATUS_FAILURE_INVALID -> {
                DebugLog.e("InstallReceiver", "Install invalid APK")
                InstallEvents.emit(InstallEvents.Result.Failure("Invalid APK: the package file is malformed"))
            }
            PackageInstaller.STATUS_FAILURE_STORAGE -> {
                DebugLog.e("InstallReceiver", "Install failed due to insufficient storage")
                InstallEvents.emit(InstallEvents.Result.Failure("Insufficient storage space"))
            }
            else -> {
                DebugLog.e("InstallReceiver", "Install FAILED: $message (status=$status)")
                InstallEvents.emit(InstallEvents.Result.Failure(message ?: "Install failed (code $status)"))
            }
        }
    }
}
