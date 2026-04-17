package com.pyxelze.roxify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pyxelze.roxify.ui.DecryptScreen
import com.pyxelze.roxify.ui.EncryptScreen
import com.pyxelze.roxify.ui.HomeScreen
import com.pyxelze.roxify.ui.QrScannerScreen
import com.pyxelze.roxify.ui.QrGeneratorScreen
import com.pyxelze.roxify.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val widgetAction = intent.getStringExtra("WIDGET_ACTION")

        setContent {
            RoxifyApp(startDestination = getStartDestination(widgetAction))
        }
    }

    private fun getStartDestination(action: String?): String {
        return when (action) {
            "ENCRYPT" -> "encrypt"
            "DECRYPT" -> "decrypt"
            "SCAN" -> "scanner"
            else -> "home"
        }
    }
}

@Composable
fun RoxifyApp(startDestination: String) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = startDestination) {
        composable("home") {
            HomeScreen(navController)
        }
        composable("encrypt") {
            EncryptScreen(navController)
        }
        composable("decrypt") {
            DecryptScreen(navController)
        }
        composable("scanner") {
            QrScannerScreen { qrResult ->
                navController.popBackStack()
                navController.navigate("decrypt_with_pass/$qrResult")
            }
        }
        composable("generate_qr") {
            QrGeneratorScreen(navController)
        }
        composable("decrypt_with_pass/{pass}") { backStackEntry ->
            val pass = backStackEntry.arguments?.getString("pass") ?: ""
            DecryptScreen(navController, initialPassword = pass)
        }
        composable("settings") {
            SettingsScreen(navController)
        }
    }
}
