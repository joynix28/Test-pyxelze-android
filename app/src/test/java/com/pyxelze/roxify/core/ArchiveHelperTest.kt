package com.pyxelze.roxify.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ArchiveHelperTest {

    private lateinit var tempDir: File

    @Before
    fun setup() {
        tempDir = Files.createTempDirectory("archive_test").toFile()
    }

    @Test
    fun testCompressAndDecompress() {
        val file1 = File(tempDir, "file1.txt")
        file1.writeText("Hello World!")
        val file2 = File(tempDir, "file2.txt")
        file2.writeText("Testing 123")

        val zipFile = File(tempDir, "output.zip")
        ArchiveHelper.compressFiles(listOf(file1, file2), zipFile)

        assertTrue(zipFile.exists())
        assertTrue(zipFile.length() > 0)

        val outputDir = File(tempDir, "extracted")
        ArchiveHelper.decompressFiles(zipFile, outputDir)

        val extracted1 = File(outputDir, "file1.txt")
        val extracted2 = File(outputDir, "file2.txt")

        assertTrue(extracted1.exists())
        assertEquals("Hello World!", extracted1.readText())

        assertTrue(extracted2.exists())
        assertEquals("Testing 123", extracted2.readText())
    }
}
