package com.pyxelze.roxify.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CustomArchiveHelperTest {

    private lateinit var tempDir: File

    @Before
    fun setup() {
        tempDir = Files.createTempDirectory("archive_test").toFile()
    }

    @Test
    fun testBuildAndExtract() {
        val file1 = File(tempDir, "file1.txt")
        file1.writeText("Hello World!")
        val dir1 = File(tempDir, "mydir")
        dir1.mkdirs()
        val file2 = File(dir1, "file2.txt")
        file2.writeText("Testing 123")

        val filesToArchive = listOf(file1, dir1, file2)
        val archiveBytes = CustomArchiveHelper.buildArchive(tempDir, filesToArchive)

        assertTrue(archiveBytes.isNotEmpty())

        val outputDir = File(tempDir, "extracted")
        outputDir.mkdirs()

        CustomArchiveHelper.extractArchive(archiveBytes, outputDir, 3, isCompressed = true)

        val extracted1 = File(outputDir, "file1.txt")
        val extractedDir = File(outputDir, "mydir")
        val extracted2 = File(extractedDir, "file2.txt")

        assertTrue(extracted1.exists())
        assertEquals("Hello World!", extracted1.readText())

        assertTrue(extractedDir.exists())
        assertTrue(extractedDir.isDirectory)

        assertTrue(extracted2.exists())
        assertEquals("Testing 123", extracted2.readText())
    }
}
