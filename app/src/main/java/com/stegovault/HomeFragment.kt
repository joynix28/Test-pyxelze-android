package com.stegovault

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

class HomeFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        view.findViewById<Button>(R.id.btn_encrypt).setOnClickListener {
            findNavController().navigate(R.id.action_home_to_encrypt)
        }
        view.findViewById<Button>(R.id.btn_decrypt).setOnClickListener {
            findNavController().navigate(R.id.action_home_to_decrypt)
        }
        view.findViewById<Button>(R.id.btn_scan).setOnClickListener {
            findNavController().navigate(R.id.action_home_to_scanner)
        }
        return view
    }
}
