package com.stegovault

import com.github.luben.zstd.ZstdInputStream
import com.github.luben.zstd.ZstdOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

object Compressor {
    fun compress(data: ByteArray): ByteArray {
        val outputStream = ByteArrayOutputStream()
        ZstdOutputStream(outputStream).use { zstdStream ->
            zstdStream.write(data)
        }
        return outputStream.toByteArray()
    }

    fun decompress(data: ByteArray): ByteArray {
        val inputStream = ByteArrayInputStream(data)
        val outputStream = ByteArrayOutputStream()
        ZstdInputStream(inputStream).use { zstdStream ->
            zstdStream.copyTo(outputStream)
        }
        return outputStream.toByteArray()
    }
}
