package com.pyxelze.roxify.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

object CustomArchiveHelper {

    fun buildArchive(baseDir: File, filesToArchive: List<File>, useCompression: Boolean = true): ByteArray {
        val entryTableStream = ByteArrayOutputStream()
        val dataStream = ByteArrayOutputStream()

        for (file in filesToArchive) {
            val relativePath = file.toRelativeString(baseDir).replace("\\", "/")
            val pathBytes = relativePath.toByteArray(StandardCharsets.UTF_8)

            entryTableStream.write(ByteBuffer.allocate(4).putInt(pathBytes.size).array())
            entryTableStream.write(pathBytes)

            if (file.isDirectory) {
                entryTableStream.write(1)
                entryTableStream.write(ByteBuffer.allocate(8).putLong(0L).array())
            } else {
                entryTableStream.write(0)
                val fileSize = file.length()
                entryTableStream.write(ByteBuffer.allocate(8).putLong(fileSize).array())

                FileInputStream(file).use { fis ->
                    fis.copyTo(dataStream)
                }
            }
        }

        val finalStream = ByteArrayOutputStream()
        finalStream.write(entryTableStream.toByteArray())
        finalStream.write(dataStream.toByteArray())

        val uncompressedBytes = finalStream.toByteArray()

        if (useCompression) {
            val compressedStream = ByteArrayOutputStream()
            val deflater = Deflater(Deflater.BEST_COMPRESSION)
            DeflaterOutputStream(compressedStream, deflater).use { dos ->
                dos.write(uncompressedBytes)
            }
            return compressedStream.toByteArray()
        }

        return uncompressedBytes
    }

    fun extractArchive(archiveData: ByteArray, destDir: File, expectedEntries: Int, isCompressed: Boolean) {
        val dataToParse = if (isCompressed) {
            val bis = ByteArrayInputStream(archiveData)
            val decompressedStream = ByteArrayOutputStream()
            InflaterInputStream(bis).use { iis ->
                iis.copyTo(decompressedStream)
            }
            decompressedStream.toByteArray()
        } else {
            archiveData
        }

        val buffer = ByteBuffer.wrap(dataToParse)

        class EntryMetadata(val path: String, val isDir: Boolean, val size: Long)
        val entries = mutableListOf<EntryMetadata>()

        for (i in 0 until expectedEntries) {
            val pathLen = buffer.int
            val pathBytes = ByteArray(pathLen)
            buffer.get(pathBytes)
            val path = String(pathBytes, StandardCharsets.UTF_8)
            val type = buffer.get().toInt()
            val size = buffer.long
            entries.add(EntryMetadata(path, type == 1, size))
        }

        for (entry in entries) {
            val targetFile = File(destDir, entry.path)
            if (!targetFile.canonicalPath.startsWith(destDir.canonicalPath + File.separator)) {
                throw SecurityException("Invalid path traversal detected.")
            }
            if (entry.isDir) {
                targetFile.mkdirs()
            } else {
                targetFile.parentFile?.mkdirs()
                val fileData = ByteArray(entry.size.toInt())
                buffer.get(fileData)
                targetFile.writeBytes(fileData)
            }
        }
    }
}
