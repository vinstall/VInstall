package com.vinstall.alwiz.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.vinstall.alwiz.App
import com.vinstall.alwiz.R
import com.vinstall.alwiz.databinding.ActivitySettingsBinding
import com.vinstall.alwiz.shizuku.ShizukuHelper
import com.vinstall.alwiz.util.CrashHandler
import com.vinstall.alwiz.util.DebugLog
import com.vinstall.alwiz.root.RootHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings)

        loadCurrentSettings()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        refreshCrashLogStatus()
    }

    private fun loadCurrentSettings() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) {
            binding.radioBtnShizuku.isEnabled = false
            binding.radioBtnShizuku.alpha = 0.4f
            binding.textShizukuStatus.text = getString(R.string.shizuku_not_supported)
            if (AppSettings.getInstallMode(this) == InstallMode.SHIZUKU) {
                AppSettings.setInstallMode(this, InstallMode.NORMAL)
            }
        }

        val mode = AppSettings.getInstallMode(this)
        updateModeUI(mode)

        binding.switchDebugWindow.isChecked = AppSettings.isDebugWindowEnabled(this)
        binding.switchClearCache.isChecked = AppSettings.isClearCacheAfterInstall(this)
        binding.switchConfirmInstall.isChecked = AppSettings.isConfirmInstall(this)

        val theme = AppSettings.getTheme(this)
        binding.textCurrentTheme.text = when (theme) {
            "light" -> getString(R.string.theme_light)
            "dark" -> getString(R.string.theme_dark)
            else -> getString(R.string.theme_system)
        }

        refreshStatusLabels()
        refreshCrashLogStatus()
    }

    private fun updateModeUI(mode: InstallMode) {
        binding.radioBtnNormal.isChecked = mode == InstallMode.NORMAL
        binding.radioBtnRoot.isChecked = mode == InstallMode.ROOT
        binding.radioBtnShizuku.isChecked = mode == InstallMode.SHIZUKU
        DebugLog.d("Settings", "Mode UI updated: $mode")
    }

    private fun refreshStatusLabels() {
        val currentMode = AppSettings.getInstallMode(this)

        val shizukuAvail = ShizukuHelper.isAvailable()
        val shizukuGranted = ShizukuHelper.isGranted()

        binding.textShizukuStatus.text = when {
            !shizukuAvail -> getString(R.string.shizuku_inactive)
            shizukuGranted -> getString(R.string.shizuku_active)
            else -> getString(R.string.shizuku_needs_grant)
        }

        if (currentMode == InstallMode.ROOT) {
            binding.textRootStatus.text = getString(R.string.root_checking)
            lifecycleScope.launch {
                val rooted = withContext(Dispatchers.IO) { RootHelper.isRooted() }
                binding.textRootStatus.text = if (rooted)
                    getString(R.string.root_available)
                else
                    getString(R.string.root_not_available)
            }
        } else {
            binding.textRootStatus.text = ""
        }
    }

    private fun refreshCrashLogStatus() {
        val count = CrashHandler.crashLogEntryCount(this)
        val hasLog = count > 0
        binding.textCrashLogStatus.text = if (hasLog)
            getString(R.string.crash_log_has_entries, count)
        else
            getString(R.string.crash_log_empty)
        binding.btnViewCrashLog.isEnabled = hasLog
        binding.btnClearCrashLog.isEnabled = hasLog
    }

    private fun setupListeners() {
        binding.radioGroupMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.radio_btn_root -> InstallMode.ROOT
                R.id.radio_btn_shizuku -> InstallMode.SHIZUKU
                else -> InstallMode.NORMAL
            }
            AppSettings.setInstallMode(this, mode)
            DebugLog.i("Settings", "Install mode changed to: $mode")
            refreshStatusLabels()
        }

        binding.switchDebugWindow.setOnCheckedChangeListener { _, checked ->
            AppSettings.setDebugWindowEnabled(this, checked)
            DebugLog.i("Settings", "Debug window: $checked")
        }

        binding.switchClearCache.setOnCheckedChangeListener { _, checked ->
            AppSettings.setClearCacheAfterInstall(this, checked)
        }

        binding.switchConfirmInstall.setOnCheckedChangeListener { _, checked ->
            AppSettings.setConfirmInstall(this, checked)
        }

        binding.layoutTheme.setOnClickListener {
            showThemeDialog()
        }

        binding.btnViewCrashLog.setOnClickListener {
            val log = CrashHandler.readCrashLogTail(this)
            if (log.isNullOrBlank()) {
                Toast.makeText(this, getString(R.string.crash_log_empty), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showCrashLogDialog(log)
        }

        binding.btnClearCrashLog.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.crash_log_clear_title))
                .setMessage(getString(R.string.crash_log_clear_confirm))
                .setPositiveButton(getString(R.string.crash_log_clear_yes)) { _, _ ->
                    CrashHandler.clearCrashLog(this)
                    refreshCrashLogStatus()
                    Toast.makeText(this, getString(R.string.crash_log_cleared), Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }

    private fun showCrashLogDialog(log: String) {
        val tv = TextView(this).apply {
            text = log
            textSize = 10.5f
            setPadding(32, 24, 32, 24)
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val scroll = ScrollView(this).apply { addView(tv) }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.crash_log_title))
            .setView(scroll)
            .setPositiveButton(getString(R.string.crash_log_copy)) { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("VInstall Crash Log", log))
                Toast.makeText(this, getString(R.string.log_copied), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.crash_log_close), null)
            .show()
    }

    private fun showThemeDialog() {
        val themes = arrayOf(
            getString(R.string.theme_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        )
        val keys = arrayOf("system", "light", "dark")
        val current = AppSettings.getTheme(this)
        val idx = keys.indexOf(current).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.choose_theme))
            .setSingleChoiceItems(themes, idx) { dialog, which ->
                AppSettings.setTheme(this, keys[which])
                binding.textCurrentTheme.text = themes[which]
                applyTheme(keys[which])
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun applyTheme(theme: String) {
        App.applyTheme(theme)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
