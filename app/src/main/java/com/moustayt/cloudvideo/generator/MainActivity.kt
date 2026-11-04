package com.moustayt.cloudvideo.generator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NeoVidsScreen()
                }
            }
        }
    }
}

@Composable
fun NeoVidsScreen() {
    var prompt by remember { mutableStateOf("") }
    // Texte de statut stocké comme ressource -> il change de langue avec le téléphone
    var statusRes by remember { mutableIntStateOf(R.string.status_initial) }
    var sentPrompt by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.app_name), fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.subtitle), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text(stringResource(R.string.prompt_label)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        Button(
            onClick = {
                if (prompt.isBlank()) {
                    statusRes = R.string.status_empty
                } else {
                    sentPrompt = prompt
                    statusRes = R.string.status_ready
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.generate))
        }
        Text(
            if (statusRes == R.string.status_ready) stringResource(statusRes, sentPrompt)
            else stringResource(statusRes),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
