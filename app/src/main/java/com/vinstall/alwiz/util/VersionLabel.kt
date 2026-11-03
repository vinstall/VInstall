package com.vinstall.alwiz.util

object VersionLabel {

    fun format(versionName: String): String =
        if (versionName.startsWith("v", ignoreCase = true)) versionName else "v$versionName"
}
