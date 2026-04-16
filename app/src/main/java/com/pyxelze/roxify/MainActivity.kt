package com.pyxelze.roxify

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.pyxelze.roxify.ui.MainScreen

class MainActivity : ComponentActivity() {

    // Safely store URIs temporarily
    private var onImagePicked: ((Uri?) -> Unit)? = null
    private var onFilesPicked: ((List<Uri>) -> Unit)? = null
    private var onSaveImage: ((Uri?) -> Unit)? = null
    private var onExtractDir: ((Uri?) -> Unit)? = null

    // Launchers for picking files/directories/saving
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        onImagePicked?.invoke(uri)
    }

    private val pickFilesLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        onFilesPicked?.invoke(uris)
    }

    private val saveImageLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        onSaveImage?.invoke(uri)
    }

    private val extractDirLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        onExtractDir?.invoke(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MainScreen(
                onPickCarrierImage = { callback ->
                    onImagePicked = callback
                    pickImageLauncher.launch("image/*")
                },
                onPickFilesToHide = { callback ->
                    onFilesPicked = callback
                    pickFilesLauncher.launch("*/*")
                },
                onSaveStegoImage = { callback ->
                    onSaveImage = callback
                    saveImageLauncher.launch("stego_archive.png")
                },
                onPickExtractDirectory = { callback ->
                    onExtractDir = callback
                    extractDirLauncher.launch(null)
                },
                context = this
            )
        }
    }
}
