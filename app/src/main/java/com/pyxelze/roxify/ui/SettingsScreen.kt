package com.pyxelze.roxify.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pyxelze.roxify.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.title_settings)) }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text(stringResource(R.string.label_language), style = MaterialTheme.typography.titleMedium)
            Text("Auto (System) / English / Français - Follows system settings.")

            Spacer(modifier = Modifier.height(24.dp))

            Text(stringResource(R.string.label_iterations), style = MaterialTheme.typography.titleMedium)
            Text("200,000 (Fixed for maximum security standard)")

            Spacer(modifier = Modifier.height(24.dp))

            Text("Info", style = MaterialTheme.typography.titleMedium)
            Text("Pyxelze Roxify v1.0")
        }
    }
}
