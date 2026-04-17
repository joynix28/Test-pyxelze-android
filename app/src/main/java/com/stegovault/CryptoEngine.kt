package com.stegovault

import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Handles AES-256-GCM encryption and PBKDF2-HMAC-SHA256 key derivation via streaming.
 * Made by JoyniX.
 */
object CryptoEngine {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128 // 16 bytes
    private const val KEY_LENGTH = 256 // 32 bytes

    fun generateSalt(): ByteArray = ByteArray(16).apply { SecureRandom().nextBytes(this) }
    fun generateNonce(): ByteArray = ByteArray(12).apply { SecureRandom().nextBytes(this) }

    fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    fun encryptStream(
        inStream: InputStream,
        outStream: OutputStream,
        passphrase: String,
        salt: ByteArray,
        nonce: ByteArray,
        iterations: Int
    ) {
        val keyBytes = deriveKey(passphrase, salt, iterations)
        val cipher = Cipher.getInstance(ALGORITHM).apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, nonce))
        }

        CipherOutputStream(outStream, cipher).use { cipherOut ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inStream.read(buffer).also { bytesRead = it } != -1) {
                cipherOut.write(buffer, 0, bytesRead)
            }
        }
    }

    fun decryptStream(
        inStream: InputStream,
        outStream: OutputStream,
        passphrase: String,
        salt: ByteArray,
        nonce: ByteArray,
        iterations: Int
    ) {
        val keyBytes = deriveKey(passphrase, salt, iterations)
        val cipher = Cipher.getInstance(ALGORITHM).apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, nonce))
        }

        CipherInputStream(inStream, cipher).use { cipherIn ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (cipherIn.read(buffer).also { bytesRead = it } != -1) {
                outStream.write(buffer, 0, bytesRead)
            }
        }
    }
}