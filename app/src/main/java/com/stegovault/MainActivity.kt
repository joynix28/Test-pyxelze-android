package com.stegovault

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.stegovault.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when(item.itemId) {
                R.id.nav_home -> navigateTo(HomeFragment())
                R.id.nav_tools -> navigateTo(ToolsFragment())
                R.id.nav_settings -> navigateTo(SettingsFragment())
            }
            true
        }

        if (savedInstanceState == null) {
            handleIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        val type = intent.type

        when {
            Intent.ACTION_SEND == action && type != null -> {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (uri != null) {
                    val fragment = EncryptFragment().apply {
                        arguments = Bundle().apply {
                            putParcelableArray("shared_uris", arrayOf(uri))
                        }
                    }
                    navigateTo(fragment)
                    return
                }
            }
            Intent.ACTION_SEND_MULTIPLE == action && type != null -> {
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                if (uris != null) {
                    val fragment = EncryptFragment().apply {
                        arguments = Bundle().apply {
                            putParcelableArray("shared_uris", uris.toTypedArray())
                        }
                    }
                    navigateTo(fragment)
                    return
                }
            }
            Intent.ACTION_VIEW == action -> {
                val uri = intent.data
                if (uri != null) {
                    val fragment = DecryptFragment().apply {
                        arguments = Bundle().apply {
                            putParcelable("view_uri", uri)
                        }
                    }
                    navigateTo(fragment)
                    return
                }
            }
        }

        // Default routing
        val destination = intent.getStringExtra("destination")
        val fragment = when (destination) {
            "tools" -> ToolsFragment()
            "encrypt" -> EncryptFragment()
            "decrypt" -> DecryptFragment()
            "scan_qr" -> QrScannerFragment()
            else -> HomeFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    fun navigateTo(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }
}