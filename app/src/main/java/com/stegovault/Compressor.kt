package com.stegovault

import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/**
 * Handles Zstd / Deflate compression dynamically on streams.
 * Made by JoyniX.
 */
object Compressor {

    enum class Algorithm { NONE, DEFLATE }

    fun compressStream(inStream: InputStream, outStream: OutputStream, algo: Algorithm, level: Int) {
        when (algo) {
            Algorithm.NONE -> {
                val buffer = ByteArray(8192)
                var read: Int
                while (inStream.read(buffer).also { read = it } != -1) {
                    outStream.write(buffer, 0, read)
                }
            }
            Algorithm.DEFLATE -> {
                val deflater = Deflater(level)
                DeflaterOutputStream(outStream, deflater).use { dos ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (inStream.read(buffer).also { read = it } != -1) {
                        dos.write(buffer, 0, read)
                    }
                }
            }
        }
    }

    fun decompressStream(inStream: InputStream, outStream: OutputStream, algo: Algorithm) {
        when (algo) {
            Algorithm.NONE -> {
                val buffer = ByteArray(8192)
                var read: Int
                while (inStream.read(buffer).also { read = it } != -1) {
                    outStream.write(buffer, 0, read)
                }
            }
            Algorithm.DEFLATE -> {
                InflaterInputStream(inStream).use { iis ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (iis.read(buffer).also { read = it } != -1) {
                        outStream.write(buffer, 0, read)
                    }
                }
            }
        }
    }
}