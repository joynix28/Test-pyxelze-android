package com.pyxelze.roxify.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pyxelze.roxify.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.title_home)) }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = { navController.navigate("encrypt") },
                modifier = Modifier.fillMaxWidth().height(60.dp)
            ) {
                Text(stringResource(R.string.action_encrypt))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { navController.navigate("decrypt") },
                modifier = Modifier.fillMaxWidth().height(60.dp)
            ) {
                Text(stringResource(R.string.action_decrypt))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(
                    onClick = { navController.navigate("scanner") },
                    modifier = Modifier.weight(1f).height(60.dp).padding(end = 4.dp)
                ) {
                    Text(stringResource(R.string.action_scan_qr))
                }
                Button(
                    onClick = { navController.navigate("generate_qr") },
                    modifier = Modifier.weight(1f).height(60.dp).padding(start = 4.dp)
                ) {
                    Text("Generate QR")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { navController.navigate("settings") },
                modifier = Modifier.fillMaxWidth().height(60.dp)
            ) {
                Text(stringResource(R.string.action_settings))
            }
        }
    }
}
