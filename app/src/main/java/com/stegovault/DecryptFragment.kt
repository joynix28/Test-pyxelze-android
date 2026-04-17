package com.stegovault

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.stegovault.databinding.FragmentDecryptBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream

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
        uri?.let { performDecryption(it) }
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

        parentFragmentManager.setFragmentResultListener("qr_passphrase", viewLifecycleOwner) { _, bundle ->
            val scannedPass = bundle.getString("passphrase")
            if (scannedPass != null) {
                binding.etPassphrase.setText(scannedPass)
            }
        }

        binding.btnSelectFile.setOnClickListener {
            pickFileLauncher.launch("*/*")
        }

        binding.btnScanQr.setOnClickListener { (activity as? MainActivity)?.navigateTo(QrScannerFragment()) }

        binding.btnStartDecrypt.setOnClickListener {
            if (targetFile == null) {
                Toast.makeText(context, "Select file first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            selectOutputDirectoryLauncher.launch(null)
        }
    }

    private fun performDecryption(outputDirUri: Uri) {
        val passphrase = binding.etPassphrase.text.toString()
        val uri = targetFile ?: return

        binding.btnStartDecrypt.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val inStream = requireContext().contentResolver.openInputStream(uri)
                    val bytes = inStream?.readBytes() ?: throw Exception("Cannot read file")

                    var payload = bytes
                    val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
                    if (bytes.size > 8 && bytes.copyOfRange(0, 8).contentEquals(signature)) {
                        payload = StegoPng.extractFromPng(bytes) ?: throw Exception("No SVLT chunk found in PNG")
                    }

                    val (header, ciphertext) = StegoPng.parsePayload(payload)

                    val compressedData = CryptoEngine.decrypt(
                        ciphertext,
                        passphrase,
                        header.salt,
                        header.nonce,
                        header.iterations
                    )

                    val decompressedStream = ArchiveManager.decompress(ByteArrayInputStream(compressedData))

                    val rootDir = DocumentFile.fromTreeUri(requireContext(), outputDirUri)
                        ?: throw Exception("Invalid output directory")

                    ArchiveManager.unpack(decompressedStream, header.entryCount) { entryHeader, entryStream ->
                        val pathParts = entryHeader.path.split("/")
                        var currentDir = rootDir

                        // Navigate or create subdirectories
                        for (i in 0 until pathParts.size - 1) {
                            val dirName = pathParts[i]
                            currentDir = currentDir.findFile(dirName) ?: currentDir.createDirectory(dirName)
                                ?: throw Exception("Cannot create directory: $dirName")
                        }

                        val fileName = pathParts.last()
                        if (entryHeader.isDirectory) {
                            currentDir.findFile(fileName) ?: currentDir.createDirectory(fileName)
                        } else if (entryStream != null) {
                            val newFile = currentDir.createFile("application/octet-stream", fileName)
                                ?: throw Exception("Cannot create file: $fileName")

                            requireContext().contentResolver.openOutputStream(newFile.uri)?.use { outStream ->
                                val buffer = ByteArray(8192)
                                var bytesRead: Int
                                while (entryStream.read(buffer).also { bytesRead = it } != -1) {
                                    outStream.write(buffer, 0, bytesRead)
                                }
                            }
                        }
                    }
                }
                Toast.makeText(context, "Decryption successful", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Decryption failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnStartDecrypt.isEnabled = true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}