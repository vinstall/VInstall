package com.vinstall.alwiz.apkv

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object ApkvCrypto {

    const val CIPHER_ALGORITHM  = "AES/CBC/PKCS5Padding"
    const val KDF_ALGORITHM     = "PBKDF2WithHmacSHA256"
    const val KDF_ITERATIONS    = 120_000
    const val KEY_BIT_LENGTH    = 256
    const val SALT_BYTE_LENGTH  = 16
    const val IV_BYTE_LENGTH    = 16

    private const val KEY_ALGORITHM = "AES"

    fun encrypt(plaintext: ByteArray, password: String): ByteArray {
        val salt = randomBytes(SALT_BYTE_LENGTH)
        val iv = randomBytes(IV_BYTE_LENGTH)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        val ciphertext = cipher.doFinal(plaintext)
        return salt + iv + ciphertext
    }

    fun decrypt(blob: ByteArray, password: String): ByteArray {
        require(blob.size > SALT_BYTE_LENGTH + IV_BYTE_LENGTH) { "Blob too short to be valid ciphertext" }
        val salt = blob.copyOfRange(0, SALT_BYTE_LENGTH)
        val iv = blob.copyOfRange(SALT_BYTE_LENGTH, SALT_BYTE_LENGTH + IV_BYTE_LENGTH)
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

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, KDF_ITERATIONS, KEY_BIT_LENGTH)
        val raw = SecretKeyFactory.getInstance(KDF_ALGORITHM).generateSecret(spec).encoded
        return SecretKeySpec(raw, KEY_ALGORITHM)
    }

    private fun randomBytes(length: Int): ByteArray =
        ByteArray(length).also { SecureRandom().nextBytes(it) }
}
