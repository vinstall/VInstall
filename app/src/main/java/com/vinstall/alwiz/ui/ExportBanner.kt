package com.vinstall.alwiz.ui

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.vinstall.alwiz.R
import com.vinstall.alwiz.backup.BackupController
import com.vinstall.alwiz.backup.BackupState
import kotlinx.coroutines.launch

fun AppCompatActivity.bindExportBanner(banner: View) {
    val title = banner.findViewById<TextView>(R.id.text_export_banner)
    val bar = banner.findViewById<LinearProgressIndicator>(R.id.progress_export_banner)
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            BackupController.state.collect { state ->
                if (state is BackupState.Running) {
                    title.text = exportStatusText(state)
                    bar.setProgressCompat(state.percent, true)
                    banner.isVisible = true
                } else {
                    banner.isVisible = false
                }
            }
        }
    }
}

fun Context.exportStatusText(state: BackupState.Running): String =
    if (state.step.isBlank()) {
        getString(R.string.export_banner_running, state.label, state.percent)
    } else {
        getString(R.string.export_status_running, state.label, state.step, state.percent)
    }
