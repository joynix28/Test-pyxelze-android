package com.stegovault

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import kotlin.random.Random

object StegoPng {

    private const val CHUNK_TYPE = "rXDT"

    fun embedIntoPngStream(payloadStream: InputStream, payloadSize: Long, outStream: OutputStream) {
        // Base cover image generator (does NOT hold payload in pixels to avoid OOM on large files).
        // It creates a fixed 256x256 image and appends the payload correctly into an rXDT chunk.

        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val r = Random.Default
        for (x in 0 until 256 step 16) {
            for (y in 0 until 256 step 16) {
                val color = Color.rgb(r.nextInt(256), r.nextInt(256), r.nextInt(256))
                val paint = Paint().apply { this.color = color }
                canvas.drawRect(x.toFloat(), y.toFloat(), (x + 16).toFloat(), (y + 16).toFloat(), paint)
            }
        }

        val imageStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, imageStream)
        val pngBytes = imageStream.toByteArray()
        bitmap.recycle()

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

            outStream.write(ByteBuffer.allocate(4).putInt(length).array())
            outStream.write(typeBytes)
            outStream.write(data)
            outStream.write(ByteBuffer.allocate(4).putInt(crc).array())

            // Inject after IHDR
            if (type == "IHDR" && !chunkInjected) {
                val chunkTypeBytes = CHUNK_TYPE.toByteArray(Charsets.US_ASCII)
                val crc32 = CRC32()
                crc32.update(chunkTypeBytes)

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

        outStream.flush()
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
                outStream.flush()
                return // Done extracting
            } else {
                pngStream.skip(length.toLong() + 4)
            }
        }
        throw Exception("Chunk $CHUNK_TYPE not found in PNG")
    }
}
