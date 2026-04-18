package com.stegovault

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.progressindicator.CircularProgressIndicator

class DecryptFragment : Fragment() {

    private lateinit var tvSelectedFile: TextView
    private lateinit var etPassphrase: EditText
    private lateinit var progressBar: CircularProgressIndicator
    private var selectedUri: String? = null

    private val selectFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        selectedUri = uri?.toString()
        tvSelectedFile.text = if (selectedUri != null) "File selected" else "No file selected"
    }

    private val selectOutputFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            executeDecryption(uri.toString())
        } else {
            Toast.makeText(context, "Extraction cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_decrypt, container, false)

        tvSelectedFile = view.findViewById(R.id.tv_selected_file)
        etPassphrase = view.findViewById(R.id.et_passphrase)
        progressBar = view.findViewById(R.id.progress_bar)
        progressBar.max = 100

        view.findViewById<Button>(R.id.btn_select_file).setOnClickListener {
            selectFileLauncher.launch(arrayOf("image/png", "application/octet-stream", "*/*"))
        }

        view.findViewById<Button>(R.id.btn_start_decrypt).setOnClickListener {
            startDecryption()
        }

        return view
    }

    private fun startDecryption() {
        if (selectedUri == null) {
            Toast.makeText(context, "No file selected", Toast.LENGTH_SHORT).show()
            return
        }

        // Prompt user for output directory using SAF DocumentTree
        selectOutputFolderLauncher.launch(null)
    }

    private fun executeDecryption(outDirUri: String) {
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0

        val data = Data.Builder()
            .putString("INPUT_URI", selectedUri)
            .putString("PASSPHRASE", etPassphrase.text.toString())
            .putString("OUTPUT_TREE_URI", outDirUri)
            .build()

        val request = OneTimeWorkRequestBuilder<DecodeWorker>()
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
                    Toast.makeText(context, "Done! Files extracted to folder.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
