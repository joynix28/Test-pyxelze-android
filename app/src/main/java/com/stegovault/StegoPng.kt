package com.stegovault

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32

/**
 * Basic PNG Chunk steganography implementation.
 * Encrypts and embeds data into a custom PNG chunk "SVLT".
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
        val salt: ByteArray,
        val nonce: ByteArray,
        val iterations: Int,
        val entryCount: Int,
        val payloadSize: Long
    )

    /**
     * Creates a payload byte array containing the StegoVault Header followed by the Ciphertext.
     */
    fun createPayload(
        header: Header,
        ciphertext: ByteArray
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val dos = DataOutputStream(out)

        dos.write(MAGIC.toByteArray(Charsets.US_ASCII))
        dos.writeByte(VERSION.toInt())

        var flags = 0
        if (header.isEncrypted) flags = flags or 1
        dos.writeByte(flags)

        dos.writeByte(2) // Compression: 2 = DEFLATE
        dos.writeByte(0) // reserved

        dos.write(header.salt)
        dos.write(header.nonce)
        dos.writeInt(header.iterations)
        dos.writeInt(header.entryCount)
        dos.writeLong(header.payloadSize)

        dos.write(ciphertext)

        dos.flush()
        return out.toByteArray()
    }

    /**
     * Reads a payload and extracts header and ciphertext.
     */
    fun parsePayload(payload: ByteArray): Pair<Header, ByteArray> {
        val dis = DataInputStream(ByteArrayInputStream(payload))

        val magicBytes = ByteArray(4)
        dis.readFully(magicBytes)
        val magic = String(magicBytes, Charsets.US_ASCII)
        if (magic != MAGIC) throw IllegalArgumentException("Invalid magic: $magic")

        val version = dis.readByte()
        if (version != VERSION) throw IllegalArgumentException("Unsupported version: $version")

        val flags = dis.readByte().toInt()
        val isEncrypted = (flags and 1) != 0

        dis.readByte() // compression
        dis.readByte() // reserved

        val salt = ByteArray(16)
        dis.readFully(salt)

        val nonce = ByteArray(12)
        dis.readFully(nonce)

        val iterations = dis.readInt()
        val entryCount = dis.readInt()
        val payloadSize = dis.readLong()

        val ciphertext = dis.readBytes()

        val header = Header(isEncrypted, salt, nonce, iterations, entryCount, payloadSize)
        return Pair(header, ciphertext)
    }

    /**
     * Simple PNG reader to extract custom SVLT chunks.
     * Searches for chunk type "SVLT".
     */
    fun extractFromPng(pngBytes: ByteArray): ByteArray? {
        val buffer = ByteBuffer.wrap(pngBytes)
        val signature = ByteArray(8)
        buffer.get(signature)
        if (!signature.contentEquals(PNG_SIGNATURE)) {
            throw IllegalArgumentException("Not a valid PNG file")
        }

        while (buffer.hasRemaining()) {
            if (buffer.remaining() < 8) break
            val length = buffer.int
            val typeBytes = ByteArray(4)
            buffer.get(typeBytes)
            val type = String(typeBytes, Charsets.US_ASCII)

            if (type == "SVLT") {
                val chunkData = ByteArray(length)
                buffer.get(chunkData)
                buffer.int // skip CRC
                return chunkData
            } else {
                if (length > 0) {
                    val skip = length + 4 // skip data and CRC
                    if (buffer.remaining() >= skip) {
                        buffer.position(buffer.position() + skip)
                    } else {
                        break
                    }
                } else if (length == 0) {
                    buffer.position(buffer.position() + 4) // skip CRC
                } else {
                   break // invalid length
                }
            }
        }
        return null
    }

    /**
     * Embeds a payload into a given PNG.
     * Injects the "SVLT" chunk before IDAT.
     */
    fun embedIntoPng(pngBytes: ByteArray, payload: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(pngBytes)
        val signature = ByteArray(8)
        buffer.get(signature)
        if (!signature.contentEquals(PNG_SIGNATURE)) {
            throw IllegalArgumentException("Not a valid PNG file")
        }

        val out = ByteArrayOutputStream()
        out.write(signature)

        var inserted = false

        while (buffer.hasRemaining()) {
            if (buffer.remaining() < 8) break
            val length = buffer.int
            val typeBytes = ByteArray(4)
            buffer.get(typeBytes)
            val type = String(typeBytes, Charsets.US_ASCII)

            // Inject before the first IDAT
            if (type == "IDAT" && !inserted) {
                writeChunk(out, "SVLT", payload)
                inserted = true
            }

            buffer.position(buffer.position() - 8)
            val fullChunk = ByteArray(length + 12) // len(4) + type(4) + data(length) + crc(4)
            buffer.get(fullChunk)
            out.write(fullChunk)
        }

        return out.toByteArray()
    }

    private fun writeChunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        val dos = DataOutputStream(out)
        dos.writeInt(data.size)
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        dos.write(typeBytes)
        dos.write(data)

        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        dos.writeInt(crc.value.toInt())
    }
}
