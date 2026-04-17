package com.stegovault

import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Sequential file archiver supporting streams.
 * Made by JoyniX.
 */
object ArchiveManager {

    data class ArchiveEntry(
        val path: String,
        val isDirectory: Boolean,
        val size: Long,
        val uri: Uri? = null
    )

    fun pack(context: Context, entries: List<ArchiveEntry>, out: OutputStream) {
        for (entry in entries) {
            val pathBytes = entry.path.toByteArray(StandardCharsets.UTF_8)
            val pathLenBuffer = ByteBuffer.allocate(4).putInt(pathBytes.size).array()
            out.write(pathLenBuffer)
            out.write(pathBytes)

            out.write(if (entry.isDirectory) 1 else 0)

            val sizeBuffer = ByteBuffer.allocate(8).putLong(if (entry.isDirectory) 0L else entry.size).array()
            out.write(sizeBuffer)
        }

        val buffer = ByteArray(8192)
        for (entry in entries) {
            if (!entry.isDirectory && entry.uri != null) {
                context.contentResolver.openInputStream(entry.uri)?.use { inputStream ->
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                    }
                }
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

    fun unpack(inStream: InputStream, entryCount: Int, onEntry: (ArchiveEntryHeader, InputStream?) -> Unit) {
        val headers = mutableListOf<ArchiveEntryHeader>()
        val intBuffer = ByteArray(4)
        val longBuffer = ByteArray(8)

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

        for (header in headers) {
            if (header.isDirectory) {
                onEntry(header, null)
            } else {
                val boundedStream = BoundedInputStream(inStream, header.size)
                onEntry(header, boundedStream)
                boundedStream.consumeRemaining()
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

        fun consumeRemaining() {
            val remaining = limit - bytesRead
            if (remaining > 0) {
                val buffer = ByteArray(8192)
                while (read(buffer) != -1) { }
            }
        }
    }
}