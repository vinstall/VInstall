package com.vinstall.alwiz.parser

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

data class XapkManifest(
    val xapkVersion: Int = 1,
    val packageName: String = "",
    val name: String = "",
    val versionCode: String = "",
    val versionName: String = "",
    val minSdkVersion: String = "",
    val targetSdkVersion: String = "",
    val splitApks: List<SplitApkEntry>? = null,
    val expansions: List<ExpansionEntry>? = null,
    val icon: String? = null
) {
    fun isSplitApkBundle(): Boolean = !splitApks.isNullOrEmpty()
    fun hasExpansions(): Boolean = !expansions.isNullOrEmpty()
}

data class SplitApkEntry(
    val file: String = "",
    val id: String = ""
)

data class ExpansionEntry(
    val file: String = "",
    val installPath: String = ""
)

class XapkManifestDeserializer : JsonDeserializer<XapkManifest> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): XapkManifest {
        val obj = json.asJsonObject

        val xapkVersion = obj.get("xapk_version")?.let { el ->
            if (el.isJsonPrimitive) {
                val p = el.asJsonPrimitive
                if (p.isNumber) p.asInt else p.asString.toIntOrNull() ?: 1
            } else 1
        } ?: 1

        val versionCode = obj.get("version_code")?.let { el ->
            if (el.isJsonPrimitive) {
                val p = el.asJsonPrimitive
                if (p.isNumber) p.asLong.toString() else p.asString
            } else ""
        } ?: ""

        val minSdk = obj.get("min_sdk_version")?.let { el ->
            if (el.isJsonPrimitive) {
                val p = el.asJsonPrimitive
                if (p.isNumber) p.asInt.toString() else p.asString
            } else ""
        } ?: ""

        val targetSdk = obj.get("target_sdk_version")?.let { el ->
            if (el.isJsonPrimitive) {
                val p = el.asJsonPrimitive
                if (p.isNumber) p.asInt.toString() else p.asString
            } else ""
        } ?: ""

        val splitApks = obj.get("split_apks")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val entry = el.asJsonObject
                SplitApkEntry(
                    file = entry.get("file")?.asString ?: "",
                    id = entry.get("id")?.asString ?: ""
                )
            }

        val expansions = obj.get("expansions")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { el ->
                if (!el.isJsonObject) return@mapNotNull null
                val entry = el.asJsonObject
                ExpansionEntry(
                    file = entry.get("file")?.asString ?: "",
                    installPath = entry.get("install_path")?.asString
                        ?: entry.get("installPath")?.asString
                        ?: ""
                )
            }

        val icon = obj.get("icon")?.asString

        return XapkManifest(
            xapkVersion = xapkVersion,
            packageName = obj.get("package_name")?.asString ?: "",
            name = obj.get("name")?.asString ?: "",
            versionCode = versionCode,
            versionName = obj.get("version_name")?.asString ?: "",
            minSdkVersion = minSdk,
            targetSdkVersion = targetSdk,
            splitApks = splitApks,
            expansions = expansions,
            icon = icon
        )
    }
}
