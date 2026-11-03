package com.vinstall.alwiz.settings

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.SharedPreferences

object AppSettings {

    private const val PREFS_NAME = "vinstall_prefs"
    private const val KEY_INSTALL_MODE = "install_mode"
    private const val KEY_DEBUG_WINDOW = "debug_window"
    private const val KEY_THEME = "theme"
    private const val KEY_AUTO_CLEAR_CACHE = "auto_clear_cache"
    private const val KEY_SHIZUKU_PERMISSION_GRANTED = "shizuku_permission_granted"
    private const val KEY_DIALOG_STYLE = "dialog_style"
    private const val KEY_SILENT_EXTERNAL = "silent_external_install"
    private const val KEY_TRUSTED_CALLERS = "trusted_callers"
    private const val KEY_ALLOW_INCOMPATIBLE_API = "allow_incompatible_api"
    private const val KEY_SILENT_INSTALL = "silent_install"
    private const val KEY_HIDE_NOTIFICATION = "hide_notification"
    private const val KEY_ALLOW_DOWNGRADE = "allow_downgrade"
    private const val KEY_DOWNGRADE_KEEP_DATA = "downgrade_keep_data"
    private const val KEY_FORCE_MISMATCH = "force_install_mismatch"
    private const val KEY_FORCE_KEEP_DATA = "force_install_keep_data"
    private const val KEY_UNTRUSTED_DELAY = "untrusted_continue_delay"
    private const val KEY_TEXT_SCALE = "text_scale"
    private const val KEY_GUI_SCALE = "gui_scale"
    private const val KEY_FONT = "font"
    private const val KEY_REDUCE_MOTION = "reduce_motion"
    private const val KEY_HIGH_CONTRAST = "high_contrast"
    private const val KEY_BOLD_TEXT = "bold_text"
    private const val KEY_LARGE_TOUCH = "large_touch"
    private const val KEY_LONG_TOAST = "long_toast"
    private const val KEY_TOAST_SECONDS = "toast_seconds"
    private const val KEY_HAPTIC_FEEDBACK = "haptic_feedback"

    private val PACKAGE_NAME_REGEX = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getInstallMode(context: Context): InstallMode =
        try {
            InstallMode.valueOf(prefs(context).getString(KEY_INSTALL_MODE, InstallMode.NORMAL.name)!!)
        } catch (_: Exception) {
            InstallMode.NORMAL
        }

    fun setInstallMode(context: Context, mode: InstallMode) {
        prefs(context).edit().putString(KEY_INSTALL_MODE, mode.name).apply()
    }

    fun isDebugTabEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DEBUG_WINDOW, isDebuggableBuild(context))

    private fun isDebuggableBuild(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    fun setDebugTabEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DEBUG_WINDOW, enabled).apply()
    }

    fun getTheme(context: Context): String =
        prefs(context).getString(KEY_THEME, "system") ?: "system"

    fun setTheme(context: Context, theme: String) {
        prefs(context).edit().putString(KEY_THEME, theme).apply()
    }

    fun getTextScale(context: Context): String =
        prefs(context).getString(KEY_TEXT_SCALE, "system") ?: "system"

    fun setTextScale(context: Context, key: String) {
        prefs(context).edit().putString(KEY_TEXT_SCALE, key).apply()
    }

    fun getGuiScale(context: Context): String =
        prefs(context).getString(KEY_GUI_SCALE, "normal") ?: "normal"

    fun setGuiScale(context: Context, key: String) {
        prefs(context).edit().putString(KEY_GUI_SCALE, key).apply()
    }

    fun getFont(context: Context): String =
        prefs(context).getString(KEY_FONT, "poppins") ?: "poppins"

    fun setFont(context: Context, key: String) {
        prefs(context).edit().putString(KEY_FONT, key).apply()
    }

    fun getReduceMotion(context: Context): String =
        when (val stored = prefs(context).getString(KEY_REDUCE_MOTION, "system") ?: "system") {
            "on" -> "0"
            "off" -> "1"
            else -> stored
        }

    fun setReduceMotion(context: Context, key: String) {
        prefs(context).edit().putString(KEY_REDUCE_MOTION, key).apply()
    }

    fun isSectionOpen(context: Context, key: String, default: Boolean): Boolean =
        prefs(context).getBoolean("section_open_$key", default)

    fun setSectionOpen(context: Context, key: String, open: Boolean) {
        prefs(context).edit().putBoolean("section_open_$key", open).apply()
    }

    fun isHighContrast(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HIGH_CONTRAST, false)

    fun setHighContrast(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_HIGH_CONTRAST, enabled).apply()
    }

    fun isBoldText(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BOLD_TEXT, false)

    fun setBoldText(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BOLD_TEXT, enabled).apply()
    }

    fun isLargeTouch(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LARGE_TOUCH, false)

    fun setLargeTouch(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_LARGE_TOUCH, enabled).apply()
    }

    fun isHapticFeedback(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HAPTIC_FEEDBACK, true)

    fun setHapticFeedback(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_HAPTIC_FEEDBACK, enabled).apply()
    }

    const val TOAST_SECONDS_MIN = 1
    const val TOAST_SECONDS_MAX = 10
    const val TOAST_SECONDS_DEFAULT = 2
    private const val TOAST_SECONDS_LEGACY_LONG = 4

    fun getToastSeconds(context: Context): Int {
        val p = prefs(context)
        val stored = when {
            p.contains(KEY_TOAST_SECONDS) -> p.getInt(KEY_TOAST_SECONDS, TOAST_SECONDS_DEFAULT)
            p.getBoolean(KEY_LONG_TOAST, false) -> TOAST_SECONDS_LEGACY_LONG
            else -> TOAST_SECONDS_DEFAULT
        }
        return stored.coerceIn(TOAST_SECONDS_MIN, TOAST_SECONDS_MAX)
    }

    fun setToastSeconds(context: Context, seconds: Int) {
        prefs(context).edit()
            .putInt(KEY_TOAST_SECONDS, seconds.coerceIn(TOAST_SECONDS_MIN, TOAST_SECONDS_MAX))
            .apply()
    }

    fun isAutoClearCache(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_CLEAR_CACHE, true)

    fun setAutoClearCache(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_CLEAR_CACHE, enabled).apply()
    }

    fun isSilentInstall(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SILENT_INSTALL, true)

    fun setSilentInstall(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SILENT_INSTALL, enabled).apply()
    }

    fun isHideNotification(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HIDE_NOTIFICATION, false)

    fun setHideNotification(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_HIDE_NOTIFICATION, enabled).apply()
    }

    fun isConfirmInstallEffective(context: Context): Boolean =
        getInstallMode(context) != InstallMode.NORMAL && !isSilentInstall(context)

    fun isShizukuPermissionGranted(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHIZUKU_PERMISSION_GRANTED, false)

    fun setShizukuPermissionGranted(context: Context, granted: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHIZUKU_PERMISSION_GRANTED, granted).apply()
    }

    fun getDialogStyle(context: Context): DialogStyle =
        try {
            DialogStyle.valueOf(prefs(context).getString(KEY_DIALOG_STYLE, DialogStyle.BOTTOM_SHEET.name)!!)
        } catch (_: Exception) {
            DialogStyle.BOTTOM_SHEET
        }

    fun setDialogStyle(context: Context, style: DialogStyle) {
        prefs(context).edit().putString(KEY_DIALOG_STYLE, style.name).apply()
    }

    fun isSilentExternalInstall(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SILENT_EXTERNAL, false)

    fun setSilentExternalInstall(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SILENT_EXTERNAL, enabled).apply()
    }

    fun isAllowIncompatibleApi(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ALLOW_INCOMPATIBLE_API, false)

    fun setAllowIncompatibleApi(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ALLOW_INCOMPATIBLE_API, enabled).apply()
    }

    fun isAllowDowngrade(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ALLOW_DOWNGRADE, false)

    fun setAllowDowngrade(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ALLOW_DOWNGRADE, enabled).apply()
    }

    fun isDowngradeKeepData(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DOWNGRADE_KEEP_DATA, false)

    fun setDowngradeKeepData(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DOWNGRADE_KEEP_DATA, enabled).apply()
    }

    fun isForceInstallMismatch(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FORCE_MISMATCH, false)

    fun setForceInstallMismatch(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_FORCE_MISMATCH, enabled).apply()
    }

    fun isForceKeepData(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FORCE_KEEP_DATA, false)

    fun setForceKeepData(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_FORCE_KEEP_DATA, enabled).apply()
    }

    fun getUntrustedContinueDelay(context: Context): Int =
        prefs(context).getInt(KEY_UNTRUSTED_DELAY, 0).coerceAtLeast(0)

    fun setUntrustedContinueDelay(context: Context, seconds: Int) {
        prefs(context).edit().putInt(KEY_UNTRUSTED_DELAY, seconds.coerceAtLeast(0)).apply()
    }

    fun getTrustedCallers(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_TRUSTED_CALLERS, null)
            ?: return emptyList()
        return parseTrustedCallers(raw)
    }

    fun setTrustedCallers(context: Context, packages: List<String>) {
        prefs(context).edit().putString(KEY_TRUSTED_CALLERS, packages.joinToString("\n")).apply()
    }

    fun parseTrustedCallers(raw: String): List<String> =
        raw.split(Regex("[\\s,;]+"))
            .map { it.trim() }
            .filter { PACKAGE_NAME_REGEX.matches(it) }
            .distinct()
}
