package com.stegovault

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.progressindicator.CircularProgressIndicator
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

    private val saveFileLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) {
            executeEncryption(uri.toString())
        } else {
            Toast.makeText(context, "Encryption cancelled", Toast.LENGTH_SHORT).show()
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

        val isPng = rbPng.isChecked
        val defaultName = "StegoVault_${UUID.randomUUID().toString().substring(0,8)}" + if (isPng) ".png" else ".stg"
        val mimeType = if (isPng) "image/png" else "application/octet-stream"

        // Use reflection workaround if CreateDocument does not support dynamic mimetype easily, but standard behavior allows overriding.
        // For simplicity, we just prompt the user where to save the file.
        saveFileLauncher.launch(defaultName)
    }

    private fun executeEncryption(outUri: String) {
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0

        val isPng = rbPng.isChecked

        val data = Data.Builder()
            .putStringArray("FILE_URIS", selectedUris.toTypedArray())
            .putString("PASSPHRASE", etPassphrase.text.toString())
            .putBoolean("IS_PNG", isPng)
            .putString("OUTPUT_URI", outUri)
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

                if (info.state.isFinished) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(context, "Done! File saved.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
