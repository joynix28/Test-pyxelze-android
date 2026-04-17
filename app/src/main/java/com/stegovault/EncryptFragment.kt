package com.stegovault

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.stegovault.databinding.FragmentEncryptBinding
import kotlinx.coroutines.launch

class EncryptFragment : Fragment() {

    private var _binding: FragmentEncryptBinding? = null
    private val binding get() = _binding!!

    private val selectedUris = mutableListOf<Uri>()
    private var isDirectorySelected = false

    private val pickFilesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            selectedUris.clear()
            selectedUris.addAll(uris)
            isDirectorySelected = false
            Toast.makeText(context, "${uris.size} files selected", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUris.clear()
            selectedUris.add(uri)
            isDirectorySelected = true
            Toast.makeText(context, "Folder selected", Toast.LENGTH_SHORT).show()
        }
    }

    private val saveFileLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri: Uri? ->
        uri?.let { startWorkManagerEncryption(it) }
    }

    private val saveStgLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        uri?.let { startWorkManagerEncryption(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEncryptBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {


        super.onViewCreated(view, savedInstanceState)

        arguments?.getParcelableArray("shared_uris")?.let { arr ->
            selectedUris.clear()
            arr.forEach { uri -> selectedUris.add(uri as Uri) }
            Toast.makeText(context, "${selectedUris.size} shared files received", Toast.LENGTH_SHORT).show()
        }



        binding.btnSelectFiles.setOnClickListener {
            pickFilesLauncher.launch("*/*")
        }

        binding.btnSelectFolder.setOnClickListener {
            pickFolderLauncher.launch(null)
        }

        binding.btnGenerateQr.setOnClickListener {
            val passphrase = binding.etPassphrase.text.toString()
            if (passphrase.isEmpty()) {
                Toast.makeText(context, "Enter passphrase to generate QR", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(requireContext())
                .setTitle("Security Warning")
                .setMessage("Generating a QR code with your passphrase means anyone scanning it can read it. Proceed?")
                .setPositiveButton("Yes") { _, _ ->
                    generateQrCode(passphrase)
                }
                .setNegativeButton("No", null)
                .show()
        }

        binding.btnStartEncrypt.setOnClickListener {
            if (selectedUris.isEmpty()) {
                Toast.makeText(context, "Select files or folder first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val isStego = binding.rbStegoPng.isChecked
            if (isStego) {
                saveFileLauncher.launch("stego_archive.png")
            } else {
                saveStgLauncher.launch("archive.stg")
            }
        }
    }

    private fun generateQrCode(text: String) {
        try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            binding.ivQrCode.setImageBitmap(bitmap)
            binding.ivQrCode.visibility = View.VISIBLE
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to generate QR", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startWorkManagerEncryption(outputUri: Uri) {
        val passphrase = binding.etPassphrase.text.toString()
        val isStego = binding.rbStegoPng.isChecked

        binding.btnStartEncrypt.isEnabled = false

        val inputData = Data.Builder()
            .putStringArray("input_uris", selectedUris.map { it.toString() }.toTypedArray())
            .putString("output_uri", outputUri.toString())
            .putString("passphrase", passphrase)
            .putBoolean("is_stego", isStego)
            .putBoolean("is_directory", isDirectorySelected)
            .build()

        val encodeWorkRequest = OneTimeWorkRequestBuilder<EncodeWorker>()
            .setInputData(inputData)
            .build()

        binding.progressBar.visibility = View.VISIBLE
        binding.tvProgress.visibility = View.VISIBLE

        val workManager = WorkManager.getInstance(requireContext())
        workManager.enqueue(encodeWorkRequest)

        workManager.getWorkInfoByIdLiveData(encodeWorkRequest.id).observe(viewLifecycleOwner) { workInfo ->
            if (workInfo != null) {
                val progress = workInfo.progress.getString("progress")
                val percent = workInfo.progress.getInt("progress_percent", -1)

                if (progress != null) {
                    binding.tvProgress.text = if (percent >= 0) "$progress ($percent%)" else progress
                }

                if (percent >= 0) {
                    binding.progressBar.isIndeterminate = false
                    binding.progressBar.progress = percent
                } else {
                    binding.progressBar.isIndeterminate = true
                }

                if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                    binding.progressBar.visibility = View.GONE
                    binding.tvProgress.text = "Complete"
                    Toast.makeText(context, "Encryption successful", Toast.LENGTH_SHORT).show()
                    binding.btnStartEncrypt.isEnabled = true
                } else if (workInfo.state == WorkInfo.State.FAILED) {
                    binding.progressBar.visibility = View.GONE
                    val error = workInfo.outputData.getString("error")
                    binding.tvProgress.text = "Error: $error"
                    Toast.makeText(context, "Encryption failed: $error", Toast.LENGTH_LONG).show()
                    binding.btnStartEncrypt.isEnabled = true
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}