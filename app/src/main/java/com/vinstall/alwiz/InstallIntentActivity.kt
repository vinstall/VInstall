package com.vinstall.alwiz

import com.vinstall.alwiz.ui.showToast
import com.vinstall.alwiz.util.Haptics
import com.vinstall.alwiz.util.VersionLabel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import com.vinstall.alwiz.ui.ScaledActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.vinstall.alwiz.databinding.ActivityInstallIntentBinding
import com.vinstall.alwiz.installer.InstallIntentViewModel
import com.vinstall.alwiz.installer.SilentInstallService
import com.vinstall.alwiz.model.InstallState
import com.vinstall.alwiz.history.InstallHistoryManager
import com.vinstall.alwiz.model.HistoryStatus
import com.vinstall.alwiz.model.PackageFormat
import com.vinstall.alwiz.settings.AppSettings
import com.vinstall.alwiz.settings.BlockDuration
import com.vinstall.alwiz.settings.BlockedCallers
import com.vinstall.alwiz.settings.DialogController
import com.vinstall.alwiz.settings.DialogHelper
import com.vinstall.alwiz.settings.InstallMode
import com.vinstall.alwiz.ui.ConfirmationBottomSheet
import com.vinstall.alwiz.util.CallerResolver
import com.vinstall.alwiz.util.CallerVerifier
import com.vinstall.alwiz.util.FileUtil
import com.vinstall.alwiz.util.MetadataReader
import com.vinstall.alwiz.util.DebugLog
import com.vinstall.alwiz.util.PackageVersions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InstallIntentActivity : ScaledActivity() {

    private lateinit var binding: ActivityInstallIntentBinding
    private val viewModel: InstallIntentViewModel by viewModels()
    private var dialogController: DialogController? = null
    private var confirmShowing = false
    private var untrustedShowing = false
    private var untrustedAccepted = false
    private var passwordDialogShown = false
    private var callerPackage: String? = null
    private var trustedCaller = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val uris = SilentInstallService.urisFrom(intent ?: Intent())
        if (uris.isEmpty()) {
            finish()
            return
        }

        callerPackage = CallerVerifier.callingPackage(this)
        trustedCaller = callerPackage?.let { it in AppSettings.getTrustedCallers(this) } == true
        DebugLog.i("InstallIntent", "Caller=$callerPackage trusted=$trustedCaller")

        val blockedCaller = callerPackage
        if (blockedCaller != null && BlockedCallers.isBlocked(this, blockedCaller)) {
            rejectBlockedCaller(blockedCaller, uris[0])
            return
        }

        if (savedInstanceState == null && shouldAttemptSilentInstall()) {
            attemptSilentInstall(uris)
            return
        }
        startInteractiveFlow(uris[0], savedInstanceState)
    }

    private fun rejectBlockedCaller(caller: String, uri: Uri) {
        DebugLog.i("InstallIntent", "Caller $caller is blocked -> request rejected")
        val label = CallerResolver.labelOf(this, caller)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val name = FileUtil.getFileName(this@InstallIntentActivity, uri)
                    val format = PackageFormat.fromFileName(name)
                    val meta = readMetaQuietly(uri, name, format)
                    InstallHistoryManager.recordExternal(
                        context = this@InstallIntentActivity,
                        sourcePackage = caller,
                        appLabel = meta.appLabel.ifBlank { meta.packageName }.ifBlank { name },
                        packageName = meta.packageName,
                        versionName = meta.versionName,
                        format = format.label,
                        fileSize = runCatching { FileUtil.getFileSize(this@InstallIntentActivity, uri) }.getOrDefault(0L),
                        status = HistoryStatus.BLOCKED,
                        detail = getString(R.string.blocked_history_detail, label),
                        durationMs = 0L
                    )
                } catch (e: Exception) {
                    DebugLog.e("InstallIntent", "Could not record blocked request: ${e.message}")
                }
            }
            Toast.makeText(
                this@InstallIntentActivity,
                getString(R.string.blocked_request_toast, label),
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    private fun readMetaQuietly(uri: Uri, name: String, format: PackageFormat): MetadataReader.AppMeta {
        return try {
            when (format) {
                PackageFormat.APK -> MetadataReader.readFromApk(this, uri, name)
                PackageFormat.XAPK -> MetadataReader.readFromXapk(this, uri)
                PackageFormat.APKM -> MetadataReader.readFromApkm(this, uri)
                PackageFormat.APKS, PackageFormat.ZIP -> MetadataReader.readFromApks(this, uri)
                else -> MetadataReader.AppMeta()
            }
        } catch (e: Exception) {
            DebugLog.e("InstallIntent", "Could not read metadata of blocked request: ${e.message}")
            MetadataReader.AppMeta()
        }
    }

    private fun shouldAttemptSilentInstall(): Boolean {
        if (!AppSettings.isSilentExternalInstall(this)) {
            DebugLog.i("InstallIntent", "Silent from other apps is OFF -> show Confirm Install")
            return false
        }
        if (AppSettings.getInstallMode(this) == InstallMode.NORMAL) return false
        if (!trustedCaller) {
            DebugLog.i("InstallIntent", "Caller not trusted -> interactive flow")
            return false
        }
        return true
    }

    private fun attemptSilentInstall(uris: List<Uri>) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        lifecycleScope.launch {
            val capable = withContext(Dispatchers.IO) { SilentInstallService.isSilentCapable(this@InstallIntentActivity) }
            if (capable) {
                try {
                    SilentInstallService.start(this@InstallIntentActivity, uris, callerPackage)
                    finish()
                    return@launch
                } catch (e: Exception) {
                    DebugLog.e("InstallIntent", "Could not start silent install: ${e.message}")
                }
            }
            DebugLog.i("InstallIntent", "Silent install unavailable -> show Confirm Install")
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            startInteractiveFlow(uris[0], null)
        }
    }

    private fun startInteractiveFlow(uri: Uri, savedInstanceState: Bundle?) {
        binding = ActivityInstallIntentBinding.inflate(layoutInflater)
        setContentView(binding.root)
        DebugLog.i("InstallIntent", "Interactive flow: confirmation required before install")

        lifecycleScope.launch {
            viewModel.state.collect { state -> handleState(state) }
        }

        if (savedInstanceState == null) {
            viewModel.loadPackageInfo(uri, callerPackage)
        }
    }

    private fun showConfirmDialog(info: ConfirmationBottomSheet.AppInstallInfo) {
        if (confirmShowing || isFinishing || isDestroyed) return
        confirmShowing = true
        val source = CallerResolver.resolve(this, callerPackage, trustedCaller)
        val title = if (source.packageName.isNotEmpty()) {
            getString(R.string.confirm_install_from, source.label)
        } else {
            getString(R.string.confirm_install_from_unknown)
        }
        dialogController = DialogHelper.showConfirmation(
            activity = this,
            title = title,
            positiveLabel = getString(R.string.install),
            negativeLabel = getString(R.string.cancel),
            appInstallInfo = info.copy(source = source),
            onDismissed = {
                confirmShowing = false
                dialogController = null
                val state = viewModel.state.value
                if (state is InstallState.FileSelected || state is InstallState.Idle || state is InstallState.FileLoading) {
                    viewModel.recordCancelled()
                    finish()
                }
            },
            onConfirm = { viewModel.install() }
        )
    }

    private fun presentConfirmation(info: ConfirmationBottomSheet.AppInstallInfo) {
        if (trustedCaller || untrustedAccepted) {
            showConfirmDialog(info)
        } else {
            showUntrustedDialog(info)
        }
    }

    private fun showUntrustedDialog(info: ConfirmationBottomSheet.AppInstallInfo) {
        if (untrustedShowing || confirmShowing || isFinishing || isDestroyed) return
        untrustedShowing = true
        val caller = CallerResolver.resolve(this, callerPackage, false)
        val target = info.appLabel.ifBlank { info.packageName }.ifBlank { getString(R.string.untrusted_behavior_unknown_target) }
        val behavior = buildString {
            append(getString(R.string.untrusted_behavior, target))
            if (info.packageName.isNotEmpty()) {
                append("\n")
                append(info.packageName)
                if (info.versionName.isNotEmpty()) append(" · ${VersionLabel.format(info.versionName)}")
            }
        }
        dialogController = DialogHelper.showUntrustedCaller(
            activity = this,
            caller = caller,
            behavior = behavior,
            onContinue = {
                untrustedShowing = false
                untrustedAccepted = true
                dialogController = null
                showConfirmDialog(info)
            },
            onBlock = { duration -> blockCaller(caller.label, duration) },
            onDismissed = {
                untrustedShowing = false
                dialogController = null
                viewModel.recordCancelled()
                finish()
            }
        )
    }

    private fun blockCaller(label: String, duration: BlockDuration) {
        val pkg = callerPackage
        untrustedShowing = false
        dialogController = null
        if (pkg == null) {
            finish()
            return
        }
        BlockedCallers.block(this, pkg, duration.millis)
        val durationLabel = getString(duration.labelRes)
        viewModel.recordBlocked(getString(R.string.history_blocked_detail, label, durationLabel))
        Toast.makeText(this, getString(R.string.untrusted_blocked_toast, label, durationLabel), Toast.LENGTH_LONG).show()
        finish()
    }

    private fun handleState(state: InstallState) {
        when (state) {
            is InstallState.PasswordRequired -> {
                if (!passwordDialogShown) {
                    passwordDialogShown = true
                    showPasswordDialog(state)
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
                presentConfirmation(info)
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
                Haptics.success(this)
                showSuccessAndFinish(state.packageName)
            }
            is InstallState.Error -> {
                dialogController?.dismiss()
                dialogController = null
                confirmShowing = false
                Haptics.failure(this)
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

    private fun showPasswordDialog(state: InstallState.PasswordRequired) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_apkv_password, null)
        val layoutPassword = dialogView.findViewById<TextInputLayout>(R.id.layout_password)
        val editPassword = dialogView.findViewById<TextInputEditText>(R.id.edit_password)
        val title = if (state.label.isNotEmpty()) state.label else state.fileName
        val subtitle = if (state.packageName.isNotEmpty() && state.versionName.isNotEmpty())
            "${state.packageName} · ${VersionLabel.format(state.versionName)}" else state.packageName
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
                viewModel.submitApkvPassword(state.uri, password)
            }
        }

        dialog.show()
    }

    private fun showSuccessAndFinish(packageName: String) {
        setResult(RESULT_OK)
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
            showToast(this, getString(R.string.install_success))
            finish()
        }
    }
}
