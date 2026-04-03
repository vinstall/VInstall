package com.vinstall.alwiz.installer

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.google.gson.GsonBuilder
import com.vinstall.alwiz.parser.XapkManifest
import com.vinstall.alwiz.parser.XapkManifestDeserializer
import com.vinstall.alwiz.util.DebugLog
import com.vinstall.alwiz.util.FileUtil
import java.io.File
import java.util.zip.ZipFile

object XapkInstaller {

    private val gson = GsonBuilder()
        .registerTypeAdapter(XapkManifest::class.java, XapkManifestDeserializer())
        .create()

    suspend fun install(
        context: Context,
        uri: Uri,
        onStep: (String) -> Unit,
        selectedSplits: List<String>? = null
    ): Result<Unit> {
        return try {
            onStep("Extracting package...")
            val cacheDir = File(context.cacheDir, "xapk_extract").also {
                it.deleteRecursively()
                it.mkdirs()
            }

            val manifest = extractWithZipFile(context, uri, cacheDir, onStep)
                ?: return Result.failure(Exception("Invalid XAPK: manifest.json not found in archive"))

            DebugLog.i("XapkInstaller", "Manifest parsed: pkg=${manifest.packageName} splitBundle=${manifest.isSplitApkBundle()} expansions=${manifest.hasExpansions()}")

            if (manifest.isSplitApkBundle()) {
                onStep("Installing split APKs...")
                val apkFiles = manifest.splitApks!!.mapNotNull { entry ->
                    val f = File(cacheDir, File(entry.file).name)
                    if (f.exists()) f else {
                        DebugLog.e("XapkInstaller", "Split APK not found in cache: ${entry.file}")
                        null
                    }
                }
                if (apkFiles.isEmpty()) {
                    return Result.failure(Exception("No split APK files could be extracted from XAPK"))
                }
                SplitInstaller.installSplits(context, apkFiles, selectedSplits)
            } else {
                val mainApk = cacheDir.listFiles { f -> f.name.endsWith(".apk") }?.firstOrNull()
                    ?: return Result.failure(Exception("No APK found inside XAPK"))

                if (manifest.hasExpansions()) {
                    onStep("Copying expansion data...")
                    manifest.expansions?.forEach { exp ->
                        val src = File(cacheDir, File(exp.file).name)
                        if (src.exists()) {
                            val dest = File(Environment.getExternalStorageDirectory(), exp.installPath)
                            dest.parentFile?.mkdirs()
                            src.copyTo(dest, overwrite = true)
                            DebugLog.d("XapkInstaller", "OBB copied to ${dest.absolutePath}")
                        }
                    }
                }

                onStep("Installing APK...")
                SplitInstaller.installSplits(context, listOf(mainApk))
            }
        } catch (e: Exception) {
            DebugLog.e("XapkInstaller", "Install failed: ${e.message}")
            Result.failure(e)
        }
    }

    fun listSplits(context: Context, uri: Uri): List<String> {
        val splits = mutableListOf<String>()
        val tempFile = File(context.cacheDir, "xapk_list_${System.nanoTime()}.xapk")
        try {
            copyUriToFile(context, uri, tempFile)
            ZipFile(tempFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (!entry.isDirectory && entry.name.endsWith(".apk")) {
                        splits.add(File(entry.name).name)
                    }
                }
            }
        } catch (e: Exception) {
            DebugLog.e("XapkInstaller", "listSplits error: ${e.message}")
        } finally {
            tempFile.delete()
        }
        return splits
    }

    private fun extractWithZipFile(
        context: Context,
        uri: Uri,
        outDir: File,
        onStep: (String) -> Unit
    ): XapkManifest? {
        val tempFile = File(context.cacheDir, "xapk_extract_${System.nanoTime()}.xapk")
        return try {
            onStep("Copying to cache...")
            copyUriToFile(context, uri, tempFile)

            var manifest: XapkManifest? = null

            ZipFile(tempFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    when {
                        !entry.isDirectory && isManifestEntry(entry.name) -> {
                            val text = zip.getInputStream(entry).readBytes()
                                .toString(Charsets.UTF_8)
                                .trimStart('\uFEFF')
                                .trim()
                            DebugLog.d("XapkInstaller", "manifest.json found at entry='${entry.name}', raw: ${text.take(512)}")
                            manifest = gson.fromJson(text, XapkManifest::class.java)
                        }
                        !entry.isDirectory && (entry.name.endsWith(".apk") || entry.name.endsWith(".obb")) -> {
                            val fileName = File(entry.name).name
                            val outFile = File(outDir, fileName)
                            onStep("Extracting $fileName...")
                            zip.getInputStream(entry).buffered(FileUtil.BUFFER_SIZE).use { input ->
                                outFile.outputStream().buffered(FileUtil.BUFFER_SIZE).use { out ->
                                    input.copyTo(out, FileUtil.BUFFER_SIZE)
                                }
                            }
                            DebugLog.d("XapkInstaller", "Extracted: $fileName (${outFile.length()} bytes)")
                        }
                    }
                }
            }
            manifest
        } finally {
            tempFile.delete()
        }
    }

    private fun copyUriToFile(context: Context, uri: Uri, dest: File) {
        FileUtil.openStream(context, uri)?.use { input ->
            dest.outputStream().buffered(FileUtil.BUFFER_SIZE).use { output ->
                input.copyTo(output, FileUtil.BUFFER_SIZE)
            }
        }
    }

    private fun isManifestEntry(name: String): Boolean {
        val normalized = name.replace('\\', '/').trimStart('/')
        return normalized.equals("manifest.json", ignoreCase = true)
            || normalized.endsWith("/manifest.json", ignoreCase = true)
    }
}
