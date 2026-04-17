package com.stegovault

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.stegovault.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnEncrypt.setOnClickListener {
            (activity as? MainActivity)?.navigateTo(EncryptFragment())
        }

        binding.btnDecrypt.setOnClickListener {
            (activity as? MainActivity)?.navigateTo(DecryptFragment())
        }

        binding.btnScanQr.setOnClickListener {
            (activity as? MainActivity)?.navigateTo(QrScannerFragment())
        }

        binding.btnSettings.setOnClickListener {
            (activity as? MainActivity)?.navigateTo(SettingsFragment())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}