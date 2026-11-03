package com.vinstall.alwiz.util

import android.app.Activity
import android.content.Intent
import android.os.Build

object CallerVerifier {

    fun callingPackage(activity: Activity): String? {
        activity.callingPackage?.takeIf { it.isNotEmpty() }?.let { return it }
        activity.callingActivity?.packageName?.takeIf { it.isNotEmpty() }?.let { return it }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return null
        val intent = activity.intent ?: return null
        if (!stripSpoofableExtras(intent)) return null
        val referrer = activity.referrer ?: return null
        if (referrer.scheme != "android-app") return null
        return referrer.host?.takeIf { it.isNotEmpty() }
    }

    private fun stripSpoofableExtras(intent: Intent): Boolean {
        return try {
            intent.removeExtra(Intent.EXTRA_REFERRER)
            intent.removeExtra(Intent.EXTRA_REFERRER_NAME)
            !intent.hasExtra(Intent.EXTRA_REFERRER) && !intent.hasExtra(Intent.EXTRA_REFERRER_NAME)
        } catch (_: Exception) {
            false
        }
    }
}
