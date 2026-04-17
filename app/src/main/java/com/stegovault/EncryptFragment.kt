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
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.stegovault.databinding.FragmentEncryptBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

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
        uri?.let { performEncryption(it) }
    }

    private val saveStgLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        uri?.let { performEncryption(it) }
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

    private fun performEncryption(outputUri: Uri) {
        val passphrase = binding.etPassphrase.text.toString()
        val isStego = binding.rbStegoPng.isChecked

        binding.btnStartEncrypt.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val entries = mutableListOf<ArchiveManager.ArchiveEntry>()

                    if (isDirectorySelected) {
                        val rootUri = selectedUris.first()
                        val rootFile = DocumentFile.fromTreeUri(requireContext(), rootUri)
                        if (rootFile != null) {
                            traverseDirectory(rootFile, rootFile.name ?: "root", entries)
                        }
                    } else {
                        selectedUris.forEach { uri ->
                            val name = getFileName(uri) ?: "unknown"
                            val size = getFileSize(uri)
                            val inputStream = requireContext().contentResolver.openInputStream(uri)
                            entries.add(ArchiveManager.ArchiveEntry(name, false, size, inputStream))
                        }
                    }

                    val salt = CryptoEngine.generateSalt()
                    val nonce = CryptoEngine.generateNonce()

                    val uncompressedStream = ByteArrayOutputStream()
                    ArchiveManager.pack(entries, uncompressedStream)
                    val uncompressedData = uncompressedStream.toByteArray()

                    val compressedStream = ByteArrayOutputStream()
                    val deflaterStream = ArchiveManager.compress(compressedStream)
                    deflaterStream.write(uncompressedData)
                    deflaterStream.close()
                    val compressedData = compressedStream.toByteArray()

                    val encryptedData = CryptoEngine.encrypt(compressedData, passphrase, salt, nonce)

                    val header = StegoPng.Header(
                        isEncrypted = passphrase.isNotEmpty(),
                        salt = salt,
                        nonce = nonce,
                        iterations = requireContext().getSharedPreferences("stegovault_prefs", android.content.Context.MODE_PRIVATE).getInt("pbkdf2_iterations", 200000),
                        entryCount = entries.size,
                        payloadSize = compressedData.size.toLong()
                    )

                    val payload = StegoPng.createPayload(header, encryptedData)

                    if (isStego) {
                        val basePng = generateDummyPng()
                        val finalPng = StegoPng.embedIntoPng(basePng, payload)
                        requireContext().contentResolver.openOutputStream(outputUri)?.use { out ->
                            out.write(finalPng)
                        }
                    } else {
                        requireContext().contentResolver.openOutputStream(outputUri)?.use { out ->
                            out.write(payload)
                        }
                    }
                }
                Toast.makeText(context, "Encryption successful", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Encryption failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnStartEncrypt.isEnabled = true
            }
        }
    }

    private fun traverseDirectory(docFile: DocumentFile, currentPath: String, entries: MutableList<ArchiveManager.ArchiveEntry>) {
        if (docFile.isDirectory) {
            entries.add(ArchiveManager.ArchiveEntry(currentPath, true, 0, null))
            docFile.listFiles().forEach { child ->
                traverseDirectory(child, "$currentPath/${child.name}", entries)
            }
        } else {
            val size = docFile.length()
            val inputStream = requireContext().contentResolver.openInputStream(docFile.uri)
            entries.add(ArchiveManager.ArchiveEntry(currentPath, false, size, inputStream))
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) result = cursor.getString(nameIndex)
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    private fun getFileSize(uri: Uri): Long {
        var result: Long = 0
        if (uri.scheme == "content") {
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) result = cursor.getLong(sizeIndex)
                }
            }
        }
        return result
    }

    private fun generateDummyPng(): ByteArray {
        val width = 512
        val height = 512
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        val paint = android.graphics.Paint()
        val shader = android.graphics.LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            Color.parseColor("#1a2a6c"),
            Color.parseColor("#b21f1f"),
            android.graphics.Shader.TileMode.CLAMP
        )
        paint.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        paint.shader = null
        paint.color = Color.WHITE
        paint.textSize = 48f
        paint.textAlign = android.graphics.Paint.Align.CENTER
        canvas.drawText("StegoVault Archive", width / 2f, height / 2f, paint)

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    private fun _generateDummyPngOld(): ByteArray {
        val base64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII="
        return android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}