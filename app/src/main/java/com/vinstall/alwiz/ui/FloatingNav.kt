package com.vinstall.alwiz.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.ImageViewCompat
import android.content.res.ColorStateList
import com.google.android.material.color.MaterialColors
import com.vinstall.alwiz.DebugWindowActivity
import com.vinstall.alwiz.R
import com.vinstall.alwiz.history.InstallHistoryActivity
import com.vinstall.alwiz.settings.AppSettings
import com.vinstall.alwiz.settings.SettingsActivity

class FloatingNav @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    enum class Tab(val iconRes: Int, val labelRes: Int) {
        INSTALL(R.drawable.ic_nav_install, R.string.nav_install),
        DEBUG(R.drawable.ic_nav_debug, R.string.nav_debug),
        SETTINGS(R.drawable.ic_nav_settings, R.string.nav_settings),
        LOG(R.drawable.ic_nav_log, R.string.nav_log)
    }

    var onSelect: ((Tab) -> Unit)? = null

    private val items = LinkedHashMap<Tab, View>()
    private val icons = HashMap<Tab, ImageView>()
    private val labels = HashMap<Tab, TextView>()
    private var selectedTab: Tab? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        setBackgroundResource(R.drawable.bg_floating_nav)
        val pad = dp(6)
        setPadding(pad, pad, pad, pad)
        elevation = dp(3).toFloat()
        Tab.values().forEach { addItem(it) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun addItem(tab: Tab) {
        val item = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setBackgroundResource(R.drawable.nav_item_bg)
            contentDescription = context.getString(tab.labelRes)
            layoutParams = LayoutParams(0, dp(56), 1f).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }
            setOnClickListener { onSelect?.invoke(tab) }
            applyPressAnimation(0.92f)
        }
        val icon = ImageView(context).apply {
            setImageResource(tab.iconRes)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = LayoutParams(dp(24), dp(24))
        }
        val label = TextView(context).apply {
            setText(tab.labelRes)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(2)
            }
        }
        item.addView(icon)
        item.addView(label)
        addView(item)
        items[tab] = item
        icons[tab] = icon
        labels[tab] = label
    }

    fun setSelectedTab(tab: Tab) {
        val changed = selectedTab != tab
        selectedTab = tab
        val active = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSecondaryContainer)
        val idle = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant)
        Tab.values().forEach { t ->
            val selected = t == tab
            val color = if (selected) active else idle
            items[t]?.isSelected = selected
            icons[t]?.let { ImageViewCompat.setImageTintList(it, ColorStateList.valueOf(color)) }
            labels[t]?.setTextColor(color)
        }
        if (changed) icons[tab]?.popIn(0.55f)
    }

    fun setDebugVisible(visible: Boolean) {
        items[Tab.DEBUG]?.visibility = if (visible) VISIBLE else GONE
    }
}

fun AppCompatActivity.bindFloatingNav(nav: FloatingNav, current: FloatingNav.Tab) {
    if (current != FloatingNav.Tab.INSTALL) fadeTransition()
    nav.setDebugVisible(AppSettings.isDebugWindowEnabled(this))
    nav.setSelectedTab(current)
    nav.onSelect = { target ->
        if (target != current) {
            if (target == FloatingNav.Tab.INSTALL) {
                finish()
                fadeTransition()
            } else {
                val destination = when (target) {
                    FloatingNav.Tab.DEBUG -> DebugWindowActivity::class.java
                    FloatingNav.Tab.SETTINGS -> SettingsActivity::class.java
                    else -> InstallHistoryActivity::class.java
                }
                startActivity(
                    Intent(this, destination).addFlags(
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    )
                )
                if (current != FloatingNav.Tab.INSTALL) {
                    finish()
                    fadeTransition()
                }
            }
        }
    }
}

private fun Activity.fadeTransition() {
    if (Build.VERSION.SDK_INT >= 34) {
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, R.anim.nav_fade_in, R.anim.nav_fade_out)
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, R.anim.nav_fade_in, R.anim.nav_fade_out)
    } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.nav_fade_in, R.anim.nav_fade_out)
    }
}
