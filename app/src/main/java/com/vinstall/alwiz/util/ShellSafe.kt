package com.vinstall.alwiz.util

object ShellSafe {

    fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
