package com.pyxelze.roxify.core

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoHelper {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12
    private const val SALT_LENGTH_BYTE = 16
    private const val ITERATION_COUNT = 100_000
    private const val KEY_LENGTH_BIT = 256

    /**
     * Encrypts plaintext using AES-256-GCM and a key derived from the password.
     * @param plaintext The data to encrypt.
     * @param password The password for key derivation.
     * @return The encrypted byte array (format: salt + iv + ciphertext).
     */
    fun encrypt(plaintext: ByteArray, password: CharArray): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTE)
        SecureRandom().nextBytes(salt)

        val iv = ByteArray(IV_LENGTH_BYTE)
        SecureRandom().nextBytes(iv)

        val secretKey = deriveKey(password, salt)
        val cipher = Cipher.getInstance(ALGORITHM)
        val parameterSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)

        val ciphertext = cipher.doFinal(plaintext)

        // Prepend salt and iv to ciphertext
        val encryptedData = ByteArray(salt.size + iv.size + ciphertext.size)
        System.arraycopy(salt, 0, encryptedData, 0, salt.size)
        System.arraycopy(iv, 0, encryptedData, salt.size, iv.size)
        System.arraycopy(ciphertext, 0, encryptedData, salt.size + iv.size, ciphertext.size)

        return encryptedData
    }

    /**
     * Decrypts an encrypted byte array (salt + iv + ciphertext) using the password.
     * @param encryptedData The encrypted byte array.
     * @param password The password.
     * @return The decrypted plaintext.
     */
    fun decrypt(encryptedData: ByteArray, password: CharArray): ByteArray {
        if (encryptedData.size < SALT_LENGTH_BYTE + IV_LENGTH_BYTE) {
            throw IllegalArgumentException("Invalid encrypted data length")
        }

        val salt = ByteArray(SALT_LENGTH_BYTE)
        System.arraycopy(encryptedData, 0, salt, 0, salt.size)

        val iv = ByteArray(IV_LENGTH_BYTE)
        System.arraycopy(encryptedData, salt.size, iv, 0, iv.size)

        val cipherTextSize = encryptedData.size - salt.size - iv.size
        val ciphertext = ByteArray(cipherTextSize)
        System.arraycopy(encryptedData, salt.size + iv.size, ciphertext, 0, cipherTextSize)

        val secretKey = deriveKey(password, salt)
        val cipher = Cipher.getInstance(ALGORITHM)
        val parameterSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)

        return cipher.doFinal(ciphertext)
    }

    /**
     * Derives a secret key from the password using PBKDF2WithHmacSHA256.
     */
    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, ITERATION_COUNT, KEY_LENGTH_BIT)
        val secretKey = factory.generateSecret(spec)
        return SecretKeySpec(secretKey.encoded, "AES")
    }
}
