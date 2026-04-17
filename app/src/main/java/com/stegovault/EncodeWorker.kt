package com.stegovault

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import androidx.documentfile.provider.DocumentFile

class EncodeWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val tempUncompressedFile = File.createTempFile("stego_pack", ".tmp", applicationContext.cacheDir)

        try {
            val inputUrisStr = inputData.getStringArray("input_uris") ?: return@withContext Result.failure()
            val outputUriStr = inputData.getString("output_uri") ?: return@withContext Result.failure()
            val passphrase = inputData.getString("passphrase")
            val isStego = inputData.getBoolean("is_stego", true)
            val isDirectory = inputData.getBoolean("is_directory", false)

            val outputUri = Uri.parse(outputUriStr)
            val entries = mutableListOf<ArchiveManager.ArchiveEntry>()

            setProgress(workDataOf("progress" to "Reading files..."))

            if (isDirectory && inputUrisStr.isNotEmpty()) {
                val rootUri = Uri.parse(inputUrisStr.first())
                val rootFile = DocumentFile.fromTreeUri(applicationContext, rootUri)
                if (rootFile != null) {
                    traverseDirectory(rootFile, rootFile.name ?: "root", entries)
                }
            } else {
                inputUrisStr.forEach { uriStr ->
                    val uri = Uri.parse(uriStr)
                    val docFile = DocumentFile.fromSingleUri(applicationContext, uri)
                    val name = docFile?.name ?: "unknown"
                    val size = docFile?.length() ?: 0L
                    entries.add(ArchiveManager.ArchiveEntry(name, false, size, uri))
                }
            }

            setProgress(workDataOf("progress" to "Packing archive..."))
            FileOutputStream(tempUncompressedFile).use { uncompressedStream ->
                ArchiveManager.pack(applicationContext, entries, uncompressedStream)
            }

            setProgress(workDataOf("progress" to "Compressing & Encrypting..."))

            val engine = DefaultStegoEngine(applicationContext)
            val options = StegoEngine.EncodeOptions(
                entryCount = entries.size,
                passphrase = passphrase?.takeIf { it.isNotEmpty() },
                mode = if (isStego) StegoEngine.EncodeMode.AUTO else StegoEngine.EncodeMode.COMPACT
            )

            setProgress(workDataOf("progress" to "Writing output..."))
            applicationContext.contentResolver.openOutputStream(outputUri)?.use { out ->
                if (isStego) {
                    FileInputStream(tempUncompressedFile).use { inStream ->
                        engine.encode(inStream, tempUncompressedFile.length(), out, options)
                    }
                } else {
                    // Extract payload only (archive.stg mode)
                    // First encode to a temp file, then extract the payload chunk
                    val tempStegoFile = File.createTempFile("stego_png", ".tmp", applicationContext.cacheDir)
                    try {
                        FileOutputStream(tempStegoFile).use { tempOut ->
                            FileInputStream(tempUncompressedFile).use { inStream ->
                                engine.encode(inStream, tempUncompressedFile.length(), tempOut, options)
                            }
                        }
                        FileInputStream(tempStegoFile).use { tempIn ->
                            StegoPng.extractFromPngStream(tempIn, out)
                        }
                    } finally {
                        tempStegoFile.delete()
                    }
                }
            }

            setProgress(workDataOf("progress" to "Complete"))
            Result.success(workDataOf("output_uri" to outputUriStr))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(workDataOf("error" to e.message))
        } finally {
            tempUncompressedFile.delete()
        }
    }

    private fun traverseDirectory(docFile: DocumentFile, currentPath: String, entries: MutableList<ArchiveManager.ArchiveEntry>) {
        if (docFile.isDirectory) {
            entries.add(ArchiveManager.ArchiveEntry(currentPath, true, 0, null))
            docFile.listFiles().forEach { child ->
                traverseDirectory(child, "$currentPath/${child.name}", entries)
            }
        } else {
            val size = docFile.length()
            entries.add(ArchiveManager.ArchiveEntry(currentPath, false, size, docFile.uri))
        }
    }
}