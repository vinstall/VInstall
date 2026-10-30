package com.vinstall.alwiz.settings

import android.content.Context
import android.content.SharedPreferences

object AppSettings {

    private const val PREFS_NAME = "vinstall_prefs"
    private const val KEY_INSTALL_MODE = "install_mode"
    private const val KEY_DEBUG_WINDOW = "debug_window"
    private const val KEY_THEME = "theme"
    private const val KEY_AUTO_CLEAR_CACHE = "auto_clear_cache"
    private const val KEY_CONFIRM_INSTALL = "confirm_install"
    private const val KEY_SHIZUKU_PERMISSION_GRANTED = "shizuku_permission_granted"
    private const val KEY_DIALOG_STYLE = "dialog_style"

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

    fun isDebugWindowEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DEBUG_WINDOW, true)

    fun setDebugWindowEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DEBUG_WINDOW, enabled).apply()
    }

    fun getTheme(context: Context): String =
        prefs(context).getString(KEY_THEME, "system") ?: "system"

    fun setTheme(context: Context, theme: String) {
        prefs(context).edit().putString(KEY_THEME, theme).apply()
    }

    fun isAutoClearCache(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_CLEAR_CACHE, true)

    fun setAutoClearCache(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_CLEAR_CACHE, enabled).apply()
    }

    fun isConfirmInstall(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CONFIRM_INSTALL, false)

    fun setConfirmInstall(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_CONFIRM_INSTALL, enabled).apply()
    }

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
}
