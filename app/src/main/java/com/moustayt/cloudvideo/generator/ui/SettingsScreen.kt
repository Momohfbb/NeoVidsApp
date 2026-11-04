package com.moustayt.cloudvideo.generator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moustayt.cloudvideo.generator.R
import com.moustayt.cloudvideo.generator.data.AppViewModel

@Composable
fun SettingsScreen(vm: AppViewModel, onRecreate: () -> Unit) {
    val theme by vm.theme.collectAsState()
    val backend by vm.backend.collectAsState()
    var url by remember { mutableStateOf(vm.settings.backendUrl) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
        LanguageChips(vm, onRecreate)

        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
        ChipRow(
            listOf("system", "light", "dark", "dynamic"), theme,
            { stringResource(when (it) { "light" -> R.string.light; "dark" -> R.string.dark; "dynamic" -> R.string.dynamic; else -> R.string.system }) },
        ) { vm.setTheme(it) }

        Text(stringResource(R.string.settings_backend), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.settings_backend_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("https://neovids.example.workers.dev") })
        Button(onClick = { vm.setBackend(url) }) { Text(stringResource(R.string.settings_save)) }
        Text(
            stringResource(if (backend.isBlank()) R.string.mode_demo else R.string.mode_live),
            color = MaterialTheme.colorScheme.primary,
        )

        Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
