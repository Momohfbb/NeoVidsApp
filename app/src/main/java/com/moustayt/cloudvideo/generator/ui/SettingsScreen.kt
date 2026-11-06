package com.moustayt.cloudvideo.generator.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.moustayt.cloudvideo.generator.BuildConfig
import com.moustayt.cloudvideo.generator.R
import com.moustayt.cloudvideo.generator.data.AppViewModel
import com.moustayt.cloudvideo.generator.data.Backends
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: AppViewModel, onRecreate: () -> Unit) {
    val theme by vm.theme.collectAsState()
    val backend by vm.backend.collectAsState()
    var url by remember { mutableStateOf(vm.settings.backendUrl) }
    var ownerKey by remember { mutableStateOf(vm.settings.ownerKey) }
    var demo by remember { mutableStateOf(vm.settings.demoMode) }
    var smart by remember { mutableStateOf(vm.settings.smartPrompts) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineMedium)

        Section(Icons.Outlined.Language, stringResource(R.string.settings_language)) {
            LanguageChips(vm, onRecreate)
        }

        Section(Icons.Outlined.Palette, stringResource(R.string.settings_theme)) {
            ChipRow(
                listOf("system", "dark", "light", "dynamic"), theme,
                { stringResource(when (it) { "light" -> R.string.light; "dark" -> R.string.dark; "dynamic" -> R.string.dynamic; else -> R.string.system }) },
            ) { vm.setTheme(it) }
        }

        Section(Icons.Outlined.Psychology, stringResource(R.string.settings_ai)) {
            ToggleRow(stringResource(R.string.smart_title), stringResource(R.string.smart_hint), smart) {
                smart = it; vm.settings.smartPrompts = it
            }
        }

        Section(Icons.Outlined.Cloud, stringResource(R.string.settings_server)) {
            Text(
                stringResource(if (backend.isBlank()) R.string.mode_demo else R.string.mode_live) + " · " + backend.ifBlank { "—" },
                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                url, { url = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.settings_backend)) },
                placeholder = { Text(Backends.DEFAULT_BACKEND, maxLines = 1) },
                supportingText = { Text(stringResource(R.string.settings_backend_hint)) },
            )
            OutlinedTextField(
                ownerKey, { ownerKey = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.settings_owner_key)) },
                supportingText = { Text(stringResource(R.string.settings_owner_key_hint)) },
                visualTransformation = PasswordVisualTransformation(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = {
                    vm.settings.ownerKey = ownerKey
                    vm.setBackend(url)
                    Toast.makeText(ctx, R.string.settings_saved, Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.settings_save)) }
                OutlinedButton(
                    enabled = !testing,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        vm.settings.ownerKey = ownerKey
                        vm.setBackend(url)
                        testing = true
                        testResult = null
                        scope.launch {
                            val r = vm.testConnection()
                            testResult = r.fold(
                                onSuccess = {
                                    ctx.getString(
                                        R.string.conn_ok,
                                        ctx.getString(if (it.owner) R.string.yes else R.string.no),
                                        ctx.getString(if (it.ai) R.string.yes else R.string.no),
                                        it.providers.joinToString().ifBlank { "-" },
                                    )
                                },
                                onFailure = { ctx.getString(R.string.conn_fail, it.message ?: it.toString()) },
                            )
                            testing = false
                        }
                    },
                ) { Text(stringResource(if (testing) R.string.testing else R.string.test_connection)) }
            }
            testResult?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            ToggleRow(stringResource(R.string.settings_demo), stringResource(R.string.settings_demo_hint), demo) {
                demo = it; vm.setDemoMode(it)
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandMark(36.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Wordmark()
                Text(
                    stringResource(R.string.settings_about, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Section(icon: ImageVector, title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

@Composable
private fun ToggleRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked, onChange)
    }
}
