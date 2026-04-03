package com.vinstall.alwiz.appmanager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.vinstall.alwiz.R
import com.vinstall.alwiz.backup.BackupManager
import com.vinstall.alwiz.backup.BackupState
import com.vinstall.alwiz.backup.BackupViewModel
import com.vinstall.alwiz.databinding.ActivityAppDetailBinding
import com.vinstall.alwiz.model.AppInfo
import com.vinstall.alwiz.settings.AppSettings
import com.vinstall.alwiz.settings.InstallMode
import com.vinstall.alwiz.util.DebugLog
import com.vinstall.alwiz.util.FileUtil
import com.vinstall.alwiz.util.HashUtil
import com.vinstall.alwiz.util.UninstallHelper
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
    private var currentApp: AppInfo? = null

    private val uninstallLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val pkg = currentApp?.packageName ?: return@registerForActivityResult
        val pm = packageManager
        try {
            pm.getPackageInfo(pkg, 0)
        } catch (_: Exception) {
            DebugLog.i("AppDetail", "Uninstall successful (activity result): $pkg")
            Toast.makeText(this, getString(R.string.uninstall_success), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        if (packageName == null) { finish(); return }

        lifecycleScope.launch {
            val app = loadAppInfo(packageName)
            if (app == null) {
                Toast.makeText(this@AppDetailActivity, getString(R.string.app_not_found), Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            currentApp = app
            renderApp(app)
        }

        lifecycleScope.launch {
            backupViewModel.backupState.collect { state ->
                when (state) {
                    is BackupState.Idle -> {
                        binding.progressBackup.visibility = View.GONE
                        binding.textBackupStatus.visibility = View.GONE
                    }
                    is BackupState.Running -> {
                        binding.progressBackup.visibility = View.VISIBLE
                        binding.textBackupStatus.visibility = View.VISIBLE
                        binding.textBackupStatus.text = state.step
                    }
                    is BackupState.Done -> {
                        binding.progressBackup.visibility = View.GONE
                        binding.textBackupStatus.visibility = View.VISIBLE
                        binding.textBackupStatus.text = getString(R.string.backup_success, state.path)
                    }
                    is BackupState.Error -> {
                        binding.progressBackup.visibility = View.GONE
                        binding.textBackupStatus.visibility = View.VISIBLE
                        binding.textBackupStatus.text = getString(R.string.backup_failed, state.message)
                    }
                }
            }
        }
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

        binding.chipSplit.visibility = if (app.isSplitApp) View.VISIBLE else View.GONE
        binding.chipSystem.visibility = if (app.isSystemApp) View.VISIBLE else View.GONE
        binding.chipDebug.visibility = if (app.isDebuggable) View.VISIBLE else View.GONE

        val splitCount = (app.splitSourceDirs?.size ?: 0) + 1
        binding.textSplitCount.text = getString(R.string.split_count, splitCount)

        val permText = if (app.requestedPermissions.isEmpty()) {
            getString(R.string.no_permissions)
        } else {
            app.requestedPermissions.joinToString("\\n") { "• $it" }
        }
        binding.textPermissions.text = permText

        binding.btnOpenApp.setOnClickListener {
            val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
            if (launchIntent != null) startActivity(launchIntent)
            else Toast.makeText(this, getString(R.string.cannot_launch), Toast.LENGTH_SHORT).show()
        }

        binding.btnAppInfo.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${app.packageName}")
                }
            )
        }

        binding.btnBackup.setOnClickListener {
            showExportDialog(app)
        }

        binding.btnUninstall.setOnClickListener {
            if (app.isSystemApp) {
                Toast.makeText(this, getString(R.string.cannot_uninstall_system), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showUninstallDialog(app)
        }

        binding.btnHash.setOnClickListener {
            computeAndShowHash(app)
        }
    }

    private fun showExportDialog(app: AppInfo) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_export, null)
        val textInfo = dialogView.findViewById<android.widget.TextView>(R.id.text_export_info)
        val checkboxEncrypt = dialogView.findViewById<MaterialCheckBox>(R.id.checkbox_encrypt)
        val layoutPassword = dialogView.findViewById<TextInputLayout>(R.id.layout_password)
        val editPassword = dialogView.findViewById<TextInputEditText>(R.id.edit_password)

        textInfo.text = getString(R.string.export_info, app.label, app.versionName)

        checkboxEncrypt.setOnCheckedChangeListener { _, checked ->
            layoutPassword.visibility = if (checked) View.VISIBLE else View.GONE
            if (!checked) editPassword.text?.clear()
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.export_dialog_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.export_button)) { _, _ ->
                val password = if (checkboxEncrypt.isChecked) {
                    editPassword.text?.toString()?.takeIf { it.isNotBlank() }
                } else null
                val outputDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "VInstall/Backups"
                )
                backupViewModel.backup(app, outputDir, password)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showUninstallDialog(app: AppInfo) {
        val mode = AppSettings.getInstallMode(this)
        val msg = when (mode) {
            InstallMode.ROOT -> getString(R.string.uninstall_confirm_message_root, app.label)
            InstallMode.SHIZUKU -> getString(R.string.uninstall_confirm_message_shizuku, app.label)
            InstallMode.NORMAL -> getString(R.string.uninstall_confirm_message, app.label)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.uninstall_confirm_title))
            .setMessage(msg)
            .setPositiveButton(getString(R.string.uninstall)) { _, _ ->
                performUninstall(app, mode)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun performUninstall(app: AppInfo, mode: InstallMode) {
        if (mode == InstallMode.NORMAL) {
            DebugLog.d("AppDetail", "Uninstall normal mode: ${app.packageName}")
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:${app.packageName}")
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
        binding.layoutHashLoading.visibility = View.VISIBLE
        binding.btnHash.isEnabled = false
        lifecycleScope.launch {
            val hashes = withContext(Dispatchers.IO) {
                HashUtil.computeAll(File(app.sourceDir))
            }
            binding.layoutHashLoading.visibility = View.GONE
            binding.btnHash.isEnabled = true

            val message = hashes.entries.joinToString("\n\n") { (algo, hash) ->
                "$algo:\n$hash"
            }

            AlertDialog.Builder(this@AppDetailActivity)
                .setTitle(getString(R.string.hash_info_title))
                .setMessage(message)
                .setPositiveButton(getString(R.string.ok), null)
                .setNeutralButton("Copy SHA-256") { _, _ ->
                    val sha256 = hashes["SHA-256"] ?: return@setNeutralButton
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("SHA-256", sha256))
                    Toast.makeText(this@AppDetailActivity, getString(R.string.hash_copied), Toast.LENGTH_SHORT).show()
                }
                .show()
        }
    }

    private suspend fun loadAppInfo(packageName: String): AppInfo? {
        return try {
            val list = BackupManager.listInstalledApps(this, includeSystem = true)
            list.firstOrNull { it.packageName == packageName }
        } catch (_: Exception) { null }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
