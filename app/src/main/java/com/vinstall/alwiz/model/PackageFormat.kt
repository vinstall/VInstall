package com.vinstall.alwiz.model

enum class PackageFormat(val extension: String, val label: String) {
    APK(".apk", "APK"),
    XAPK(".xapk", "XAPK"),
    APKS(".apks", "APKS"),
    APKM(".apkm", "APKM"),
    APKV(".apkv", "APKv"),
    ZIP(".zip", "ZIP"),
    UNKNOWN("", "Unknown");

    companion object {
        fun fromFileName(name: String): PackageFormat {
            val lower = name.lowercase()
            return entries.firstOrNull { it.extension.isNotEmpty() && lower.endsWith(it.extension) }
                ?: UNKNOWN
        }
    }
}
