package com.stegovault

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.FilterOutputStream
import java.io.FilterInputStream

class ProgressOutputStream(out: OutputStream, private val totalSize: Long, private val onProgress: (Int) -> Unit) : FilterOutputStream(out) {
    private var bytesWritten: Long = 0
    private var lastPercent: Int = 0

    override fun write(b: Int) {
        super.write(b)
        updateProgress(1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        super.write(b, off, len)
        updateProgress(len.toLong())
    }

    private fun updateProgress(len: Long) {
        bytesWritten += len
        val percent = ((bytesWritten.toDouble() / totalSize) * 100).toInt()
        if (percent > lastPercent) {
            lastPercent = percent
            onProgress(percent)
        }
    }
}

class ProgressInputStream(input: InputStream, private val totalSize: Long, private val onProgress: (Int) -> Unit) : FilterInputStream(input) {
    private var bytesRead: Long = 0
    private var lastPercent: Int = 0

    override fun read(): Int {
        val b = super.read()
        if (b != -1) updateProgress(1)
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = super.read(b, off, len)
        if (n != -1) updateProgress(n.toLong())
        return n
    }

    private fun updateProgress(len: Long) {
        if (totalSize <= 0) return
        bytesRead += len
        val percent = ((bytesRead.toDouble() / totalSize) * 100).toInt()
        if (percent > lastPercent) {
            lastPercent = percent
            onProgress(percent)
        }
    }
}

class EncodeWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        return try {
            val fileUris = inputData.getStringArray("FILE_URIS") ?: return Result.failure()
            val passphrase = inputData.getString("PASSPHRASE")
            val isPng = inputData.getBoolean("IS_PNG", true)
            val outputPathStr = inputData.getString("OUTPUT_PATH") ?: return Result.failure()

            val tempFiles = mutableListOf<File>()
            val contentResolver = applicationContext.contentResolver

            for (uriStr in fileUris) {
                val uri = Uri.parse(uriStr)
                val docFile = DocumentFile.fromSingleUri(applicationContext, uri)
                val tempFile = File(applicationContext.cacheDir, docFile?.name ?: "saf_input_${System.currentTimeMillis()}")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { out ->
                        input.copyTo(out)
                    }
                }
                tempFiles.add(tempFile)
            }

            val outputFile = File(outputPathStr)
            val outputStream = FileOutputStream(outputFile)

            if (isPng) {
                val tempPayload = File.createTempFile("temp_payload", ".dat", applicationContext.cacheDir)
                FileOutputStream(tempPayload).use { out ->
                    StegoEngine.buildPayload(tempFiles, passphrase, isPng, out)
                }

                val payloadSize = tempPayload.length()
                val progressStream = ProgressInputStream(FileInputStream(tempPayload), payloadSize) { percent ->
                    setProgressAsync(workDataOf("PROGRESS" to (50 + (percent / 2))))
                }

                StegoPng.embedIntoPngStream(progressStream, payloadSize, outputStream)
                progressStream.close()
                tempPayload.delete()
            } else {
                StegoEngine.buildPayload(tempFiles, passphrase, isPng, outputStream)
            }

            outputStream.close()

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
            val outputPathStr = inputData.getString("OUTPUT_PATH") ?: return Result.failure()
            val isPng = inputData.getBoolean("IS_PNG", true)

            val uri = Uri.parse(inputUriStr)
            val outputDir = File(outputPathStr)

            val contentResolver = applicationContext.contentResolver

            val docFile = DocumentFile.fromSingleUri(applicationContext, uri)
            val fileSize = docFile?.length() ?: 0L

            val rawInputStream: InputStream = contentResolver.openInputStream(uri) ?: throw Exception("Cannot open URI")
            val progressStream = ProgressInputStream(rawInputStream, fileSize) { percent ->
                setProgressAsync(workDataOf("PROGRESS" to percent))
            }

            outputDir.mkdirs()

            if (isPng) {
                val tempPayload = File.createTempFile("temp_payload_dec", ".dat", applicationContext.cacheDir)
                FileOutputStream(tempPayload).use { out ->
                    StegoPng.extractFromPngStream(progressStream, out)
                }
                FileInputStream(tempPayload).use { input ->
                    StegoEngine.extractPayload(input, passphrase, outputDir)
                }
                tempPayload.delete()
            } else {
                StegoEngine.extractPayload(progressStream, passphrase, outputDir)
            }

            progressStream.close()

            setProgressAsync(workDataOf("PROGRESS" to 100))
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}
