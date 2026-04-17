package com.stegovault

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.stegovault.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNavigation.setupWithNavController(navController)

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
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        when {
            Intent.ACTION_SEND == action && type != null -> {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (uri != null) {
                    val bundle = Bundle().apply { putParcelableArray("shared_uris", arrayOf(uri)) }
                    navController.navigate(R.id.nav_vault, bundle)
                    return
                }
            }
            Intent.ACTION_SEND_MULTIPLE == action && type != null -> {
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                if (uris != null) {
                    val bundle = Bundle().apply { putParcelableArray("shared_uris", uris.toTypedArray()) }
                    navController.navigate(R.id.nav_vault, bundle)
                    return
                }
            }
            Intent.ACTION_VIEW == action -> {
                val uri = intent.data
                if (uri != null) {
                    val bundle = Bundle().apply { putParcelable("view_uri", uri) }
                    navController.navigate(R.id.nav_vault, bundle)
                    return
                }
            }
        }

        val destination = intent.getStringExtra("destination")
        when (destination) {
            "vault" -> navController.navigate(R.id.nav_vault)
            "scan_qr" -> navController.navigate(R.id.nav_qr)
        }
    }
}