package com.pyxelze.roxify.core

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ArchiveHelper {

    /**
     * Compresses a list of files into a single zip file.
     * @param files List of files to compress.
     * @param outputFile The resulting zip file.
     */
    fun compressFiles(files: List<File>, outputFile: File) {
        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            files.forEach { file ->
                if (file.exists() && file.isFile) {
                    val zipEntry = ZipEntry(file.name)
                    zos.putNextEntry(zipEntry)
                    FileInputStream(file).use { fis ->
                        fis.copyTo(zos)
                    }
                    zos.closeEntry()
                }
            }
        }
    }

    /**
     * Decompresses a zip file into a target directory.
     * @param zipFile The zip file to decompress.
     * @param outputDir The directory to extract the files into.
     */
    fun decompressFiles(zipFile: File, outputDir: File) {
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val newFile = File(outputDir, entry.name)
                // Prevent Zip Slip vulnerability
                if (!newFile.canonicalPath.startsWith(outputDir.canonicalPath + File.separator)) {
                    throw SecurityException("Entry is outside of the target dir")
                }
                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
