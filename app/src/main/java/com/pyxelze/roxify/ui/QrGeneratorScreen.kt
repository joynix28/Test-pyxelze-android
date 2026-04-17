package com.pyxelze.roxify.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pyxelze.roxify.core.QrHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrGeneratorScreen(navController: NavController) {
    var textToEncode by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Generate QR Code") }) }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = textToEncode,
                onValueChange = { textToEncode = it },
                label = { Text("Content (e.g. Passphrase/Metadata)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text("Warning: Anyone who scans this QR can read the contents.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(24.dp))

            if (textToEncode.isNotEmpty()) {
                val bitmap = remember(textToEncode) {
                    QrHelper.generateQrCode(textToEncode, 800)
                }
                Image(bitmap = bitmap.asImageBitmap(), contentDescription = "QR Code", modifier = Modifier.size(300.dp))
            }
        }
    }
}
