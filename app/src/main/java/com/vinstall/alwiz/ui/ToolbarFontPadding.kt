package com.vinstall.alwiz.ui

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.Toolbar

fun Toolbar.syncTitleFontPadding() {
    val platformDefault = TextView(context).includeFontPadding
    val apply = Runnable {
        val includePadding = if (subtitle.isNullOrEmpty()) platformDefault else false
        for (index in 0 until childCount) {
            (getChildAt(index) as? TextView)?.includeFontPadding = includePadding
        }
    }
    apply.run()
    setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
        override fun onChildViewAdded(parent: View?, child: View?) {
            post(apply)
        }

        override fun onChildViewRemoved(parent: View?, child: View?) {
            post(apply)
        }
    })
}
