package com.pyxelze.roxify.core

import android.graphics.Bitmap
import android.graphics.Color
import java.nio.ByteBuffer

object SteganographyHelper {

    /**
     * Hides the given payload (byte array) inside the carrier Bitmap using LSB steganography.
     * The first 4 bytes of the hidden data will store the payload length.
     * @param carrier The original image.
     * @param payload The data to hide.
     * @return A new Bitmap containing the hidden data.
     * @throws IllegalArgumentException If the payload is too large for the image.
     */
    fun encode(carrier: Bitmap, payload: ByteArray): Bitmap {
        // We need 4 bytes for the length, plus the payload size
        val totalBytesToHide = 4 + payload.size

        // 1 byte = 8 bits. We encode 1 bit per pixel channel (R, G, B, A), so 4 bits per pixel.
        // Or to be safer and not mess with alpha premultiplication on Android too much, we can encode
        // 1 bit in R, 1 in G, 1 in B. 3 bits per pixel.
        // Let's stick to 3 bits per pixel (R, G, B) to ensure stability of the image data when saved.
        val requiredPixels = Math.ceil((totalBytesToHide * 8).toDouble() / 3.0).toInt()

        val width = carrier.width
        val height = carrier.height
        val totalPixels = width * height

        if (requiredPixels > totalPixels) {
            throw IllegalArgumentException("Payload is too large for the given image. Requires $requiredPixels pixels, but image has $totalPixels.")
        }

        // Prepare the data to hide
        val lengthBuffer = ByteBuffer.allocate(4).putInt(payload.size).array()
        val dataToHide = ByteArray(totalBytesToHide)
        System.arraycopy(lengthBuffer, 0, dataToHide, 0, 4)
        System.arraycopy(payload, 0, dataToHide, 4, payload.size)

        // Make a mutable copy of the carrier
        val resultBitmap = carrier.copy(Bitmap.Config.ARGB_8888, true)

        var dataIndex = 0
        var bitIndex = 0 // 0 to 7

        outer@ for (y in 0 until height) {
            for (x in 0 until width) {
                if (dataIndex >= dataToHide.size) {
                    break@outer
                }

                val pixel = resultBitmap.getPixel(x, y)
                var a = Color.alpha(pixel)
                var r = Color.red(pixel)
                var g = Color.green(pixel)
                var b = Color.blue(pixel)

                // R channel
                if (dataIndex < dataToHide.size) {
                    val bit = (dataToHide[dataIndex].toInt() shr (7 - bitIndex)) and 1
                    r = (r and 0xFE) or bit
                    bitIndex++
                    if (bitIndex > 7) { bitIndex = 0; dataIndex++ }
                }

                // G channel
                if (dataIndex < dataToHide.size) {
                    val bit = (dataToHide[dataIndex].toInt() shr (7 - bitIndex)) and 1
                    g = (g and 0xFE) or bit
                    bitIndex++
                    if (bitIndex > 7) { bitIndex = 0; dataIndex++ }
                }

                // B channel
                if (dataIndex < dataToHide.size) {
                    val bit = (dataToHide[dataIndex].toInt() shr (7 - bitIndex)) and 1
                    b = (b and 0xFE) or bit
                    bitIndex++
                    if (bitIndex > 7) { bitIndex = 0; dataIndex++ }
                }

                resultBitmap.setPixel(x, y, Color.argb(a, r, g, b))
            }
        }

        return resultBitmap
    }

    /**
     * Extracts hidden data from a stego Bitmap.
     * @param stegoImage The image containing hidden data.
     * @return The extracted payload byte array.
     * @throws IllegalArgumentException If no valid data is found.
     */
    fun decode(stegoImage: Bitmap): ByteArray {
        val width = stegoImage.width
        val height = stegoImage.height

        var extractedBits = 0
        var currentByte = 0
        var byteCount = 0

        val lengthBytes = ByteArray(4)
        var payloadSize = -1
        var payload: ByteArray? = null

        outer@ for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = stegoImage.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // Process R
                currentByte = (currentByte shl 1) or (r and 1)
                extractedBits++
                if (extractedBits == 8) {
                    if (payloadSize == -1) {
                        lengthBytes[byteCount] = currentByte.toByte()
                        byteCount++
                        if (byteCount == 4) {
                            payloadSize = ByteBuffer.wrap(lengthBytes).int
                            if (payloadSize <= 0 || payloadSize > (width * height * 3 / 8) - 4) {
                                throw IllegalArgumentException("Invalid hidden data length found.")
                            }
                            payload = ByteArray(payloadSize)
                            byteCount = 0
                        }
                    } else {
                        payload!![byteCount] = currentByte.toByte()
                        byteCount++
                        if (byteCount == payloadSize) {
                            break@outer
                        }
                    }
                    extractedBits = 0
                    currentByte = 0
                }

                // Process G
                currentByte = (currentByte shl 1) or (g and 1)
                extractedBits++
                if (extractedBits == 8) {
                    if (payloadSize == -1) {
                        lengthBytes[byteCount] = currentByte.toByte()
                        byteCount++
                        if (byteCount == 4) {
                            payloadSize = ByteBuffer.wrap(lengthBytes).int
                            if (payloadSize <= 0 || payloadSize > (width * height * 3 / 8) - 4) {
                                throw IllegalArgumentException("Invalid hidden data length found.")
                            }
                            payload = ByteArray(payloadSize)
                            byteCount = 0
                        }
                    } else {
                        payload!![byteCount] = currentByte.toByte()
                        byteCount++
                        if (byteCount == payloadSize) {
                            break@outer
                        }
                    }
                    extractedBits = 0
                    currentByte = 0
                }

                // Process B
                currentByte = (currentByte shl 1) or (b and 1)
                extractedBits++
                if (extractedBits == 8) {
                    if (payloadSize == -1) {
                        lengthBytes[byteCount] = currentByte.toByte()
                        byteCount++
                        if (byteCount == 4) {
                            payloadSize = ByteBuffer.wrap(lengthBytes).int
                            if (payloadSize <= 0 || payloadSize > (width * height * 3 / 8) - 4) {
                                throw IllegalArgumentException("Invalid hidden data length found.")
                            }
                            payload = ByteArray(payloadSize)
                            byteCount = 0
                        }
                    } else {
                        payload!![byteCount] = currentByte.toByte()
                        byteCount++
                        if (byteCount == payloadSize) {
                            break@outer
                        }
                    }
                    extractedBits = 0
                    currentByte = 0
                }
            }
        }

        if (payload == null || byteCount < payloadSize) {
            throw IllegalArgumentException("Could not fully extract data. Image might be corrupted.")
        }

        return payload
    }
}
