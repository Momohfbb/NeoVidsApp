package com.moustayt.cloudvideo.generator.ui

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.moustayt.cloudvideo.generator.R
import com.moustayt.cloudvideo.generator.data.AppViewModel
import com.moustayt.cloudvideo.generator.data.Kind
import com.moustayt.cloudvideo.generator.data.LibraryItem
import com.moustayt.cloudvideo.generator.data.Status
import java.io.File

private fun durationMs(ctx: android.content.Context, uri: Uri): Long = runCatching {
    val r = MediaMetadataRetriever()
    r.setDataSource(ctx, uri)
    val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
    r.release()
    d
}.getOrDefault(0L)

private fun fmt(ms: Long): String = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: AppViewModel, onExported: () -> Unit) {
    val ctx = LocalContext.current
    var clips by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var total by remember { mutableStateOf(0L) }
    var range by remember { mutableStateOf(0f..1f) }
    var exporting by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        clips = uris
        total = if (uris.size == 1) durationMs(ctx, uris[0]) else 0L
        range = 0f..1f
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.editor_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.editor_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)

        OutlinedButton(onClick = { picker.launch("video/*") }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.editor_pick)) }

        if (clips.isNotEmpty()) {
            Text(stringResource(R.string.editor_selected, clips.size))
        }
        if (clips.size == 1 && total > 0) {
            val start = (total * range.start).toLong()
            val end = (total * range.endInclusive).toLong()
            Text(stringResource(R.string.editor_trim, fmt(start), fmt(end)))
            RangeSlider(value = range, onValueChange = { range = it })
        }

        Button(
            enabled = clips.isNotEmpty() && !exporting,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                exporting = true
                val items = clips.map { uri ->
                    val mi = MediaItem.Builder().setUri(uri).apply {
                        if (clips.size == 1 && total > 0) {
                            setClippingConfiguration(
                                MediaItem.ClippingConfiguration.Builder()
                                    .setStartPositionMs((total * range.start).toLong())
                                    .setEndPositionMs((total * range.endInclusive).toLong())
                                    .build(),
                            )
                        }
                    }.build()
                    EditedMediaItem.Builder(mi).build()
                }
                val composition = Composition.Builder(listOf(EditedMediaItemSequence(items))).build()
                val dir = File(ctx.externalCacheDir ?: ctx.cacheDir, "exports").apply { mkdirs() }
                val out = File(dir, "neovids_edit_${System.currentTimeMillis()}.mp4")
                val transformer = Transformer.Builder(ctx)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            exporting = false
                            vm.addLocal(
                                LibraryItem(
                                    id = "local.${System.currentTimeMillis()}", kind = Kind.TEXT_TO_VIDEO,
                                    prompt = ctx.getString(R.string.editor_prompt), createdAt = System.currentTimeMillis(),
                                    status = Status.DONE, progress = 1f, resultUrl = Uri.fromFile(out).toString(), local = true,
                                ),
                            )
                            Toast.makeText(ctx, R.string.editor_done, Toast.LENGTH_SHORT).show()
                            onExported()
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            exporting = false
                            Toast.makeText(ctx, ctx.getString(R.string.editor_failed, exportException.message ?: ""), Toast.LENGTH_LONG).show()
                        }
                    })
                    .build()
                transformer.start(composition, out.absolutePath)
            },
        ) { Text(stringResource(if (exporting) R.string.editor_exporting else R.string.editor_export)) }
    }
}
