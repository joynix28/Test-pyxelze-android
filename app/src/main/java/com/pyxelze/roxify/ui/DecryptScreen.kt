package com.pyxelze.roxify.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecryptScreen(navController: NavController, initialPassword: String = "") {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var password by remember { mutableStateOf(initialPassword) }
    var archiveUri by remember { mutableStateOf<Uri?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        archiveUri = uri
    }

    val destPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null && archiveUri != null) {
            isProcessing = true
            coroutineScope.launch {
                try {
                    performExtraction(context, password, archiveUri!!, uri)
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
        topBar = { TopAppBar(title = { Text(stringResource(R.string.title_decrypt)) }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.label_passphrase)) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = { filePicker.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
                Text(if (archiveUri == null) "Select Stego PNG or .stg Archive" else "Archive Selected")
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (password.isEmpty() || archiveUri == null) {
                        Toast.makeText(context, context.getString(R.string.msg_empty_fields), Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    destPicker.launch(null)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isProcessing
            ) {
                Text(if (isProcessing) stringResource(R.string.msg_processing) else stringResource(R.string.action_decrypt))
            }
        }
    }
}

private suspend fun performExtraction(context: Context, password: String, archiveUri: Uri, destUri: Uri) {
    withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(archiveUri)
            ?: throw IllegalArgumentException("Could not read input file")

        val inputBytes = inputStream.readBytes()
        inputStream.close()

        // Detect if it's a PNG or raw archive
        val encryptedData = if (inputBytes.size > 8 && inputBytes[0] == 137.toByte() && inputBytes[1] == 80.toByte()) {
            StegoPngHelper.extractChunk(inputBytes.inputStream())
        } else {
            inputBytes // Assume it's a raw .stg file
        }

        val (archiveBytes, entryCount, isCompressed) = CryptoHelper.decrypt(encryptedData, password.toCharArray())

        val extractDir = File(context.cacheDir, "roxify_extract_${System.currentTimeMillis()}")
        extractDir.mkdirs()
        CustomArchiveHelper.extractArchive(archiveBytes, extractDir, entryCount, isCompressed)

        val destDoc = DocumentFile.fromTreeUri(context, destUri) ?: throw IllegalArgumentException("Cannot access destination")

        fun copyToSaf(source: File, target: DocumentFile) {
            source.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    val newDir = target.createDirectory(file.name) ?: target
                    copyToSaf(file, newDir)
                } else {
                    val newFile = target.createFile("*/*", file.name)
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

        copyToSaf(extractDir, destDoc)
        extractDir.deleteRecursively()
    }
}
