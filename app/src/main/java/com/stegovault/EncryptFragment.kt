package com.stegovault

import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.io.File
import java.util.UUID

class EncryptFragment : Fragment() {

    private lateinit var tvSelectedFiles: TextView
    private lateinit var etPassphrase: EditText
    private lateinit var rbPng: RadioButton
    private lateinit var progressBar: CircularProgressIndicator
    private var selectedUris: List<String> = emptyList()

    private val selectFilesLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        selectedUris = uris.map { it.toString() }
        tvSelectedFiles.text = "${selectedUris.size} files selected"
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            proceedWithEncryption()
        } else {
            Toast.makeText(context, "Storage permission required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_encrypt, container, false)

        tvSelectedFiles = view.findViewById(R.id.tv_selected_files)
        etPassphrase = view.findViewById(R.id.et_passphrase)
        rbPng = view.findViewById(R.id.rb_png)
        progressBar = view.findViewById(R.id.progress_bar)
        progressBar.max = 100

        view.findViewById<Button>(R.id.btn_select_files).setOnClickListener {
            selectFilesLauncher.launch(arrayOf("*/*"))
        }

        view.findViewById<Button>(R.id.btn_start_encrypt).setOnClickListener {
            startEncryption()
        }

        return view
    }

    private fun startEncryption() {
        if (selectedUris.isEmpty()) {
            Toast.makeText(context, "No files selected", Toast.LENGTH_SHORT).show()
            return
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }

        proceedWithEncryption()
    }

    private fun proceedWithEncryption() {
        val isPng = rbPng.isChecked
        val defaultName = "StegoVault_${UUID.randomUUID().toString().substring(0,8)}" + if (isPng) ".png" else ".stg"

        // Output directly to standard Downloads folder to bypass slow SAF prompt loop
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val stegoDir = File(downloadsDir, "StegoVault")
        if (!stegoDir.exists()) stegoDir.mkdirs()

        val outFile = File(stegoDir, defaultName)
        executeEncryption(outFile.absolutePath)
    }

    private fun executeEncryption(outPath: String) {
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0

        val isPng = rbPng.isChecked

        val data = Data.Builder()
            .putStringArray("FILE_URIS", selectedUris.toTypedArray())
            .putString("PASSPHRASE", etPassphrase.text.toString())
            .putBoolean("IS_PNG", isPng)
            .putString("OUTPUT_PATH", outPath)
            .build()

        val request = OneTimeWorkRequestBuilder<EncodeWorker>()
            .setInputData(data)
            .build()

        WorkManager.getInstance(requireContext()).enqueue(request)

        WorkManager.getInstance(requireContext()).getWorkInfoByIdLiveData(request.id).observe(viewLifecycleOwner) { info ->
            if (info != null) {
                val progress = info.progress.getInt("PROGRESS", 0)
                if (progress > 0) {
                    progressBar.isIndeterminate = false
                    progressBar.progress = progress
                }

                if (info.state == androidx.work.WorkInfo.State.SUCCEEDED) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(context, "Done! Saved to Downloads/StegoVault", Toast.LENGTH_LONG).show()
                } else if (info.state == androidx.work.WorkInfo.State.FAILED) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(context, "Encryption failed. Ensure files are valid and space is available.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
