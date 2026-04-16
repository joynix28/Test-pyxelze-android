package com.pyxelze.roxify.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.pyxelze.roxify.core.ArchiveHelper
import com.pyxelze.roxify.core.CryptoHelper
import com.pyxelze.roxify.core.SteganographyHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onPickCarrierImage: ((Uri?) -> Unit) -> Unit,
    onPickFilesToHide: ((List<Uri>) -> Unit) -> Unit,
    onSaveStegoImage: ((Uri?) -> Unit) -> Unit,
    onPickExtractDirectory: ((Uri?) -> Unit) -> Unit,
    context: Context
) {
    var password by remember { mutableStateOf("") }
    var carrierImageUri by remember { mutableStateOf<Uri?>(null) }
    var filesToHide by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var stegoImageUri by remember { mutableStateOf<Uri?>(null) }

    var isProcessing by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Pyxelze Roxify") })
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Text("Hide Files", modifier = Modifier.padding(16.dp))
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                    Text("Extract Files", modifier = Modifier.padding(16.dp))
                }
            }

            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedTab == 0) {
                    // Hide Files Mode
                    Button(onClick = {
                        onPickCarrierImage { uri -> carrierImageUri = uri }
                    }) {
                        Text(if (carrierImageUri == null) "Select Carrier Image (PNG)" else "Carrier Image Selected")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(onClick = {
                        onPickFilesToHide { uris -> filesToHide = uris }
                    }) {
                        Text(if (filesToHide.isEmpty()) "Select Files to Hide" else "${filesToHide.size} Files Selected")
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (password.isEmpty() || carrierImageUri == null || filesToHide.isEmpty()) {
                                Toast.makeText(context, "Fill all fields", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            onSaveStegoImage { saveUri ->
                                if (saveUri != null) {
                                    isProcessing = true
                                    coroutineScope.launch {
                                        try {
                                            hideFiles(context, password, carrierImageUri!!, filesToHide, saveUri)
                                            Toast.makeText(context, "Files successfully hidden", Toast.LENGTH_LONG).show()
                                            // Reset
                                            carrierImageUri = null
                                            filesToHide = emptyList()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                        } finally {
                                            isProcessing = false
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !isProcessing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isProcessing) "Processing..." else "Hide and Save PNG")
                    }
                } else {
                    // Extract Files Mode
                    Button(onClick = {
                        onPickCarrierImage { uri -> stegoImageUri = uri }
                    }) {
                        Text(if (stegoImageUri == null) "Select Stego Image (PNG)" else "Stego Image Selected")
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (password.isEmpty() || stegoImageUri == null) {
                                Toast.makeText(context, "Fill all fields", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            onPickExtractDirectory { extractDirUri ->
                                if (extractDirUri != null) {
                                    isProcessing = true
                                    coroutineScope.launch {
                                        try {
                                            extractFiles(context, password, stegoImageUri!!, extractDirUri)
                                            Toast.makeText(context, "Files successfully extracted", Toast.LENGTH_LONG).show()
                                            // Reset
                                            stegoImageUri = null
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                        } finally {
                                            isProcessing = false
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !isProcessing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isProcessing) "Processing..." else "Extract Files")
                    }
                }
            }
        }
    }
}

suspend fun hideFiles(context: Context, password: String, carrierUri: Uri, fileUris: List<Uri>, saveUri: Uri) {
    withContext(Dispatchers.IO) {
        // 1. Copy selected files to cache dir
        val tempFiles = mutableListOf<File>()
        for (uri in fileUris) {
            val docFile = DocumentFile.fromSingleUri(context, uri)
            val tempFile = File(context.cacheDir, docFile?.name ?: "temp_${System.currentTimeMillis()}")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            tempFiles.add(tempFile)
        }

        // 2. Compress files
        val zipFile = File(context.cacheDir, "archive.zip")
        ArchiveHelper.compressFiles(tempFiles, zipFile)

        // 3. Encrypt
        val zipBytes = zipFile.readBytes()
        val encryptedData = CryptoHelper.encrypt(zipBytes, password.toCharArray())

        // 4. Steganography - Hide
        val carrierBitmap = context.contentResolver.openInputStream(carrierUri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: throw IllegalArgumentException("Cannot decode carrier image")

        val stegoBitmap = SteganographyHelper.encode(carrierBitmap, encryptedData)

        // 5. Save to destination
        context.contentResolver.openOutputStream(saveUri)?.use { out ->
            stegoBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        // Cleanup
        tempFiles.forEach { it.delete() }
        zipFile.delete()
        carrierBitmap.recycle()
        stegoBitmap.recycle()
    }
}

suspend fun extractFiles(context: Context, password: String, stegoUri: Uri, extractDirUri: Uri) {
    withContext(Dispatchers.IO) {
        // 1. Read stego image
        val stegoBitmap = context.contentResolver.openInputStream(stegoUri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: throw IllegalArgumentException("Cannot decode stego image")

        // 2. Steganography - Decode
        val encryptedData = SteganographyHelper.decode(stegoBitmap)

        // 3. Decrypt
        val zipBytes = CryptoHelper.decrypt(encryptedData, password.toCharArray())

        // 4. Write zip to cache
        val zipFile = File(context.cacheDir, "extracted.zip")
        zipFile.writeBytes(zipBytes)

        // 5. Decompress to destination
        val destDir = DocumentFile.fromTreeUri(context, extractDirUri)
            ?: throw IllegalArgumentException("Cannot open destination directory")

        // Decompress to local cache first
        val localExtractDir = File(context.cacheDir, "extracted_files")
        if (localExtractDir.exists()) localExtractDir.deleteRecursively()
        localExtractDir.mkdirs()

        ArchiveHelper.decompressFiles(zipFile, localExtractDir)

        // 6. Copy extracted files to chosen SAF directory
        fun copyFilesToSaf(sourceDir: File, targetDir: DocumentFile) {
            sourceDir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    val newDir = targetDir.createDirectory(file.name) ?: targetDir
                    copyFilesToSaf(file, newDir)
                } else {
                    val newFile = targetDir.createFile("*/*", file.name)
                    if (newFile != null) {
                        context.contentResolver.openOutputStream(newFile.uri)?.use { out ->
                            file.inputStream().use { input ->
                                input.copyTo(out)
                            }
                        }
                    }
                }
            }
        }
        copyFilesToSaf(localExtractDir, destDir)

        // Cleanup
        zipFile.delete()
        localExtractDir.deleteRecursively()
        stegoBitmap.recycle()
    }
}
