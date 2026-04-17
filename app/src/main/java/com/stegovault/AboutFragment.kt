package com.stegovault

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.stegovault.databinding.FragmentAboutBinding

class AboutFragment : Fragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvTitle.text = getString(R.string.about_title)
        binding.tvAuthor.text = getString(R.string.about_author)
        binding.tvWebsite.text = getString(R.string.about_website)
        binding.tvContact.text = getString(R.string.about_contact)
        binding.tvLicense.text = getString(R.string.about_license)
        binding.tvCredits.text = getString(R.string.about_credits)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}