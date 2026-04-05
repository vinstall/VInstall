package com.vinstall.alwiz.apkv

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object ApkvCrypto {

    const val CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding"
    const val KDF_ALGORITHM    = "PBKDF2WithHmacSHA256"
    const val KDF_ITERATIONS   = 120_000
    const val KEY_BIT_LENGTH   = 256
    const val SALT_BYTE_LENGTH = 16
    const val IV_BYTE_LENGTH   = 16

    private const val KEY_ALGORITHM  = "AES"
    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val HASH_LEN       = 32

    fun encrypt(plaintext: ByteArray, password: String): ByteArray {
        val salt = randomBytes(SALT_BYTE_LENGTH)
        val iv   = randomBytes(IV_BYTE_LENGTH)
        val key  = deriveKey(password, salt)
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        val ciphertext = cipher.doFinal(plaintext)
        return salt + iv + ciphertext
    }

    fun decrypt(blob: ByteArray, password: String): ByteArray {
        require(blob.size > SALT_BYTE_LENGTH + IV_BYTE_LENGTH) {
            "Blob too short to be valid ciphertext"
        }
        val salt       = blob.copyOfRange(0, SALT_BYTE_LENGTH)
        val iv         = blob.copyOfRange(SALT_BYTE_LENGTH, SALT_BYTE_LENGTH + IV_BYTE_LENGTH)
        val ciphertext = blob.copyOfRange(SALT_BYTE_LENGTH + IV_BYTE_LENGTH, blob.size)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
        return cipher.doFinal(ciphertext)
    }

    fun tryDecrypt(blob: ByteArray, password: String): ByteArray? {
        return try {
            decrypt(blob, password)
        } catch (_: Exception) {
            null
        }
    }

    fun deriveKeyBytes(password: String, salt: ByteArray): ByteArray =
        pbkdf2(password.toByteArray(Charsets.UTF_8), salt, KDF_ITERATIONS, KEY_BIT_LENGTH / 8)

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec =
        SecretKeySpec(deriveKeyBytes(password, salt), KEY_ALGORITHM)

    private fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int, keyLen: Int): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM).apply {
            init(SecretKeySpec(password, HMAC_ALGORITHM))
        }
        val blockCount = (keyLen + HASH_LEN - 1) / HASH_LEN
        val dk = ByteArray(keyLen)
        var dkOffset = 0
        for (blockIndex in 1..blockCount) {
            mac.reset()
            mac.update(salt)
            mac.update(byteArrayOf(
                (blockIndex ushr 24).toByte(),
                (blockIndex ushr 16).toByte(),
                (blockIndex ushr  8).toByte(),
                (blockIndex        ).toByte()
            ))
            val t = mac.doFinal()
            val u = t.copyOf()
            repeat(iterations - 1) {
                mac.reset()
                mac.update(u)
                val next = mac.doFinal()
                next.copyInto(u)
                for (i in t.indices) t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
            }
            val copyLen = minOf(HASH_LEN, keyLen - dkOffset)
            t.copyInto(dk, dkOffset, 0, copyLen)
            dkOffset += copyLen
        }
        return dk
    }

    private fun randomBytes(length: Int): ByteArray =
        ByteArray(length).also { SecureRandom().nextBytes(it) }
}
