package com.stegovault

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.stegovault.databinding.FragmentToolsBinding

class ToolsFragment : Fragment() {

    private var _binding: FragmentToolsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentToolsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnClipboard.setOnClickListener {
            // Simplified stub for Clipboard locker
            Toast.makeText(context, "Clipboard locker initiated", Toast.LENGTH_SHORT).show()
        }

        binding.btnDiagnostics.setOnClickListener {
            Toast.makeText(context, "Running diagnostic benchmark...", Toast.LENGTH_SHORT).show()
            // Stub for running device diagnostics to determine LOW/MEDIUM/HIGH profile
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}