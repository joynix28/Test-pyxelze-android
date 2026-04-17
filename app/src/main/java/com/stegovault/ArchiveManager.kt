package com.stegovault

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/**
 * Packs and unpacks files into a custom binary archive format using streams.
 */
object ArchiveManager {

    data class ArchiveEntry(
        val path: String,
        val isDirectory: Boolean,
        val size: Long,
        val inputStream: InputStream? = null
    )

    /**
     * Packs entries into a single OutputStream.
     * Format:
     * [Entries Headers]
     * For each entry:
     * - 4 bytes: path length N (uint32)
     * - N bytes: UTF-8 path
     * - 1 byte: type (0 = file, 1 = dir)
     * - 8 bytes: uncompressed size (0 for dir)
     *
     * [Entries Data]
     * After all headers, the raw data of files in the same order.
     */
    fun pack(entries: List<ArchiveEntry>, out: OutputStream) {
        // Write headers
        for (entry in entries) {
            val pathBytes = entry.path.toByteArray(StandardCharsets.UTF_8)
            val pathLenBuffer = ByteBuffer.allocate(4).putInt(pathBytes.size).array()
            out.write(pathLenBuffer)
            out.write(pathBytes)

            out.write(if (entry.isDirectory) 1 else 0)

            val sizeBuffer = ByteBuffer.allocate(8).putLong(if (entry.isDirectory) 0L else entry.size).array()
            out.write(sizeBuffer)
        }

        // Write data
        val buffer = ByteArray(8192)
        for (entry in entries) {
            if (!entry.isDirectory && entry.inputStream != null) {
                var bytesRead: Int
                while (entry.inputStream.read(buffer).also { bytesRead = it } != -1) {
                    out.write(buffer, 0, bytesRead)
                }
                entry.inputStream.close()
            }
        }
    }

    data class ArchiveEntryHeader(
        val path: String,
        val isDirectory: Boolean,
        val size: Long
    )

    private fun readFully(inputStream: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val bytesRead = inputStream.read(buffer, offset, buffer.size - offset)
            if (bytesRead == -1) throw java.io.EOFException()
            offset += bytesRead
        }
    }

    /**
     * Unpacks entries from the stream and calls a callback for each file's data.
     */
    fun unpack(inStream: InputStream, entryCount: Int, onEntry: (ArchiveEntryHeader, InputStream?) -> Unit) {
        val headers = mutableListOf<ArchiveEntryHeader>()
        val intBuffer = ByteArray(4)
        val longBuffer = ByteArray(8)

        // Read headers
        for (i in 0 until entryCount) {
            readFully(inStream, intBuffer)
            val pathLen = ByteBuffer.wrap(intBuffer).int
            val pathBytes = ByteArray(pathLen)
            readFully(inStream, pathBytes)
            val path = String(pathBytes, StandardCharsets.UTF_8)

            val type = inStream.read()
            if (type == -1) throw java.io.EOFException()
            val isDirectory = type == 1

            readFully(inStream, longBuffer)
            val size = ByteBuffer.wrap(longBuffer).long

            headers.add(ArchiveEntryHeader(path, isDirectory, size))
        }

        // Read data and call callback
        for (header in headers) {
            if (header.isDirectory) {
                onEntry(header, null)
            } else {
                val boundedStream = BoundedInputStream(inStream, header.size)
                onEntry(header, boundedStream)
                // Consume any remaining bytes if the callback didn't read everything
                boundedStream.skip(header.size)
            }
        }
    }

    class BoundedInputStream(private val inStream: InputStream, private val limit: Long) : InputStream() {
        private var bytesRead: Long = 0

        override fun read(): Int {
            if (bytesRead >= limit) return -1
            val result = inStream.read()
            if (result != -1) bytesRead++
            return result
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (bytesRead >= limit) return -1
            val maxRead = minOf(len.toLong(), limit - bytesRead).toInt()
            val result = inStream.read(b, off, maxRead)
            if (result != -1) bytesRead += result
            return result
        }

        override fun skip(n: Long): Long {
            if (bytesRead >= limit) return 0
            val maxSkip = minOf(n, limit - bytesRead)
            val result = inStream.skip(maxSkip)
            bytesRead += result
            return result
        }
    }

    fun compress(out: OutputStream): OutputStream {
        return DeflaterOutputStream(out)
    }

    fun decompress(inStream: InputStream): InputStream {
        return InflaterInputStream(inStream)
    }
}