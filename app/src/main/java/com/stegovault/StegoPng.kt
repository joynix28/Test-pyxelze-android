package com.stegovault

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

object StegoPng {

    // Implements LSB / Screenshot dense mode embedding instead of a custom PNG chunk.
    // The user demanded we encode the payload exactly into RGB pixels to avoid custom chunk detection
    // and to behave exactly like "screenshot" dense mode in Roxify.

    fun embedIntoPngStream(payloadStream: InputStream, payloadSize: Long, outStream: OutputStream) {
        // Read full payload into memory as we need to calculate required image dimensions.
        // For production, we should compute this block-by-block, but standard LSB/Dense RGB
        // implies calculating pixel counts based on payload.
        // We use 3 bytes per pixel (R, G, B) mapping directly to payload bytes.
        // Alpha channel is forced to 255 (fully opaque) to prevent premultiplied alpha loss.

        val payloadBytes = payloadStream.readBytes()
        val totalBytes = 8 + payloadBytes.size // 8 bytes for length prefix
        val requiredPixels = Math.ceil(totalBytes / 3.0).toInt()

        // Calculate square dimensions
        var width = Math.ceil(Math.sqrt(requiredPixels.toDouble())).toInt()
        // Ensure minimum size and power of 2 bounds if desired, but square is fine.
        if (width < 32) width = 32

        val bitmap = Bitmap.createBitmap(width, width, Bitmap.Config.ARGB_8888)

        val dataBuffer = ByteBuffer.allocate(totalBytes)
        dataBuffer.putLong(payloadBytes.size.toLong())
        dataBuffer.put(payloadBytes)
        val dataArray = dataBuffer.array()

        var byteIndex = 0
        for (y in 0 until width) {
            for (x in 0 until width) {
                var r = 0
                var g = 0
                var b = 0

                if (byteIndex < dataArray.size) r = dataArray[byteIndex++].toInt() and 0xFF
                if (byteIndex < dataArray.size) g = dataArray[byteIndex++].toInt() and 0xFF
                if (byteIndex < dataArray.size) b = dataArray[byteIndex++].toInt() and 0xFF

                // For pixels beyond payload, fill with random noise to look like abstract data
                if (byteIndex >= dataArray.size && (r == 0 && g == 0 && b == 0)) {
                   r = (Math.random() * 255).toInt()
                   g = (Math.random() * 255).toInt()
                   b = (Math.random() * 255).toInt()
                }

                bitmap.setPixel(x, y, Color.argb(255, r, g, b))
            }
        }

        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
    }

    fun extractFromPngStream(pngStream: InputStream, outStream: OutputStream) {
        val bitmap = android.graphics.BitmapFactory.decodeStream(pngStream)
            ?: throw Exception("Invalid image provided")

        // First 8 bytes (from first 3 pixels) give us the payload length
        val headerBuffer = ByteBuffer.allocate(9) // 3 pixels = 9 channels
        var pxIndex = 0

        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (pxIndex >= 3) break
                val color = bitmap.getPixel(x, y)
                headerBuffer.put((Color.red(color)).toByte())
                headerBuffer.put((Color.green(color)).toByte())
                headerBuffer.put((Color.blue(color)).toByte())
                pxIndex++
            }
            if (pxIndex >= 3) break
        }

        headerBuffer.position(0)
        val payloadLength = headerBuffer.long
        if (payloadLength <= 0 || payloadLength > bitmap.width * bitmap.height * 3L) {
            throw Exception("Corrupted image: Invalid payload length extracted.")
        }

        var remaining = payloadLength

        // We've already read 8 bytes of header, which spans across 2 pixels and 2 channels of the 3rd pixel.
        // That means we must start extracting payload exactly at byte offset 8 (the Blue channel of the 3rd pixel).
        var bytesExtracted = 0
        var currentPx = 0

        val payloadBuffer = ByteArrayOutputStream()

        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (remaining <= 0) break
                val color = bitmap.getPixel(x, y)

                if (currentPx == 0 || currentPx == 1) {
                    // skip fully, these hold the first 6 bytes of the 8-byte length
                } else if (currentPx == 2) {
                    // This pixel holds bytes 6 and 7 of the length in R and G.
                    // The B channel is the first byte of our actual payload.
                    if (remaining > 0) {
                        payloadBuffer.write(Color.blue(color))
                        remaining--
                    }
                } else {
                    if (remaining > 0) { payloadBuffer.write(Color.red(color)); remaining-- }
                    if (remaining > 0) { payloadBuffer.write(Color.green(color)); remaining-- }
                    if (remaining > 0) { payloadBuffer.write(Color.blue(color)); remaining-- }
                }
                currentPx++
            }
        }

        outStream.write(payloadBuffer.toByteArray())
    }
}
