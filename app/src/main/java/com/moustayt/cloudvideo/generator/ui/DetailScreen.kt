package com.moustayt.cloudvideo.generator.ui

import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.moustayt.cloudvideo.generator.R
import com.moustayt.cloudvideo.generator.data.AppViewModel
import com.moustayt.cloudvideo.generator.data.Status
import com.moustayt.cloudvideo.generator.saveToGallery
import com.moustayt.cloudvideo.generator.shareItem
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun DetailScreen(vm: AppViewModel, id: String, onBack: () -> Unit) {
    val items by vm.items.collectAsState()
    val item = items.firstOrNull { it.id == id }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
        if (item == null) return@Column

        if (item.status == Status.DONE && item.resultUrl != null) {
            if (item.kind.isVideo) {
                val player = remember(item.resultUrl) {
                    ExoPlayer.Builder(ctx).build().apply {
                        setMediaItem(MediaItem.fromUri(item.resultUrl))
                        prepare()
                    }
                }
                DisposableEffect(player) { onDispose { player.release() } }
                AndroidView(
                    factory = { PlayerView(it).apply { this.player = player } },
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                )
            } else {
                AsyncImage(item.resultUrl, null, Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
            }
        } else {
            Text(statusText(item), style = MaterialTheme.typography.titleMedium)
        }

        Text(item.prompt, style = MaterialTheme.typography.bodyLarge)

        if (item.status == Status.DONE) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        val ok = saveToGallery(ctx, item)
                        Toast.makeText(ctx, if (ok) R.string.saved else R.string.save_failed, Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.save)) }
                OutlinedButton(onClick = {
                    scope.launch {
                        if (!shareItem(ctx, item)) Toast.makeText(ctx, R.string.share_failed, Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.share)) }
            }
        }
        OutlinedButton(onClick = { vm.delete(item.id); onBack() }) { Text(stringResource(R.string.delete)) }
    }
}
