package com.vinstall.alwiz.ui

import android.content.Context
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import com.vinstall.alwiz.R

fun statusOk(context: Context): Int =
    MaterialColors.getColor(context, R.attr.vStatusOk, ContextCompat.getColor(context, R.color.status_ok))

fun statusWarn(context: Context): Int =
    MaterialColors.getColor(context, R.attr.vStatusWarn, ContextCompat.getColor(context, R.color.status_warn))

fun statusBlocked(context: Context): Int =
    MaterialColors.getColor(context, R.attr.vStatusBlocked, ContextCompat.getColor(context, R.color.status_blocked))
