package com.stegovault

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Concrete implementation of the StegoEngine using native Kotlin constructs and streaming.
 */
class DefaultStegoEngine(private val context: Context) : StegoEngine {

    override suspend fun encode(inStream: InputStream, inputSize: Long, outStream: OutputStream, options: StegoEngine.EncodeOptions): StegoEngine.StegoResult = withContext(Dispatchers.IO) {
        val warnings = mutableListOf<String>()
        val finalMode = determineMode(inputSize, options.mode)

        val tempCompressedFile = File.createTempFile("stego_compressed", ".tmp", context.cacheDir)
        val tempEncryptedFile = File.createTempFile("stego_encrypted", ".tmp", context.cacheDir)
        val tempPayloadFile = File.createTempFile("stego_payload", ".tmp", context.cacheDir)

        try {
            // 1. Compress Stream
            FileOutputStream(tempCompressedFile).use { fos ->
                if (options.compression == StegoEngine.CompressionAlgo.DEFLATE) {
                    val deflater = java.util.zip.Deflater(options.compressionLevel)
                    val dos = DeflaterOutputStream(fos, deflater)
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (inStream.read(buffer).also { bytesRead = it } != -1) {
                        dos.write(buffer, 0, bytesRead)
                    }
                    dos.close()
                } else {
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (inStream.read(buffer).also { bytesRead = it } != -1) {
                        fos.write(buffer, 0, bytesRead)
                    }
                }
            }

            // 2. Encrypt Stream
            val salt = CryptoEngine.generateSalt()
            val nonce = CryptoEngine.generateNonce()
            val isEncrypted = !options.passphrase.isNullOrEmpty()
            val iterations = if (isEncrypted) getAdaptiveIterations(options.targetDeviceClass) else 0

            FileOutputStream(tempEncryptedFile).use { fos ->
                if (isEncrypted) {
                    FileInputStream(tempCompressedFile).use { fis ->
                        CryptoEngine.encryptStream(fis, fos, options.passphrase!!, salt, nonce, iterations)
                    }
                } else {
                    FileInputStream(tempCompressedFile).use { fis ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (fis.read(buffer).also { bytesRead = it } != -1) {
                            fos.write(buffer, 0, bytesRead)
                        }
                    }
                }
            }

            // 3. Assemble Header
            val header = StegoPng.Header(
                isEncrypted = isEncrypted,
                compression = if (options.compression == StegoEngine.CompressionAlgo.DEFLATE) 2 else 0,
                salt = salt,
                nonce = nonce,
                iterations = iterations,
                entryCount = options.entryCount,
                payloadSize = tempEncryptedFile.length()
            )

            // 4. Create Payload Stream (Header + Encrypted Data)
            FileOutputStream(tempPayloadFile).use { fos ->
                header.writeTo(fos)
                FileInputStream(tempEncryptedFile).use { fis ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        fos.write(buffer, 0, bytesRead)
                    }
                }
            }

            // 5. Generate Base PNG and Embed Stream
            if (finalMode == StegoEngine.EncodeMode.SCREENSHOT) {
                FileInputStream(tempPayloadFile).use { fis ->
                    StegoPng.encodePixels(fis, outStream, tempPayloadFile.length(), 512, 512)
                }
            } else {
                val basePng = generateCoverImage(finalMode)
                FileInputStream(tempPayloadFile).use { fis ->
                    StegoPng.embedIntoPngStream(java.io.ByteArrayInputStream(basePng), fis, tempPayloadFile.length(), outStream)
                }
            }

            if (inputSize > 50 * 1024 * 1024) { // 50MB warning threshold
                warnings.add("Large payload detected. Used chunked streaming.")
            }

            StegoEngine.StegoResult(finalMode, warnings, header)
        } finally {
            tempCompressedFile.delete()
            tempEncryptedFile.delete()
            tempPayloadFile.delete()
        }
    }

    override suspend fun decode(inStream: InputStream, outStream: OutputStream, options: StegoEngine.DecodeOptions): StegoEngine.DecodeResult = withContext(Dispatchers.IO) {
        val warnings = mutableListOf<String>()
        options.onProgress?.invoke("Extracting PNG Payload", 0, 0)

        val tempPayloadFile = File.createTempFile("stego_decode_payload", ".tmp", context.cacheDir)
        val tempDecryptedFile = File.createTempFile("stego_decode_decrypted", ".tmp", context.cacheDir)
        val tempInputFile = File.createTempFile("stego_decode_input", ".tmp", context.cacheDir)

        try {
            // Buffer input to file so we can read it twice if needed (screenshot mode fallback)
            FileOutputStream(tempInputFile).use { fos ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inStream.read(buffer).also { bytesRead = it } != -1) {
                    fos.write(buffer, 0, bytesRead)
                }
            }

            // Determine if SVLT or pixels
            val extracted = FileInputStream(tempInputFile).use { fis ->
                FileOutputStream(tempPayloadFile).use { fos ->
                    StegoPng.extractFromPngStream(fis, fos)
                }
            }

            if (!extracted) {
                // Try pixel decode
                FileInputStream(tempInputFile).use { fis ->
                    FileOutputStream(tempPayloadFile).use { fos ->
                        StegoPng.decodePixels(fis, fos)
                    }
                }
            }

            val header: StegoPng.Header
            val payloadFis = FileInputStream(tempPayloadFile)

            try {
                options.onProgress?.invoke("Parsing Header", 0, tempPayloadFile.length())
                header = StegoPng.Header.readFrom(payloadFis)

                FileOutputStream(tempDecryptedFile).use { fos ->
                    if (header.isEncrypted) {
                        if (options.passphrase.isNullOrEmpty()) {
                            throw Exception("Payload is encrypted, but no passphrase provided.")
                        }
                        options.onProgress?.invoke("Decrypting", 0, tempPayloadFile.length())
                        CryptoEngine.decryptStream(payloadFis, fos, options.passphrase, header.salt, header.nonce, header.iterations)
                    } else {
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (payloadFis.read(buffer).also { bytesRead = it } != -1) {
                            fos.write(buffer, 0, bytesRead)
                        }
                    }
                }
            } finally {
                payloadFis.close()
            }

            options.onProgress?.invoke("Decompressing", 0, tempDecryptedFile.length())
            FileInputStream(tempDecryptedFile).use { fis ->
                val decompressor = InflaterInputStream(fis)
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (decompressor.read(buffer).also { bytesRead = it } != -1) {
                    outStream.write(buffer, 0, bytesRead)
                }
            }

            options.onProgress?.invoke("Complete", 100, 100)
            StegoEngine.DecodeResult(warnings, header)
        } finally {
            tempPayloadFile.delete()
            tempDecryptedFile.delete()
            tempInputFile.delete()
        }
    }

    private fun determineMode(size: Long, requestedMode: StegoEngine.EncodeMode): StegoEngine.EncodeMode {
        // Screenshot mode requires a full Bitmap in RAM.
        // 10MB payload = ~150MB Bitmap required. We cap SCREENSHOT mode at 10MB to prevent OutOfMemoryError.
        val maxScreenshotSize = 10L * 1024 * 1024

        if (requestedMode == StegoEngine.EncodeMode.SCREENSHOT && size > maxScreenshotSize) {
            return StegoEngine.EncodeMode.COMPACT
        }

        if (requestedMode != StegoEngine.EncodeMode.AUTO) return requestedMode

        return if (size < 1024 * 512 || size > maxScreenshotSize) {
            // Very small payloads fit nicely in chunks. Very large payloads MUST use chunks to stream safely.
            StegoEngine.EncodeMode.COMPACT
        } else {
            // Medium payloads (512KB - 10MB) can safely fit in memory for pixel distribution.
            StegoEngine.EncodeMode.SCREENSHOT
        }
    }

    private fun getAdaptiveIterations(deviceClass: StegoEngine.DeviceClass): Int {
        val sharedPrefs = context.getSharedPreferences("stegovault_prefs", Context.MODE_PRIVATE)
        val userSetting = sharedPrefs.getInt("pbkdf2_iterations", -1)
        if (userSetting > 0) return userSetting

        return when (deviceClass) {
            StegoEngine.DeviceClass.LOW -> 50_000
            StegoEngine.DeviceClass.MEDIUM -> 200_000
            StegoEngine.DeviceClass.HIGH -> 500_000
        }
    }

    private fun generateCoverImage(mode: StegoEngine.EncodeMode): ByteArray {
        val width = if (mode == StegoEngine.EncodeMode.COMPACT) 256 else 1024
        val height = if (mode == StegoEngine.EncodeMode.COMPACT) 256 else 1024

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint()
        val shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            Color.parseColor("#1a2a6c"),
            Color.parseColor("#b21f1f"),
            Shader.TileMode.CLAMP
        )
        paint.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        paint.shader = null
        paint.color = Color.WHITE
        paint.textSize = if (mode == StegoEngine.EncodeMode.COMPACT) 32f else 64f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("StegoVault Archive", width / 2f, height / 2f, paint)

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }
}
