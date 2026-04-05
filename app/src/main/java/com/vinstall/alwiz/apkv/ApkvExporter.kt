package com.vinstall.alwiz.apkv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import com.vinstall.alwiz.apkv.ApkvCrypto
import com.vinstall.alwiz.apkv.ApkvHeader
import com.vinstall.alwiz.apkv.ApkvManifest
import com.vinstall.alwiz.model.AppInfo
import com.vinstall.alwiz.util.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ApkvExporter {

    private const val TAG = "ApkvExporter"

    private const val ENTRY_ENCRYPTED_MARKER = ".apkv_enc"
    private const val ENTRY_HEADER = "header.json"
    private const val ENTRY_MANIFEST_PLAIN = "manifest.json"
    private const val ENTRY_MANIFEST_ENC = "manifest.enc"
    private const val ENTRY_PAYLOAD_ENC = "payload.enc"
    private const val ENTRY_ICON_PLAIN = "icon.webp"
    private const val ENTRY_ICON_ENC = "icon.enc"

    private const val ICON_SIZE_PX = 192
    private const val ICON_QUALITY = 80

    private const val KEY_ALGORITHM = "AES"
    private const val STREAM_BUFFER_SIZE = 65_536

    suspend fun export(
        context: Context,
        appInfo: AppInfo,
        outputDir: File,
        password: String? = null,
        onStep: (String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            outputDir.mkdirs()
            val safeName = appInfo.packageName.replace(Regex("[^a-zA-Z0-9._]"), "_")
            val safeVersion = appInfo.versionName.replace(Regex("[^a-zA-Z0-9._]"), "_").take(32)
            val outFile = File(outputDir, "${safeName}_${safeVersion}.apkv")

            onStep("Collecting APK files...")
            DebugLog.d(TAG, "Export start: ${appInfo.packageName} encrypted=${password != null}")

            val apkFiles = collectApkFiles(appInfo)
            if (apkFiles.isEmpty()) {
                return@withContext Result.failure(Exception("No APK files found for ${appInfo.packageName}"))
            }

            onStep("Exporting icon...")
            val iconBytes = appInfo.icon?.let { drawableToWebP(it) }
            val hasIcon = iconBytes != null

            val splitNames = apkFiles.map { it.name }
            val encrypted = password != null

            onStep("Computing checksums...")
            val checksums = computeChecksums(apkFiles)
            val totalSize = apkFiles.sumOf { it.length() }

            val exportedAt = System.currentTimeMillis()

            val manifest = ApkvManifest(
                packageName = appInfo.packageName,
                versionName = appInfo.versionName,
                versionCode = appInfo.versionCode,
                label = appInfo.label,
                isSplit = appInfo.isSplitApp,
                splits = splitNames,
                encrypted = encrypted,
                hasIcon = hasIcon,
                exportedAt = exportedAt,
                minSdkVersion = appInfo.minSdk,
                targetSdkVersion = appInfo.targetSdk,
                checksums = checksums,
                totalSize = totalSize,
                permissions = appInfo.requestedPermissions.takeIf { it.isNotEmpty() }
            )

            if (encrypted) {
                onStep("Encrypting package...")
                writeEncrypted(apkFiles, manifest, appInfo, outFile, password!!, iconBytes, exportedAt, onStep)
            } else {
                onStep("Archiving package...")
                writePlain(apkFiles, manifest, outFile, iconBytes, onStep)
            }

            DebugLog.i(TAG, "Export complete: ${outFile.absolutePath}")
            Result.success(outFile)
        } catch (e: Exception) {
            DebugLog.e(TAG, "Export failed: ${e.message}")
            Result.failure(e)
        }
    }

    private fun collectApkFiles(appInfo: AppInfo): List<File> {
        val files = mutableListOf<File>()
        File(appInfo.sourceDir).takeIf { it.exists() }?.let { files.add(it) }
        appInfo.splitSourceDirs?.forEach { path ->
            File(path).takeIf { it.exists() }?.let { files.add(it) }
        }
        return files
    }

    private fun computeChecksums(apkFiles: List<File>): Map<String, String> {
        return apkFiles.associate { file ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(STREAM_BUFFER_SIZE)
            FileInputStream(file).buffered(STREAM_BUFFER_SIZE).use { fis ->
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            val hex = digest.digest().joinToString("") { "%02x".format(it) }
            file.name to "sha256:$hex"
        }
    }

    private fun drawableToWebP(drawable: Drawable): ByteArray? {
        return try {
            val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
                drawable.bitmap
            } else {
                val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: ICON_SIZE_PX
                val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: ICON_SIZE_PX
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            }

            val scaled = if (bitmap.width != ICON_SIZE_PX || bitmap.height != ICON_SIZE_PX) {
                Bitmap.createScaledBitmap(bitmap, ICON_SIZE_PX, ICON_SIZE_PX, true)
            } else {
                bitmap
            }

            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }

            val out = ByteArrayOutputStream()
            scaled.compress(format, ICON_QUALITY, out)
            out.toByteArray()
        } catch (e: Exception) {
            DebugLog.e(TAG, "Icon export failed: ${e.message}")
            null
        }
    }

    private fun writePlain(
        apkFiles: List<File>,
        manifest: ApkvManifest,
        outFile: File,
        iconBytes: ByteArray?,
        onStep: (String) -> Unit
    ) {
        ZipOutputStream(outFile.outputStream().buffered()).use { zos ->
            zos.putNextEntry(ZipEntry(ENTRY_MANIFEST_PLAIN))
            zos.write(manifest.toJson().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            if (iconBytes != null) {
                zos.putNextEntry(ZipEntry(ENTRY_ICON_PLAIN))
                zos.write(iconBytes)
                zos.closeEntry()
            }

            for (apk in apkFiles) {
                onStep("Archiving ${apk.name}...")
                zos.putNextEntry(ZipEntry(apk.name))
                FileInputStream(apk).use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    private fun writeEncrypted(
        apkFiles: List<File>,
        manifest: ApkvManifest,
        appInfo: AppInfo,
        outFile: File,
        password: String,
        iconBytes: ByteArray?,
        exportedAt: Long,
        onStep: (String) -> Unit
    ) {
        val header = ApkvHeader(
            packageName = appInfo.packageName,
            versionName = appInfo.versionName,
            label = appInfo.label,
            labels = manifest.labels,
            encrypted = true,
            hasIcon = iconBytes != null,
            exportedAt = exportedAt
        )

        onStep("Encrypting manifest...")
        val encryptedManifest = ApkvCrypto.encrypt(
            manifest.toJson().toByteArray(Charsets.UTF_8),
            password
        )

        val encryptedIcon = iconBytes?.let {
            onStep("Encrypting icon...")
            ApkvCrypto.encrypt(it, password)
        }

        val tempPayload = File(outFile.parent, "${outFile.name}.tmp")
        try {
            onStep("Building payload archive...")
            buildPayloadZipToFile(apkFiles, tempPayload, onStep)

            ZipOutputStream(outFile.outputStream().buffered()).use { zos ->
                zos.putNextEntry(ZipEntry(ENTRY_ENCRYPTED_MARKER))
                zos.write(ByteArray(0))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry(ENTRY_HEADER))
                zos.write(header.toJson().toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry(ENTRY_MANIFEST_ENC))
                zos.write(encryptedManifest)
                zos.closeEntry()

                if (encryptedIcon != null) {
                    zos.putNextEntry(ZipEntry(ENTRY_ICON_ENC))
                    zos.write(encryptedIcon)
                    zos.closeEntry()
                }

                onStep("Encrypting payload...")
                zos.putNextEntry(ZipEntry(ENTRY_PAYLOAD_ENC))
                streamEncryptFileTo(tempPayload, zos, password)
                zos.closeEntry()
            }
        } finally {
            tempPayload.delete()
        }
    }

    private fun buildPayloadZipToFile(
        apkFiles: List<File>,
        destFile: File,
        onStep: (String) -> Unit
    ) {
        ZipOutputStream(destFile.outputStream().buffered(STREAM_BUFFER_SIZE)).use { zos ->
            for (apk in apkFiles) {
                onStep("Packing ${apk.name}...")
                zos.putNextEntry(ZipEntry(apk.name))
                FileInputStream(apk).use { it.copyTo(zos, STREAM_BUFFER_SIZE) }
                zos.closeEntry()
            }
        }
    }

    private fun streamEncryptFileTo(inputFile: File, out: OutputStream, password: String) {
        val salt = ByteArray(ApkvCrypto.SALT_BYTE_LENGTH).also { SecureRandom().nextBytes(it) }
        val iv   = ByteArray(ApkvCrypto.IV_BYTE_LENGTH).also   { SecureRandom().nextBytes(it) }

        val raw = ApkvCrypto.deriveKeyBytes(password, salt)
        val key  = SecretKeySpec(raw, KEY_ALGORITHM)

        val cipher = Cipher.getInstance(ApkvCrypto.CIPHER_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))

        out.write(salt)
        out.write(iv)

        FileInputStream(inputFile).use { fis ->
            val buffer = ByteArray(STREAM_BUFFER_SIZE)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                val chunk = cipher.update(buffer, 0, read)
                if (chunk != null) out.write(chunk)
            }
            out.write(cipher.doFinal())
        }
    }
}
