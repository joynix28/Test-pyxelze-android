package com.stegovault

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.stegovault.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sharedPrefs = requireContext().getSharedPreferences("stegovault_prefs", Context.MODE_PRIVATE)

        val languages = arrayOf("System Default", "English", "Français")
        val languageCodes = arrayOf("", "en", "fr")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, languages)
        binding.spinnerLanguage.adapter = adapter

        val savedLang = sharedPrefs.getString("language", "System Default")
        val langIndex = languages.indexOf(savedLang).takeIf { it >= 0 } ?: 0
        binding.spinnerLanguage.setSelection(langIndex)

        binding.spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedLang = languages[position]
                if (savedLang != selectedLang) {
                    sharedPrefs.edit().putString("language", selectedLang).apply()
                    val localeCode = languageCodes[position]
                    val localeList = if (localeCode.isNotEmpty()) {
                        LocaleListCompat.forLanguageTags(localeCode)
                    } else {
                        LocaleListCompat.getEmptyLocaleList()
                    }
                    AppCompatDelegate.setApplicationLocales(localeList)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val iterations = sharedPrefs.getInt("pbkdf2_iterations", 200000)
        binding.etIterations.setText(iterations.toString())

        binding.etIterations.doAfterTextChanged {
            val iterStr = it.toString()
            val iterInt = iterStr.toIntOrNull()
            if (iterInt != null && iterInt > 0) {
                sharedPrefs.edit().putInt("pbkdf2_iterations", iterInt).apply()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}