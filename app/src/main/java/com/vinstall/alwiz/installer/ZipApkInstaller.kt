package com.vinstall.alwiz.installer

import android.content.Context
import android.net.Uri
import com.vinstall.alwiz.util.DebugLog
import com.vinstall.alwiz.util.FileUtil
import java.io.File
import java.util.zip.ZipInputStream

object ZipApkInstaller {

    suspend fun install(
        context: Context,
        uri: Uri,
        onStep: (String) -> Unit,
        selectedSplits: List<String>? = null
    ): Result<Unit> {
        return try {
            onStep("Inspecting ZIP contents...")
            val cacheDir = File(context.cacheDir, "zip_extract").also {
                it.deleteRecursively()
                it.mkdirs()
            }
            val apkNames = extractApks(context, uri, cacheDir, onStep)
            if (apkNames.isEmpty()) {
                return Result.failure(Exception("No APK files found inside the ZIP archive"))
            }
            DebugLog.i("ZipApkInstaller", "Found ${apkNames.size} APK(s) in ZIP")
            onStep("Installing APK(s)...")
            val apkFiles = cacheDir.listFiles { f -> f.name.endsWith(".apk") }?.toList() ?: emptyList()
            SplitInstaller.installSplits(context, apkFiles, selectedSplits)
        } catch (e: Exception) {
            DebugLog.e("ZipApkInstaller", "Install failed: ${e.message}")
            Result.failure(e)
        }
    }

    fun listSplits(context: Context, uri: Uri): List<String> {
        val splits = mutableListOf<String>()
        try {
            val stream = FileUtil.openStream(context, uri) ?: return splits
            ZipInputStream(stream.buffered(FileUtil.BUFFER_SIZE)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".apk")) {
                        splits.add(File(entry.name).name)
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            DebugLog.e("ZipApkInstaller", "listSplits error: ${e.message}")
        }
        return splits
    }

    private fun extractApks(
        context: Context,
        uri: Uri,
        outDir: File,
        onStep: (String) -> Unit
    ): List<String> {
        val extracted = mutableListOf<String>()
        val stream = FileUtil.openStream(context, uri) ?: return extracted
        ZipInputStream(stream.buffered(FileUtil.BUFFER_SIZE)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".apk")) {
                    val fileName = File(entry.name).name
                    onStep("Extracting $fileName...")
                    val out = File(outDir, fileName)
                    out.outputStream().buffered(FileUtil.BUFFER_SIZE).use { zip.copyTo(it, FileUtil.BUFFER_SIZE) }
                    extracted.add(fileName)
                    DebugLog.d("ZipApkInstaller", "Extracted: $fileName")
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return extracted
    }
}
