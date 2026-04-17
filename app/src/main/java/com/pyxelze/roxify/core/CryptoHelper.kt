package com.pyxelze.roxify.core

import java.nio.ByteBuffer
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
    private const val KEY_LENGTH_BIT = 256
    const val DEFAULT_ITERATIONS = 200_000

    val MAGIC_BYTES = byteArrayOf('S'.code.toByte(), 'V'.code.toByte(), '0'.code.toByte(), '1'.code.toByte())

    fun encrypt(payload: ByteArray, password: CharArray, entryCount: Int, iterations: Int = DEFAULT_ITERATIONS, useCompression: Boolean = true): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTE)
        SecureRandom().nextBytes(salt)

        val iv = ByteArray(IV_LENGTH_BYTE)
        SecureRandom().nextBytes(iv)

        val secretKey = deriveKey(password, salt, iterations)
        val cipher = Cipher.getInstance(ALGORITHM)
        val parameterSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)

        val ciphertext = cipher.doFinal(payload)

        val headerSize = 52
        val buffer = ByteBuffer.allocate(headerSize + ciphertext.size)

        val compressionByte = if (useCompression) 0x02.toByte() else 0x00.toByte() // 0x02 = Deflate

        buffer.put(MAGIC_BYTES)
        buffer.put(0x01.toByte())
        buffer.put(0x01.toByte())
        buffer.put(compressionByte)
        buffer.put(0x00.toByte())
        buffer.put(salt)
        buffer.put(iv)
        buffer.putInt(iterations)
        buffer.putInt(entryCount)
        buffer.putLong(ciphertext.size.toLong())

        buffer.put(ciphertext)

        return buffer.array()
    }

    // Pair(decryptedBytes, entryCount, isCompressed)
    fun decrypt(encryptedData: ByteArray, password: CharArray): Triple<ByteArray, Int, Boolean> {
        if (encryptedData.size < 52) {
            throw IllegalArgumentException("Data is too small.")
        }

        val buffer = ByteBuffer.wrap(encryptedData)
        val magic = ByteArray(4)
        buffer.get(magic)

        if (!magic.contentEquals(MAGIC_BYTES)) {
            throw IllegalArgumentException("Invalid magic signature.")
        }

        buffer.get() // version
        buffer.get() // flags
        val compression = buffer.get()
        buffer.get() // reserved

        val isCompressed = compression == 0x02.toByte()

        val salt = ByteArray(SALT_LENGTH_BYTE)
        buffer.get(salt)

        val iv = ByteArray(IV_LENGTH_BYTE)
        buffer.get(iv)

        val iterations = buffer.int
        val entryCount = buffer.int
        val payloadSize = buffer.long

        val ciphertext = ByteArray(payloadSize.toInt())
        buffer.get(ciphertext)

        val secretKey = deriveKey(password, salt, iterations)
        val cipher = Cipher.getInstance(ALGORITHM)
        val parameterSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)

        val plaintext = cipher.doFinal(ciphertext)
        return Triple(plaintext, entryCount, isCompressed)
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, iterations, KEY_LENGTH_BIT)
        val secretKey = factory.generateSecret(spec)
        return SecretKeySpec(secretKey.encoded, "AES")
    }
}
