package com.vinstall.alwiz.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

object FileUtil {

    const val BUFFER_SIZE = 256 * 1024

    fun getFileName(context: Context, uri: Uri): String {
        var name = "unknown"
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx != -1) name = it.getString(idx)
            }
        }
        if (name == "unknown") {
            name = uri.lastPathSegment ?: "unknown"
        }
        return name
    }

    fun getFileSize(context: Context, uri: Uri): Long {
        var size = 0L
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(OpenableColumns.SIZE)
                if (idx != -1) size = it.getLong(idx)
            }
        }
        return size
    }

    fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824L -> "%.2f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576L -> "%.2f MB".format(bytes / 1_048_576.0)
            bytes >= 1_024L -> "%.2f KB".format(bytes / 1_024.0)
            else -> "$bytes B"
        }
    }

    fun openStream(context: Context, uri: Uri): InputStream? =
        context.contentResolver.openInputStream(uri)

    fun extractToCache(context: Context, uri: Uri, targetName: String): File {
        val target = File(context.cacheDir, targetName)
        openStream(context, uri)?.use { input ->
            target.outputStream().buffered(BUFFER_SIZE).use { output ->
                input.copyTo(output, BUFFER_SIZE)
            }
        }
        return target
    }

    fun copyWithProgress(
        input: InputStream,
        output: OutputStream,
        totalBytes: Long,
        onProgress: (percent: Int) -> Unit
    ) {
        val buf = ByteArray(BUFFER_SIZE)
        var bytesCopied = 0L
        var lastReported = -1
        var read: Int
        while (input.read(buf).also { read = it } != -1) {
            output.write(buf, 0, read)
            bytesCopied += read
            if (totalBytes > 0) {
                val percent = ((bytesCopied * 100) / totalBytes).toInt().coerceIn(0, 100)
                if (percent != lastReported) {
                    lastReported = percent
                    onProgress(percent)
                }
            }
        }
    }

    fun computeHash(context: Context, uri: Uri, algorithm: String = "SHA-256"): String {
        return try {
            val digest = MessageDigest.getInstance(algorithm)
            openStream(context, uri)?.use { input ->
                val buf = ByteArray(BUFFER_SIZE)
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    digest.update(buf, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    fun clearCache(context: Context) {
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }
}
