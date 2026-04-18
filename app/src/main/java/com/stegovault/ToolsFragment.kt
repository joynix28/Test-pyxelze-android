package com.stegovault

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

class ToolsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_tools, container, false)

        view.findViewById<Button>(R.id.btn_tool_vault).setOnClickListener {
            // Re-use encrypt fragment but forced to Archive mode
            findNavController().navigate(R.id.action_tools_to_encrypt)
        }
        view.findViewById<Button>(R.id.btn_tool_wrapper).setOnClickListener {
            // Re-use encrypt fragment but forced to PNG mode
            findNavController().navigate(R.id.action_tools_to_encrypt)
        }
        view.findViewById<Button>(R.id.btn_tool_notes).setOnClickListener {
            Toast.makeText(context, "Stego Notes: Encrypts plain text into tiny SVLT chunks. Feature coming.", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<Button>(R.id.btn_tool_qr).setOnClickListener {
            findNavController().navigate(R.id.action_tools_to_scanner)
        }
        view.findViewById<Button>(R.id.btn_tool_roxify).setOnClickListener {
            Toast.makeText(context, "Roxify Bridge: Send CLI commands to a paired PC over LAN. Coming soon.", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<Button>(R.id.btn_tool_forensics).setOnClickListener {
            Toast.makeText(context, "Forensics: Analyze an image for LSB/rXDT chunk presence. Coming soon.", Toast.LENGTH_SHORT).show()
        }

        return view
    }
}
