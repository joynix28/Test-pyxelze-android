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

object CryptoEngine {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128 // in bits
    private const val KEY_LENGTH = 256 // in bits
    private const val ITERATIONS = 200_000

    fun generateSalt(): ByteArray {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return salt
    }

    fun generateNonce(): ByteArray {
        val nonce = ByteArray(12)
        SecureRandom().nextBytes(nonce)
        return nonce
    }

    fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    fun encrypt(
        plaintext: ByteArray,
        passphrase: String,
        salt: ByteArray,
        nonce: ByteArray,
        iterations: Int = ITERATIONS
    ): ByteArray {
        val keyBytes = deriveKey(passphrase, salt, iterations)
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance(ALGORITHM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        return cipher.doFinal(plaintext)
    }

    fun encryptStream(
        inStream: InputStream,
        outStream: OutputStream,
        passphrase: String,
        salt: ByteArray,
        nonce: ByteArray,
        iterations: Int = ITERATIONS
    ) {
        val keyBytes = deriveKey(passphrase, salt, iterations)
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance(ALGORITHM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        val cipherOut = CipherOutputStream(outStream, cipher)
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (inStream.read(buffer).also { bytesRead = it } != -1) {
            cipherOut.write(buffer, 0, bytesRead)
        }
        cipherOut.close()
    }

    fun decrypt(
        ciphertextAndTag: ByteArray,
        passphrase: String,
        salt: ByteArray,
        nonce: ByteArray,
        iterations: Int = ITERATIONS
    ): ByteArray {
        val keyBytes = deriveKey(passphrase, salt, iterations)
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance(ALGORITHM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, nonce)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

        return cipher.doFinal(ciphertextAndTag)
    }

    fun decryptStream(
        inStream: InputStream,
        outStream: OutputStream,
        passphrase: String,
        salt: ByteArray,
        nonce: ByteArray,
        iterations: Int = ITERATIONS
    ) {
        val keyBytes = deriveKey(passphrase, salt, iterations)
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance(ALGORITHM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, nonce)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

        val cipherIn = CipherInputStream(inStream, cipher)
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (cipherIn.read(buffer).also { bytesRead = it } != -1) {
            outStream.write(buffer, 0, bytesRead)
        }
        cipherIn.close()
    }
}