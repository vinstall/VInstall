package com.vinstall.alwiz.model

import android.graphics.drawable.Drawable

data class CallerInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable?,
    val trusted: Boolean
)
