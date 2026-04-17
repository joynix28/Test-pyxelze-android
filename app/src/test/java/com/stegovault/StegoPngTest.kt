package com.stegovault

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import kotlin.random.Random

class StegoPngTest {

    @Test
    fun testExtractionFromLargeChunk() {
        // Build a fake PNG manually
        val out = ByteArrayOutputStream()
        // Signature
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))

        // IHDR chunk
        writeChunk(out, "IHDR", ByteArray(13))

        // SVLT chunk (large)
        val payload = ByteArray(100) // Dummy payload
        Random.nextBytes(payload)
        writeChunk(out, "SVLT", payload)

        // IDAT chunk
        writeChunk(out, "IDAT", ByteArray(50))

        // IEND chunk
        writeChunk(out, "IEND", ByteArray(0))

        val pngBytes = out.toByteArray()
        val extracted = StegoPng.extractFromPng(pngBytes)

        assertNotNull("Payload should be extracted", extracted)
        assertArrayEquals("Extracted payload should match", payload, extracted)
    }

    private fun writeChunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        val dos = java.io.DataOutputStream(out)
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
