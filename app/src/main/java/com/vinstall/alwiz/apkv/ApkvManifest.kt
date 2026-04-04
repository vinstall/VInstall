package com.vinstall.alwiz.apkv

import org.json.JSONArray
import org.json.JSONObject

data class ApkvManifest(
    val formatVersion: Int = 2,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val label: String,
    val labels: Map<String, String>? = null,
    val isSplit: Boolean,
    val splits: List<String>,
    val encrypted: Boolean,
    val hasIcon: Boolean = false,
    val exportedAt: Long = System.currentTimeMillis(),
    val minSdkVersion: Int,
    val targetSdkVersion: Int,
    val checksums: Map<String, String>? = null,
    val totalSize: Long? = null,
    val permissions: List<String>? = null
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("format", "apkv")
            put("formatVersion", formatVersion)
            put("packageName", packageName)
            put("versionName", versionName)
            put("versionCode", versionCode)
            put("label", label)
            labels?.let { map ->
                put("labels", JSONObject().apply {
                    map.forEach { (locale, name) -> put(locale, name) }
                })
            }
            put("isSplit", isSplit)
            put("splits", JSONArray(splits))
            put("encrypted", encrypted)
            put("hasIcon", hasIcon)
            put("exportedAt", exportedAt)
            put("minSdkVersion", minSdkVersion)
            put("targetSdkVersion", targetSdkVersion)
            checksums?.let { map ->
                put("checksums", JSONObject().apply {
                    map.forEach { (filename, hash) -> put(filename, hash) }
                })
            }
            totalSize?.let { put("totalSize", it) }
            permissions?.let { put("permissions", JSONArray(it)) }
        }.toString()
    }

    companion object {
        fun fromJson(json: String): ApkvManifest {
            val obj = JSONObject(json)

            val format = obj.optString("format", "")
            require(format == "apkv") { "Invalid APKv manifest: expected format=\"apkv\", got \"$format\"" }

            val arr = obj.getJSONArray("splits")
            val splits = (0 until arr.length()).map { arr.getString(it) }

            val labels = obj.optJSONObject("labels")?.let { labelsObj ->
                labelsObj.keys().asSequence().associateBy(
                    keySelector = { it.lowercase() },
                    valueTransform = { labelsObj.getString(it) }
                )
            }

            val checksums = obj.optJSONObject("checksums")?.let { csObj ->
                csObj.keys().asSequence().associateWith { csObj.getString(it) }
            }

            val permissionsArr = obj.optJSONArray("permissions")
            val permissions = permissionsArr?.let {
                (0 until it.length()).map { i -> it.getString(i) }
            }

            return ApkvManifest(
                formatVersion = obj.optInt("formatVersion", 1),
                packageName = obj.getString("packageName"),
                versionName = obj.getString("versionName"),
                versionCode = obj.getLong("versionCode"),
                label = obj.getString("label"),
                labels = labels,
                isSplit = obj.getBoolean("isSplit"),
                splits = splits,
                encrypted = obj.optBoolean("encrypted", false),
                hasIcon = obj.optBoolean("hasIcon", false),
                exportedAt = obj.getLong("exportedAt"),
                minSdkVersion = obj.getInt("minSdkVersion"),
                targetSdkVersion = obj.getInt("targetSdkVersion"),
                checksums = checksums,
                totalSize = if (obj.has("totalSize")) obj.getLong("totalSize") else null,
                permissions = permissions
            )
        }
    }
}

data class ApkvHeader(
    val packageName: String,
    val versionName: String,
    val label: String,
    val labels: Map<String, String>? = null,
    val encrypted: Boolean,
    val hasIcon: Boolean = false,
    val exportedAt: Long? = null
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("packageName", packageName)
            put("versionName", versionName)
            put("label", label)
            labels?.let { map ->
                put("labels", JSONObject().apply {
                    map.forEach { (locale, name) -> put(locale, name) }
                })
            }
            put("encrypted", encrypted)
            put("hasIcon", hasIcon)
            exportedAt?.let { put("exportedAt", it) }
        }.toString()
    }

    companion object {
        fun fromJson(json: String): ApkvHeader {
            val obj = JSONObject(json)

            val labels = obj.optJSONObject("labels")?.let { labelsObj ->
                // Normalize keys to lowercase per §11.1
                labelsObj.keys().asSequence().associateBy(
                    keySelector = { it.lowercase() },
                    valueTransform = { labelsObj.getString(it) }
                )
            }

            return ApkvHeader(
                packageName = obj.optString("packageName", ""),
                versionName = obj.optString("versionName", ""),
                label = obj.optString("label", ""),
                labels = labels,
                encrypted = obj.optBoolean("encrypted", false),
                hasIcon = obj.optBoolean("hasIcon", false),
                exportedAt = if (obj.has("exportedAt")) obj.getLong("exportedAt") else null
            )
        }
    }
}
