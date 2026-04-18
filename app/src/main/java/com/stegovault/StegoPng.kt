package com.stegovault

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import kotlin.random.Random

object StegoPng {

    private const val CHUNK_TYPE = "rXDT"

    fun embedIntoPngStream(payloadStream: InputStream, payloadSize: Long, outStream: OutputStream) {
        // As requested by the user, we should not create a fake "purple image".
        // A "screenshot" mode or noisy image mode is preferred.
        // We generate a randomized gradient-noise abstract cover.
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val r = Random.Default
        for (x in 0 until 512 step 16) {
            for (y in 0 until 512 step 16) {
                val color = Color.rgb(r.nextInt(256), r.nextInt(256), r.nextInt(256))
                val paint = android.graphics.Paint().apply { this.color = color }
                canvas.drawRect(x.toFloat(), y.toFloat(), (x + 16).toFloat(), (y + 16).toFloat(), paint)
            }
        }

        val imageStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, imageStream)
        val pngBytes = imageStream.toByteArray()

        val buffer = ByteBuffer.wrap(pngBytes)

        // Write PNG signature
        val signature = ByteArray(8)
        buffer.get(signature)
        outStream.write(signature)

        var chunkInjected = false

        while (buffer.remaining() >= 8) {
            val length = buffer.int
            val typeBytes = ByteArray(4)
            buffer.get(typeBytes)
            val type = String(typeBytes)

            val data = ByteArray(length)
            if (length > 0) {
                buffer.get(data)
            }
            val crc = buffer.int

            // Write the current chunk
            outStream.write(ByteBuffer.allocate(4).putInt(length).array())
            outStream.write(typeBytes)
            outStream.write(data)
            outStream.write(ByteBuffer.allocate(4).putInt(crc).array())

            // Inject after IHDR
            if (type == "IHDR" && !chunkInjected) {
                val chunkTypeBytes = CHUNK_TYPE.toByteArray(Charsets.US_ASCII)
                val crc32 = CRC32()
                crc32.update(chunkTypeBytes)

                // Write payload in chunks, tracking exact size written to inject size later (since we must know size ahead of time for chunk header)
                // Actually, the PNG chunk format requires knowing the size upfront.
                // We've been provided payloadSize by the worker!
                outStream.write(ByteBuffer.allocate(4).putInt(payloadSize.toInt()).array())
                outStream.write(chunkTypeBytes)

                val bufferSize = 8192
                val chunkBuffer = ByteArray(bufferSize)
                var bytesRead: Int
                while (payloadStream.read(chunkBuffer).also { bytesRead = it } != -1) {
                    outStream.write(chunkBuffer, 0, bytesRead)
                    crc32.update(chunkBuffer, 0, bytesRead)
                }

                outStream.write(ByteBuffer.allocate(4).putInt(crc32.value.toInt()).array())
                chunkInjected = true
            }
        }
    }

    fun extractFromPngStream(pngStream: InputStream, outStream: OutputStream) {
        val signature = ByteArray(8)
        pngStream.read(signature)

        while (true) {
            val lengthBytes = ByteArray(4)
            if (pngStream.read(lengthBytes) < 4) break
            val length = ByteBuffer.wrap(lengthBytes).int

            val typeBytes = ByteArray(4)
            pngStream.read(typeBytes)
            val type = String(typeBytes)

            if (type == CHUNK_TYPE) {
                val bufferSize = 8192
                val chunkBuffer = ByteArray(bufferSize)
                var remaining = length
                while (remaining > 0) {
                    val toRead = if (remaining > bufferSize) bufferSize else remaining
                    val bytesRead = pngStream.read(chunkBuffer, 0, toRead)
                    if (bytesRead == -1) break
                    outStream.write(chunkBuffer, 0, bytesRead)
                    remaining -= bytesRead
                }
                pngStream.skip(4) // skip CRC
                return
            } else {
                pngStream.skip(length.toLong() + 4)
            }
        }
        throw Exception("Chunk $CHUNK_TYPE not found in PNG")
    }
}
