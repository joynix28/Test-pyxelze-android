package com.stegovault

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

class ToolsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_tools, container, false)

        view.findViewById<Button>(R.id.btn_tool_vault).setOnClickListener {
            findNavController().navigate(R.id.action_tools_to_encrypt)
        }
        view.findViewById<Button>(R.id.btn_tool_wrapper).setOnClickListener {
            findNavController().navigate(R.id.action_tools_to_encrypt)
        }
        view.findViewById<Button>(R.id.btn_tool_qr).setOnClickListener {
            findNavController().navigate(R.id.action_tools_to_scanner)
        }

        return view
    }
}
