package com.vinstall.alwiz.apkv

import org.json.JSONArray
import org.json.JSONObject

data class ApkvManifest(
    val formatVersion: Int = 1,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val label: String,
    val isSplit: Boolean,
    val splits: List<String>,
    val encrypted: Boolean,
    val hasIcon: Boolean = false,
    val exportedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("format", "apkv")
            put("formatVersion", formatVersion)
            put("packageName", packageName)
            put("versionName", versionName)
            put("versionCode", versionCode)
            put("label", label)
            put("isSplit", isSplit)
            put("splits", JSONArray(splits))
            put("encrypted", encrypted)
            put("hasIcon", hasIcon)
            put("exportedAt", exportedAt)
        }.toString()
    }

    companion object {
        fun fromJson(json: String): ApkvManifest {
            val obj = JSONObject(json)

            val format = obj.optString("format", "")
            require(format == "apkv") { "Invalid APKv manifest: expected format=\"apkv\", got \"$format\"" }

            val arr = obj.getJSONArray("splits")
            val splits = (0 until arr.length()).map { arr.getString(it) }
            return ApkvManifest(
                formatVersion = obj.optInt("formatVersion", 1),
                packageName = obj.getString("packageName"),
                versionName = obj.getString("versionName"),
                versionCode = obj.getLong("versionCode"),
                label = obj.getString("label"),
                isSplit = obj.getBoolean("isSplit"),
                splits = splits,
                encrypted = obj.optBoolean("encrypted", false),
                hasIcon = obj.optBoolean("hasIcon", false),
                exportedAt = obj.getLong("exportedAt")
            )
        }
    }
}

data class ApkvHeader(
    val packageName: String,
    val versionName: String,
    val label: String,
    val encrypted: Boolean,
    val hasIcon: Boolean = false
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("packageName", packageName)
            put("versionName", versionName)
            put("label", label)
            put("encrypted", encrypted)
            put("hasIcon", hasIcon)
        }.toString()
    }

    companion object {
        fun fromJson(json: String): ApkvHeader {
            val obj = JSONObject(json)
            return ApkvHeader(
                packageName = obj.optString("packageName", ""),
                versionName = obj.optString("versionName", ""),
                label = obj.optString("label", ""),
                encrypted = obj.optBoolean("encrypted", false),
                hasIcon = obj.optBoolean("hasIcon", false)
            )
        }
    }
}
