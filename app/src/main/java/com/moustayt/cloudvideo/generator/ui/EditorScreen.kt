package com.moustayt.cloudvideo.generator.ui

import android.graphics.Color as AColor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
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

@OptIn(UnstableApi::class)
private fun colorEffects(filter: String): List<Effect> = when (filter) {
    "bw" -> listOf(RgbFilter.createGrayscaleFilter(), Contrast(0.15f))
    "vivid" -> listOf(HslAdjustment.Builder().adjustSaturation(35f).build(), Contrast(0.12f))
    "soft" -> listOf(HslAdjustment.Builder().adjustSaturation(-20f).adjustLightness(6f).build(), Contrast(-0.1f))
    "dark" -> listOf(HslAdjustment.Builder().adjustSaturation(-15f).build(), Contrast(0.25f), Brightness(-0.08f))
    else -> emptyList()
}

@OptIn(UnstableApi::class)
private fun titleOverlay(text: String): Effect {
    val s = SpannableString("  $text  ")
    val all = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
    s.setSpan(AbsoluteSizeSpan(72), 0, s.length, all)
    s.setSpan(ForegroundColorSpan(AColor.WHITE), 0, s.length, all)
    s.setSpan(BackgroundColorSpan(0x88000000.toInt()), 0, s.length, all)
    s.setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, s.length, all)
    return OverlayEffect(ImmutableList.of<TextureOverlay>(TextOverlay.createStaticTextOverlay(s)))
}

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: AppViewModel, onExported: () -> Unit) {
    val ctx = LocalContext.current
    var clips by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var total by remember { mutableStateOf(0L) }
    var range by remember { mutableStateOf(0f..1f) }
    var exporting by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("none") }
    var speed by remember { mutableStateOf(1f) }
    var music by remember { mutableStateOf<Uri?>(null) }
    var mute by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        clips = uris
        total = if (uris.size == 1) durationMs(ctx, uris[0]) else 0L
        range = 0f..1f
    }
    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) music = uri }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.editor_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.editor_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)

        OutlinedButton(onClick = { picker.launch("video/*") }, Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Outlined.VideoLibrary, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.editor_pick))
        }

        if (clips.isNotEmpty()) {
            Text(stringResource(R.string.editor_selected, clips.size), color = MaterialTheme.colorScheme.primary)
        }
        if (clips.size == 1 && total > 0) {
            EditorCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ContentCut, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.editor_trim, fmt((total * range.start).toLong()), fmt((total * range.endInclusive).toLong())))
                }
                RangeSlider(value = range, onValueChange = { range = it })
            }
        }

        EditorCard {
            SectionTitle(stringResource(R.string.editor_text))
            OutlinedTextField(
                title, { if (it.length <= 60) title = it }, Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text(stringResource(R.string.editor_text_hint)) },
            )
            SectionTitle(stringResource(R.string.editor_filter))
            ChipRow(listOf("none", "vivid", "soft", "dark", "bw"), filter, {
                stringResource(
                    when (it) {
                        "bw" -> R.string.filter_bw
                        "vivid" -> R.string.filter_vivid
                        "soft" -> R.string.filter_soft
                        "dark" -> R.string.filter_dark
                        else -> R.string.filter_none
                    },
                )
            }) { filter = it }
            SectionTitle(stringResource(R.string.editor_speed))
            ChipRow(listOf(0.5f, 1f, 1.5f, 2f), speed, { "${it}×".replace(".0×", "×") }) { speed = it }
        }

        EditorCard {
            SectionTitle(stringResource(R.string.editor_music))
            OutlinedButton(onClick = { musicPicker.launch("audio/*") }, Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.MusicNote, null); Spacer(Modifier.width(8.dp))
                Text(stringResource(if (music == null) R.string.editor_pick_music else R.string.editor_music_chosen))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.editor_mute), Modifier.weight(1f))
                Switch(mute, { mute = it })
            }
        }

        GradientButton(
            text = stringResource(if (exporting) R.string.editor_exporting else R.string.editor_export),
            enabled = clips.isNotEmpty(),
            loading = exporting,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                exporting = true
                val videoEffects = buildList {
                    addAll(colorEffects(filter))
                    if (speed != 1f) add(SpeedChangeEffect(speed))
                    if (title.isNotBlank()) add(titleOverlay(title.trim()))
                }
                val audio: List<AudioProcessor> = if (speed != 1f) listOf(SonicAudioProcessor().apply { setSpeed(speed) }) else emptyList()
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
                    EditedMediaItem.Builder(mi)
                        .setRemoveAudio(mute)
                        .setEffects(Effects(audio, videoEffects))
                        .build()
                }
                val sequences = buildList {
                    add(EditedMediaItemSequence(items))
                    music?.let { add(EditedMediaItemSequence(listOf(EditedMediaItem.Builder(MediaItem.fromUri(it)).build()), true)) }
                }
                val composition = Composition.Builder(sequences).build()
                val dir = File(ctx.externalCacheDir ?: ctx.cacheDir, "exports").apply { mkdirs() }
                val out = File(dir, "neovids_edit_${System.currentTimeMillis()}.mp4")
                val transformer = Transformer.Builder(ctx)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            exporting = false
                            vm.addLocal(
                                LibraryItem(
                                    id = "local.${System.currentTimeMillis()}", kind = Kind.TEXT_TO_VIDEO,
                                    prompt = title.ifBlank { ctx.getString(R.string.editor_prompt) }, createdAt = System.currentTimeMillis(),
                                    status = Status.DONE, progress = 1f, resultUrl = Uri.fromFile(out).toString(), local = true,
                                    aspect = "16:9",
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
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun EditorCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
