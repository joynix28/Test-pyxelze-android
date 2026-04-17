package com.stegovault

import java.io.InputStream
import java.io.OutputStream

/**
 * Abstraction defining the Steganography and Encryption engine.
 * Inspired conceptually by tools like Roxify, abstracting the complex pipeline
 * to provide a clear, adaptive, and modern interface.
 */
interface StegoEngine {

    /**
     * Encodes an input stream into a PNG image output stream according to the specified options.
     */
    suspend fun encode(inStream: InputStream, inputSize: Long, outStream: OutputStream, options: EncodeOptions): StegoResult

    /**
     * Decodes a PNG image input stream into a binary output stream using the specified options.
     */
    suspend fun decode(inStream: InputStream, outStream: OutputStream, options: DecodeOptions): DecodeResult

    data class EncodeOptions(
        val entryCount: Int = 1,
        val passphrase: String? = null,
        val compression: CompressionAlgo = CompressionAlgo.DEFLATE,
        val compressionLevel: Int = 6, // 0-9 for deflate, standard is 6
        val mode: EncodeMode = EncodeMode.AUTO,
        val targetDeviceClass: DeviceClass = DeviceClass.MEDIUM
    )

    data class DecodeOptions(
        val passphrase: String? = null,
        val onProgress: ((phase: String, loaded: Long, total: Long) -> Unit)? = null
    )

    data class StegoResult(
        val modeUsed: EncodeMode,
        val warnings: List<String> = emptyList(),
        val header: StegoPng.Header? = null
    )

    data class DecodeResult(
        val warnings: List<String> = emptyList(),
        val header: StegoPng.Header? = null
    )

    enum class CompressionAlgo { NONE, DEFLATE } // Zstd and BWT-ANS represent future extensions

    enum class EncodeMode { AUTO, COMPACT, SCREENSHOT }

    enum class DeviceClass { LOW, MEDIUM, HIGH }
}