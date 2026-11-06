package com.moustayt.cloudvideo.generator.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MovieFilter
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.moustayt.cloudvideo.generator.R
import com.moustayt.cloudvideo.generator.data.AppViewModel
import com.moustayt.cloudvideo.generator.data.GenRequest
import com.moustayt.cloudvideo.generator.data.Kind
import com.moustayt.cloudvideo.generator.encodeImage
import kotlinx.coroutines.launch

val STYLES = listOf("cinematic", "realistic", "anime", "3d", "cyberpunk", "fantasy", "watercolor", "vintage")

fun styleLabel(style: String): Int = when (style) {
    "anime" -> R.string.style_anime
    "realistic" -> R.string.style_realistic
    "3d" -> R.string.style_3d
    "watercolor" -> R.string.style_watercolor
    "cyberpunk" -> R.string.style_cyberpunk
    "fantasy" -> R.string.style_fantasy
    "vintage" -> R.string.style_vintage
    else -> R.string.style_cinematic
}

private data class KindInfo(val kind: Kind, val title: Int, val sub: Int, val icon: ImageVector)

@Composable
fun CreateScreen(vm: AppViewModel, onSubmitted: () -> Unit, onStory: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val backend by vm.backend.collectAsState()
    val server by vm.server.collectAsState()
    val draft by vm.draft.collectAsState()

    var kind by rememberSaveable { mutableStateOf(Kind.TEXT_TO_VIDEO) }
    var prompt by rememberSaveable { mutableStateOf("") }
    var style by rememberSaveable { mutableStateOf("cinematic") }
    var aspect by rememberSaveable { mutableStateOf("16:9") }
    var duration by rememberSaveable { mutableStateOf(5) }
    var imageUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var imageData by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var assisting by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var smart by remember { mutableStateOf(vm.settings.smartPrompts) }
    var count by rememberSaveable { mutableStateOf(1) }
    var quality by rememberSaveable { mutableStateOf("fast") }

    LaunchedEffect(draft) {
        draft?.let {
            kind = it.kind; prompt = it.prompt; style = it.style; aspect = it.aspect; duration = it.durationSec
            if (it.sourceUrl != null) imageUri = Uri.parse(it.sourceUrl)
            error = null
            vm.consumeDraft()
        }
    }
    // Re-encode after process death / rotation.
    LaunchedEffect(imageUri) {
        val u = imageUri
        // Earlier creations are already online: send their URL instead of re-uploading the pixels.
        imageData = when {
            u == null -> null
            u.scheme == "http" || u.scheme == "https" -> u.toString()
            else -> encodeImage(ctx, u)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) imageUri = uri
    }

    val kinds = listOf(
        KindInfo(Kind.TEXT_TO_VIDEO, R.string.kind_t2v, R.string.kind_t2v_sub, Icons.Outlined.Movie),
        KindInfo(Kind.IMAGE_TO_VIDEO, R.string.kind_i2v, R.string.kind_i2v_sub, Icons.Outlined.MovieFilter),
        KindInfo(Kind.TEXT_TO_IMAGE, R.string.kind_t2i, R.string.kind_t2i_sub, Icons.Outlined.Image),
        KindInfo(Kind.IMAGE_EDIT, R.string.kind_edit, R.string.kind_edit_sub, Icons.Outlined.AutoFixHigh),
    )
    val ideas = listOf(R.string.idea1, R.string.idea2, R.string.idea3, R.string.idea4, R.string.idea5, R.string.idea6)
    val ops = listOf(
        R.string.op_remove_bg to R.string.op_remove_bg_prompt,
        R.string.op_upscale to R.string.op_upscale_prompt,
        R.string.op_restyle to R.string.op_restyle_prompt,
    )
    val live = backend.isNotBlank()

    fun runAssist(mode: String) {
        if (mode == "enhance" && prompt.isBlank()) {
            Toast.makeText(ctx, R.string.need_prompt_enhance, Toast.LENGTH_SHORT).show()
            return
        }
        assisting = mode
        error = null
        vm.assist(mode, prompt, kind, style) { r ->
            assisting = null
            r.onSuccess { prompt = it }.onFailure { error = it }
        }
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).brandGlow(0.18f).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Wordmark()
                Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusPill(live = live, ai = server?.ai == true)
        }

        if (!live) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text(stringResource(R.string.demo_banner), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        // What to make
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            kinds.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { k ->
                        KindTile(k, selected = kind == k.kind, modifier = Modifier.weight(1f)) {
                            kind = k.kind
                            error = null
                            if (k.kind == Kind.IMAGE_EDIT && prompt.isBlank()) prompt = ctx.getString(ops.first().second)
                        }
                    }
                }
            }
        }

        StoryTile(onStory)

        AnimatedVisibility(kind.needsImage) {
            ImagePickerCard(imageUri, onPick = { picker.launch("image/*") }, onClear = { imageUri = null })
        }

        if (kind == Kind.IMAGE_EDIT) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ops.forEach { op ->
                    SuggestionChip(onClick = { prompt = ctx.getString(op.second) }, label = { Text(stringResource(op.first)) })
                }
            }
        }

        // Prompt
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(Modifier.padding(14.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = prompt, onValueChange = { if (it.length <= 1500) prompt = it },
                    label = { Text(stringResource(R.string.prompt_label)) },
                    placeholder = { Text(stringResource(R.string.prompt_placeholder), style = MaterialTheme.typography.bodyMedium) },
                    modifier = Modifier.fillMaxWidth(), minLines = 4, maxLines = 10,
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                    trailingIcon = {
                        if (prompt.isNotEmpty()) IconButton(onClick = { prompt = "" }) { Icon(Icons.Outlined.Close, null) }
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AssistButton(
                        stringResource(if (assisting == "enhance") R.string.thinking else R.string.enhance),
                        Icons.Outlined.AutoAwesome, loading = assisting == "enhance", enabled = assisting == null && live,
                        modifier = Modifier.weight(1f),
                    ) { runAssist("enhance") }
                    AssistButton(
                        stringResource(if (assisting == "surprise") R.string.thinking else R.string.surprise),
                        Icons.Outlined.Casino, loading = assisting == "surprise", enabled = assisting == null && live,
                        modifier = Modifier.weight(1f),
                    ) { runAssist("surprise") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Psychology, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.smart_title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.smart_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(smart, { smart = it; vm.settings.smartPrompts = it })
                }
            }
        }

        if (kind != Kind.IMAGE_EDIT) {
            SectionTitle(stringResource(R.string.ideas))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ideas.forEach { id ->
                    SuggestionChip(
                        onClick = { prompt = ctx.getString(id) },
                        label = { Text(stringResource(id), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        shape = RoundedCornerShape(50),
                    )
                }
            }
        }

        if (kind != Kind.IMAGE_EDIT) {
            SectionTitle(stringResource(R.string.style))
            ChipRow(STYLES, style, { stringResource(styleLabel(it)) }) { style = it }

            SectionTitle(stringResource(R.string.aspect))
            ChipRow(listOf("16:9", "9:16", "1:1"), aspect, {
                stringResource(when (it) { "9:16" -> R.string.aspect_tall; "1:1" -> R.string.aspect_square; else -> R.string.aspect_wide }) + " · " + it
            }) { aspect = it }
        }

        if (kind.isVideo) {
            SectionTitle(stringResource(R.string.duration))
            ChipRow(listOf(5, 10), duration, { ctx.getString(R.string.seconds, it) }) { duration = it }
            SectionTitle(stringResource(R.string.quality))
            QualityChips(quality) { quality = it }
        } else {
            SectionTitle(stringResource(R.string.variations))
            ChipRow(listOf(1, 2, 4), count, { "×$it" }) { count = it }
        }

        GradientButton(
            text = stringResource(if (busy) R.string.generating else R.string.generate),
            loading = busy,
            modifier = Modifier.fillMaxWidth(),
            leading = { Icon(Icons.Outlined.AutoAwesome, null, tint = Color.White) },
            onClick = {
                when {
                    prompt.isBlank() -> error = IllegalStateException(ctx.getString(R.string.need_prompt))
                    kind.needsImage && imageData == null -> error = IllegalStateException(ctx.getString(R.string.need_image))
                    else -> {
                        busy = true
                        error = null
                        vm.submit(
                            GenRequest(
                                kind, prompt.trim(), style, if (kind == Kind.IMAGE_EDIT) "" else aspect, duration,
                                if (kind.needsImage) imageData else null,
                                quality = if (kind.isVideo) quality else "fast",
                            ),
                            count = if (kind.isVideo) 1 else count,
                        ) { e ->
                            busy = false
                            if (e == null) onSubmitted() else error = e
                        }
                    }
                }
            },
        )

        error?.let { e ->
            if (e is IllegalStateException) ErrorCard(e.message ?: "", null)
            else ErrorCard(friendlyError(e), e.message)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StoryTile(onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.large
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Brand.Violet.copy(alpha = 0.9f), Brand.Pink.copy(alpha = 0.85f))))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.AutoStories, null, tint = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.kind_story), style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(stringResource(R.string.kind_story_sub), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
        }
        Text("→", color = Color.White, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun StatusPill(live: Boolean, ai: Boolean) {
    val color = if (live) Color(0xFF34D399) else MaterialTheme.colorScheme.outline
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(if (!live) R.string.mode_demo else if (ai) R.string.server_ai_on else R.string.mode_live),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun KindTile(info: KindInfo, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.large
    Card(
        modifier = modifier
            .then(if (selected) Modifier.border(BorderStroke(2.dp, Brand.gradient), shape) else Modifier)
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (selected) Brand.gradient else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceContainerHighest)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(info.icon, null, Modifier.size(22.dp), tint = if (selected) Color.White else MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(info.title), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(info.sub), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AssistButton(text: String, icon: ImageVector, loading: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        else Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ImagePickerCard(uri: Uri?, onPick: () -> Unit, onClear: () -> Unit) {
    val shape = MaterialTheme.shapes.large
    Box(
        Modifier.fillMaxWidth().height(200.dp).clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onPick),
        contentAlignment = Alignment.Center,
    ) {
        if (uri != null) {
            AsyncImage(uri, null, Modifier.fillMaxWidth().height(200.dp), contentScale = ContentScale.Crop)
            IconButton(
                onClick = onClear,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)),
            ) { Icon(Icons.Outlined.Close, null, tint = Color.White) }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.AddPhotoAlternate, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.pick_image), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
