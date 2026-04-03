package com.vinstall.alwiz.util

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

object HashUtil {

    fun computeAll(file: File): Map<String, String> = mapOf(
        "MD5"    to compute(file, "MD5"),
        "SHA-1"  to compute(file, "SHA-1"),
        "SHA-256" to compute(file, "SHA-256")
    )

    private fun compute(file: File, algorithm: String): String = try {
        file.inputStream().buffered(FileUtil.BUFFER_SIZE).use { compute(it, algorithm) }
    } catch (e: Exception) {
        "Error: ${e.message}"
    }

    private fun compute(stream: InputStream, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm)
        val buffer = ByteArray(FileUtil.BUFFER_SIZE)
        var read: Int
        while (stream.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
