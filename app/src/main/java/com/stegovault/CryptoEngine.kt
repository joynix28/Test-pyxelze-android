package com.stegovault

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoEngine {
    private const val ALGORITHM = "AES"
    private const val CIPHER_MODE = "AES/GCM/NoPadding"
    private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16
    private const val NONCE_LENGTH = 12
    private const val TAG_LENGTH = 128

    fun generateSalt(): ByteArray = ByteArray(SALT_LENGTH).apply { SecureRandom().nextBytes(this) }
    fun generateNonce(): ByteArray = ByteArray(NONCE_LENGTH).apply { SecureRandom().nextBytes(this) }

    fun deriveKey(passphrase: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
        return factory.generateSecret(spec).encoded
    }

    fun encrypt(plaintext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        val secretKey = SecretKeySpec(key, ALGORITHM)
        val cipher = Cipher.getInstance(CIPHER_MODE)
        val spec = GCMParameterSpec(TAG_LENGTH, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        return cipher.doFinal(plaintext)
    }

    fun decrypt(ciphertext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        val secretKey = SecretKeySpec(key, ALGORITHM)
        val cipher = Cipher.getInstance(CIPHER_MODE)
        val spec = GCMParameterSpec(TAG_LENGTH, nonce)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(ciphertext)
    }
}
