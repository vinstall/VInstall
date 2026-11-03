package com.vinstall.alwiz

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.vinstall.alwiz.settings.AppSettings
import com.vinstall.alwiz.timeline.AppTimelineManager
import com.vinstall.alwiz.ui.DisplayScale
import com.vinstall.alwiz.util.CrashHandler
import com.vinstall.alwiz.util.FileUtil
import com.vinstall.alwiz.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
        NotificationHelper.createChannel(this)
        applyTheme(AppSettings.getTheme(this))
        DisplayScale.install(this)
        AppTimelineManager.syncAsync(this)
        if (AppSettings.isAutoClearCache(this)) {
            val startedAt = System.currentTimeMillis()
            CoroutineScope(Dispatchers.IO).launch { FileUtil.clearStaleCache(this@App, startedAt) }
        }
    }

    companion object {
        fun applyTheme(theme: String) {
            val mode = when (theme) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }
}
