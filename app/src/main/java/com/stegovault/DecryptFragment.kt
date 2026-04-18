package com.stegovault

import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
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

class DecryptFragment : Fragment() {

    private lateinit var tvSelectedFile: TextView
    private lateinit var etPassphrase: EditText
    private lateinit var progressBar: CircularProgressIndicator
    private var selectedUri: String? = null

    private val selectFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        selectedUri = uri?.toString()
        tvSelectedFile.text = if (selectedUri != null) "File selected" else "No file selected"
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            proceedWithDecryption()
        } else {
            Toast.makeText(context, "Storage permission required", Toast.LENGTH_SHORT).show()
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

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }

        proceedWithDecryption()
    }

    private fun proceedWithDecryption() {
        // Output directly to standard Downloads folder to bypass slow SAF prompt loop
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val stegoDir = File(downloadsDir, "StegoVault/Extracted_${UUID.randomUUID().toString().substring(0,8)}")
        if (!stegoDir.exists()) stegoDir.mkdirs()

        executeDecryption(stegoDir.absolutePath)
    }

    private fun executeDecryption(outDirPath: String) {
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0

        val data = Data.Builder()
            .putString("INPUT_URI", selectedUri)
            .putString("PASSPHRASE", etPassphrase.text.toString())
            .putString("OUTPUT_PATH", outDirPath)
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

                if (info.state == androidx.work.WorkInfo.State.SUCCEEDED) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(context, "Done! Extracted to Downloads/StegoVault", Toast.LENGTH_LONG).show()
                } else if (info.state == androidx.work.WorkInfo.State.FAILED) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(context, "Decryption failed. Incorrect passphrase or corrupt file.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
