package com.stegovault

import android.content.Context
import android.net.Uri
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

class EncodeWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        return try {
            val fileUris = inputData.getStringArray("FILE_URIS") ?: return Result.failure()
            val passphrase = inputData.getString("PASSPHRASE")
            val isPng = inputData.getBoolean("IS_PNG", true)
            val outputPath = inputData.getString("OUTPUT_PATH") ?: return Result.failure()

            val tempFiles = mutableListOf<File>()
            val contentResolver = applicationContext.contentResolver

            // SAF: Copy to temp files to use java.io.File logic in ArchiveManager
            for (uriStr in fileUris) {
                val uri = Uri.parse(uriStr)
                val tempFile = File.createTempFile("saf_input", ".dat")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { out ->
                        input.copyTo(out)
                    }
                }
                tempFiles.add(tempFile)
            }

            setProgressAsync(workDataOf("PROGRESS" to 10))

            val outputFile = File(outputPath)

            if (isPng) {
                val tempPayload = File.createTempFile("temp_payload", ".dat")
                FileOutputStream(tempPayload).use { out ->
                    StegoEngine.buildPayload(tempFiles, passphrase, isPng, out)
                }
                setProgressAsync(workDataOf("PROGRESS" to 50))

                FileOutputStream(outputFile).use { out ->
                    StegoPng.embedIntoPngStream(FileInputStream(tempPayload), tempPayload.length(), out)
                }
                tempPayload.delete()
            } else {
                FileOutputStream(outputFile).use { out ->
                    StegoEngine.buildPayload(tempFiles, passphrase, isPng, out)
                }
            }

            setProgressAsync(workDataOf("PROGRESS" to 100))

            for (f in tempFiles) {
                f.delete()
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}

class DecodeWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        return try {
            val inputUriStr = inputData.getString("INPUT_URI") ?: return Result.failure()
            val passphrase = inputData.getString("PASSPHRASE")
            val outputDirPath = inputData.getString("OUTPUT_DIR") ?: return Result.failure()
            val isPng = inputData.getBoolean("IS_PNG", true)

            val uri = Uri.parse(inputUriStr)
            val contentResolver = applicationContext.contentResolver
            val inputStream: InputStream = contentResolver.openInputStream(uri) ?: throw Exception("Cannot open URI")

            setProgressAsync(workDataOf("PROGRESS" to 10))

            if (isPng) {
                val tempPayload = File.createTempFile("temp_payload_dec", ".dat")
                FileOutputStream(tempPayload).use { out ->
                    StegoPng.extractFromPngStream(inputStream, out)
                }
                setProgressAsync(workDataOf("PROGRESS" to 50))

                FileInputStream(tempPayload).use { input ->
                    StegoEngine.extractPayload(input, passphrase, File(outputDirPath))
                }
                tempPayload.delete()
            } else {
                StegoEngine.extractPayload(inputStream, passphrase, File(outputDirPath))
            }

            setProgressAsync(workDataOf("PROGRESS" to 100))
            inputStream.close()

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}
