package com.moustayt.cloudvideo.generator.ui

import android.net.Uri
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import coil.compose.AsyncImage
import com.moustayt.cloudvideo.generator.R
import com.moustayt.cloudvideo.generator.data.AppViewModel
import com.moustayt.cloudvideo.generator.data.Kind
import com.moustayt.cloudvideo.generator.data.LibraryItem
import com.moustayt.cloudvideo.generator.data.Status
import kotlinx.coroutines.launch
import java.io.File

/** Write a story: idea -> AI scenes (editable) -> one video job per scene. */
@Composable
fun StoryScreen(vm: AppViewModel, onBack: () -> Unit, onStarted: (String) -> Unit) {
    val ctx = LocalContext.current
    var idea by rememberSaveable { mutableStateOf("") }
    var count by rememberSaveable { mutableStateOf(4) }
    var style by rememberSaveable { mutableStateOf("cinematic") }
    var aspect by rememberSaveable { mutableStateOf("16:9") }
    var quality by rememberSaveable { mutableStateOf("fast") }
    var title by rememberSaveable { mutableStateOf("") }
    val scenes = remember { mutableStateListOf<String>() }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).brandGlow(0.2f).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(R.string.story_title), style = MaterialTheme.typography.headlineSmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Brand.gradient), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.AutoStories, null, tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.story_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        OutlinedTextField(
            idea, { if (it.length <= 600) idea = it }, Modifier.fillMaxWidth(), minLines = 3,
            label = { Text(stringResource(R.string.story_idea)) },
            placeholder = { Text(stringResource(R.string.story_idea_placeholder)) },
            shape = MaterialTheme.shapes.medium,
        )
        SectionTitle(stringResource(R.string.story_scenes))
        ChipRow(listOf(3, 4, 5, 6), count, { "$it" }) { count = it }
        SectionTitle(stringResource(R.string.style))
        ChipRow(STYLES, style, { stringResource(styleLabel(it)) }) { style = it }
        SectionTitle(stringResource(R.string.aspect))
        ChipRow(listOf("16:9", "9:16", "1:1"), aspect, { it }) { aspect = it }
        SectionTitle(stringResource(R.string.quality))
        QualityChips(quality) { quality = it }

        GradientButton(
            stringResource(if (busy && scenes.isEmpty()) R.string.story_writing else R.string.story_write),
            loading = busy && scenes.isEmpty(),
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (idea.isBlank()) { error = IllegalStateException(ctx.getString(R.string.need_prompt)); return@GradientButton }
                busy = true; error = null; scenes.clear()
                vm.writeStory(idea, count, style) { r ->
                    busy = false
                    r.onSuccess { s -> title = s.title; scenes.addAll(s.scenes) }.onFailure { error = it }
                }
            },
        )

        if (scenes.isNotEmpty()) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            scenes.forEachIndexed { i, sc ->
                OutlinedTextField(
                    sc, { scenes[i] = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.story_scene, i + 1)) },
                    shape = MaterialTheme.shapes.medium,
                )
            }
            Text(stringResource(R.string.story_cost, scenes.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            GradientButton(
                stringResource(R.string.story_make),
                loading = busy,
                modifier = Modifier.fillMaxWidth(),
                leading = { Icon(Icons.Outlined.Movie, null, tint = Color.White) },
                onClick = {
                    busy = true; error = null
                    vm.startStory(title, scenes.filter { it.isNotBlank() }, style, aspect, quality) { r ->
                        busy = false
                        r.onSuccess(onStarted).onFailure { error = it }
                    }
                },
            )
        }

        error?.let { e ->
            if (e is IllegalStateException) ErrorCard(e.message ?: "", null) else ErrorCard(friendlyError(e), e.message)
        }
    }
}

@Composable
fun QualityChips(quality: String, onChange: (String) -> Unit) {
    ChipRow(listOf("fast", "pro"), quality, {
        stringResource(if (it == "pro") R.string.quality_pro else R.string.quality_fast)
    }, onChange)
    if (quality == "pro") {
        Text(stringResource(R.string.quality_pro_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Progress of a story's shots, and joining them into one film. */
@OptIn(UnstableApi::class)
@Composable
fun StoryProgressScreen(vm: AppViewModel, groupId: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val all by remember(groupId) { vm.group(groupId) }.collectAsState(initial = emptyList())
    val shots = all.filter { !it.local }
    val film = all.firstOrNull { it.local }
    val ready = shots.filter { it.status == Status.DONE && it.resultUrl != null }
    val failed = shots.count { it.status == Status.FAILED }
    val pending = shots.size - ready.size - failed
    var joining by remember { mutableStateOf(false) }
    val title = shots.firstOrNull()?.groupTitle ?: film?.groupTitle ?: ""

    fun join() {
        joining = true
        val items = ready.map { EditedMediaItem.Builder(MediaItem.fromUri(it.resultUrl!!)).build() }
        val composition = Composition.Builder(listOf(EditedMediaItemSequence(items))).build()
        val dir = File(ctx.externalCacheDir ?: ctx.cacheDir, "exports").apply { mkdirs() }
        val out = File(dir, "neovids_story_${System.currentTimeMillis()}.mp4")
        Transformer.Builder(ctx)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    scope.launch {
                        vm.addLocalNow(
                            LibraryItem(
                                id = "film.${System.currentTimeMillis()}", kind = Kind.TEXT_TO_VIDEO,
                                prompt = title.ifBlank { ctx.getString(R.string.film) }, style = shots.firstOrNull()?.style ?: "",
                                aspect = shots.firstOrNull()?.aspect ?: "16:9", createdAt = System.currentTimeMillis(),
                                status = Status.DONE, progress = 1f, resultUrl = Uri.fromFile(out).toString(), local = true,
                                groupId = groupId, groupTitle = title, sceneIndex = 999,
                            ),
                        )
                        joining = false
                        Toast.makeText(ctx, R.string.story_done, Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    joining = false
                    Toast.makeText(ctx, ctx.getString(R.string.editor_failed, exportException.message ?: ""), Toast.LENGTH_LONG).show()
                }
            })
            .build()
            .start(composition, out.absolutePath)
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (shots.isNotEmpty()) {
            Text(stringResource(R.string.story_progress, ready.size, shots.size), style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(progress = { ready.size / shots.size.toFloat() }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)))
        }

        film?.let { f ->
            Card(
                onClick = { onOpen(f.id) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.large,
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Movie, null)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.story_done), style = MaterialTheme.typography.titleSmall)
                }
            }
        }

        if (pending == 0 && ready.isNotEmpty() && film == null) {
            if (failed > 0) ErrorCard(stringResource(R.string.story_failed_shots), null)
            GradientButton(
                stringResource(if (joining) R.string.story_assembling else R.string.story_assemble),
                onClick = { join() }, loading = joining, modifier = Modifier.fillMaxWidth(),
                leading = { Icon(Icons.Outlined.Movie, null, tint = Color.White) },
            )
        }

        shots.forEachIndexed { i, s ->
            Card(
                onClick = { onOpen(s.id) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = MaterialTheme.shapes.large,
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(96.dp).aspectRatio(16f / 10f).clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        when {
                            s.status == Status.DONE && s.resultUrl != null ->
                                AsyncImage(s.resultUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            s.status == Status.FAILED -> Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                            else -> CreatingPlaceholder(true)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.story_scene, i + 1), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(s.prompt, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    if (s.status == Status.DONE) {
                        Box(Modifier.size(24.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF34D399))
                        }
                    }
                }
            }
        }
    }
}
