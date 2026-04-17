package com.pyxelze.roxify.core

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32

object StegoPngHelper {

    private val PNG_SIGNATURE = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
    private val CHUNK_TYPE_IEND = byteArrayOf('I'.code.toByte(), 'E'.code.toByte(), 'N'.code.toByte(), 'D'.code.toByte())
    private val CHUNK_TYPE_SVLT = byteArrayOf('S'.code.toByte(), 'V'.code.toByte(), 'L'.code.toByte(), 'T'.code.toByte())

    fun embedChunk(inputStream: InputStream, payload: ByteArray): ByteArray {
        val pngBytes = inputStream.readBytes()
        if (pngBytes.size < 8 || !pngBytes.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE)) {
            throw IllegalArgumentException("Input is not a valid PNG file")
        }

        val outStream = ByteArrayOutputStream()
        outStream.write(PNG_SIGNATURE)

        var offset = 8
        while (offset < pngBytes.size) {
            val lengthBuf = ByteBuffer.wrap(pngBytes, offset, 4)
            val length = lengthBuf.int
            val type = pngBytes.copyOfRange(offset + 4, offset + 8)

            if (type.contentEquals(CHUNK_TYPE_IEND)) {
                outStream.write(ByteBuffer.allocate(4).putInt(payload.size).array())
                outStream.write(CHUNK_TYPE_SVLT)
                outStream.write(payload)

                val crc = CRC32()
                crc.update(CHUNK_TYPE_SVLT)
                crc.update(payload)
                outStream.write(ByteBuffer.allocate(4).putInt(crc.value.toInt()).array())

                outStream.write(pngBytes, offset, length + 12)
                break
            } else {
                outStream.write(pngBytes, offset, length + 12)
            }
            offset += length + 12
        }

        return outStream.toByteArray()
    }

    fun extractChunk(inputStream: InputStream): ByteArray {
        val pngBytes = inputStream.readBytes()
        if (pngBytes.size < 8 || !pngBytes.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE)) {
            throw IllegalArgumentException("Input is not a valid PNG file")
        }

        var offset = 8
        while (offset < pngBytes.size) {
            val lengthBuf = ByteBuffer.wrap(pngBytes, offset, 4)
            val length = lengthBuf.int
            val type = pngBytes.copyOfRange(offset + 4, offset + 8)

            if (type.contentEquals(CHUNK_TYPE_SVLT)) {
                return pngBytes.copyOfRange(offset + 8, offset + 8 + length)
            }

            offset += length + 12
        }

        throw IllegalArgumentException("No StegoVault (SVLT) chunk found in this PNG.")
    }
}
