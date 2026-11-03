package com.vinstall.alwiz.ui

import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.app.AppCompatActivity
import com.vinstall.alwiz.R
import com.vinstall.alwiz.settings.AppSettings

open class ScaledActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(DisplayScale.wrap(newBase))
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        (findViewById<android.view.View>(R.id.toolbar) as? Toolbar)?.syncTitleFontPadding()
    }

    override fun onApplyThemeResource(theme: Resources.Theme, resid: Int, first: Boolean) {
        super.onApplyThemeResource(theme, resid, first)
        if (AppSettings.getFont(baseContext) == "system") {
            theme.applyStyle(R.style.ThemeOverlay_VInstall_SystemFont, true)
        }
        if (AppSettings.isBoldText(baseContext)) {
            val overlay = if (AppSettings.getFont(baseContext) == "system")
                R.style.ThemeOverlay_VInstall_BoldTextSystem
            else
                R.style.ThemeOverlay_VInstall_BoldText
            theme.applyStyle(overlay, true)
        }
        if (AppSettings.isHighContrast(baseContext)) {
            theme.applyStyle(R.style.ThemeOverlay_VInstall_HighContrast, true)
        }
        if (AppSettings.isLargeTouch(baseContext)) {
            theme.applyStyle(R.style.ThemeOverlay_VInstall_LargeTouch, true)
        }
        val motionOverlay = when {
            ReduceMotion.isEnabled(baseContext) -> R.style.ThemeOverlay_VInstall_NoMotion
            ReduceMotion.tier(baseContext) == 0.5f -> R.style.ThemeOverlay_VInstall_Motion050
            ReduceMotion.tier(baseContext) == 2f -> R.style.ThemeOverlay_VInstall_Motion200
            ReduceMotion.tier(baseContext) == 3f -> R.style.ThemeOverlay_VInstall_Motion300
            else -> 0
        }
        if (motionOverlay != 0) theme.applyStyle(motionOverlay, true)
    }
}
