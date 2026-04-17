package com.pyxelze.roxify.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.NavController
import com.pyxelze.roxify.R
import com.pyxelze.roxify.core.CryptoHelper
import com.pyxelze.roxify.core.CustomArchiveHelper
import com.pyxelze.roxify.core.StegoPngHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncryptScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var password by remember { mutableStateOf("") }
    var carrierUri by remember { mutableStateOf<Uri?>(null) }
    var filesToHide by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var isStegoMode by remember { mutableStateOf(true) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        carrierUri = uri
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        filesToHide = filesToHide + uris
    }
    val dirPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            filesToHide = filesToHide + uri
        }
    }

    val savePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(if (isStegoMode) "image/png" else "application/x-stegovault")) { uri ->
        if (uri != null) {
            isProcessing = true
            coroutineScope.launch {
                try {
                    performEncryption(context, password, carrierUri, filesToHide, uri, isStegoMode)
                    Toast.makeText(context, context.getString(R.string.msg_success), Toast.LENGTH_LONG).show()
                    navController.popBackStack()
                } catch (e: Exception) {
                    Toast.makeText(context, context.getString(R.string.msg_error, e.message), Toast.LENGTH_LONG).show()
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.title_encrypt)) }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.label_passphrase)) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isStegoMode, onCheckedChange = { isStegoMode = it })
                Text("Hide in PNG Image (Steganography)")
            }

            if (isStegoMode) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { imagePicker.launch("image/png") }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (carrierUri == null) "Select Carrier PNG" else "Carrier PNG Selected")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = { filePicker.launch("*/*") }, modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                    Text("Pick Files")
                }
                Button(onClick = { dirPicker.launch(null) }, modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                    Text("Pick Folders")
                }
            }
            if (filesToHide.isNotEmpty()) {
                Text("${filesToHide.size} items selected", style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (password.isEmpty() || filesToHide.isEmpty()) {
                        Toast.makeText(context, context.getString(R.string.msg_empty_fields), Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (isStegoMode && carrierUri == null) {
                        Toast.makeText(context, context.getString(R.string.msg_empty_fields), Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val defaultName = if (isStegoMode) "roxify_archive.png" else "roxify_archive.stg"
                    savePicker.launch(defaultName)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isProcessing
            ) {
                Text(if (isProcessing) stringResource(R.string.msg_processing) else stringResource(R.string.action_encrypt))
            }
        }
    }
}

private suspend fun performEncryption(context: Context, password: String, carrierUri: Uri?, fileUris: List<Uri>, saveUri: Uri, isStegoMode: Boolean) {
    withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "roxify_temp_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        val tempFiles = mutableListOf<File>()

        fun copyDocumentFile(docFile: DocumentFile, currentDir: File) {
            if (docFile.isDirectory) {
                val newDir = File(currentDir, docFile.name ?: "dir_${System.currentTimeMillis()}")
                newDir.mkdirs()
                tempFiles.add(newDir)
                docFile.listFiles().forEach { child ->
                    copyDocumentFile(child, newDir)
                }
            } else {
                val file = File(currentDir, docFile.name ?: "file_${System.currentTimeMillis()}")
                context.contentResolver.openInputStream(docFile.uri)?.use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                tempFiles.add(file)
            }
        }

        for (uri in fileUris) {
            // First check if it's a tree URI (directory)
            val docFile = try { DocumentFile.fromTreeUri(context, uri) } catch (e: Exception) { null }
            val singleFile = if (docFile == null || !docFile.isDirectory) DocumentFile.fromSingleUri(context, uri) else docFile

            singleFile?.let { copyDocumentFile(it, tempDir) }
        }

        val archiveBytes = CustomArchiveHelper.buildArchive(tempDir, tempFiles)
        val encryptedData = CryptoHelper.encrypt(archiveBytes, password.toCharArray(), tempFiles.size)

        if (isStegoMode && carrierUri != null) {
            context.contentResolver.openInputStream(carrierUri)?.use { carrierStream ->
                val newPngBytes = StegoPngHelper.embedChunk(carrierStream, encryptedData)

                context.contentResolver.openOutputStream(saveUri)?.use { outStream ->
                    outStream.write(newPngBytes)
                }
            } ?: throw IllegalArgumentException("Could not read carrier image")
        } else {
            // Standalone Archive Mode
            context.contentResolver.openOutputStream(saveUri)?.use { outStream ->
                outStream.write(encryptedData)
            }
        }

        tempDir.deleteRecursively()
    }
}
