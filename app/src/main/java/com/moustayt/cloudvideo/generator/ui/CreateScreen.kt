package com.moustayt.cloudvideo.generator.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.moustayt.cloudvideo.generator.R
import com.moustayt.cloudvideo.generator.data.AppViewModel
import com.moustayt.cloudvideo.generator.data.GenRequest
import com.moustayt.cloudvideo.generator.data.Kind
import com.moustayt.cloudvideo.generator.encodeImage
import kotlinx.coroutines.launch

@Composable
fun CreateScreen(vm: AppViewModel, onSubmitted: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val backend by vm.backend.collectAsState()

    var kind by remember { mutableStateOf(Kind.TEXT_TO_VIDEO) }
    var prompt by remember { mutableStateOf("") }
    var style by remember { mutableStateOf("cinematic") }
    var aspect by remember { mutableStateOf("16:9") }
    var duration by remember { mutableStateOf(5) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var imageData by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            imageUri = uri
            scope.launch { imageData = encodeImage(ctx, uri) }
        }
    }

    val kindLabels = mapOf(
        Kind.TEXT_TO_VIDEO to R.string.kind_t2v, Kind.IMAGE_TO_VIDEO to R.string.kind_i2v,
        Kind.TEXT_TO_IMAGE to R.string.kind_t2i, Kind.IMAGE_EDIT to R.string.kind_edit,
    )
    val styleLabels = mapOf(
        "cinematic" to R.string.style_cinematic, "anime" to R.string.style_anime, "realistic" to R.string.style_realistic,
        "3d" to R.string.style_3d, "watercolor" to R.string.style_watercolor,
    )
    val ideas = listOf(R.string.idea1, R.string.idea2, R.string.idea3, R.string.idea4)
    val ops = listOf(
        R.string.op_remove_bg to R.string.op_remove_bg_prompt,
        R.string.op_upscale to R.string.op_upscale_prompt,
        R.string.op_restyle to R.string.op_restyle_prompt,
    )

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        if (backend.isBlank()) {
            Card { Text(stringResource(R.string.demo_banner), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
        }

        ChipRow(Kind.entries, kind, { stringResource(kindLabels.getValue(it)) }) { kind = it }

        if (kind.needsImage) {
            if (imageUri != null) {
                AsyncImage(imageUri, null, Modifier.fillMaxWidth().height(180.dp), contentScale = ContentScale.Fit)
            }
            OutlinedButton(onClick = { picker.launch("image/*") }, Modifier.fillMaxWidth()) {
                Text(stringResource(if (imageUri == null) R.string.pick_image else R.string.change_image))
            }
        }

        if (kind == Kind.IMAGE_EDIT) {
            ChipRow(ops, ops.first(), { stringResource(it.first) }) { prompt = ctx.getString(it.second) }
        }

        OutlinedTextField(
            value = prompt, onValueChange = { prompt = it },
            label = { Text(stringResource(R.string.prompt_label)) },
            modifier = Modifier.fillMaxWidth(), minLines = 3,
        )

        if (kind != Kind.IMAGE_EDIT) {
            Text(stringResource(R.string.ideas), style = MaterialTheme.typography.labelLarge)
            ChipRow(ideas, -1, { stringResource(it) }) { prompt = ctx.getString(it) }
        }

        Text(stringResource(R.string.style), style = MaterialTheme.typography.labelLarge)
        ChipRow(styleLabels.keys.toList(), style, { stringResource(styleLabels.getValue(it)) }) { style = it }

        if (kind != Kind.IMAGE_EDIT) {
            Text(stringResource(R.string.aspect), style = MaterialTheme.typography.labelLarge)
            ChipRow(listOf("16:9", "9:16", "1:1"), aspect, { it }) { aspect = it }
        }

        if (kind.isVideo) {
            Text(stringResource(R.string.duration), style = MaterialTheme.typography.labelLarge)
            ChipRow(listOf(5, 10), duration, { ctx.getString(R.string.seconds, it) }) { duration = it }
        }

        Button(
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                when {
                    prompt.isBlank() -> Toast.makeText(ctx, R.string.need_prompt, Toast.LENGTH_SHORT).show()
                    kind.needsImage && imageData == null -> Toast.makeText(ctx, R.string.need_image, Toast.LENGTH_SHORT).show()
                    else -> {
                        busy = true
                        error = null
                        vm.submit(
                            GenRequest(kind, prompt.trim(), style, aspect, duration, if (kind.needsImage) imageData else null),
                        ) { e ->
                            busy = false
                            if (e == null) onSubmitted() else error = e.message ?: e.toString()
                        }
                    }
                }
            },
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.generate))
            }
        }

        error?.let {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    stringResource(R.string.error_generic, it),
                    Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}
