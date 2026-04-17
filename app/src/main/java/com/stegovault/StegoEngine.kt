package com.stegovault

import java.io.InputStream
import java.io.OutputStream

/**
 * Abstract Steganography engine handling logic and configurations.
 * Concept inspired by Roxify. Made by JoyniX.
 */
interface StegoEngine {

    suspend fun encode(
        inStream: InputStream,
        inputSize: Long,
        outStream: OutputStream,
        options: EncodeOptions
    ): StegoResult

    suspend fun decode(
        inStream: InputStream,
        outStream: OutputStream,
        options: DecodeOptions
    ): DecodeResult

    data class EncodeOptions(
        val passphrase: String? = null,
        val compression: Compressor.Algorithm = Compressor.Algorithm.DEFLATE,
        val compressionLevel: Int = 6,
        val mode: EncodeMode = EncodeMode.AUTO,
        val entryCount: Int = 1,
        val targetDeviceClass: DeviceClass = DeviceClass.MEDIUM,
        val onProgress: ((phase: String, progress: Int) -> Unit)? = null
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

    enum class EncodeMode { AUTO, COMPACT, PIXEL }

    enum class DeviceClass { LOW, MEDIUM, HIGH }
}