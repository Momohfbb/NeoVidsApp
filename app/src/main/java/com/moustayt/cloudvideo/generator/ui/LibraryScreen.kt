package com.moustayt.cloudvideo.generator.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.moustayt.cloudvideo.generator.R
import com.moustayt.cloudvideo.generator.data.AppViewModel
import com.moustayt.cloudvideo.generator.data.LibraryItem
import com.moustayt.cloudvideo.generator.data.Status

@Composable
fun statusText(item: LibraryItem): String = when (item.status) {
    Status.QUEUED -> stringResource(R.string.status_queued)
    Status.RUNNING -> stringResource(R.string.status_running, (item.progress * 100).toInt())
    Status.DONE -> stringResource(R.string.status_done)
    Status.FAILED -> item.error?.let { stringResource(R.string.error_generic, it) } ?: stringResource(R.string.status_failed)
}

@Composable
fun LibraryScreen(vm: AppViewModel, onOpen: (String) -> Unit) {
    val items by vm.items.collectAsState()
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.library_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items, key = { it.id }) { item ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(item.id) }) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                        if (!item.kind.isVideo && item.status == Status.DONE && item.resultUrl != null) {
                            AsyncImage(item.resultUrl, null, Modifier.size(72.dp), contentScale = ContentScale.Crop)
                        } else {
                            Icon(if (item.kind.isVideo) Icons.Default.Movie else Icons.Default.Image, null, Modifier.size(40.dp))
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.prompt, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(statusText(item), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (item.status == Status.RUNNING || item.status == Status.QUEUED) {
                            LinearProgressIndicator(progress = { item.progress }, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}
