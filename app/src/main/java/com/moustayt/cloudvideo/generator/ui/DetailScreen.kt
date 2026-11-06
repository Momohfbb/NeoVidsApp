package com.moustayt.cloudvideo.generator.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.moustayt.cloudvideo.generator.R
import com.moustayt.cloudvideo.generator.data.AppViewModel
import com.moustayt.cloudvideo.generator.data.ApiException
import com.moustayt.cloudvideo.generator.data.Status
import com.moustayt.cloudvideo.generator.saveToGallery
import com.moustayt.cloudvideo.generator.shareItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun DetailScreen(vm: AppViewModel, id: String, onBack: () -> Unit, onStory: (String) -> Unit) {
    val items by vm.items.collectAsState()
    val item = items.firstOrNull { it.id == id }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var confirmDelete by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var retryError by remember { mutableStateOf<Throwable?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(item?.status) {
        while (item != null && (item.status == Status.QUEUED || item.status == Status.RUNNING)) {
            now = System.currentTimeMillis(); delay(1000)
        }
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            Spacer(Modifier.width(4.dp))
            if (item != null) Text(statusText(item, now), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (item != null) {
                IconButton(onClick = { vm.toggleFavorite(item) }) {
                    Icon(
                        if (item.favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        stringResource(R.string.favorite), tint = if (item.favorite) Brand.Pink else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (item == null) return@Column

        val ratio = when (item.aspect) { "9:16" -> 9f / 16f; "1:1" -> 1f; else -> 16f / 9f }
        val mediaShape = MaterialTheme.shapes.large
        when {
            item.status == Status.DONE && item.resultUrl != null && item.kind.isVideo -> {
                val player = remember(item.resultUrl) {
                    ExoPlayer.Builder(ctx).build().apply {
                        setMediaItem(MediaItem.fromUri(item.resultUrl))
                        repeatMode = Player.REPEAT_MODE_ONE
                        playWhenReady = true
                        prepare()
                    }
                }
                DisposableEffect(player) { onDispose { player.release() } }
                AndroidView(
                    factory = { PlayerView(it).apply { this.player = player; setShowNextButton(false); setShowPreviousButton(false) } },
                    modifier = Modifier.fillMaxWidth().aspectRatio(ratio.coerceAtLeast(0.7f)).clip(mediaShape).background(Color.Black),
                )
            }
            item.status == Status.DONE && item.resultUrl != null -> {
                AsyncImage(
                    item.resultUrl, null,
                    Modifier.fillMaxWidth().clip(mediaShape).background(MaterialTheme.colorScheme.surfaceContainer),
                    contentScale = ContentScale.FillWidth,
                )
            }
            item.status == Status.FAILED -> {
                ErrorCard(friendlyError(item.errorCode, item.error), item.error)
            }
            else -> {
                Box(Modifier.fillMaxWidth().aspectRatio(ratio.coerceAtLeast(0.8f)).clip(mediaShape)) {
                    CreatingPlaceholder(item.kind.isVideo, Modifier.fillMaxSize())
                }
                LinearProgressIndicator(progress = { estimatedProgress(item, now) }, modifier = Modifier.fillMaxWidth())
            }
        }

        // Actions
        if (item.status == Status.DONE) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(stringResource(R.string.save), Icons.Outlined.Download, Modifier.weight(1f), primary = true) {
                    scope.launch {
                        working = true
                        val ok = saveToGallery(ctx, item)
                        working = false
                        Toast.makeText(ctx, if (ok) R.string.saved else R.string.save_failed, Toast.LENGTH_SHORT).show()
                    }
                }
                ActionButton(stringResource(R.string.share), Icons.Outlined.Share, Modifier.weight(1f)) {
                    scope.launch {
                        working = true
                        if (!shareItem(ctx, item)) Toast.makeText(ctx, R.string.share_failed, Toast.LENGTH_SHORT).show()
                        working = false
                    }
                }
            }
            if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!item.local) {
                ActionButton(stringResource(R.string.remix), Icons.Outlined.AutoAwesome, Modifier.weight(1f)) {
                    vm.remix(item)
                }
            }
            if (item.status == Status.FAILED && !item.kind.needsImage && !item.local) {
                ActionButton(stringResource(R.string.retry), Icons.Outlined.Refresh, Modifier.weight(1f), primary = true) {
                    working = true
                    retryError = null
                    vm.retry(item) { e ->
                        working = false
                        if (e == null) onBack() else retryError = e
                    }
                }
            }
        }
        if (item.status == Status.DONE && !item.kind.isVideo && item.resultUrl?.startsWith("http") == true) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(stringResource(R.string.animate), Icons.Outlined.Movie, Modifier.weight(1f), primary = true) { vm.animate(item) }
                ActionButton(stringResource(R.string.edit_image), Icons.Outlined.AutoFixHigh, Modifier.weight(1f)) { vm.editImage(item) }
            }
        }
        item.groupId?.let { g ->
            ActionButton(stringResource(R.string.story_open), Icons.Outlined.AutoStories, Modifier.fillMaxWidth()) { onStory(g) }
        }
        retryError?.let { ErrorCard(friendlyError(it), (it as? ApiException)?.message ?: it.message) }

        PromptCard(stringResource(R.string.your_prompt), item.prompt) {
            clipboard.setText(AnnotatedString(item.prompt))
            Toast.makeText(ctx, R.string.copied, Toast.LENGTH_SHORT).show()
        }
        item.finalPrompt?.takeIf { it.isNotBlank() && it != item.prompt }?.let { fp ->
            PromptCard(stringResource(R.string.ai_prompt), fp, accent = true) {
                clipboard.setText(AnnotatedString(fp))
                Toast.makeText(ctx, R.string.copied, Toast.LENGTH_SHORT).show()
            }
        }

        TextButton(onClick = { confirmDelete = true }) {
            Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
        }
    }

    if (confirmDelete && item != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(item.prompt, maxLines = 3) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete(item.id); onBack() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.back)) } },
        )
    }
}

@Composable
private fun ActionButton(text: String, icon: ImageVector, modifier: Modifier, primary: Boolean = false, onClick: () -> Unit) {
    if (primary) {
        FilledTonalButton(onClick = onClick, modifier = modifier.height(48.dp)) {
            Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(text)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier.height(48.dp)) {
            Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(text)
        }
    }
}

@Composable
private fun PromptCard(title: String, text: String, accent: Boolean = false, onCopy: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (accent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (accent) {
                    Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                }
                SectionTitle(title, Modifier.weight(1f))
                IconButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.copy), Modifier.size(18.dp)) }
            }
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 12.dp))
        }
    }
}
