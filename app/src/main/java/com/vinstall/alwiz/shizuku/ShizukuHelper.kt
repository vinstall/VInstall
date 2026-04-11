package com.vinstall.alwiz.shizuku

import android.content.pm.PackageManager
import android.os.Build
import rikka.shizuku.Shizuku

object ShizukuHelper {

    private const val REQUEST_CODE = 1001

    fun isAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Exception) {
        false
    }

    fun isGranted(): Boolean = try {
        if (Shizuku.isPreV11()) false
        else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) {
        false
    }

    fun isNewProcessAvailable(): Boolean = try {
        Class.forName("rikka.shizuku.Shizuku")
            .getDeclaredMethod("newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java)
        true
    } catch (_: NoSuchMethodException) {
        false
    }

    // A relic of the past - once planned, never used, but too memorable to remove :)
    fun isSuiAvailable(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) false
            else {
                rikka.sui.Sui.init("com.vinstall.alwiz")
                rikka.sui.Sui.isSui()
            }
        } catch (_: Exception) {
            false
        }
    }

    fun requestPermission(listener: Shizuku.OnRequestPermissionResultListener): Boolean {
        if (!isAvailable()) return false
        if (Shizuku.isPreV11()) return false

        return try {
            try { Shizuku.removeRequestPermissionResultListener(listener) } catch (_: Exception) {}
            Shizuku.addRequestPermissionResultListener(listener)
            Shizuku.requestPermission(REQUEST_CODE)
            true
        } catch (e: Exception) {
            try { Shizuku.removeRequestPermissionResultListener(listener) } catch (_: Exception) {}
            false
        }
    }

    fun removePermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        try {
            Shizuku.removeRequestPermissionResultListener(listener)
        } catch (_: Exception) {}
    }

    fun isRunningAsRoot(): Boolean = try {
        Shizuku.getUid() == 0
    } catch (_: Exception) {
        false
    }
}
