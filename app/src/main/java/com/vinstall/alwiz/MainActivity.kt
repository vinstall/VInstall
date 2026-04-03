package com.vinstall.alwiz

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.vinstall.alwiz.appmanager.AppManagerActivity
import com.vinstall.alwiz.backup.BackupActivity
import com.vinstall.alwiz.databinding.ActivityMainBinding
import com.vinstall.alwiz.model.InstallState
import com.vinstall.alwiz.model.PackageFormat
import com.vinstall.alwiz.settings.AppSettings
import com.vinstall.alwiz.settings.InstallMode
import com.vinstall.alwiz.settings.SettingsActivity
import com.vinstall.alwiz.shizuku.ShizukuHelper
import com.vinstall.alwiz.util.DebugLog
import com.vinstall.alwiz.util.FileUtil
import com.vinstall.alwiz.util.CrashHandler
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        val granted = result == PackageManager.PERMISSION_GRANTED
        val msg = if (granted) getString(R.string.shizuku_granted) else getString(R.string.shizuku_denied)
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        updateInstallModeStatus()
    }

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.onFileSelected(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        CrashHandler.showCrashDialogIfNeeded(this)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        setSupportActionBar(binding.toolbar)

        binding.btnSelect.setOnClickListener { filePicker.launch("*/*") }

        binding.btnInstall.setOnClickListener {
            if (needsStoragePermission()) {
                showStoragePermissionDialog()
            } else if (AppSettings.isConfirmInstall(this)) {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.confirm_install_title))
                    .setMessage(getString(R.string.confirm_install_message))
                    .setPositiveButton(getString(R.string.install)) { _, _ -> viewModel.install() }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            } else {
                viewModel.install()
            }
        }

        binding.btnCancel.setOnClickListener {
            viewModel.cancelInstall()
        }

        binding.btnSelectSplits.setOnClickListener {
            viewModel.loadSplitsIfNeeded()
            showSplitPicker()
        }

        binding.btnAppManager.setOnClickListener {
            startActivity(Intent(this, AppManagerActivity::class.java))
        }

        binding.btnBackup.setOnClickListener {
            startActivity(Intent(this, BackupActivity::class.java))
        }

        binding.btnDebug.setOnClickListener {
            startActivity(Intent(this, DebugWindowActivity::class.java))
        }

        lifecycleScope.launch {
            viewModel.state.collect { state -> renderState(state) }
        }

        lifecycleScope.launch {
            viewModel.availableSplits.collect { splits ->
                val hasSplits = (viewModel.state.value as? InstallState.FileSelected)?.hasSplits ?: false
                binding.btnSelectSplits.isVisible = splits.isNotEmpty() || hasSplits
            }
        }

        updateInstallModeStatus()
        DebugLog.i("MainActivity", "Application started")

        intent?.data?.let { uri ->
            if (savedInstanceState == null) viewModel.onFileSelected(uri)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val debugEnabled = AppSettings.isDebugWindowEnabled(this)
        menu.findItem(R.id.action_debug)?.isVisible = debugEnabled
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_debug -> {
                startActivity(Intent(this, DebugWindowActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun renderState(state: InstallState) {
        DebugLog.d("MainActivity", "renderState: ${state::class.simpleName}")
        when (state) {
            is InstallState.Idle -> {
                binding.layoutEmptyState.isVisible = true
                binding.layoutFileInfo.isVisible = false
                binding.progressBar.isVisible = false
                binding.textStatus.isVisible = false
                binding.btnInstall.isEnabled = false
                binding.btnSelectSplits.isVisible = false
                binding.btnCancel.isVisible = false
            }
            is InstallState.FileLoading -> {
                binding.layoutEmptyState.isVisible = false
                binding.layoutFileInfo.isVisible = false
                binding.progressBar.isVisible = true
                binding.textStatus.isVisible = true
                binding.textStatus.text = getString(R.string.analyzing)
                binding.btnInstall.isEnabled = false
                binding.btnSelect.isEnabled = false
                binding.btnSelectSplits.isVisible = false
                binding.btnCancel.isVisible = false
            }
            is InstallState.FileSelected -> {
                binding.layoutEmptyState.isVisible = false
                binding.layoutFileInfo.isVisible = true

                binding.textFileName.text = if (state.appLabel.isNotEmpty()) state.appLabel else state.name

                if (state.appIcon != null) {
                    binding.imageAppIcon.setImageBitmap(state.appIcon)
                    binding.imageAppIcon.isVisible = true
                } else {
                    binding.imageAppIcon.isVisible = false
                }

                if (state.packageName.isNotEmpty()) {
                    binding.textPackageMeta.text = getString(R.string.package_meta, state.packageName, state.versionName)
                    binding.textPackageMeta.isVisible = true
                } else {
                    binding.textPackageMeta.isVisible = false
                }

                if (state.appLabel.isNotEmpty()) {
                    binding.textFilePath.text = state.name
                    binding.textFilePath.isVisible = true
                } else {
                    binding.textFilePath.isVisible = false
                }

                binding.textFormat.text = if (state.format == PackageFormat.UNKNOWN)
                    getString(R.string.unknown) else state.format.label
                binding.textSize.text = FileUtil.formatSize(state.size)

                if (state.hash.isNotEmpty()) {
                    binding.textHash.text = state.hash
                    binding.layoutHash.isVisible = true
                } else {
                    binding.layoutHash.isVisible = false
                }

                binding.progressBar.isVisible = false
                binding.textStatus.isVisible = false
                binding.btnInstall.isEnabled = state.format != PackageFormat.UNKNOWN
                binding.btnSelect.isEnabled = true
                binding.btnSelectSplits.isVisible = state.hasSplits
                binding.btnCancel.isVisible = false
            }
            is InstallState.Analyzing -> {
                binding.progressBar.isVisible = true
                binding.textStatus.isVisible = true
                binding.textStatus.text = getString(R.string.analyzing)
                binding.btnInstall.isEnabled = false
                binding.btnSelect.isEnabled = false
                binding.btnSelectSplits.isEnabled = false
                binding.btnCancel.isVisible = true
            }
            is InstallState.Installing -> {
                binding.progressBar.isVisible = true
                binding.textStatus.isVisible = true
                binding.textStatus.text = state.step
                binding.btnInstall.isEnabled = false
                binding.btnSelect.isEnabled = false
                binding.btnCancel.isVisible = true
            }
            is InstallState.Success -> {
                binding.progressBar.isVisible = false
                binding.textStatus.isVisible = true
                binding.textStatus.text = getString(R.string.install_success)
                binding.btnInstall.isEnabled = false
                binding.btnSelect.isEnabled = true
                binding.btnSelectSplits.isEnabled = true
                binding.btnCancel.isVisible = false
            }
            is InstallState.Error -> {
                binding.progressBar.isVisible = false
                binding.textStatus.isVisible = true
                binding.textStatus.text = getString(R.string.install_failed, state.message)
                binding.btnInstall.isEnabled = true
                binding.btnSelect.isEnabled = true
                binding.btnSelectSplits.isEnabled = true
                binding.btnCancel.isVisible = false
            }
            is InstallState.Cancelled -> {
                binding.progressBar.isVisible = false
                binding.textStatus.isVisible = true
                binding.textStatus.text = state.reason
                binding.btnInstall.isEnabled = true
                binding.btnSelect.isEnabled = true
                binding.btnSelectSplits.isEnabled = true
                binding.btnCancel.isVisible = false
            }
            is InstallState.PasswordRequired -> {
                binding.progressBar.isVisible = false
                binding.textStatus.isVisible = false
                binding.btnInstall.isEnabled = false
                binding.btnSelect.isEnabled = true
                binding.btnSelectSplits.isEnabled = false
                binding.btnCancel.isVisible = false
                showPasswordDialog(state)
            }
        }
    }

    private fun showPasswordDialog(state: InstallState.PasswordRequired) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_apkv_password, null)
        val layoutPassword = dialogView.findViewById<TextInputLayout>(R.id.layout_password)
        val editPassword = dialogView.findViewById<TextInputEditText>(R.id.edit_password)

        val title = if (state.label.isNotEmpty()) state.label else state.fileName
        val subtitle = if (state.packageName.isNotEmpty() && state.versionName.isNotEmpty())
            "${state.packageName} · v${state.versionName}" else state.packageName
        dialogView.findViewById<android.widget.TextView>(R.id.text_apkv_info).text = subtitle

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.apkv_unlock), null)
            .setNegativeButton(getString(R.string.cancel)) { _, _ -> viewModel.reset() }
            .setCancelable(false)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val password = editPassword.text?.toString()?.trim() ?: ""
                if (password.isBlank()) {
                    layoutPassword.error = getString(R.string.apkv_password_empty)
                    return@setOnClickListener
                }
                layoutPassword.error = null
                dialog.dismiss()
                viewModel.submitApkvPassword(password)
            }
        }

        dialog.show()
    }

    private fun showSplitPicker() {
        val splits = viewModel.availableSplits.value
        val selected = viewModel.selectedSplits.value.toMutableList()
        val checkedItems = splits.map { selected.contains(it) }.toBooleanArray()

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_splits))
            .setMultiChoiceItems(splits.toTypedArray(), checkedItems) { _, which, isChecked ->
                viewModel.toggleSplit(splits[which], isChecked)
            }
            .setPositiveButton(getString(R.string.ok), null)
            .setNeutralButton(getString(R.string.select_all)) { _, _ ->
                viewModel.selectAllSplits()
            }
            .setNegativeButton(getString(R.string.deselect_all)) { _, _ ->
                viewModel.deselectAllSplits()
            }
            .show()
    }

    private fun updateInstallModeStatus() {
        val mode = AppSettings.getInstallMode(this)
        val shizukuAvail = ShizukuHelper.isAvailable()
        val shizukuGranted = ShizukuHelper.isGranted()

        val modeLabel = when (mode) {
            InstallMode.NORMAL -> getString(R.string.mode_normal)
            InstallMode.ROOT -> getString(R.string.mode_root)
            InstallMode.SHIZUKU -> getString(R.string.mode_shizuku)
        }

        val statusText = when (mode) {
            InstallMode.SHIZUKU -> when {
                !shizukuAvail -> "$modeLabel — ${getString(R.string.shizuku_inactive)}"
                !shizukuGranted -> "$modeLabel — ${getString(R.string.shizuku_needs_grant)}"
                else -> "$modeLabel — ${getString(R.string.shizuku_active)}"
            }
            else -> modeLabel
        }

        binding.textInstallModeStatus.text = statusText
        binding.installModeDot.setBackgroundResource(
            when (mode) {
                InstallMode.NORMAL -> R.drawable.dot_active
                InstallMode.ROOT -> R.drawable.dot_active
                InstallMode.SHIZUKU -> if (shizukuAvail && shizukuGranted) R.drawable.dot_active else R.drawable.dot_pending
            }
        )

        if (mode == InstallMode.SHIZUKU && shizukuAvail && !shizukuGranted) {
            ShizukuHelper.requestPermission(shizukuPermissionListener)
        }
    }

    private fun needsStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val state = viewModel.state.value
        if (state is InstallState.FileSelected && state.format == PackageFormat.XAPK) {
            return !Environment.isExternalStorageManager()
        }
        return false
    }

    private fun showStoragePermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.permission_required))
            .setMessage(getString(R.string.storage_permission_rationale))
            .setPositiveButton(getString(R.string.grant_permission)) { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        updateInstallModeStatus()
        val debugEnabled = AppSettings.isDebugWindowEnabled(this)
        binding.btnDebug.isVisible = debugEnabled
        invalidateOptionsMenu()
    }

    override fun onDestroy() {
        super.onDestroy()
        ShizukuHelper.removePermissionListener(shizukuPermissionListener)
    }
}
