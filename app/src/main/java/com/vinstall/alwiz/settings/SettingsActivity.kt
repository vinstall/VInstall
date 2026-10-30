package com.vinstall.alwiz.settings

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.vinstall.alwiz.App
import com.vinstall.alwiz.R
import com.vinstall.alwiz.crash.CrashLogActivity
import com.vinstall.alwiz.credits.CreditsActivity
import com.vinstall.alwiz.databinding.ActivitySettingsBinding
import com.vinstall.alwiz.shizuku.ShizukuHelper
import com.vinstall.alwiz.ui.FloatingNav
import com.vinstall.alwiz.ui.applySystemBarInsets
import com.vinstall.alwiz.ui.applyPressAnimation
import com.vinstall.alwiz.ui.bindFloatingNav
import com.vinstall.alwiz.ui.staggerChildren
import com.vinstall.alwiz.util.CrashHandler
import com.vinstall.alwiz.util.DebugLog
import com.vinstall.alwiz.settings.DialogHelper
import com.vinstall.alwiz.root.RootHelper
import com.vinstall.alwiz.history.InstallHistoryActivity
import com.vinstall.alwiz.history.InstallHistoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        val granted = result == PackageManager.PERMISSION_GRANTED
        val msg = if (granted) getString(R.string.shizuku_granted) else getString(R.string.shizuku_denied)
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        refreshStatusLabels()
    }

    private val shizukuBinderReceivedListener = Shizuku.OnBinderReceivedListener {
        refreshStatusLabels()
    }

    private val shizukuBinderDeadListener = Shizuku.OnBinderDeadListener {
        refreshStatusLabels()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()

        setSupportActionBar(binding.toolbar)
        bindFloatingNav(binding.floatingNav, FloatingNav.Tab.SETTINGS)
        supportActionBar?.title = getString(R.string.settings)

        Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceivedListener)
        Shizuku.addBinderDeadListener(shizukuBinderDeadListener)

        loadCurrentSettings()
        setupListeners()
        setupAnimations(savedInstanceState == null)
    }

    private fun setupAnimations(playEntrance: Boolean) {
        listOf(
            binding.layoutLanguage,
            binding.layoutTheme,
            binding.layoutDialogStyle,
            binding.layoutCredits,
            binding.btnViewHistory,
            binding.btnClearHistory,
            binding.btnViewCrashLog,
            binding.btnClearCrashLog,
            binding.btnRequestShizuku
        ).forEach { it.applyPressAnimation(0.97f) }

        if (playEntrance) binding.settingsContent.staggerChildren()
    }

    override fun onResume() {
        super.onResume()
        refreshCrashLogStatus()
        refreshHistoryStatus()
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
        binding.switchAutoClearCache.isChecked = AppSettings.isAutoClearCache(this)
        binding.switchConfirmInstall.isChecked = AppSettings.isConfirmInstall(this)

        val dialogStyle = AppSettings.getDialogStyle(this)
        binding.textCurrentDialogStyle.text = when (dialogStyle) {
            com.vinstall.alwiz.settings.DialogStyle.BOTTOM_SHEET -> getString(R.string.dialog_style_bottom_sheet)
            com.vinstall.alwiz.settings.DialogStyle.ALERT_DIALOG -> getString(R.string.dialog_style_alert_dialog)
        }

        val theme = AppSettings.getTheme(this)
        binding.textCurrentLanguage.text = languageLabels()[currentLanguageIndex()]
        binding.textCurrentTheme.text = when (theme) {
            "light" -> getString(R.string.theme_light)
            "dark" -> getString(R.string.theme_dark)
            else -> getString(R.string.theme_system)
        }

        refreshStatusLabels()
    }

    private fun updateModeUI(mode: InstallMode) {
        binding.radioBtnNormal.isChecked = mode == InstallMode.NORMAL
        binding.radioBtnRoot.isChecked = mode == InstallMode.ROOT
        binding.radioBtnShizuku.isChecked = mode == InstallMode.SHIZUKU
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

        if (currentMode == InstallMode.SHIZUKU) {
            binding.btnRequestShizuku.isVisible = true
            binding.btnRequestShizuku.isEnabled = shizukuAvail && !shizukuGranted
        } else {
            binding.btnRequestShizuku.isVisible = false
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

    private fun refreshHistoryStatus() {
        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) { InstallHistoryManager.count(this@SettingsActivity) }
            binding.textHistoryStatus.text = if (count > 0)
                getString(R.string.history_entry_count, count)
            else
                getString(R.string.history_empty)
            binding.btnViewHistory.isEnabled = count > 0
            binding.btnClearHistory.isEnabled = count > 0
        }
    }

    private fun refreshCrashLogStatus() {
        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) { CrashHandler.crashLogEntryCount(this@SettingsActivity) }
            val hasLog = count > 0
            binding.textCrashLogStatus.text = if (hasLog)
                getString(R.string.crash_log_has_entries, count)
            else
                getString(R.string.crash_log_empty)
            binding.btnViewCrashLog.isEnabled = hasLog
            binding.btnClearCrashLog.isEnabled = hasLog
        }
    }

    private fun setupListeners() {
        binding.radioGroupMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.radio_btn_root -> InstallMode.ROOT
                R.id.radio_btn_shizuku -> InstallMode.SHIZUKU
                else -> InstallMode.NORMAL
            }
            if (mode == AppSettings.getInstallMode(this)) return@setOnCheckedChangeListener
            AppSettings.setInstallMode(this, mode)
            DebugLog.i("Settings", "Install mode changed to: $mode")
            refreshStatusLabels()
        }

        binding.btnRequestShizuku.setOnClickListener {
            if (!ShizukuHelper.isAvailable()) {
                Toast.makeText(this, getString(R.string.shizuku_not_available_toast), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (ShizukuHelper.isGranted()) {
                Toast.makeText(this, getString(R.string.shizuku_already_granted_toast), Toast.LENGTH_SHORT).show()
                refreshStatusLabels()
                return@setOnClickListener
            }
            if (!ShizukuHelper.requestPermission(shizukuPermissionListener)) {
                Toast.makeText(this, getString(R.string.shizuku_not_available_toast), Toast.LENGTH_SHORT).show()
            }
        }

        binding.switchDebugWindow.setOnCheckedChangeListener { _, checked ->
            binding.floatingNav.setDebugVisible(checked)
            AppSettings.setDebugWindowEnabled(this, checked)
            DebugLog.i("Settings", "Debug window: $checked")
        }

        binding.switchAutoClearCache.setOnCheckedChangeListener { _, checked ->
            AppSettings.setAutoClearCache(this, checked)
        }

        binding.switchConfirmInstall.setOnCheckedChangeListener { _, checked ->
            AppSettings.setConfirmInstall(this, checked)
        }

        binding.layoutLanguage.setOnClickListener { showLanguageDialog() }
        binding.layoutTheme.setOnClickListener {
            showThemeDialog()
        }

        binding.layoutDialogStyle.setOnClickListener {
            showDialogStyleDialog()
        }

        binding.layoutCredits.setOnClickListener {
            startActivity(android.content.Intent(this, CreditsActivity::class.java))
        }

        binding.btnViewHistory.setOnClickListener {
            startActivity(android.content.Intent(this, InstallHistoryActivity::class.java))
        }

        binding.btnClearHistory.setOnClickListener {
            DialogHelper.showConfirmation(
                activity = this,
                title = getString(R.string.history_clear_title),
                message = getString(R.string.history_clear_confirm),
                positiveLabel = getString(R.string.history_clear_yes),
                negativeLabel = getString(R.string.cancel),
                isDangerous = true,
                onConfirm = {
                    InstallHistoryManager.clear(this)
                    refreshHistoryStatus()
                    Toast.makeText(this, getString(R.string.history_cleared), Toast.LENGTH_SHORT).show()
                }
            )
        }

        binding.btnViewCrashLog.setOnClickListener {
            startActivity(android.content.Intent(this, CrashLogActivity::class.java))
        }

        binding.btnClearCrashLog.setOnClickListener {
            DialogHelper.showConfirmation(
                activity = this,
                title = getString(R.string.crash_log_clear_title),
                message = getString(R.string.crash_log_clear_confirm),
                positiveLabel = getString(R.string.crash_log_clear_yes),
                negativeLabel = getString(R.string.cancel),
                isDangerous = true,
                onConfirm = {
                    CrashHandler.clearCrashLog(this)
                    refreshCrashLogStatus()
                    Toast.makeText(this, getString(R.string.crash_log_cleared), Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private val languageTags = arrayOf("", "en", "in", "ru", "zh")

    private fun currentLanguageIndex(): Int {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return 0
        val tag = locales.get(0)?.language.orEmpty()
        val normalized = if (tag == "id") "in" else tag
        return languageTags.indexOf(normalized).coerceAtLeast(0)
    }

    private fun languageLabels(): Array<String> = arrayOf(
        getString(R.string.language_system),
        getString(R.string.language_en),
        getString(R.string.language_in),
        getString(R.string.language_ru),
        getString(R.string.language_zh)
    )

    private fun showLanguageDialog() {
        DialogHelper.showSingleChoice(
            activity = this,
            title = getString(R.string.choose_language),
            items = languageLabels().toList(),
            selectedIndex = currentLanguageIndex(),
            cancelLabel = getString(R.string.cancel),
        ) { which ->
            val tag = languageTags[which]
            val locales = if (tag.isEmpty()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(if (tag == "in") "id" else tag)
            }
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    private fun showDialogStyleDialog() {
        val styles = listOf(
            getString(R.string.dialog_style_bottom_sheet),
            getString(R.string.dialog_style_alert_dialog)
        )
        val keys = arrayOf(DialogStyle.BOTTOM_SHEET, DialogStyle.ALERT_DIALOG)
        val idx = keys.indexOf(AppSettings.getDialogStyle(this)).coerceAtLeast(0)

        DialogHelper.showSingleChoice(
            activity = this,
            title = getString(R.string.choose_dialog_style),
            items = styles,
            selectedIndex = idx,
            cancelLabel = getString(R.string.cancel),
        ) { which ->
            AppSettings.setDialogStyle(this, keys[which])
            binding.textCurrentDialogStyle.text = styles[which]
        }
    }

    private fun showThemeDialog() {
        val themes = listOf(
            getString(R.string.theme_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        )
        val keys = arrayOf("system", "light", "dark")
        val idx = keys.indexOf(AppSettings.getTheme(this)).coerceAtLeast(0)

        DialogHelper.showSingleChoice(
            activity = this,
            title = getString(R.string.choose_theme),
            items = themes,
            selectedIndex = idx,
            cancelLabel = getString(R.string.cancel),
        ) { which ->
            AppSettings.setTheme(this, keys[which])
            binding.textCurrentTheme.text = themes[which]
            applyTheme(keys[which])
        }
    }

    private fun applyTheme(theme: String) {
        App.applyTheme(theme)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener)
        Shizuku.removeBinderDeadListener(shizukuBinderDeadListener)
        ShizukuHelper.removePermissionListener(shizukuPermissionListener)
    }
}
