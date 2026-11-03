package com.vinstall.alwiz.ui

import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager

fun DialogFragment.showSafely(fragmentManager: FragmentManager, tag: String) {
    if (fragmentManager.isStateSaved) {
        fragmentManager.beginTransaction().add(this, tag).commitAllowingStateLoss()
    } else {
        show(fragmentManager, tag)
    }
}
