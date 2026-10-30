package com.vinstall.alwiz

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.vinstall.alwiz.databinding.ActivityInstallIntentBinding
import com.vinstall.alwiz.installer.InstallIntentViewModel
import com.vinstall.alwiz.model.InstallState
import com.vinstall.alwiz.settings.DialogController
import com.vinstall.alwiz.settings.DialogHelper
import com.vinstall.alwiz.ui.ConfirmationBottomSheet
import com.vinstall.alwiz.util.PackageVersions
import kotlinx.coroutines.launch

class InstallIntentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInstallIntentBinding
    private val viewModel: InstallIntentViewModel by viewModels()
    private var dialogController: DialogController? = null
    private var confirmShowing = false
    private var passwordDialogShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityInstallIntentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uri = intent?.data
        if (uri == null) {
            finish()
            return
        }

        lifecycleScope.launch {
            viewModel.state.collect { state -> handleState(state, uri) }
        }

        if (savedInstanceState == null) {
            val infoFlow = viewModel.loadPackageInfo(uri)
            lifecycleScope.launch {
                infoFlow.collect { info ->
                    if (info != null) {
                        showConfirmDialog(info, uri)
                        return@collect
                    }
                }
            }
        }
    }

    private fun showConfirmDialog(info: ConfirmationBottomSheet.AppInstallInfo, uri: android.net.Uri) {
        if (confirmShowing) return
        confirmShowing = true
        dialogController = DialogHelper.showConfirmation(
            activity = this,
            title = getString(R.string.confirm_install_title),
            positiveLabel = getString(R.string.install),
            negativeLabel = getString(R.string.cancel),
            appInstallInfo = info,
            onDismissed = {
                confirmShowing = false
                dialogController = null
                val state = viewModel.state.value
                if (state is InstallState.FileSelected || state is InstallState.Idle || state is InstallState.FileLoading) {
                    finish()
                }
            },
            onConfirm = { viewModel.install(uri) }
        )
    }

    private fun handleState(state: InstallState, uri: android.net.Uri) {
        when (state) {
            is InstallState.PasswordRequired -> {
                if (!passwordDialogShown) {
                    passwordDialogShown = true
                    showPasswordDialog(state, uri)
                }
            }
            is InstallState.FileSelected -> {
                passwordDialogShown = false
                val installed = PackageVersions.installed(this, state.packageName)
                val info = ConfirmationBottomSheet.AppInstallInfo(
                    icon = state.appIcon,
                    appLabel = state.appLabel,
                    packageName = state.packageName,
                    versionName = state.versionName,
                    versionCode = state.versionCode,
                    installedVersionName = installed?.first,
                    installedVersionCode = installed?.second,
                    minSdk = state.minSdk,
                    targetSdk = state.targetSdk
                )
                showConfirmDialog(info, uri)
            }
            is InstallState.Analyzing -> {
                dialogController?.updateProgress(getString(R.string.analyzing), -1f)
            }
            is InstallState.Installing -> {
                dialogController?.updateProgress(state.step, state.progress)
            }
            is InstallState.Success -> {
                dialogController?.dismiss()
                dialogController = null
                confirmShowing = false
                showSuccessAndFinish(state.packageName)
            }
            is InstallState.Error -> {
                dialogController?.dismiss()
                dialogController = null
                confirmShowing = false
                Toast.makeText(this, getString(R.string.install_failed, state.message), Toast.LENGTH_LONG).show()
                finish()
            }
            is InstallState.Cancelled -> {
                dialogController?.dismiss()
                dialogController = null
                confirmShowing = false
                finish()
            }
            else -> {}
        }
    }

    private fun showPasswordDialog(state: InstallState.PasswordRequired, uri: android.net.Uri) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_apkv_password, null)
        val layoutPassword = dialogView.findViewById<TextInputLayout>(R.id.layout_password)
        val editPassword = dialogView.findViewById<TextInputEditText>(R.id.edit_password)
        val title = if (state.label.isNotEmpty()) state.label else state.fileName
        val subtitle = if (state.packageName.isNotEmpty() && state.versionName.isNotEmpty())
            "${state.packageName} · v${state.versionName}" else state.packageName
        dialogView.findViewById<android.widget.TextView>(R.id.text_apkv_info).text = subtitle

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.apkv_unlock), null)
            .setNegativeButton(getString(R.string.cancel)) { _, _ -> finish() }
            .setCancelable(false)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val password = editPassword.text?.toString().orEmpty()
                if (password.isBlank()) {
                    layoutPassword.error = getString(R.string.apkv_password_empty)
                    return@setOnClickListener
                }
                layoutPassword.error = null
                dialog.dismiss()
                passwordDialogShown = false
                viewModel.submitApkvPassword(uri, password)
            }
        }

        dialog.show()
    }

    private fun showSuccessAndFinish(packageName: String) {
        val launch = if (packageName.isNotEmpty()) packageManager.getLaunchIntentForPackage(packageName) else null
        if (launch != null) {
            Snackbar.make(binding.container, getString(R.string.install_success), Snackbar.LENGTH_LONG)
                .setAction(getString(R.string.open_app)) { startActivity(launch) }
                .addCallback(object : Snackbar.Callback() {
                    override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                        finish()
                    }
                })
                .show()
        } else {
            Toast.makeText(this, getString(R.string.install_success), Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
