package com.vinstall.alwiz.util

import android.content.Context
import com.vinstall.alwiz.R
import com.vinstall.alwiz.model.CallerInfo

object CallerResolver {

    @Suppress("DEPRECATION")
    fun labelOf(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    fun resolve(context: Context, packageName: String?, trusted: Boolean): CallerInfo {
        if (packageName.isNullOrEmpty()) {
            return CallerInfo(context.getString(R.string.caller_unknown), "", null, false)
        }
        val icon = try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (_: Exception) {
            null
        }
        return CallerInfo(labelOf(context, packageName), packageName, icon, trusted)
    }
}
