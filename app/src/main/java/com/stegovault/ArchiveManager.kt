package com.stegovault

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

object ArchiveManager {
    fun createTarArchive(files: List<File>, outStream: OutputStream, baseDir: File? = null) {
        TarArchiveOutputStream(outStream).use { tarOut ->
            tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            for (file in files) {
                addFileToTar(tarOut, file, baseDir ?: file.parentFile)
            }
        }
    }

    private fun addFileToTar(tarOut: TarArchiveOutputStream, file: File, baseDir: File?) {
        val entryName = if (baseDir != null) file.relativeTo(baseDir).path.replace('\\', '/') else file.name
        val tarEntry = TarArchiveEntry(file, entryName)
        tarOut.putArchiveEntry(tarEntry)

        if (file.isFile) {
            FileInputStream(file).use { input ->
                input.copyTo(tarOut)
            }
            tarOut.closeArchiveEntry()
        } else if (file.isDirectory) {
            tarOut.closeArchiveEntry()
            val children = file.listFiles()
            if (children != null) {
                for (child in children) {
                    addFileToTar(tarOut, child, baseDir)
                }
            }
        }
    }

    fun extractTarArchive(inStream: InputStream, outputDir: File) {
        TarArchiveInputStream(inStream).use { tarIn ->
            var entry = tarIn.nextEntry
            while (entry != null) {
                val destFile = File(outputDir, entry.name)
                if (entry.isDirectory) {
                    destFile.mkdirs()
                } else {
                    destFile.parentFile?.mkdirs()
                    FileOutputStream(destFile).use { out ->
                        tarIn.copyTo(out)
                    }
                    destFile.setLastModified(entry.lastModifiedDate?.time ?: System.currentTimeMillis())
                }
                entry = tarIn.nextEntry
            }
        }
    }
}
