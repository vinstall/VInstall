package com.vinstall.alwiz.appmanager

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.color.MaterialColors
import com.vinstall.alwiz.R
import com.vinstall.alwiz.backup.BackupManager
import com.vinstall.alwiz.backup.BackupState
import com.vinstall.alwiz.backup.BackupViewModel
import com.vinstall.alwiz.databinding.ActivityAppDetailBinding
import com.vinstall.alwiz.installer.UninstallHelper
import com.vinstall.alwiz.model.AppInfo
import com.vinstall.alwiz.settings.AppSettings
import com.vinstall.alwiz.settings.DialogHelper
import com.vinstall.alwiz.settings.InstallMode
import com.vinstall.alwiz.ui.ExportDialog
import com.vinstall.alwiz.ui.StorageAccess
import com.vinstall.alwiz.ui.applyPressAnimation
import com.vinstall.alwiz.ui.staggerIn
import com.vinstall.alwiz.ui.applySystemBarInsets
import com.vinstall.alwiz.util.DebugLog
import com.vinstall.alwiz.util.FileUtil
import com.vinstall.alwiz.util.HashUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
    }

    private lateinit var binding: ActivityAppDetailBinding
    private val backupViewModel: BackupViewModel by viewModels()
    private val storageAccess = StorageAccess(this)
    private var currentApp: AppInfo? = null
    private var entered = false

    private val uninstallLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            DebugLog.i("AppDetail", "Uninstall successful (normal mode): ${currentApp?.packageName}")
            Toast.makeText(this, getString(R.string.uninstall_success), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityAppDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        if (packageName == null) {
            finish()
            return
        }

        lifecycleScope.launch {
            val app = BackupManager.getApp(this@AppDetailActivity, packageName)
            if (app == null) {
                Toast.makeText(this@AppDetailActivity, getString(R.string.app_not_found), Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            currentApp = app
            renderApp(app)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                backupViewModel.backupState.collect { renderBackupState(it) }
            }
        }
    }

    private fun renderBackupState(state: BackupState) {
        var color = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorOnSurfaceVariant
        )
        when (state) {
            is BackupState.Idle -> {
                binding.progressBackup.isVisible = false
                binding.textBackupStatus.isVisible = false
                binding.btnBackup.isEnabled = true
                return
            }
            is BackupState.Running -> {
                binding.progressBackup.isVisible = true
                binding.textBackupStatus.text = state.step
                binding.btnBackup.isEnabled = false
            }
            is BackupState.Done -> {
                binding.progressBackup.isVisible = false
                binding.textBackupStatus.text = getString(R.string.backup_success, state.path)
                binding.btnBackup.isEnabled = true
                color = ContextCompat.getColor(this, R.color.status_ok)
            }
            is BackupState.Error -> {
                binding.progressBackup.isVisible = false
                binding.textBackupStatus.text = getString(R.string.backup_failed, state.message)
                binding.btnBackup.isEnabled = true
                color = MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorError)
            }
        }
        binding.textBackupStatus.setTextColor(color)
        binding.textBackupStatus.isVisible = true
    }

    private fun renderApp(app: AppInfo) {
        val fmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        binding.imageIcon.setImageDrawable(app.icon)
        binding.textAppName.text = app.label
        binding.textPackageName.text = app.packageName
        binding.textVersion.text = getString(R.string.version_detail, app.versionName, app.versionCode)
        binding.textSdkInfo.text = getString(R.string.sdk_detail, app.minSdk, app.targetSdk)
        binding.textInstallDate.text = getString(R.string.install_date, fmt.format(Date(app.installTimeMs)))
        binding.textUpdateDate.text = getString(R.string.update_date, fmt.format(Date(app.updateTimeMs)))
        binding.textApkSize.text = getString(R.string.apk_size, FileUtil.formatSize(app.sizeBytes))
        binding.textDataDir.text = app.dataDir

        binding.chipSplit.isVisible = app.isSplitApp
        binding.chipSystem.isVisible = app.isSystemApp
        binding.chipDebug.isVisible = app.isDebuggable

        val splitCount = (app.splitSourceDirs?.size ?: 0) + 1
        binding.textSplitCount.text = getString(R.string.split_count, splitCount)

        binding.textPermissions.text = if (app.requestedPermissions.isEmpty()) {
            getString(R.string.no_permissions)
        } else {
            app.requestedPermissions.joinToString("\n")
        }

        val launchable = packageManager.getLaunchIntentForPackage(app.packageName) != null
        binding.btnOpenApp.isEnabled = launchable
        binding.btnOpenApp.setOnClickListener {
            packageManager.getLaunchIntentForPackage(app.packageName)?.let { startActivity(it) }
                ?: Toast.makeText(this, getString(R.string.cannot_launch), Toast.LENGTH_SHORT).show()
        }

        binding.btnAppInfo.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${app.packageName}")
                }
            )
        }

        binding.btnBackup.setOnClickListener {
            if (backupViewModel.isRunning()) {
                Toast.makeText(this, R.string.export_in_progress, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            ExportDialog.show(this, app) { password ->
                storageAccess.run {
                    val outputDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                        "VInstall/Backups"
                    )
                    backupViewModel.backup(app, outputDir, password)
                }
            }
        }

        binding.btnUninstall.isEnabled = !app.isSystemApp
        binding.textSystemNote.isVisible = app.isSystemApp
        binding.btnUninstall.setOnClickListener { showUninstallDialog(app) }

        binding.btnHash.setOnClickListener { computeAndShowHash(app) }

        listOf(
            binding.btnOpenApp,
            binding.btnAppInfo,
            binding.btnBackup,
            binding.btnHash,
            binding.btnUninstall
        ).forEach { it.applyPressAnimation() }

        if (!entered) {
            entered = true
            listOf<android.view.View>(
                binding.cardHeader,
                binding.cardInfo,
                binding.layoutActions,
                binding.btnUninstall,
                binding.cardPermissions
            ).staggerIn()
        }
    }

    private fun showUninstallDialog(app: AppInfo) {
        val mode = AppSettings.getInstallMode(this)
        val msg = when (mode) {
            InstallMode.ROOT -> getString(R.string.uninstall_confirm_message_root, app.label)
            InstallMode.SHIZUKU -> getString(R.string.uninstall_confirm_message_shizuku, app.label)
            InstallMode.NORMAL -> getString(R.string.uninstall_confirm_message, app.label)
        }

        DialogHelper.showConfirmation(
            activity = this,
            title = getString(R.string.uninstall_confirm_title),
            message = msg,
            positiveLabel = getString(R.string.uninstall),
            negativeLabel = getString(R.string.cancel),
            isDangerous = true,
            onConfirm = { performUninstall(app, mode) }
        )
    }

    private fun performUninstall(app: AppInfo, mode: InstallMode) {
        if (mode == InstallMode.NORMAL) {
            DebugLog.d("AppDetail", "Uninstall normal mode: ${app.packageName}")
            val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                data = Uri.parse("package:${app.packageName}")
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
            }
            uninstallLauncher.launch(intent)
            return
        }

        binding.btnUninstall.isEnabled = false
        lifecycleScope.launch {
            DebugLog.i("AppDetail", "Uninstall via $mode: ${app.packageName}")
            val result = withContext(Dispatchers.IO) {
                UninstallHelper.uninstall(this@AppDetailActivity, app.packageName)
            }
            binding.btnUninstall.isEnabled = true
            result.fold(
                onSuccess = {
                    DebugLog.i("AppDetail", "Uninstall successful: ${app.packageName}")
                    Toast.makeText(this@AppDetailActivity, getString(R.string.uninstall_success), Toast.LENGTH_SHORT).show()
                    finish()
                },
                onFailure = { e ->
                    val errMsg = e.message ?: "Unknown error"
                    DebugLog.e("AppDetail", "Uninstall failed: $errMsg")
                    Toast.makeText(this@AppDetailActivity, getString(R.string.uninstall_failed, errMsg), Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun computeAndShowHash(app: AppInfo) {
        binding.layoutHashLoading.isVisible = true
        binding.btnHash.isEnabled = false
        lifecycleScope.launch {
            val hashes = withContext(Dispatchers.IO) {
                HashUtil.computeAll(File(app.sourceDir))
            }
            binding.layoutHashLoading.isVisible = false
            binding.btnHash.isEnabled = true

            val message = hashes.entries.joinToString("\n\n") { (algo, hash) -> "$algo:\n$hash" }

            DialogHelper.showInfo(
                activity = this@AppDetailActivity,
                title = getString(R.string.hash_info_title),
                body = message,
                closeLabel = getString(R.string.ok),
                subtitle = app.label,
                monospace = true,
                primaryLabel = getString(R.string.copy_sha256),
                onPrimary = {
                    val sha256 = hashes["SHA-256"] ?: return@showInfo
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("SHA-256", sha256))
                    Toast.makeText(this@AppDetailActivity, getString(R.string.hash_copied), Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
