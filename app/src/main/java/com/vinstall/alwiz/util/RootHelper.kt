package com.vinstall.alwiz.util

object RootHelper {

    @Volatile
    private var cachedResult: Boolean? = null

    fun isRooted(): Boolean {
        cachedResult?.let { return it }
        val result = checkRooted()
        cachedResult = result
        DebugLog.d("RootHelper", "isRooted=$result")
        return result
    }

    private fun checkRooted(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            process.destroy()
            exitCode == 0 && output.contains("uid=0")
        } catch (_: Exception) {
            false
        }
    }
}
