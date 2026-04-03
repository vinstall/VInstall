package com.vinstall.alwiz.util

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.vinstall.alwiz.apkv.ApkvCrypto
import com.vinstall.alwiz.parser.XapkManifest
import com.vinstall.alwiz.parser.XapkManifestDeserializer
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

object MetadataReader {

    data class AppMeta(
        val packageName: String = "",
        val versionName: String = "",
        val appLabel: String = "",
        val appIcon: Bitmap? = null
    )

    private val gson = GsonBuilder()
        .registerTypeAdapter(XapkManifest::class.java, XapkManifestDeserializer())
        .create()

    private val ICON_DENSITY_PRIORITY = listOf(
        "mipmap-xxxhdpi", "mipmap-xxhdpi", "mipmap-xhdpi", "mipmap-hdpi", "mipmap-mdpi",
        "mipmap-anydpi", "mipmap",
        "drawable-xxxhdpi", "drawable-xxhdpi", "drawable-xhdpi", "drawable-hdpi",
        "drawable-mdpi", "drawable-anydpi", "drawable"
    )

    private val ICON_NAME_HINTS = listOf(
        "ic_launcher_round", "ic_launcher_foreground", "ic_launcher",
        "app_icon", "icon", "launcher"
    )

    private fun extractIconFromApkZip(apkPath: String): Bitmap? {
        return try {
            var bestDIdx = Int.MAX_VALUE
            var bestNIdx = Int.MAX_VALUE
            var bestBytes: ByteArray? = null

            ZipInputStream(
                File(apkPath).inputStream().buffered(FileUtil.BUFFER_SIZE)
            ).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = entry.name.replace('\\', '/').trimStart('/')
                        val lower = name.lowercase()
                        if (lower.startsWith("res/") &&
                            (lower.endsWith(".png") || lower.endsWith(".webp"))
                        ) {
                            val parts = name.split("/")
                            if (parts.size >= 3) {
                                val folder = parts[1].lowercase()
                                val file = parts.last().lowercase().substringBeforeLast(".")
                                val dIdx = ICON_DENSITY_PRIORITY.indexOfFirst { folder == it.lowercase() }
                                val nIdx = ICON_NAME_HINTS.indexOfFirst {
                                    file == it.lowercase() || file.startsWith(it.lowercase())
                                }
                                if (dIdx >= 0 && nIdx >= 0) {
                                    if (dIdx < bestDIdx || (dIdx == bestDIdx && nIdx < bestNIdx)) {
                                        bestDIdx = dIdx
                                        bestNIdx = nIdx
                                        bestBytes = zip.readBytes()
                                    }
                                }
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            bestBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        } catch (e: Exception) {
            DebugLog.e("MetadataReader", "extractIconFromApkZip failed: ${e.message}")
            null
        }
    }

    fun readFromApk(context: Context, uri: Uri, fileName: String): AppMeta {
        return try {
            val cached = FileUtil.extractToCache(
                context, uri,
                "meta_${fileName.hashCode()}.apk"
            )
            readApkFile(context, cached.absolutePath)
        } catch (e: Exception) {
            DebugLog.e("MetadataReader", "readFromApk failed: ${e.message}")
            AppMeta()
        }
    }

    fun readFromApkv(context: Context, uri: Uri, password: String? = null): AppMeta {
        val stream = FileUtil.openStream(context, uri) ?: return AppMeta()

        var isEncrypted = false
        var packageName = ""
        var versionName = ""
        var appLabel = ""
        var iconBitmap: Bitmap? = null
        var hasPlainApk = false

        try {
            ZipInputStream(stream.buffered(FileUtil.BUFFER_SIZE)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when {
                        entry.name == ".apkv_enc" -> {
                            isEncrypted = true
                        }

                        entry.name == "header.json" -> {
                            val text = zip.readBytes().toString(Charsets.UTF_8)
                            try {
                                val obj = org.json.JSONObject(text)
                                if (packageName.isEmpty()) packageName = obj.optString("packageName", "")
                                if (versionName.isEmpty()) versionName = obj.optString("versionName", "")
                                if (appLabel.isEmpty()) appLabel = obj.optString("label", "")
                            } catch (e: Exception) {
                                DebugLog.e("MetadataReader", "apkv header.json parse failed: ${e.message}")
                            }
                        }

                        entry.name == "manifest.json" -> {
                            val text = zip.readBytes().toString(Charsets.UTF_8)
                            try {
                                val obj = org.json.JSONObject(text)
                                packageName = obj.optString("packageName", packageName)
                                versionName = obj.optString("versionName", versionName)
                                appLabel = obj.optString("label", appLabel)
                            } catch (e: Exception) {
                                DebugLog.e("MetadataReader", "apkv manifest.json parse failed: ${e.message}")
                            }
                        }

                        entry.name == "manifest.enc" && password != null -> {
                            val blob = zip.readBytes()
                            val decrypted = ApkvCrypto.tryDecrypt(blob, password)
                            if (decrypted != null) {
                                try {
                                    val obj = org.json.JSONObject(decrypted.toString(Charsets.UTF_8))
                                    packageName = obj.optString("packageName", packageName)
                                    versionName = obj.optString("versionName", versionName)
                                    appLabel = obj.optString("label", appLabel)
                                } catch (e: Exception) {
                                    DebugLog.e("MetadataReader", "apkv manifest.enc parse failed: ${e.message}")
                                }
                            }
                        }

                        iconBitmap == null && !entry.isDirectory && run {
                            val n = entry.name.lowercase()
                            n == "icon.webp" || n == "icon.png" || n == "icon.jpg" || n == "icon.jpeg"
                        } -> {
                            val bytes = zip.readBytes()
                            iconBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            DebugLog.d("MetadataReader", "apkv ${entry.name} decoded: ${iconBitmap != null}")
                        }

                        entry.name == "icon.enc" && iconBitmap == null && password != null -> {
                            val blob = zip.readBytes()
                            val decrypted = ApkvCrypto.tryDecrypt(blob, password)
                            if (decrypted != null) {
                                iconBitmap = BitmapFactory.decodeByteArray(decrypted, 0, decrypted.size)
                                DebugLog.d("MetadataReader", "apkv icon.enc decoded: ${iconBitmap != null}")
                            }
                        }

                        !entry.isDirectory && entry.name.lowercase().endsWith(".apk") && !isEncrypted -> {
                            hasPlainApk = true
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            DebugLog.e("MetadataReader", "readFromApkv scan failed: ${e.message}")
        }

        if (iconBitmap == null && !isEncrypted && hasPlainApk) {
            iconBitmap = extractIconFromPlainApkv(context, uri)
        }

        DebugLog.d(
            "MetadataReader",
            "readFromApkv: pkg=$packageName ver=$versionName encrypted=$isEncrypted icon=${iconBitmap != null}"
        )

        return AppMeta(
            packageName = packageName,
            versionName = versionName,
            appLabel = appLabel,
            appIcon = iconBitmap
        )
    }

    private fun extractIconFromPlainApkv(context: Context, uri: Uri): Bitmap? {
        val tmpApk = File(context.cacheDir, "meta_apkv_base_${System.nanoTime()}.apk")
        return try {
            val extracted = FileUtil.openStream(context, uri)?.use { raw ->
                ZipInputStream(raw.buffered(FileUtil.BUFFER_SIZE)).use { zip ->
                    var found = false
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val entryName = File(entry.name).name
                        val isBase = entryName.equals("base.apk", ignoreCase = true)
                        val isAnyApk = !found && !entry.isDirectory && entry.name.lowercase().endsWith(".apk")
                        if (isBase || isAnyApk) {
                            tmpApk.outputStream().buffered(FileUtil.BUFFER_SIZE).use { out ->
                                zip.copyTo(out, FileUtil.BUFFER_SIZE)
                            }
                            found = true
                            if (isBase) break
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                    found
                }
            } ?: false

            if (!extracted) return null

            val meta = readApkFile(context, tmpApk.absolutePath)
            meta.appIcon ?: extractIconFromApkZip(tmpApk.absolutePath)
        } catch (e: Exception) {
            DebugLog.e("MetadataReader", "extractIconFromPlainApkv failed: ${e.message}")
            null
        } finally {
            tmpApk.delete()
        }
    }

    fun readFromXapk(context: Context, uri: Uri): AppMeta {
        val tempFile = File(context.cacheDir, "meta_xapk_${System.nanoTime()}.xapk")
        return try {
            FileUtil.openStream(context, uri)?.use { input ->
                tempFile.outputStream().buffered(FileUtil.BUFFER_SIZE).use { output ->
                    input.copyTo(output, FileUtil.BUFFER_SIZE)
                }
            }

            var manifest: XapkManifest? = null
            var iconBitmap: Bitmap? = null

            ZipFile(tempFile).use { zip ->
                val manifestEntry = zip.entries().asSequence().firstOrNull { entry ->
                    val n = entry.name.replace('\\', '/').trimStart('/')
                    !entry.isDirectory && (
                        n.equals("manifest.json", ignoreCase = true)
                        || n.endsWith("/manifest.json", ignoreCase = true)
                    )
                }

                if (manifestEntry != null) {
                    val text = zip.getInputStream(manifestEntry).readBytes()
                        .toString(Charsets.UTF_8)
                        .trimStart('\uFEFF')
                        .trim()
                    DebugLog.d("MetadataReader", "XAPK manifest: ${text.take(256)}")
                    manifest = gson.fromJson(text, XapkManifest::class.java)
                }

                val iconFileName = manifest?.icon
                    ?.replace('\\', '/')
                    ?.trimStart('/')
                    ?.takeIf { it.isNotEmpty() }
                    ?: "icon.png"

                val iconEntry = zip.getEntry(iconFileName)
                    ?: zip.entries().asSequence().firstOrNull { entry ->
                        val n = entry.name.replace('\\', '/').trimStart('/').lowercase()
                        !entry.isDirectory && (n == "icon.png" || n == "icon.webp"
                            || n.endsWith("/icon.png") || n.endsWith("/icon.webp"))
                    }

                if (iconEntry != null) {
                    val bytes = zip.getInputStream(iconEntry).readBytes()
                    iconBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    DebugLog.d("MetadataReader", "XAPK icon decoded from '${iconEntry.name}': ${iconBitmap != null}")
                }

                if (iconBitmap == null) {
                    DebugLog.d("MetadataReader", "XAPK icon.png null — trying base APK fallback")
                    val baseApkEntry = manifest?.splitApks
                        ?.firstOrNull { it.id == "base" }
                        ?.file
                        ?.let { zip.getEntry(it) }
                        ?: zip.entries().asSequence().firstOrNull { entry ->
                            val n = entry.name.lowercase()
                            !entry.isDirectory && n.endsWith(".apk") && !n.contains("config.")
                        }

                    if (baseApkEntry != null) {
                        val fallbackApk = File(context.cacheDir, "meta_xapk_base_${System.nanoTime()}.apk")
                        zip.getInputStream(baseApkEntry).use { input ->
                            fallbackApk.outputStream().buffered(FileUtil.BUFFER_SIZE)
                                .use { out -> input.copyTo(out, FileUtil.BUFFER_SIZE) }
                        }
                        iconBitmap = readApkFile(context, fallbackApk.absolutePath).appIcon
                            ?: extractIconFromApkZip(fallbackApk.absolutePath)
                        DebugLog.d("MetadataReader", "XAPK base APK icon fallback result: ${iconBitmap != null}")
                    }
                }
            }

            manifest?.let {
                AppMeta(
                    packageName = it.packageName,
                    versionName = it.versionName,
                    appLabel = it.name,
                    appIcon = iconBitmap
                )
            } ?: AppMeta()
        } catch (e: Exception) {
            DebugLog.e("MetadataReader", "readFromXapk failed: ${e.message}")
            AppMeta()
        } finally {
            tempFile.delete()
        }
    }

    fun readFromApkm(context: Context, uri: Uri): AppMeta {
        return try {
            val stream = FileUtil.openStream(context, uri) ?: return AppMeta()
            var packageName = ""
            var versionName = ""
            var appLabel = ""
            var iconBitmap: Bitmap? = null
            var foundInfo = false

            ZipInputStream(stream.buffered(FileUtil.BUFFER_SIZE)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val isInfo = entry.name.equals("info.json", ignoreCase = true)
                        val isIcon = entry.name.equals("icon.png", ignoreCase = true)

                        when {
                            isInfo && !foundInfo -> {
                                val text = zip.readBytes().toString(Charsets.UTF_8).trim()
                                DebugLog.d("MetadataReader", "APKM info.json: ${text.take(256)}")
                                val json = gson.fromJson(text, JsonObject::class.java)
                                packageName = json.get("pname")?.asString ?: ""
                                versionName = json.get("release_version")?.asString ?: ""
                                appLabel = json.get("app_name")?.asString ?: ""
                                foundInfo = true
                            }
                            isIcon && iconBitmap == null -> {
                                val bytes = zip.readBytes()
                                iconBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                DebugLog.d("MetadataReader", "APKM icon decoded: ${iconBitmap != null}")
                            }
                        }
                    }
                    zip.closeEntry()
                    if (foundInfo && iconBitmap != null) break
                    entry = zip.nextEntry
                }
            }

            if (packageName.isNotEmpty()) {
                AppMeta(packageName, versionName, appLabel, iconBitmap)
            } else {
                DebugLog.w("MetadataReader", "APKM info.json missing or empty, falling back to base.apk")
                val fallback = readFromApks(context, uri)
                fallback.copy(appIcon = fallback.appIcon ?: iconBitmap)
            }
        } catch (e: Exception) {
            DebugLog.e("MetadataReader", "readFromApkm failed: ${e.message}")
            AppMeta()
        }
    }

    fun readFromApks(context: Context, uri: Uri): AppMeta {
        val tocMeta = readFromTocPb(context, uri)
        DebugLog.d("MetadataReader", "toc.pb packageName=${tocMeta?.packageName}")

        val tmpFile = File(context.cacheDir, "meta_split_base_${System.nanoTime()}.apk")
        var extracted = false

        try {
            FileUtil.openStream(context, uri)?.use { raw ->
                ZipInputStream(raw.buffered(FileUtil.BUFFER_SIZE)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory &&
                            File(entry.name).name.equals("base.apk", ignoreCase = true)
                        ) {
                            tmpFile.outputStream().buffered(FileUtil.BUFFER_SIZE).use { out ->
                                zip.copyTo(out, FileUtil.BUFFER_SIZE)
                            }
                            extracted = true
                            break
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            DebugLog.e("MetadataReader", "readFromApks pass1 failed: ${e.message}")
        }

        if (!extracted) {
            try {
                FileUtil.openStream(context, uri)?.use { raw ->
                    ZipInputStream(raw.buffered(FileUtil.BUFFER_SIZE)).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory && entry.name.endsWith(".apk")) {
                                tmpFile.outputStream().buffered(FileUtil.BUFFER_SIZE).use { out ->
                                    zip.copyTo(out, FileUtil.BUFFER_SIZE)
                                }
                                extracted = true
                                break
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                }
            } catch (e: Exception) {
                DebugLog.e("MetadataReader", "readFromApks pass2 failed: ${e.message}")
            }
        }

        if (!extracted) return tocMeta ?: AppMeta()

        val apkMeta = readApkFile(context, tmpFile.absolutePath)
        return apkMeta.copy(
            packageName = tocMeta?.packageName
                ?.takeIf { it.isNotEmpty() }
                ?: apkMeta.packageName
        )
    }

    private fun readFromTocPb(context: Context, uri: Uri): AppMeta? {
        return try {
            FileUtil.openStream(context, uri)?.use { raw ->
                ZipInputStream(raw.buffered(FileUtil.BUFFER_SIZE)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory &&
                            entry.name.equals("toc.pb", ignoreCase = true)
                        ) {
                            val bytes = zip.readBytes()
                            DebugLog.d("MetadataReader", "toc.pb size=${bytes.size}")
                            return@use parseTocPb(bytes)
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                    null
                }
            }
        } catch (e: Exception) {
            DebugLog.e("MetadataReader", "readFromTocPb failed: ${e.message}")
            null
        }
    }

    private fun parseTocPb(data: ByteArray): AppMeta? {
        var packageName = ""
        var pos = 0

        while (pos < data.size) {
            val tagResult = readProtoVarint(data, pos)
            pos = tagResult.second
            val tag = tagResult.first
            val fieldNum = (tag shr 3).toInt()
            val wireType = (tag and 0x7).toInt()

            when (wireType) {
                0 -> {
                    val r = readProtoVarint(data, pos)
                    pos = r.second
                }
                1 -> { pos += 8 }
                2 -> {
                    val lenResult = readProtoVarint(data, pos)
                    pos = lenResult.second
                    val len = lenResult.first.toInt()
                    val end = pos + len

                    if (len in 4..200) {
                        val candidate = data.sliceArray(pos until minOf(end, data.size))
                            .toString(Charsets.UTF_8)
                        if (packageName.isEmpty() &&
                            candidate.contains('.') &&
                            candidate.matches(Regex("[a-zA-Z][a-zA-Z0-9_.]*"))
                        ) {
                            packageName = candidate
                            DebugLog.d("MetadataReader", "toc.pb candidate pkg at field $fieldNum: $candidate")
                        }
                    }
                    pos = end
                }
                5 -> { pos += 4 }
                else -> break
            }

            if (pos > data.size) break
        }

        return if (packageName.isNotEmpty()) AppMeta(packageName = packageName) else null
    }

    private fun readProtoVarint(data: ByteArray, pos: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var p = pos
        while (p < data.size) {
            val b = data[p++].toLong() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80L == 0L) break
            shift += 7
        }
        return result to p
    }

    private fun readApkFile(context: Context, apkPath: String): AppMeta {
        return try {
            val pm = context.packageManager
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(
                    apkPath,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkPath, PackageManager.GET_ACTIVITIES)
            } ?: return AppMeta()

            val packageName = pkgInfo.packageName ?: return AppMeta()
            val versionName = pkgInfo.versionName ?: ""
            val appLabel = pkgInfo.applicationInfo?.let { ai ->
                ai.sourceDir = apkPath
                ai.publicSourceDir = apkPath
                try {
                    pm.getApplicationLabel(ai).toString()
                } catch (_: Exception) {
                    ""
                }
            } ?: ""

            var appIcon: Bitmap? = pkgInfo.applicationInfo?.let { ai ->
                ai.sourceDir = apkPath
                ai.publicSourceDir = apkPath
                try {
                    val drawable = pm.getApplicationIcon(ai)
                    if (drawable is BitmapDrawable) {
                        drawable.bitmap
                    } else {
                        val bmp = Bitmap.createBitmap(
                            drawable.intrinsicWidth.coerceAtLeast(1),
                            drawable.intrinsicHeight.coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        )
                        val canvas = Canvas(bmp)
                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                        drawable.draw(canvas)
                        bmp
                    }
                } catch (_: Exception) { null }
            }

            if (appIcon == null) {
                DebugLog.d("MetadataReader", "PM icon null — trying zip fallback for $apkPath")
                appIcon = extractIconFromApkZip(apkPath)
            }

            DebugLog.d(
                "MetadataReader",
                "readApkFile: pkg=$packageName ver=$versionName label=$appLabel icon=${appIcon != null}"
            )
            AppMeta(packageName, versionName, appLabel, appIcon)
        } catch (e: Exception) {
            DebugLog.e("MetadataReader", "readApkFile failed: ${e.message}")
            AppMeta()
        }
    }
}
