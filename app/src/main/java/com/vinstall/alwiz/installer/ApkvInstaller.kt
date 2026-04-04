package com.vinstall.alwiz.installer

import android.content.Context
import android.net.Uri
import com.vinstall.alwiz.apkv.ApkvCrypto
import com.vinstall.alwiz.apkv.ApkvHeader
import com.vinstall.alwiz.apkv.ApkvManifest
import com.vinstall.alwiz.util.FileUtil
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest
import java.util.zip.ZipInputStream

object ApkvInstaller {

    private const val ENTRY_ENCRYPTED_MARKER = ".apkv_enc"
    private const val ENTRY_HEADER = "header.json"
    private const val ENTRY_MANIFEST_PLAIN = "manifest.json"
    private const val ENTRY_MANIFEST_ENC = "manifest.enc"
    private const val ENTRY_PAYLOAD_ENC = "payload.enc"
    private const val ENTRY_ICON_PLAIN = "icon.webp"
    private const val ENTRY_ICON_ENC = "icon.enc"

    const val ERROR_PASSWORD_REQUIRED = "PASSWORD_REQUIRED"
    const val ERROR_WRONG_PASSWORD = "WRONG_PASSWORD"
    const val ERROR_EXPORT_BLOCKED = "EXPORT_BLOCKED"

    fun isEncrypted(context: Context, uri: Uri): Boolean {
        val stream = FileUtil.openStream(context, uri) ?: return false
        return ZipInputStream(stream.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == ENTRY_ENCRYPTED_MARKER) return@use true
                zip.closeEntry()
                entry = zip.nextEntry
            }
            false
        }
    }

    fun readHeader(context: Context, uri: Uri): ApkvHeader? {
        val stream = FileUtil.openStream(context, uri) ?: return null
        return ZipInputStream(stream.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == ENTRY_HEADER) {
                    val json = zip.readBytes().toString(Charsets.UTF_8)
                    return@use ApkvHeader.fromJson(json)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
            null
        }
    }

    fun readManifest(context: Context, uri: Uri, password: String? = null): ApkvManifest? {
        val stream = FileUtil.openStream(context, uri) ?: return null
        return ZipInputStream(stream.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when (entry.name) {
                    ENTRY_MANIFEST_PLAIN -> {
                        val json = zip.readBytes().toString(Charsets.UTF_8)
                        return@use ApkvManifest.fromJson(json)
                    }
                    ENTRY_MANIFEST_ENC -> {
                        if (password == null) return@use null
                        val blob = zip.readBytes()
                        val decrypted = ApkvCrypto.tryDecrypt(blob, password) ?: return@use null
                        return try {
                            ApkvManifest.fromJson(decrypted.toString(Charsets.UTF_8))
                        } finally {
                            decrypted.fill(0)
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
            null
        }
    }

    fun readIcon(context: Context, uri: Uri, password: String? = null): ByteArray? {
        val stream = FileUtil.openStream(context, uri) ?: return null
        return ZipInputStream(stream.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when (entry.name) {
                    ENTRY_ICON_PLAIN -> {
                        return@use zip.readBytes()
                    }
                    ENTRY_ICON_ENC -> {
                        if (password == null) return@use null
                        val blob = zip.readBytes()
                        return@use ApkvCrypto.tryDecrypt(blob, password)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
            null
        }
    }

    fun verifyPassword(context: Context, uri: Uri, password: String): Boolean {
        return readManifest(context, uri, password) != null
    }

    fun listSplits(context: Context, uri: Uri, password: String? = null): List<String> {
        return readManifest(context, uri, password)?.splits ?: emptyList()
    }

    suspend fun install(
        context: Context,
        uri: Uri,
        password: String? = null,
        onStep: (String) -> Unit,
        selectedSplits: List<String>? = null,
        onChecksumMismatch: (suspend (mismatches: List<String>) -> Boolean)? = null
    ): Result<Unit> {
        return try {
            val encrypted = isEncrypted(context, uri)

            if (encrypted && password == null) {
                return Result.failure(Exception(ERROR_PASSWORD_REQUIRED))
            }

            onStep("Preparing extraction...")
            val cacheDir = File(context.cacheDir, "apkv_extract").also {
                it.deleteRecursively()
                it.mkdirs()
            }

            if (encrypted) {
                val success = extractEncrypted(context, uri, password!!, cacheDir, onStep)
                if (!success) return Result.failure(Exception(ERROR_WRONG_PASSWORD))
            } else {
                extractPlain(context, uri, cacheDir, onStep)
            }

            val apkFiles = cacheDir.listFiles { f -> f.name.endsWith(".apk") }?.toList()
                ?: emptyList()

            if (apkFiles.isEmpty()) {
                return Result.failure(Exception("No APK splits found in archive"))
            }

            val manifest = readManifest(context, uri, password)
            val checksums = manifest?.checksums
            if (!checksums.isNullOrEmpty()) {
                onStep("Verifying checksums...")
                val mismatches = verifyChecksums(apkFiles, checksums)
                if (mismatches.isNotEmpty()) {
                    val shouldContinue = onChecksumMismatch?.invoke(mismatches) ?: false
                    if (!shouldContinue) {
                        return Result.failure(Exception("Installation cancelled: checksum mismatch"))
                    }
                }
            }

            onStep("Installing splits...")
            SplitInstaller.installSplits(context, apkFiles, selectedSplits)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun verifyChecksums(apkFiles: List<File>, checksums: Map<String, String>): List<String> {
        val mismatches = mutableListOf<String>()
        for (file in apkFiles) {
            val declared = checksums[file.name] ?: continue
            if (!declared.startsWith("sha256:")) continue
            val actual = MessageDigest.getInstance("SHA-256").let { md ->
                FileInputStream(file).use { fis ->
                    val buf = ByteArray(65_536)
                    var read: Int
                    while (fis.read(buf).also { read = it } != -1) md.update(buf, 0, read)
                }
                md.digest().joinToString("") { "%02x".format(it) }
            }
            if (actual != declared.substring(7)) {
                mismatches.add(file.name)
            }
        }
        return mismatches
    }

    private fun extractPlain(context: Context, uri: Uri, outDir: File, onStep: (String) -> Unit) {
        val stream = FileUtil.openStream(context, uri) ?: return
        ZipInputStream(stream.buffered(FileUtil.BUFFER_SIZE)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".apk")) {
                    val name = File(entry.name).name
                    onStep("Extracting $name...")
                    File(outDir, name).outputStream().buffered().use { zip.copyTo(it) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun extractEncrypted(
        context: Context,
        uri: Uri,
        password: String,
        outDir: File,
        onStep: (String) -> Unit
    ): Boolean {
        val tempEncrypted = File(outDir, "payload_enc.tmp")
        val tempDecrypted = File(outDir, "payload_dec.tmp")
        try {
            val written = FileUtil.openStream(context, uri)?.use { raw ->
                ZipInputStream(raw.buffered(FileUtil.BUFFER_SIZE)).use { zip ->
                    var entry = zip.nextEntry
                    var found = false
                    while (entry != null) {
                        if (entry.name == ENTRY_PAYLOAD_ENC) {
                            FileOutputStream(tempEncrypted).buffered(FileUtil.BUFFER_SIZE).use { out ->
                                zip.copyTo(out, FileUtil.BUFFER_SIZE)
                            }
                            found = true
                            break
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                    found
                }
            } ?: false

            if (!written) return false

            onStep("Decrypting payload...")
            if (!streamDecryptFile(tempEncrypted, tempDecrypted, password)) return false

            ZipInputStream(FileInputStream(tempDecrypted).buffered(FileUtil.BUFFER_SIZE)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.lowercase().endsWith(".apk")) {
                        val name = File(entry.name).name
                        onStep("Extracting $name...")
                        File(outDir, name).outputStream().buffered(FileUtil.BUFFER_SIZE).use { out ->
                            zip.copyTo(out, FileUtil.BUFFER_SIZE)
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            return true
        } finally {
            tempEncrypted.delete()
            tempDecrypted.delete()
        }
    }

    private fun streamDecryptFile(input: File, output: File, password: String): Boolean {
        return try {
            FileInputStream(input).use { fis ->
                val dis = DataInputStream(fis)
                val salt = ByteArray(ApkvCrypto.SALT_BYTE_LENGTH).also { dis.readFully(it) }
                val iv   = ByteArray(ApkvCrypto.IV_BYTE_LENGTH).also   { dis.readFully(it) }

                val keySpec = PBEKeySpec(password.toCharArray(), salt, ApkvCrypto.KDF_ITERATIONS, ApkvCrypto.KEY_BIT_LENGTH)
                val rawKey  = SecretKeyFactory.getInstance(ApkvCrypto.KDF_ALGORITHM).generateSecret(keySpec).encoded
                val key     = SecretKeySpec(rawKey, "AES")

                val cipher = Cipher.getInstance(ApkvCrypto.CIPHER_ALGORITHM)
                cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))

                CipherInputStream(fis, cipher).use { cis ->
                    FileOutputStream(output).buffered(FileUtil.BUFFER_SIZE).use { out ->
                        cis.copyTo(out, FileUtil.BUFFER_SIZE)
                    }
                }
            }

            val magic = ByteArray(4)
            FileInputStream(output).use { fis ->
                fis.read(magic)
            }
            val isValidZip = magic.size == 4
                && magic[0] == 0x50.toByte()
                && magic[1] == 0x4B.toByte()
                && magic[2] == 0x03.toByte()
                && magic[3] == 0x04.toByte()
            if (!isValidZip) {
                com.vinstall.alwiz.util.DebugLog.e("ApkvInstaller", "streamDecryptFile: ZIP magic check failed — wrong password or corrupt payload")
                return false
            }

            true
        } catch (e: Exception) {
            com.vinstall.alwiz.util.DebugLog.e("ApkvInstaller", "streamDecryptFile failed: ${e.message}")
            false
        }
    }
}
