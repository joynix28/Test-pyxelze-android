package com.stegovault

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import java.io.InputStream
import java.io.OutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Strict Steganographic Encoder / Decoder.
 * Protects against excessive PNG bloat by applying strict Bit-Capacity maths.
 * Made by JoyniX.
 */
object StegoPng {

    const val MAGIC = "SV01"
    const val VERSION = 0x01.toByte()

    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    )

    data class Header(
        val isEncrypted: Boolean,
        val compression: Compressor.Algorithm,
        val salt: ByteArray,
        val nonce: ByteArray,
        val iterations: Int,
        val entryCount: Int,
        val payloadSize: Long
    ) {
        fun writeTo(out: OutputStream) {
            val dos = DataOutputStream(out)
            dos.write(MAGIC.toByteArray(Charsets.US_ASCII))
            dos.writeByte(VERSION.toInt())
            dos.writeByte(if (isEncrypted) 1 else 0)
            dos.writeByte(if (compression == Compressor.Algorithm.DEFLATE) 2 else 0)
            dos.writeByte(0) // reserved

            dos.write(salt)
            dos.write(nonce)
            dos.writeInt(iterations)
            dos.writeInt(entryCount)
            dos.writeLong(payloadSize)
        }

        companion object {
            fun readFrom(inputStream: InputStream): Header {
                val dis = DataInputStream(inputStream)
                val magicBytes = ByteArray(4)
                dis.readFully(magicBytes)
                if (String(magicBytes, Charsets.US_ASCII) != MAGIC) throw IllegalArgumentException("Invalid StegoVault Magic")

                if (dis.readByte() != VERSION) throw IllegalArgumentException("Unsupported Vault Version")

                val isEncrypted = (dis.readByte().toInt() and 1) != 0
                val compression = if (dis.readByte().toInt() == 2) Compressor.Algorithm.DEFLATE else Compressor.Algorithm.NONE
                dis.readByte() // skip reserved

                val salt = ByteArray(16)
                dis.readFully(salt)
                val nonce = ByteArray(12)
                dis.readFully(nonce)

                val iterations = dis.readInt()
                val entryCount = dis.readInt()
                val payloadSize = dis.readLong()

                return Header(isEncrypted, compression, salt, nonce, iterations, entryCount, payloadSize)
            }
        }
    }

    /**
     * Chunk Mode Embedding: Appends SVLT chunk to existing image pixels without altering image bits.
     * Prevents image inflation on high capacity datasets.
     */
    fun embedIntoPngStream(basePngStream: InputStream, payloadStream: InputStream, payloadSize: Long, outStream: OutputStream) {
        val dis = DataInputStream(basePngStream)
        val dos = DataOutputStream(outStream)

        val signature = ByteArray(8)
        dis.readFully(signature)
        if (!signature.contentEquals(PNG_SIGNATURE)) throw IllegalArgumentException("Not a valid PNG")
        dos.write(signature)

        var inserted = false

        while (true) {
            val length = try { dis.readInt() } catch (e: Exception) { break }
            val typeBytes = ByteArray(4)
            dis.readFully(typeBytes)
            val type = String(typeBytes, Charsets.US_ASCII)

            if (type == "IDAT" && !inserted) {
                dos.writeInt(payloadSize.toInt())
                val customTypeBytes = "SVLT".toByteArray(Charsets.US_ASCII)
                dos.write(customTypeBytes)

                val crc = CRC32()
                crc.update(customTypeBytes)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (payloadStream.read(buffer).also { bytesRead = it } != -1) {
                    dos.write(buffer, 0, bytesRead)
                    crc.update(buffer, 0, bytesRead)
                }

                dos.writeInt(crc.value.toInt())
                inserted = true
            }

            dos.writeInt(length)
            dos.write(typeBytes)

            var remaining = length
            val buffer = ByteArray(8192)
            while (remaining > 0) {
                val toRead = minOf(buffer.size, remaining)
                val read = dis.read(buffer, 0, toRead)
                if (read == -1) break
                dos.write(buffer, 0, read)
                remaining -= read
            }
            dos.writeInt(dis.readInt())
        }
    }

    fun extractFromPngStream(pngStream: InputStream, outStream: OutputStream): Boolean {
        val dis = DataInputStream(pngStream)
        val signature = ByteArray(8)
        try { dis.readFully(signature) } catch (e: Exception) { return false }
        if (!signature.contentEquals(PNG_SIGNATURE)) throw IllegalArgumentException("Not a valid PNG")

        while (true) {
            val length = try { dis.readInt() } catch (e: Exception) { break }
            val typeBytes = ByteArray(4)
            try { dis.readFully(typeBytes) } catch (e: Exception) { break }
            val type = String(typeBytes, Charsets.US_ASCII)

            if (type == "SVLT") {
                var remaining = length
                val buffer = ByteArray(8192)
                while (remaining > 0) {
                    val toRead = minOf(buffer.size, remaining)
                    val read = dis.read(buffer, 0, toRead)
                    if (read == -1) break
                    outStream.write(buffer, 0, read)
                    remaining -= read
                }
                dis.readInt() // Skip CRC
                return true
            } else {
                var remaining = length
                val buffer = ByteArray(8192)
                while (remaining > 0) {
                    val toRead = minOf(buffer.size, remaining)
                    val read = dis.read(buffer, 0, toRead)
                    if (read == -1) break
                    remaining -= read
                }
                dis.readInt()
            }
        }
        return false
    }

    /**
     * Strict 1-bit per channel LSB embedding (3 bits per pixel).
     * Bounded mathematically: PayloadSize * 8 <= width * height * 3.
     */
    fun encodePixels(inStream: InputStream, outStream: OutputStream, payloadSize: Long) {
        val neededPixels = (payloadSize * 8) / 3 + 1
        val size = maxOf(256, ceil(sqrt(neededPixels.toDouble())).toInt())
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

        // Generate neutral gradient
        for (y in 0 until size) {
            for (x in 0 until size) {
                val r = (x * 255 / size)
                val g = (y * 255 / size)
                val b = 128
                bitmap.setPixel(x, y, Color.rgb(r, g, b))
            }
        }

        var x = 0
        var y = 0
        var bitBuffer = payloadSize.toInt()
        var bitsBuffered = 32
        var pixelCount = 0L

        fun writeBits() {
            while (bitsBuffered >= 3 && pixelCount < neededPixels) {
                val rBit = (bitBuffer shr (bitsBuffered - 1)) and 1
                val gBit = (bitBuffer shr (bitsBuffered - 2)) and 1
                val bBit = (bitBuffer shr (bitsBuffered - 3)) and 1
                bitsBuffered -= 3

                val color = bitmap.getPixel(x, y)
                val r = (Color.red(color) and 0xFE) or rBit
                val g = (Color.green(color) and 0xFE) or gBit
                val b = (Color.blue(color) and 0xFE) or bBit

                bitmap.setPixel(x, y, Color.rgb(r, g, b))

                x++
                if (x >= size) { x = 0; y++ }
                pixelCount++
            }
        }

        writeBits()

        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (inStream.read(buffer).also { bytesRead = it } != -1) {
            for (i in 0 until bytesRead) {
                bitBuffer = (bitBuffer shl 8) or (buffer[i].toInt() and 0xFF)
                bitsBuffered += 8
                writeBits()
            }
        }

        if (bitsBuffered > 0) {
            bitBuffer = bitBuffer shl (3 - bitsBuffered)
            bitsBuffered = 3
            writeBits()
        }

        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
    }

    fun decodePixels(inStream: InputStream, outStream: OutputStream) {
        val bitmap = BitmapFactory.decodeStream(inStream) ?: throw IllegalArgumentException("Could not decode PNG bitmap")

        var x = 0; var y = 0
        var bitBuffer = 0; var bitsBuffered = 0
        val size = bitmap.width

        fun readBits(count: Int): Int {
            var result = 0
            for (i in 0 until count) {
                if (bitsBuffered == 0) {
                    if (y >= size) throw Exception("Unexpected end of image")
                    val color = bitmap.getPixel(x, y)
                    bitBuffer = ((Color.red(color) and 1) shl 2) or ((Color.green(color) and 1) shl 1) or (Color.blue(color) and 1)
                    bitsBuffered = 3

                    x++
                    if (x >= size) { x = 0; y++ }
                }
                result = (result shl 1) or ((bitBuffer shr (bitsBuffered - 1)) and 1)
                bitsBuffered--
            }
            return result
        }

        val payloadSize = readBits(32).toLong() and 0xFFFFFFFFL
        var bytesWritten = 0L

        while (bytesWritten < payloadSize) {
            outStream.write(readBits(8))
            bytesWritten++
        }
    }

    fun generateCoverImage(context: Context): ByteArray {
        val width = 512
        val height = 512
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint()
        paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), Color.parseColor("#E0E0E0"), Color.parseColor("#9E9E9E"), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        paint.shader = null
        paint.color = Color.DKGRAY
        paint.textSize = 48f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("StegoVault", width / 2f, height / 2f, paint)

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }
}