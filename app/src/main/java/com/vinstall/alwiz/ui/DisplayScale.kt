package com.vinstall.alwiz.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView
import com.vinstall.alwiz.settings.AppSettings
import java.util.WeakHashMap
import kotlin.math.roundToInt

object DisplayScale {

    const val TEXT_SYSTEM = "system"
    val TEXT_KEYS = arrayOf("small", "normal", "large", "xlarge")
    val GUI_KEYS = arrayOf("small", "normal", "large")
    const val TEXT_NORMAL_INDEX = 1

    private val TEXT_FACTORS = floatArrayOf(0.85f, 1f, 1.25f, 1.5f)
    private val GUI_FACTORS = floatArrayOf(0.9f, 1f, 1.15f)

    private const val STATE_SCROLL_RATIO = "vinstall_scroll_ratio"

    private val applied = WeakHashMap<Activity, String>()
    private val pendingScroll = WeakHashMap<Activity, Float>()

    fun textFactor(index: Int): Float = TEXT_FACTORS[index.coerceIn(0, TEXT_FACTORS.lastIndex)]

    fun textIndex(key: String): Int = TEXT_KEYS.indexOf(key)

    fun guiIndex(key: String): Int = GUI_KEYS.indexOf(key).let { if (it < 0) 1 else it }

    fun guiFactor(key: String): Float = GUI_FACTORS[guiIndex(key)]

    fun systemFontScale(): Float = Resources.getSystem().configuration.fontScale

    fun signature(context: Context): String {
        val textKey = AppSettings.getTextScale(context)
        val textIdx = textIndex(textKey)
        val text = if (textIdx < 0) systemFontScale() else textFactor(textIdx)
        val gui = guiFactor(AppSettings.getGuiScale(context))
        val motion = if (ReduceMotion.isEnabled(context)) "off" else ReduceMotion.tier(context).toString()
        return listOf(
            text,
            gui,
            AppSettings.getFont(context),
            motion,
            AppSettings.isHighContrast(context),
            AppSettings.isBoldText(context),
            AppSettings.isLargeTouch(context)
        ).joinToString("|")
    }

    fun recreateIfNeeded(activity: Activity) {
        val previous = applied[activity] ?: return
        val current = signature(activity)
        if (previous == current) return
        applied[activity] = current
        activity.recreate()
    }

    private fun findScroll(view: View): ViewGroup? {
        if ((view is NestedScrollView || view is ScrollView) && (view as ViewGroup).childCount > 0) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findScroll(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun saveScroll(activity: Activity, outState: Bundle) {
        val scroll = findScroll(activity.window.decorView) ?: return
        val height = scroll.getChildAt(0).height
        if (height <= 0) return
        outState.putFloat(STATE_SCROLL_RATIO, scroll.scrollY.toFloat() / height)
    }

    private fun restoreScroll(activity: Activity) {
        val ratio = pendingScroll.remove(activity) ?: return
        val scroll = findScroll(activity.window.decorView) ?: return
        scroll.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val observer = scroll.viewTreeObserver
                if (observer.isAlive) observer.removeOnPreDrawListener(this)
                val child = scroll.getChildAt(0) ?: return true
                val maxScroll = (child.height - scroll.height).coerceAtLeast(0)
                scroll.scrollTo(0, (ratio * child.height).toInt().coerceIn(0, maxScroll))
                return true
            }
        })
    }

    fun wrap(base: Context): Context {
        val textKey = AppSettings.getTextScale(base)
        val guiKey = AppSettings.getGuiScale(base)
        val followSystem = textIndex(textKey) < 0
        val gui = guiFactor(guiKey)
        if (followSystem && gui == 1f) return base
        val config = Configuration(base.resources.configuration)
        val text = if (followSystem) config.fontScale else textFactor(textIndex(textKey))
        config.fontScale = text / gui
        if (gui != 1f) {
            if (config.densityDpi > 0) {
                config.densityDpi = (config.densityDpi * gui).roundToInt().coerceAtLeast(1)
            }
            if (config.screenWidthDp > 0) config.screenWidthDp = (config.screenWidthDp / gui).toInt()
            if (config.screenHeightDp > 0) config.screenHeightDp = (config.screenHeightDp / gui).toInt()
            if (config.smallestScreenWidthDp > 0) {
                config.smallestScreenWidthDp = (config.smallestScreenWidthDp / gui).toInt()
            }
        }
        return base.createConfigurationContext(config)
    }

    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity !is ScaledActivity) return
                applied[activity] = signature(activity)
                val ratio = savedInstanceState?.getFloat(STATE_SCROLL_RATIO, -1f) ?: -1f
                if (ratio >= 0f) pendingScroll[activity] = ratio
            }

            override fun onActivityResumed(activity: Activity) {
                if (activity is ScaledActivity) recreateIfNeeded(activity)
            }

            override fun onActivityStarted(activity: Activity) {
                if (activity !is ScaledActivity) return
                ReduceMotion.applyLayoutAnimations(activity.window.decorView, activity)
                restoreScroll(activity)
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                if (activity is ScaledActivity) saveScroll(activity, outState)
            }

            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
