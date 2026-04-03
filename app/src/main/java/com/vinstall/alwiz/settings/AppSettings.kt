package com.vinstall.alwiz.settings

import android.content.Context
import android.content.SharedPreferences

enum class InstallMode { NORMAL, ROOT, SHIZUKU }

object AppSettings {

    private const val PREFS_NAME = "vinstall_prefs"
    private const val KEY_INSTALL_MODE = "install_mode"
    private const val KEY_DEBUG_WINDOW = "debug_window"
    private const val KEY_THEME = "theme"
    private const val KEY_CLEAR_CACHE_AFTER = "clear_cache_after"
    private const val KEY_CONFIRM_INSTALL = "confirm_install"

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

    fun isClearCacheAfterInstall(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CLEAR_CACHE_AFTER, true)

    fun setClearCacheAfterInstall(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_CLEAR_CACHE_AFTER, enabled).apply()
    }

    fun isConfirmInstall(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CONFIRM_INSTALL, false)

    fun setConfirmInstall(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_CONFIRM_INSTALL, enabled).apply()
    }
}
