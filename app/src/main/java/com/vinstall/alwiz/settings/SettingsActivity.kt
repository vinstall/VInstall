package com.vinstall.alwiz.settings

import com.vinstall.alwiz.ui.showToast
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.vinstall.alwiz.ui.ScaledActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.vinstall.alwiz.App
import com.vinstall.alwiz.blocked.BlockedAppsActivity
import com.vinstall.alwiz.R
import com.vinstall.alwiz.crash.CrashLogActivity
import com.vinstall.alwiz.credits.CreditsActivity
import com.vinstall.alwiz.databinding.ActivitySettingsBinding
import com.vinstall.alwiz.shizuku.ShizukuHelper
import com.vinstall.alwiz.ui.DisplayScale
import com.vinstall.alwiz.ui.FloatingNav
import com.vinstall.alwiz.ui.ReduceMotion
import com.vinstall.alwiz.ui.TrustedAppsBottomSheet
import com.vinstall.alwiz.appmanager.AppIconLoader
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import com.vinstall.alwiz.ui.setChildRevealed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class SettingsActivity : ScaledActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        val granted = result == PackageManager.PERMISSION_GRANTED
        val msg = if (granted) getString(R.string.shizuku_granted) else getString(R.string.shizuku_denied)
        showToast(this, msg)
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
        setupSections()

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
            binding.layoutGuiScale,
            binding.layoutFont,
            binding.layoutMotion,
            binding.layoutTrustedCallers,
            binding.layoutBlockedCallers,
            binding.layoutUntrustedDelay,
            binding.layoutCredits,
            binding.btnViewHistory,
            binding.btnClearHistory,
            binding.btnViewCrashLog,
            binding.btnClearCrashLog,
            binding.btnRequestShizuku
        ).forEach { it.applyPressAnimation(0.97f) }

        if (playEntrance) binding.settingsContent.staggerChildren()
    }

    private fun setupSections() {
        setupCollapsibleSection(binding.titleBehavior, "behavior", true)
        setupCollapsibleSection(binding.titleSecurity, "security", false)
        setupCollapsibleSection(
            binding.titleExperimental,
            "experimental",
            false,
            getString(R.string.experimental_help_description)
        ) { showExperimentalHelp() }
        setupCollapsibleSection(binding.titleAppearance, "appearance", true)
        setupCollapsibleSection(binding.titleAccessibility, "accessibility", false)
        setupCollapsibleSection(binding.titleHistory, "history", true)
        setupCollapsibleSection(binding.titleCrash, "crash", true)
        setupCollapsibleSection(binding.titleAbout, "about", true)
    }

    private fun showExperimentalHelp() {
        DialogHelper.showInfo(
            activity = this,
            title = getString(R.string.experimental_help_title),
            body = getString(R.string.experimental_help_message),
            closeLabel = getString(R.string.ok)
        )
    }

    override fun onResume() {
        super.onResume()
        refreshCrashLogStatus()
        refreshHistoryStatus()
        refreshBlockedCallersSummary()
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

        binding.switchDebugTab.isChecked = AppSettings.isDebugTabEnabled(this)
        binding.switchAutoClearCache.isChecked = AppSettings.isAutoClearCache(this)
        syncModeSwitches()
        binding.switchHideNotification.isChecked = AppSettings.isHideNotification(this)
        binding.switchSilentExternal.isChecked = AppSettings.isSilentExternalInstall(this)
        refreshTrustedCallersSummary()
        refreshBlockedCallersSummary()
        refreshUntrustedDelaySummary()

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

        loadAccessibilitySettings()

        refreshStatusLabels()
    }

    private var syncingSwitches = false
    private var syncingAccessibility = false
    private val scaleHandler = Handler(Looper.getMainLooper())
    private val applyScaleRunnable = Runnable { DisplayScale.recreateIfNeeded(this) }

    private fun scheduleScaleApply() {
        scaleHandler.removeCallbacks(applyScaleRunnable)
        scaleHandler.postDelayed(applyScaleRunnable, 450L)
    }

    private fun textScaleName(index: Int): String = when (index) {
        0 -> getString(R.string.scale_small)
        1 -> getString(R.string.scale_normal)
        2 -> getString(R.string.scale_large)
        else -> getString(R.string.scale_xlarge)
    }

    private fun guiScaleName(key: String): String = when (DisplayScale.guiIndex(key)) {
        0 -> getString(R.string.scale_small)
        2 -> getString(R.string.scale_large)
        else -> getString(R.string.scale_normal)
    }

    private fun loadAccessibilitySettings() {
        syncingAccessibility = true
        val key = AppSettings.getTextScale(this)
        val follow = DisplayScale.textIndex(key) < 0
        val index = DisplayScale.textIndex(key).let { if (it < 0) DisplayScale.TEXT_NORMAL_INDEX else it }
        binding.switchTextScaleSystem.isChecked = follow
        binding.sliderTextScale.value = index.toFloat()
        binding.sliderTextScale.isEnabled = !follow
        binding.switchHighContrast.isChecked = AppSettings.isHighContrast(this)
        binding.switchBoldText.isChecked = AppSettings.isBoldText(this)
        binding.switchLargeTouch.isChecked = AppSettings.isLargeTouch(this)
        binding.switchHaptic.isChecked = AppSettings.isHapticFeedback(this)
        binding.sliderToastDuration.valueFrom = AppSettings.TOAST_SECONDS_MIN.toFloat()
        binding.sliderToastDuration.valueTo = AppSettings.TOAST_SECONDS_MAX.toFloat()
        binding.sliderToastDuration.value = AppSettings.getToastSeconds(this).toFloat()
        syncingAccessibility = false
        refreshTextScalePreview()
        refreshToastDurationLabel()
        refreshGuiScaleSummary()
        refreshFontSummary()
        refreshMotionSummary()
    }

    private fun refreshTextScalePreview() {
        val follow = binding.switchTextScaleSystem.isChecked
        val index = binding.sliderTextScale.value.toInt()
        val factor = if (follow) DisplayScale.systemFontScale() else DisplayScale.textFactor(index)
        val percent = (factor * 100).toInt()
        binding.textTextScaleLabel.text = if (follow)
            getString(R.string.text_size_label_system, percent)
        else
            getString(R.string.text_size_label, textScaleName(index), percent)
        val guiApplied = DisplayScale.guiFactor(AppSettings.getGuiScale(this))
        val basePx = 16f * (resources.displayMetrics.density / guiApplied)
        binding.textTextScalePreview.setTextSize(TypedValue.COMPLEX_UNIT_PX, basePx * factor)
    }

    private fun refreshGuiScaleSummary() {
        binding.textCurrentGuiScale.text = guiScaleName(AppSettings.getGuiScale(this))
    }

    private val fontKeys = arrayOf("poppins", "system")

    private fun fontLabels(): List<String> =
        listOf(getString(R.string.font_poppins), getString(R.string.font_system))

    private fun refreshFontSummary() {
        val idx = fontKeys.indexOf(AppSettings.getFont(this)).coerceAtLeast(0)
        binding.textCurrentFont.text = fontLabels()[idx]
    }

    private fun showFontDialog() {
        val idx = fontKeys.indexOf(AppSettings.getFont(this)).coerceAtLeast(0)

        DialogHelper.showSingleChoice(
            activity = this,
            title = getString(R.string.choose_font),
            items = fontLabels(),
            selectedIndex = idx,
            cancelLabel = getString(R.string.cancel),
        ) { which ->
            val key = fontKeys[which]
            if (key == AppSettings.getFont(this)) return@showSingleChoice
            AppSettings.setFont(this, key)
            refreshFontSummary()
            DebugLog.i("Settings", "Font: $key")
            scheduleScaleApply()
        }
    }

    private fun motionLabels(): List<String> = ReduceMotion.KEYS.map { key ->
        if (key == ReduceMotion.SYSTEM) getString(R.string.motion_follow_system) else ReduceMotion.label(key)
    }

    private fun refreshMotionSummary() {
        val idx = ReduceMotion.KEYS.indexOf(AppSettings.getReduceMotion(this)).coerceAtLeast(0)
        binding.textCurrentMotion.text = motionLabels()[idx]
    }

    private fun showMotionDialog() {
        val idx = ReduceMotion.KEYS.indexOf(AppSettings.getReduceMotion(this)).coerceAtLeast(0)

        DialogHelper.showSingleChoice(
            activity = this,
            title = getString(R.string.choose_motion),
            items = motionLabels(),
            selectedIndex = idx,
            cancelLabel = getString(R.string.cancel),
        ) { which ->
            val key = ReduceMotion.KEYS[which]
            if (key == AppSettings.getReduceMotion(this)) return@showSingleChoice
            AppSettings.setReduceMotion(this, key)
            refreshMotionSummary()
            DebugLog.i("Settings", "Animation speed: $key")
            scheduleScaleApply()
        }
    }

    private fun showGuiScaleDialog() {
        val labels = DisplayScale.GUI_KEYS.indices.map { guiScaleName(DisplayScale.GUI_KEYS[it]) }
        val idx = DisplayScale.guiIndex(AppSettings.getGuiScale(this))

        DialogHelper.showSingleChoice(
            activity = this,
            title = getString(R.string.choose_gui_scale),
            items = labels,
            selectedIndex = idx,
            cancelLabel = getString(R.string.cancel),
        ) { which ->
            val key = DisplayScale.GUI_KEYS[which]
            if (key == AppSettings.getGuiScale(this)) return@showSingleChoice
            AppSettings.setGuiScale(this, key)
            refreshGuiScaleSummary()
            DebugLog.i("Settings", "GUI scale: $key")
            scheduleScaleApply()
        }
    }

    private fun confirmExperimental(title: Int, message: Int, onConfirm: () -> Unit) {
        DialogHelper.showConfirmation(
            activity = this,
            title = getString(title),
            message = getString(message),
            positiveLabel = getString(R.string.experimental_enable),
            negativeLabel = getString(R.string.cancel),
            isDangerous = true,
            onConfirm = onConfirm
        )
    }

    private fun syncModeSwitches(animate: Boolean = false) {
        val privileged = AppSettings.getInstallMode(this) != InstallMode.NORMAL
        val silent = AppSettings.isSilentInstall(this)
        syncingSwitches = true
        binding.switchSilentInstall.isChecked = silent
        binding.switchSilentInstall.isEnabled = privileged
        binding.switchAllowIncompatibleApi.isChecked = AppSettings.isAllowIncompatibleApi(this)
        binding.switchAllowIncompatibleApi.isEnabled = privileged
        val downgrade = AppSettings.isAllowDowngrade(this)
        binding.switchAllowDowngrade.isChecked = downgrade
        binding.switchAllowDowngrade.isEnabled = privileged
        binding.switchDowngradeKeepData.isChecked = AppSettings.isDowngradeKeepData(this)
        binding.switchDowngradeKeepData.isEnabled = privileged
        binding.layoutDowngradeKeepData.setChildRevealed(downgrade && privileged, animate)
        val force = AppSettings.isForceInstallMismatch(this)
        binding.switchForceMismatch.isChecked = force
        binding.switchForceMismatch.isEnabled = privileged
        binding.switchForceKeepData.isChecked = AppSettings.isForceKeepData(this)
        binding.switchForceKeepData.isEnabled = privileged
        binding.layoutForceKeepData.setChildRevealed(force && privileged, animate)
        syncingSwitches = false
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
            syncModeSwitches(animate = true)
            refreshStatusLabels()
        }

        binding.btnRequestShizuku.setOnClickListener {
            if (!ShizukuHelper.isAvailable()) {
                showToast(this, getString(R.string.shizuku_not_available_toast))
                return@setOnClickListener
            }
            if (ShizukuHelper.isGranted()) {
                showToast(this, getString(R.string.shizuku_already_granted_toast))
                refreshStatusLabels()
                return@setOnClickListener
            }
            if (!ShizukuHelper.requestPermission(shizukuPermissionListener)) {
                showToast(this, getString(R.string.shizuku_not_available_toast))
            }
        }

        binding.switchDebugTab.setOnCheckedChangeListener { _, checked ->
            binding.floatingNav.setDebugVisible(checked)
            AppSettings.setDebugTabEnabled(this, checked)
            DebugLog.i("Settings", "Debug tab: $checked")
        }

        binding.switchAutoClearCache.setOnCheckedChangeListener { _, checked ->
            AppSettings.setAutoClearCache(this, checked)
        }

        binding.switchSilentInstall.setOnCheckedChangeListener { _, checked ->
            if (syncingSwitches) return@setOnCheckedChangeListener
            AppSettings.setSilentInstall(this, checked)
            DebugLog.i("Settings", "Silent install: $checked")
            syncModeSwitches()
        }

        binding.switchHideNotification.setOnCheckedChangeListener { _, checked ->
            AppSettings.setHideNotification(this, checked)
            DebugLog.i("Settings", "Hide notifications: $checked")
        }

        binding.switchSilentExternal.setOnCheckedChangeListener { _, checked ->
            AppSettings.setSilentExternalInstall(this, checked)
            DebugLog.i("Settings", "Silent install from other apps: $checked")
        }

        binding.layoutTrustedCallers.setOnClickListener { showTrustedCallersDialog() }
        binding.layoutUntrustedDelay.setOnClickListener { showUntrustedDelayDialog() }
        binding.layoutBlockedCallers.setOnClickListener {
            startActivity(android.content.Intent(this, BlockedAppsActivity::class.java))
        }

        binding.switchAllowIncompatibleApi.setOnCheckedChangeListener { _, checked ->
            if (syncingSwitches) return@setOnCheckedChangeListener
            AppSettings.setAllowIncompatibleApi(this, checked)
            DebugLog.i("Settings", "Install apps with incompatible API: $checked")
        }

        binding.switchAllowDowngrade.setOnCheckedChangeListener { _, checked ->
            if (syncingSwitches) return@setOnCheckedChangeListener
            if (checked) {
                syncModeSwitches()
                confirmExperimental(R.string.downgrade_warning_title, R.string.downgrade_warning_message) {
                    AppSettings.setAllowDowngrade(this, true)
                    DebugLog.i("Settings", "Allow downgrade: true")
                    syncModeSwitches(animate = true)
                }
            } else {
                AppSettings.setAllowDowngrade(this, false)
                DebugLog.i("Settings", "Allow downgrade: false")
                syncModeSwitches(animate = true)
            }
        }

        binding.switchDowngradeKeepData.setOnCheckedChangeListener { _, checked ->
            if (syncingSwitches) return@setOnCheckedChangeListener
            AppSettings.setDowngradeKeepData(this, checked)
            DebugLog.i("Settings", "Downgrade keeps data: $checked")
        }

        binding.switchForceMismatch.setOnCheckedChangeListener { _, checked ->
            if (syncingSwitches) return@setOnCheckedChangeListener
            if (checked) {
                syncModeSwitches()
                confirmExperimental(R.string.force_warning_title, R.string.force_warning_message) {
                    AppSettings.setForceInstallMismatch(this, true)
                    DebugLog.i("Settings", "Force install on signature mismatch: true")
                    syncModeSwitches(animate = true)
                }
            } else {
                AppSettings.setForceInstallMismatch(this, false)
                DebugLog.i("Settings", "Force install on signature mismatch: false")
                syncModeSwitches(animate = true)
            }
        }

        binding.switchForceKeepData.setOnCheckedChangeListener { _, checked ->
            if (syncingSwitches) return@setOnCheckedChangeListener
            if (checked) {
                syncModeSwitches()
                confirmExperimental(R.string.force_keep_warning_title, R.string.force_keep_warning_message) {
                    AppSettings.setForceKeepData(this, true)
                    DebugLog.i("Settings", "Force install keeps data: true")
                    syncModeSwitches()
                }
            } else {
                AppSettings.setForceKeepData(this, false)
                DebugLog.i("Settings", "Force install keeps data: false")
            }
        }

        binding.layoutLanguage.setOnClickListener { showLanguageDialog() }
        binding.layoutTheme.setOnClickListener {
            showThemeDialog()
        }

        binding.layoutDialogStyle.setOnClickListener {
            showDialogStyleDialog()
        }

        binding.switchTextScaleSystem.setOnCheckedChangeListener { _, checked ->
            if (syncingAccessibility) return@setOnCheckedChangeListener
            binding.sliderTextScale.isEnabled = !checked
            val key = if (checked) DisplayScale.TEXT_SYSTEM
            else DisplayScale.TEXT_KEYS[binding.sliderTextScale.value.toInt()]
            AppSettings.setTextScale(this, key)
            DebugLog.i("Settings", "Text scale: $key")
            refreshTextScalePreview()
            scheduleScaleApply()
        }

        binding.sliderTextScale.addOnChangeListener { _, value, fromUser ->
            if (!fromUser || syncingAccessibility) return@addOnChangeListener
            val key = DisplayScale.TEXT_KEYS[value.toInt()]
            AppSettings.setTextScale(this, key)
            DebugLog.i("Settings", "Text scale: $key")
            refreshTextScalePreview()
            scheduleScaleApply()
        }

        binding.sliderToastDuration.addOnChangeListener { _, value, fromUser ->
            if (!fromUser || syncingAccessibility) return@addOnChangeListener
            AppSettings.setToastSeconds(this, value.toInt())
            DebugLog.i("Settings", "Message duration: ${value.toInt()}s")
            refreshToastDurationLabel()
        }

        binding.sliderToastDuration.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}

            override fun onStopTrackingTouch(slider: Slider) {
                showToast(this@SettingsActivity, R.string.toast_duration_preview)
            }
        })

        binding.layoutGuiScale.setOnClickListener { showGuiScaleDialog() }
        binding.layoutFont.setOnClickListener { showFontDialog() }

        binding.switchHighContrast.setOnCheckedChangeListener { _, checked ->
            if (syncingAccessibility) return@setOnCheckedChangeListener
            AppSettings.setHighContrast(this, checked)
            DebugLog.i("Settings", "High contrast: $checked")
            scheduleScaleApply()
        }

        binding.switchLargeTouch.setOnCheckedChangeListener { _, checked ->
            if (syncingAccessibility) return@setOnCheckedChangeListener
            AppSettings.setLargeTouch(this, checked)
            DebugLog.i("Settings", "Large touch targets: $checked")
            scheduleScaleApply()
        }

        binding.switchHaptic.setOnCheckedChangeListener { _, checked ->
            if (syncingAccessibility) return@setOnCheckedChangeListener
            AppSettings.setHapticFeedback(this, checked)
            DebugLog.i("Settings", "Haptic feedback: $checked")
        }

        binding.switchBoldText.setOnCheckedChangeListener { _, checked ->
            if (syncingAccessibility) return@setOnCheckedChangeListener
            AppSettings.setBoldText(this, checked)
            DebugLog.i("Settings", "Bold text: $checked")
            scheduleScaleApply()
        }
        binding.layoutMotion.setOnClickListener { showMotionDialog() }

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
                    showToast(this, getString(R.string.history_cleared))
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
                    showToast(this, getString(R.string.crash_log_cleared))
                }
            )
        }
    }

    private fun refreshBlockedCallersSummary() {
        binding.textBlockedCallersSummary.text =
            getString(R.string.settings_blocked_callers_summary, BlockedCallers.count(this))
    }

    private fun refreshTrustedCallersSummary() {
        binding.textTrustedCallersSummary.text =
            getString(R.string.settings_trusted_callers_summary, AppSettings.getTrustedCallers(this).size)
    }

    @Suppress("DEPRECATION")
    private fun loadTrustableApps(): List<TrustableApp> {
        val pm = packageManager
        val launcher = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        val resolved = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            pm.queryIntentActivities(launcher, 0)
        }
        val labels = LinkedHashMap<String, String>()
        val system = HashSet<String>()
        val sysMask = android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        for (info in resolved) {
            val pkg = info.activityInfo.packageName
            if (pkg == packageName) continue
            labels.getOrPut(pkg) { info.loadLabel(pm).toString() }
            if (info.activityInfo.applicationInfo.flags and sysMask != 0) system.add(pkg)
        }
        for (pkg in AppSettings.getTrustedCallers(this)) {
            if (pkg == packageName || pkg in labels) continue
            val ai = try {
                pm.getApplicationInfo(pkg, 0)
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
            if (ai != null) {
                labels[pkg] = ai.loadLabel(pm).toString()
                if (ai.flags and sysMask != 0) system.add(pkg)
            }
        }
        return labels.map { TrustableApp(it.key, it.value, it.key in system) }.sortedBy { it.label.lowercase() }
    }

    private fun showTrustedCallersDialog() {
        lifecycleScope.launch {
            val trusted = AppSettings.getTrustedCallers(this@SettingsActivity).toSet()
            val onConfirm: (List<String>) -> Unit = { picked ->
                AppSettings.setTrustedCallers(this@SettingsActivity, picked)
                refreshTrustedCallersSummary()
            }
            when (AppSettings.getDialogStyle(this@SettingsActivity)) {
                DialogStyle.BOTTOM_SHEET -> TrustedAppsBottomSheet.show(
                    fragmentManager = supportFragmentManager,
                    title = getString(R.string.settings_trusted_callers),
                    loader = ::loadTrustableApps,
                    trusted = trusted,
                    onConfirm = onConfirm,
                )
                DialogStyle.ALERT_DIALOG -> {
                    val apps = withContext(Dispatchers.IO) { loadTrustableApps() }
                    val selected = trusted.toMutableSet()
                    val view = layoutInflater.inflate(R.layout.dialog_trusted_apps, null) as RecyclerView
                    view.layoutManager = LinearLayoutManager(this@SettingsActivity)
                    view.adapter = TrustedAppsAdapter(
                        apps, AppIconLoader(this@SettingsActivity, lifecycleScope), selected
                    )
                    MaterialAlertDialogBuilder(this@SettingsActivity)
                        .setTitle(R.string.settings_trusted_callers)
                        .setView(view)
                        .setPositiveButton(R.string.ok) { _, _ ->
                            onConfirm(apps.filter { it.packageName in selected }.map { it.packageName })
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
            }
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
            if (which == currentLanguageIndex()) return@showSingleChoice
            val tag = languageTags[which]
            val locales = if (tag.isEmpty()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(if (tag == "in") "id" else tag)
            }
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    private fun untrustedDelayLabel(seconds: Int): String =
        if (seconds <= 0) getString(R.string.untrusted_delay_off) else getString(R.string.untrusted_delay_seconds, seconds)

    private fun refreshUntrustedDelaySummary() {
        binding.textUntrustedDelaySummary.text = untrustedDelayLabel(AppSettings.getUntrustedContinueDelay(this))
    }

    private fun showUntrustedDelayDialog() {
        val options = intArrayOf(0, 3, 5, 10)
        val labels = options.map { untrustedDelayLabel(it) }
        val idx = options.indexOf(AppSettings.getUntrustedContinueDelay(this)).coerceAtLeast(0)

        DialogHelper.showSingleChoice(
            activity = this,
            title = getString(R.string.choose_untrusted_delay),
            items = labels,
            selectedIndex = idx,
            cancelLabel = getString(R.string.cancel),
        ) { which ->
            AppSettings.setUntrustedContinueDelay(this, options[which])
            refreshUntrustedDelaySummary()
            DebugLog.i("Settings", "Untrusted continue delay: ${options[which]}s")
        }
    }

    private fun refreshToastDurationLabel() {
        binding.textToastDurationLabel.text =
            getString(R.string.toast_duration_value, binding.sliderToastDuration.value.toInt())
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
            if (keys[which] == AppSettings.getTheme(this)) return@showSingleChoice
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
        scaleHandler.removeCallbacks(applyScaleRunnable)
        Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener)
        Shizuku.removeBinderDeadListener(shizukuBinderDeadListener)
        ShizukuHelper.removePermissionListener(shizukuPermissionListener)
    }
}
