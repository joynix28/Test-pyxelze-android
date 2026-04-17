package com.stegovault

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.stegovault.databinding.FragmentDecryptBinding

class DecryptFragment : Fragment() {

    private var _binding: FragmentDecryptBinding? = null
    private val binding get() = _binding!!

    private var targetFile: Uri? = null

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            targetFile = uri
            Toast.makeText(context, "File selected", Toast.LENGTH_SHORT).show()
        }
    }

    private val selectOutputDirectoryLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { startWorkManagerDecryption(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDecryptBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {


        super.onViewCreated(view, savedInstanceState)

        arguments?.getParcelable<Uri>("view_uri")?.let { uri ->
            targetFile = uri
            Toast.makeText(context, "Shared file ready to decrypt", Toast.LENGTH_SHORT).show()
        }



        parentFragmentManager.setFragmentResultListener("qr_passphrase", viewLifecycleOwner) { _, bundle ->
            val scannedPass = bundle.getString("passphrase")
            if (scannedPass != null) {
                binding.etPassphrase.setText(scannedPass)
            }
        }

        binding.btnSelectFile.setOnClickListener {
            pickFileLauncher.launch("*/*")
        }

        binding.btnScanQr.setOnClickListener { requireActivity().supportFragmentManager.findFragmentById(R.id.nav_host_fragment)?.let { (it as androidx.navigation.fragment.NavHostFragment).navController.navigate(R.id.nav_qr) } }

        binding.btnStartDecrypt.setOnClickListener {
            if (targetFile == null) {
                Toast.makeText(context, "Select file first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            selectOutputDirectoryLauncher.launch(null)
        }
    }

    private fun startWorkManagerDecryption(outputDirUri: Uri) {
        val passphrase = binding.etPassphrase.text.toString()
        val uri = targetFile ?: return

        binding.btnStartDecrypt.isEnabled = false

        val inputData = Data.Builder()
            .putString("input_uri", uri.toString())
            .putString("output_dir_uri", outputDirUri.toString())
            .putString("passphrase", passphrase)
            .build()

        val decodeWorkRequest = OneTimeWorkRequestBuilder<DecodeWorker>()
            .setInputData(inputData)
            .build()

        binding.progressBar.visibility = View.VISIBLE
        binding.tvProgress.visibility = View.VISIBLE

        val workManager = WorkManager.getInstance(requireContext())
        workManager.enqueue(decodeWorkRequest)

        workManager.getWorkInfoByIdLiveData(decodeWorkRequest.id).observe(viewLifecycleOwner) { workInfo ->
            if (workInfo != null) {
                val progress = workInfo.progress.getString("progress")
                if (progress != null) {
                    binding.tvProgress.text = progress
                }
                if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                    binding.progressBar.visibility = View.GONE
                    binding.tvProgress.text = "Complete"
                    Toast.makeText(context, "Decryption successful", Toast.LENGTH_SHORT).show()
                    binding.btnStartDecrypt.isEnabled = true
                } else if (workInfo.state == WorkInfo.State.FAILED) {
                    binding.progressBar.visibility = View.GONE
                    val error = workInfo.outputData.getString("error")
                    binding.tvProgress.text = "Error: $error"
                    Toast.makeText(context, "Decryption failed: $error", Toast.LENGTH_LONG).show()
                    binding.btnStartDecrypt.isEnabled = true
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}