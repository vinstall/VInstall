package com.vinstall.alwiz.installer

import android.content.Context
import android.net.Uri
import com.vinstall.alwiz.util.FileUtil
import java.io.File
import java.util.zip.ZipInputStream

object ApksInstaller {

    suspend fun install(
        context: Context,
        uri: Uri,
        onStep: (String) -> Unit,
        selectedSplits: List<String>? = null
    ): Result<Unit> {
        return try {
            onStep("Extracting splits...")
            val cacheDir = File(context.cacheDir, "apks_extract").also {
                it.deleteRecursively()
                it.mkdirs()
            }
            extractApks(context, uri, cacheDir, onStep)
            val apkFiles = cacheDir.listFiles { f -> f.name.endsWith(".apk") }?.toList()
                ?: emptyList()
            if (apkFiles.isEmpty()) return Result.failure(Exception("No APK splits found in archive"))
            onStep("Installing splits...")
            SplitInstaller.installSplits(context, apkFiles, selectedSplits)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun listSplits(context: Context, uri: Uri): List<String> {
        val splits = mutableListOf<String>()
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
        return splits
    }

    private fun extractApks(context: Context, uri: Uri, outDir: File, onStep: (String) -> Unit) {
        val totalSize = FileUtil.getFileSize(context, uri).coerceAtLeast(1L)
        var extractedBytes = 0L
        val stream = FileUtil.openStream(context, uri) ?: return
        ZipInputStream(stream.buffered(FileUtil.BUFFER_SIZE)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".apk")) {
                    val fileName = File(entry.name).name
                    val out = File(outDir, fileName)
                    out.outputStream().buffered(FileUtil.BUFFER_SIZE).use { outStream ->
                        FileUtil.copyWithProgress(zip, outStream, entry.size.coerceAtLeast(1L)) { pct ->
                            onStep("Extracting $fileName… $pct%")
                        }
                    }
                    extractedBytes += out.length()
                    val totalPct = ((extractedBytes * 100) / totalSize).coerceIn(0, 100)
                    onStep("Extracting splits… $totalPct%")
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}
