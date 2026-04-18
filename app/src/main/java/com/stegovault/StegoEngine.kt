package com.stegovault

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

object StegoEngine {

    const val MAGIC = "ROX1"

    fun buildPayload(
        files: List<File>,
        passphrase: String?,
        isStegoPng: Boolean,
        outputStream: OutputStream
    ) {
        val tempTarFile = File.createTempFile("temp_tar", ".tar")
        val tempCompressedFile = File.createTempFile("temp_zstd", ".zstd")

        try {
            FileOutputStream(tempTarFile).use { tarOut ->
                ArchiveManager.createTarArchive(files, tarOut)
            }

            FileInputStream(tempTarFile).use { tarIn ->
                FileOutputStream(tempCompressedFile).use { zstdOut ->
                    val compressedOut = com.github.luben.zstd.ZstdOutputStream(zstdOut)
                    tarIn.copyTo(compressedOut)
                    compressedOut.close()
                }
            }

            val salt = if (!passphrase.isNullOrEmpty()) CryptoEngine.generateSalt() else ByteArray(16)
            val nonce = if (!passphrase.isNullOrEmpty()) CryptoEngine.generateNonce() else ByteArray(12)

            val headerBuffer = ByteBuffer.allocate(4 + 1 + 1 + 16 + 12 + 8)
            headerBuffer.order(ByteOrder.LITTLE_ENDIAN)

            headerBuffer.put(MAGIC.toByteArray(StandardCharsets.US_ASCII))
            headerBuffer.put(0x01.toByte()) // version

            var flags: Byte = 0
            if (!passphrase.isNullOrEmpty()) flags = (flags.toInt() or 0x01).toByte()
            // Using compact chunk mode instead of LSB for safety with large payloads
            headerBuffer.put(flags)

            headerBuffer.put(salt)
            headerBuffer.put(nonce)
            headerBuffer.putLong(tempCompressedFile.length())

            outputStream.write(headerBuffer.array())

            FileInputStream(tempCompressedFile).use { zstdIn ->
                if (!passphrase.isNullOrEmpty()) {
                    val key = CryptoEngine.deriveKey(passphrase, salt)
                    val secretKey = javax.crypto.spec.SecretKeySpec(key, "AES")
                    val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
                    val spec = javax.crypto.spec.GCMParameterSpec(128, nonce)
                    cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey, spec)

                    javax.crypto.CipherOutputStream(outputStream, cipher).use { cipherOut ->
                        zstdIn.copyTo(cipherOut)
                    }
                } else {
                    zstdIn.copyTo(outputStream)
                }
            }

            // Ensure streams are fully written
            outputStream.flush()
        } finally {
            tempTarFile.delete()
            tempCompressedFile.delete()
        }
    }

    fun extractPayload(
        inputStream: InputStream,
        passphrase: String?,
        outputDir: File
    ) {
        val magicBytes = ByteArray(4)
        var readCount = inputStream.read(magicBytes)
        if (readCount < 4 || String(magicBytes, StandardCharsets.US_ASCII) != MAGIC) throw Exception("Invalid magic, not a Roxify/StegoVault file.")

        val version = inputStream.read().toByte()
        val flags = inputStream.read().toByte()

        val salt = ByteArray(16)
        inputStream.read(salt)

        val nonce = ByteArray(12)
        inputStream.read(nonce)

        val sizeBuffer = ByteArray(8)
        inputStream.read(sizeBuffer)
        val compressedSize = ByteBuffer.wrap(sizeBuffer).order(ByteOrder.LITTLE_ENDIAN).long

        val isEncrypted = (flags.toInt() and 0x01) != 0

        val tempDecryptedFile = File.createTempFile("temp_decrypted", ".zstd")

        try {
            FileOutputStream(tempDecryptedFile).use { decOut ->
                if (isEncrypted) {
                    if (passphrase.isNullOrEmpty()) throw Exception("Passphrase required")
                    val key = CryptoEngine.deriveKey(passphrase, salt)
                    val secretKey = javax.crypto.spec.SecretKeySpec(key, "AES")
                    val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
                    val spec = javax.crypto.spec.GCMParameterSpec(128, nonce)
                    cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey, spec)

                    javax.crypto.CipherInputStream(inputStream, cipher).use { cipherIn ->
                        cipherIn.copyTo(decOut)
                    }
                } else {
                    inputStream.copyTo(decOut)
                }
            }

            FileInputStream(tempDecryptedFile).use { decIn ->
                val zstdIn = com.github.luben.zstd.ZstdInputStream(decIn)
                ArchiveManager.extractTarArchive(zstdIn, outputDir)
                zstdIn.close()
            }
        } finally {
            tempDecryptedFile.delete()
        }
    }
}
