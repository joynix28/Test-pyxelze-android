package com.stegovault

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.io.File
import java.util.UUID

class EncryptFragment : Fragment() {

    private lateinit var tvSelectedFiles: TextView
    private lateinit var etPassphrase: EditText
    private lateinit var rbPng: RadioButton
    private lateinit var progressBar: ProgressBar
    private var selectedUris: List<String> = emptyList()

    private val selectFilesLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        selectedUris = uris.map { it.toString() }
        tvSelectedFiles.text = "${selectedUris.size} files selected"
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

        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0

        val isPng = rbPng.isChecked
        val outFileName = "stego_output_${UUID.randomUUID()}" + if (isPng) ".png" else ".stg"
        val outPath = File(requireContext().cacheDir, outFileName).absolutePath

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
                progressBar.progress = progress

                if (info.state.isFinished) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(context, "Done! Saved to $outPath", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
