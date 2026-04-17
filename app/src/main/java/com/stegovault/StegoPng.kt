package com.stegovault

import java.io.InputStream
import java.io.OutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.ByteArrayOutputStream

/**
 * Basic PNG steganography implementation supporting streams.
 * Encrypts and embeds data into a custom PNG chunk "SVLT" or uses Screenshot pixel-mode.
 */
object StegoPng {

    const val MAGIC = "SV01"
    const val VERSION = 0x01.toByte()

    // PNG Magic
    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    )

    data class Header(
        val isEncrypted: Boolean,
        val compression: Int = 2,
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

            var flags = 0
            if (isEncrypted) flags = flags or 1
            dos.writeByte(flags)

            dos.writeByte(compression)
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
                val magic = String(magicBytes, Charsets.US_ASCII)
                if (magic != MAGIC) throw IllegalArgumentException("Invalid magic: $magic")

                val version = dis.readByte()
                if (version != VERSION) throw IllegalArgumentException("Unsupported version: $version")

                val flags = dis.readByte().toInt()
                val isEncrypted = (flags and 1) != 0

                val compression = dis.readByte().toInt()
                dis.readByte() // reserved

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
     * Embeds a payload stream into a given base PNG stream, writing to output.
     * Injects the "SVLT" chunk before IDAT.
     */
    fun embedIntoPngStream(basePngStream: InputStream, payloadStream: InputStream, payloadSize: Long, outStream: OutputStream) {
        val dis = DataInputStream(basePngStream)
        val dos = DataOutputStream(outStream)

        val signature = ByteArray(8)
        dis.readFully(signature)
        if (!signature.contentEquals(PNG_SIGNATURE)) {
            throw IllegalArgumentException("Not a valid PNG file")
        }
        dos.write(signature)

        var inserted = false

        while (true) {
            val length = try { dis.readInt() } catch (e: Exception) { break }
            val typeBytes = ByteArray(4)
            dis.readFully(typeBytes)
            val type = String(typeBytes, Charsets.US_ASCII)

            if (type == "IDAT" && !inserted) {
                // Write our custom chunk
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

            // Write original chunk
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

            val crcInt = dis.readInt()
            dos.writeInt(crcInt)
        }
    }

    /**
     * Extracts the SVLT chunk payload from a PNG stream, piping it to outStream.
     * Returns true if chunk was found, false otherwise.
     */
    fun extractFromPngStream(pngStream: InputStream, outStream: OutputStream): Boolean {
        val dis = DataInputStream(pngStream)

        val signature = ByteArray(8)
        try {
            dis.readFully(signature)
        } catch (e: Exception) { return false }

        if (!signature.contentEquals(PNG_SIGNATURE)) {
            throw IllegalArgumentException("Not a valid PNG file")
        }

        while (true) {
            val length = try { dis.readInt() } catch (e: Exception) { break }
            val typeBytes = ByteArray(4)
            try {
                dis.readFully(typeBytes)
            } catch (e: Exception) { break }
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
                dis.readInt() // skip CRC
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
                dis.readInt() // skip CRC
            }
        }
        return false
    }

    /**
     * Pixel-based steganography: encode bit-stream into LSB of a Bitmap.
     * Generates a screenshot-mode image directly.
     */
    fun encodePixels(inStream: InputStream, outStream: OutputStream, payloadSize: Long, minWidth: Int, minHeight: Int) {
        // Calculate needed pixels: 3 bits per pixel (1 bit per RGB channel)
        val neededPixels = (payloadSize * 8) / 3 + 1
        var size = maxOf(minWidth, Math.ceil(Math.sqrt(neededPixels.toDouble())).toInt())
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

        // Fill with gradient to make it look like "screenshot" noise/gradient
        for (y in 0 until size) {
            for (x in 0 until size) {
                val r = (x * 255 / size)
                val g = (y * 255 / size)
                val b = 128
                bitmap.setPixel(x, y, android.graphics.Color.rgb(r, g, b))
            }
        }

        var x = 0
        var y = 0
        var bitBuffer = 0
        var bitsBuffered = 0
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
                if (x >= size) {
                    x = 0
                    y++
                }
                pixelCount++
            }
        }

        // First write a 32-bit payload size header
        bitBuffer = payloadSize.toInt()
        bitsBuffered = 32
        writeBits()

        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (inStream.read(buffer).also { bytesRead = it } != -1) {
            for (i in 0 until bytesRead) {
                val byte = buffer[i].toInt() and 0xFF
                bitBuffer = (bitBuffer shl 8) or byte
                bitsBuffered += 8
                writeBits()
            }
        }

        // Flush remaining bits
        if (bitsBuffered > 0) {
            bitBuffer = bitBuffer shl (3 - bitsBuffered)
            bitsBuffered = 3
            writeBits()
        }

        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
    }

    /**
     * Decode LSB bits from a pixel-based stego PNG.
     */
    fun decodePixels(inStream: InputStream, outStream: OutputStream) {
        val bitmap = BitmapFactory.decodeStream(inStream) ?: throw IllegalArgumentException("Could not decode PNG bitmap")

        var x = 0
        var y = 0
        var bitBuffer = 0
        var bitsBuffered = 0

        var payloadSize = -1L
        var bytesWritten = 0L

        val size = bitmap.width

        fun readBits(count: Int): Int {
            var result = 0
            for (i in 0 until count) {
                if (bitsBuffered == 0) {
                    if (y >= size) throw Exception("Unexpected end of image")
                    val color = bitmap.getPixel(x, y)
                    val rBit = Color.red(color) and 1
                    val gBit = Color.green(color) and 1
                    val bBit = Color.blue(color) and 1
                    bitBuffer = (rBit shl 2) or (gBit shl 1) or bBit
                    bitsBuffered = 3

                    x++
                    if (x >= size) {
                        x = 0
                        y++
                    }
                }
                result = (result shl 1) or ((bitBuffer shr (bitsBuffered - 1)) and 1)
                bitsBuffered--
            }
            return result
        }

        // Read 32-bit length
        payloadSize = readBits(32).toLong() and 0xFFFFFFFFL

        while (bytesWritten < payloadSize) {
            val byte = readBits(8)
            outStream.write(byte)
            bytesWritten++
        }
    }
}