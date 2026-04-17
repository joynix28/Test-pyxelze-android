package com.stegovault

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Concrete implementation of the strictly bounded StegoEngine.
 * Enforces compression-first rules and safeguards against memory bounds and file size explosions.
 * Made by JoyniX.
 */
class DefaultStegoEngine(private val context: Context) : StegoEngine {

    override suspend fun encode(
        inStream: InputStream,
        inputSize: Long,
        outStream: OutputStream,
        options: StegoEngine.EncodeOptions
    ): StegoEngine.StegoResult = withContext(Dispatchers.IO) {
        val warnings = mutableListOf<String>()
        val finalMode = determineMode(inputSize, options.mode)

        if (finalMode != options.mode) {
            warnings.add("Requested mode ignored to prevent invalid or excessively large PNG. Used: $finalMode")
        }

        val tempCompressedFile = File.createTempFile("stego_compress", ".tmp", context.cacheDir)
        val tempEncryptedFile = File.createTempFile("stego_encrypt", ".tmp", context.cacheDir)
        val tempPayloadFile = File.createTempFile("stego_payload", ".tmp", context.cacheDir)

        try {
            // 1. Strict Compression First
            options.onProgress?.invoke("Compressing Archive", 10)
            FileOutputStream(tempCompressedFile).use { fos ->
                Compressor.compressStream(inStream, fos, options.compression, options.compressionLevel)
            }

            // 2. Encryption
            val isEncrypted = !options.passphrase.isNullOrEmpty()
            val salt = CryptoEngine.generateSalt()
            val nonce = CryptoEngine.generateNonce()
            val iterations = if (isEncrypted) getAdaptiveIterations(options.targetDeviceClass) else 0

            options.onProgress?.invoke("Encrypting Stream", 30)
            FileOutputStream(tempEncryptedFile).use { fos ->
                if (isEncrypted) {
                    FileInputStream(tempCompressedFile).use { fis ->
                        CryptoEngine.encryptStream(fis, fos, options.passphrase!!, salt, nonce, iterations)
                    }
                } else {
                    Compressor.compressStream(FileInputStream(tempCompressedFile), fos, Compressor.Algorithm.NONE, 0)
                }
            }

            // 3. Header Synthesis
            val header = StegoPng.Header(
                isEncrypted = isEncrypted,
                compression = options.compression,
                salt = salt,
                nonce = nonce,
                iterations = iterations,
                entryCount = options.entryCount,
                payloadSize = tempEncryptedFile.length()
            )

            // 4. Assemble Payload
            options.onProgress?.invoke("Packing Final Chunk", 60)
            FileOutputStream(tempPayloadFile).use { fos ->
                header.writeTo(fos)
                FileInputStream(tempEncryptedFile).use { fis ->
                    Compressor.compressStream(fis, fos, Compressor.Algorithm.NONE, 0)
                }
            }

            // 5. Final PNG Embed
            options.onProgress?.invoke("Generating Stego Image", 80)
            if (finalMode == StegoEngine.EncodeMode.PIXEL) {
                FileInputStream(tempPayloadFile).use { fis ->
                    StegoPng.encodePixels(fis, outStream, tempPayloadFile.length())
                }
            } else {
                val coverBytes = StegoPng.generateCoverImage(context)
                FileInputStream(tempPayloadFile).use { fis ->
                    StegoPng.embedIntoPngStream(java.io.ByteArrayInputStream(coverBytes), fis, tempPayloadFile.length(), outStream)
                }
            }

            options.onProgress?.invoke("Done", 100)
            StegoEngine.StegoResult(finalMode, warnings, header)
        } finally {
            tempCompressedFile.delete()
            tempEncryptedFile.delete()
            tempPayloadFile.delete()
        }
    }

    override suspend fun decode(
        inStream: InputStream,
        outStream: OutputStream,
        options: StegoEngine.DecodeOptions
    ): StegoEngine.DecodeResult = withContext(Dispatchers.IO) {
        val warnings = mutableListOf<String>()

        val tempInputFile = File.createTempFile("stego_input", ".tmp", context.cacheDir)
        val tempPayloadFile = File.createTempFile("stego_decode_payload", ".tmp", context.cacheDir)
        val tempDecryptedFile = File.createTempFile("stego_decode_decrypted", ".tmp", context.cacheDir)

        try {
            options.onProgress?.invoke("Reading Image", 10, 100)
            FileOutputStream(tempInputFile).use { fos ->
                Compressor.compressStream(inStream, fos, Compressor.Algorithm.NONE, 0)
            }

            options.onProgress?.invoke("Extracting Payload", 30, 100)
            // Try extracting chunk mode
            var extracted = FileInputStream(tempInputFile).use { fis ->
                FileOutputStream(tempPayloadFile).use { fos ->
                    StegoPng.extractFromPngStream(fis, fos)
                }
            }

            if (!extracted) {
                // Try pixel LSB mode
                FileInputStream(tempInputFile).use { fis ->
                    FileOutputStream(tempPayloadFile).use { fos ->
                        StegoPng.decodePixels(fis, fos)
                    }
                }
            }

            options.onProgress?.invoke("Reading Header & Decrypting", 60, 100)
            val header: StegoPng.Header
            FileInputStream(tempPayloadFile).use { fis ->
                header = StegoPng.Header.readFrom(fis)
                FileOutputStream(tempDecryptedFile).use { fos ->
                    if (header.isEncrypted) {
                        if (options.passphrase.isNullOrEmpty()) throw Exception("Passphrase required to decode.")
                        CryptoEngine.decryptStream(fis, fos, options.passphrase, header.salt, header.nonce, header.iterations)
                    } else {
                        Compressor.compressStream(fis, fos, Compressor.Algorithm.NONE, 0)
                    }
                }
            }

            options.onProgress?.invoke("Decompressing", 90, 100)
            FileInputStream(tempDecryptedFile).use { fis ->
                Compressor.decompressStream(fis, outStream, header.compression)
            }

            options.onProgress?.invoke("Done", 100, 100)
            StegoEngine.DecodeResult(warnings, header)
        } finally {
            tempInputFile.delete()
            tempPayloadFile.delete()
            tempDecryptedFile.delete()
        }
    }

    /**
     * Strictly controls mode to prevent 40x PNG size blowups.
     * PIXEL LSB Mode is bounded to max 1MB payload to ensure we don't build 2GB uncompressed bitmaps.
     */
    private fun determineMode(size: Long, requestedMode: StegoEngine.EncodeMode): StegoEngine.EncodeMode {
        val maxPixelSizeLimit = 1L * 1024 * 1024 // 1 MB strict cap for LSB

        if (requestedMode == StegoEngine.EncodeMode.PIXEL && size > maxPixelSizeLimit) {
            return StegoEngine.EncodeMode.COMPACT // Force override
        }

        if (requestedMode != StegoEngine.EncodeMode.AUTO) return requestedMode

        return if (size <= maxPixelSizeLimit) StegoEngine.EncodeMode.PIXEL else StegoEngine.EncodeMode.COMPACT
    }

    private fun getAdaptiveIterations(deviceClass: StegoEngine.DeviceClass): Int {
        val sharedPrefs = context.getSharedPreferences("stegovault_prefs", Context.MODE_PRIVATE)
        val userSetting = sharedPrefs.getInt("pbkdf2_iterations", -1)
        if (userSetting > 0) return userSetting

        return when (deviceClass) {
            StegoEngine.DeviceClass.LOW -> 50_000
            StegoEngine.DeviceClass.MEDIUM -> 100_000
            StegoEngine.DeviceClass.HIGH -> 300_000
        }
    }
}