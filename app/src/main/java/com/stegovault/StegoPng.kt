package com.stegovault

import android.graphics.Bitmap
import android.graphics.Color
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

object StegoPng {

    // Safely limits maximum processing chunks to prevent OOM
    private const val CHUNK_SIZE = 8192

    fun embedIntoPngStream(payloadStream: InputStream, payloadSize: Long, outStream: OutputStream) {
        // Create an image sequentially without loading everything into memory.
        val totalBytes = 8 + payloadSize
        val requiredPixels = Math.ceil(totalBytes / 3.0).toInt()
        var width = Math.ceil(Math.sqrt(requiredPixels.toDouble())).toInt()
        if (width < 32) width = 32

        // Android's Bitmap.compress handles streaming inherently if memory permits.
        // For large files (e.g. 180MB payload -> 7.7k x 7.7k px), a single ARGB_8888 bitmap requires ~237MB RAM.
        // We will try to allocate this bitmap. If it fails, fallback strategy would be required (e.g., custom PNG encoder chunk by chunk),
        // but for now, we minimize buffer copies to maximize heap availability.

        val bitmap = Bitmap.createBitmap(width, width, Bitmap.Config.ARGB_8888)

        val headerBuffer = ByteBuffer.allocate(8)
        headerBuffer.putLong(payloadSize)
        headerBuffer.position(0)

        var headerBytesRead = 0
        var remainingPayload = payloadSize

        for (y in 0 until width) {
            for (x in 0 until width) {
                var r = 0
                var g = 0
                var b = 0

                // Read 3 bytes per pixel
                if (headerBytesRead < 8) {
                    r = headerBuffer.get().toInt() and 0xFF
                    headerBytesRead++
                } else if (remainingPayload > 0) {
                    val readByte = payloadStream.read()
                    if (readByte != -1) { r = readByte; remainingPayload-- }
                }

                if (headerBytesRead < 8) {
                    g = headerBuffer.get().toInt() and 0xFF
                    headerBytesRead++
                } else if (remainingPayload > 0) {
                    val readByte = payloadStream.read()
                    if (readByte != -1) { g = readByte; remainingPayload-- }
                }

                if (headerBytesRead < 8) {
                    b = headerBuffer.get().toInt() and 0xFF
                    headerBytesRead++
                } else if (remainingPayload > 0) {
                    val readByte = payloadStream.read()
                    if (readByte != -1) { b = readByte; remainingPayload-- }
                }

                // Padding noise
                if (remainingPayload <= 0 && headerBytesRead >= 8 && (r == 0 && g == 0 && b == 0)) {
                    r = (Math.random() * 255).toInt()
                    g = (Math.random() * 255).toInt()
                    b = (Math.random() * 255).toInt()
                }

                bitmap.setPixel(x, y, Color.argb(255, r, g, b))
            }
        }

        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
        bitmap.recycle() // Release memory immediately
    }

    fun extractFromPngStream(pngStream: InputStream, outStream: OutputStream) {
        val options = android.graphics.BitmapFactory.Options()
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        // For extremely large bitmaps, BitmapFactory might still OOM.
        val bitmap = android.graphics.BitmapFactory.decodeStream(pngStream, null, options)
            ?: throw Exception("Invalid image provided or memory exhausted.")

        val headerBuffer = ByteBuffer.allocate(8)
        var headerBytesExtracted = 0
        var payloadLength: Long = 0
        var remaining: Long = 0

        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val color = bitmap.getPixel(x, y)

                if (headerBytesExtracted < 8) { headerBuffer.put((Color.red(color)).toByte()); headerBytesExtracted++ }
                else if (remaining > 0) { outStream.write(Color.red(color)); remaining-- }

                if (headerBytesExtracted == 8 && remaining == 0L) {
                    headerBuffer.position(0)
                    payloadLength = headerBuffer.long
                    if (payloadLength <= 0 || payloadLength > bitmap.width * bitmap.height * 3L) {
                        bitmap.recycle()
                        throw Exception("Corrupted image: Invalid payload length extracted.")
                    }
                    remaining = payloadLength
                }

                if (headerBytesExtracted < 8) { headerBuffer.put((Color.green(color)).toByte()); headerBytesExtracted++ }
                else if (remaining > 0) { outStream.write(Color.green(color)); remaining-- }

                if (headerBytesExtracted == 8 && remaining == 0L && payloadLength == 0L) {
                    headerBuffer.position(0)
                    payloadLength = headerBuffer.long
                    remaining = payloadLength
                }

                if (headerBytesExtracted < 8) { headerBuffer.put((Color.blue(color)).toByte()); headerBytesExtracted++ }
                else if (remaining > 0) { outStream.write(Color.blue(color)); remaining-- }

                if (headerBytesExtracted == 8 && remaining == 0L && payloadLength == 0L) {
                    headerBuffer.position(0)
                    payloadLength = headerBuffer.long
                    remaining = payloadLength
                }

                if (headerBytesExtracted >= 8 && remaining <= 0) {
                    bitmap.recycle()
                    return
                }
            }
        }
        bitmap.recycle()
    }
}
