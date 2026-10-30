package com.vinstall.alwiz.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.vinstall.alwiz.R
import com.vinstall.alwiz.settings.DialogHelper

class StorageAccess(private val activity: AppCompatActivity) {

    private var pending: (() -> Unit)? = null

    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pending
        pending = null
        if (granted) {
            action?.invoke()
        } else {
            Toast.makeText(activity, R.string.export_permission_rationale, Toast.LENGTH_LONG).show()
        }
    }

    fun run(action: () -> Unit) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                if (Environment.isExternalStorageManager()) action() else promptAllFilesAccess()
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !hasLegacyWrite() -> {
                pending = action
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            else -> action()
        }
    }

    private fun hasLegacyWrite(): Boolean =
        ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    fun promptAllFilesAccess(messageRes: Int = R.string.export_permission_rationale) {
        DialogHelper.showConfirmation(
            activity = activity,
            title = activity.getString(R.string.permission_required),
            message = activity.getString(messageRes),
            positiveLabel = activity.getString(R.string.grant_permission),
            negativeLabel = activity.getString(R.string.cancel),
            onConfirm = { openAllFilesSettings() }
        )
    }

    private fun openAllFilesSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val direct = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${activity.packageName}")
        )
        try {
            activity.startActivity(direct)
        } catch (_: ActivityNotFoundException) {
            try {
                activity.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(activity, R.string.export_permission_rationale, Toast.LENGTH_LONG).show()
            }
        }
    }
}
