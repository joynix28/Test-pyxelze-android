package com.stegovault

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.stegovault.databinding.FragmentToolsBinding

class ToolsFragment : Fragment() {

    private var _binding: FragmentToolsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentToolsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnClipboard.setOnClickListener {
            Toast.makeText(context, "Clipboard Locker not yet enabled", Toast.LENGTH_SHORT).show()
        }

        binding.btnDiagnostics.setOnClickListener {
            Toast.makeText(context, "Running Device Diagnostics...", Toast.LENGTH_SHORT).show()
            // Placeholder: would benchmark AES-GCM times to set low/medium/high
        }

        binding.btnCameraStego.setOnClickListener {
            Toast.makeText(context, "Instant Camera Vault triggered", Toast.LENGTH_SHORT).show()
        }

        binding.btnMultipart.setOnClickListener {
            Toast.makeText(context, "Multi-part Archive Manager", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}