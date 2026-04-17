package com.stegovault

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.ByteArrayInputStream

class DecodeWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val tempPayloadFile = File.createTempFile("stego_decode", ".tmp", applicationContext.cacheDir)
        val tempDecryptedArchiveFile = File.createTempFile("stego_decrypted_archive", ".tmp", applicationContext.cacheDir)

        try {
            val inputUriStr = inputData.getString("input_uri") ?: return@withContext Result.failure()
            val outputDirUriStr = inputData.getString("output_dir_uri") ?: return@withContext Result.failure()
            val passphrase = inputData.getString("passphrase")

            val inputUri = Uri.parse(inputUriStr)
            val outputDirUri = Uri.parse(outputDirUriStr)

            setProgress(workDataOf("progress" to "Reading file..."))

            // Try reading as PNG
            var isPng = false
            applicationContext.contentResolver.openInputStream(inputUri)?.use { inStream ->
                val signature = ByteArray(8)
                val read = inStream.read(signature)
                if (read == 8 && signature.contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))) {
                    isPng = true
                }
            }

            setProgress(workDataOf("progress" to "Decrypting..."))
            val engine = DefaultStegoEngine(applicationContext)

            val decodeResult: StegoEngine.DecodeResult
            if (isPng) {
                decodeResult = applicationContext.contentResolver.openInputStream(inputUri)?.use { inStream ->
                    FileOutputStream(tempDecryptedArchiveFile).use { outStream ->
                        engine.decode(inStream, outStream, StegoEngine.DecodeOptions(
                            passphrase = passphrase?.takeIf { it.isNotEmpty() }
                        ) { phase, _, _ ->
                            setProgressAsync(workDataOf("progress" to phase))
                        })
                    }
                } ?: throw Exception("Failed to open input stream")
            } else {
                 applicationContext.contentResolver.openInputStream(inputUri)?.use { inStream ->
                    FileOutputStream(tempPayloadFile).use { outStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (inStream.read(buffer).also { bytesRead = it } != -1) {
                            outStream.write(buffer, 0, bytesRead)
                        }
                    }
                }

                // If raw file, just parse payload natively bypassing StegoEngine's PNG extract logic
                var header: StegoPng.Header? = null
                FileInputStream(tempPayloadFile).use { payloadFis ->
                    header = StegoPng.Header.readFrom(payloadFis)
                    val tempCryptoOut = File.createTempFile("stego_crypto_out", ".tmp", applicationContext.cacheDir)
                    try {
                        FileOutputStream(tempCryptoOut).use { fos ->
                            if (header!!.isEncrypted) {
                                if (passphrase.isNullOrEmpty()) {
                                    throw Exception("Payload is encrypted, but no passphrase provided.")
                                }
                                CryptoEngine.decryptStream(payloadFis, fos, passphrase, header!!.salt, header!!.nonce, header!!.iterations)
                            } else {
                                val buffer = ByteArray(8192)
                                var bytesRead: Int
                                while (payloadFis.read(buffer).also { bytesRead = it } != -1) {
                                    fos.write(buffer, 0, bytesRead)
                                }
                            }
                        }

                        // Decompress it
                        FileInputStream(tempCryptoOut).use { fis ->
                            val decompressor = java.util.zip.InflaterInputStream(fis)
                            FileOutputStream(tempDecryptedArchiveFile).use { outStream ->
                                val buffer = ByteArray(8192)
                                var bytesRead: Int
                                while (decompressor.read(buffer).also { bytesRead = it } != -1) {
                                    outStream.write(buffer, 0, bytesRead)
                                }
                            }
                        }
                    } finally {
                        tempCryptoOut.delete()
                    }
                }
                decodeResult = StegoEngine.DecodeResult(emptyList(), header)
            }

            setProgress(workDataOf("progress" to "Unpacking..."))

            val rootDir = DocumentFile.fromTreeUri(applicationContext, outputDirUri)
                ?: throw Exception("Invalid output directory")

            val header = decodeResult.header ?: throw Exception("Decoding failed to return header")

            FileInputStream(tempDecryptedArchiveFile).use { inStream ->
                ArchiveManager.unpack(inStream, header.entryCount) { entryHeader, entryStream ->
                    val pathParts = entryHeader.path.split("/")
                    var currentDir = rootDir

                    for (i in 0 until pathParts.size - 1) {
                        val dirName = pathParts[i]
                        currentDir = currentDir.findFile(dirName) ?: currentDir.createDirectory(dirName)
                            ?: throw Exception("Cannot create directory: $dirName")
                    }

                    val fileName = pathParts.last()
                    if (entryHeader.isDirectory) {
                        currentDir.findFile(fileName) ?: currentDir.createDirectory(fileName)
                    } else if (entryStream != null) {
                        val newFile = currentDir.createFile("application/octet-stream", fileName)
                            ?: throw Exception("Cannot create file: $fileName")

                        applicationContext.contentResolver.openOutputStream(newFile.uri)?.use { outStream ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            while (entryStream.read(buffer).also { bytesRead = it } != -1) {
                                outStream.write(buffer, 0, bytesRead)
                            }
                        }
                    }
                }
            }

            setProgress(workDataOf("progress" to "Complete"))
            Result.success(workDataOf("output_dir_uri" to outputDirUriStr))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(workDataOf("error" to e.message))
        } finally {
            tempPayloadFile.delete()
            tempDecryptedArchiveFile.delete()
        }
    }
}
