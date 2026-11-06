package com.moustayt.cloudvideo.generator.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.moustayt.cloudvideo.generator.R
import com.moustayt.cloudvideo.generator.data.AppViewModel
import com.moustayt.cloudvideo.generator.data.LibraryItem
import com.moustayt.cloudvideo.generator.data.Status
import kotlinx.coroutines.delay

/** Providers report coarse progress, so blend it with elapsed time for a bar that keeps moving. */
fun estimatedProgress(item: LibraryItem, now: Long): Float {
    if (item.status == Status.DONE) return 1f
    val expected = if (item.kind.isVideo) 90_000f else 15_000f
    val byTime = ((now - item.createdAt) / expected).coerceIn(0f, 0.95f)
    return maxOf(item.progress, byTime).coerceIn(0.02f, 0.97f)
}

@Composable
fun statusText(item: LibraryItem, now: Long = System.currentTimeMillis()): String = when (item.status) {
    Status.QUEUED -> stringResource(R.string.status_queued)
    Status.RUNNING -> stringResource(R.string.status_running, (estimatedProgress(item, now) * 100).toInt())
    Status.DONE -> stringResource(R.string.status_done)
    Status.FAILED -> if (item.errorCode == "BALANCE" || (item.error ?: "").contains("balance", true)) {
        stringResource(R.string.err_balance_short)
    } else stringResource(R.string.status_failed)
}

@Composable
fun LibraryScreen(vm: AppViewModel, onOpen: (String) -> Unit, onCreate: () -> Unit) {
    val items by vm.items.collectAsState()
    var filter by rememberSaveable { mutableStateOf("all") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val anyActive = items.any { it.status == Status.QUEUED || it.status == Status.RUNNING }
    LaunchedEffect(anyActive) {
        while (anyActive) { now = System.currentTimeMillis(); delay(1000) }
    }

    if (items.isEmpty()) {
        Column(
            Modifier.fillMaxSize().brandGlow(0.25f).padding(32.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(96.dp).clip(CircleShape).background(Brand.gradient), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.VideoLibrary, null, Modifier.size(44.dp), tint = Color.White)
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.library_empty), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.library_empty_body), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            GradientButton(
                stringResource(R.string.library_create), onCreate, Modifier.fillMaxWidth(0.8f),
                leading = { Icon(Icons.Outlined.AutoAwesome, null, tint = Color.White) },
            )
        }
        return
    }

    val shown = when (filter) {
        "video" -> items.filter { it.kind.isVideo }
        "image" -> items.filter { !it.kind.isVideo }
        else -> items
    }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.tab_library), style = MaterialTheme.typography.headlineMedium)
                ChipRow(listOf("all", "video", "image"), filter, {
                    stringResource(when (it) { "video" -> R.string.filter_videos; "image" -> R.string.filter_images; else -> R.string.filter_all })
                }) { filter = it }
            }
        }
        items(shown, key = { it.id }) { item -> LibraryTile(item, now) { onOpen(item.id) } }
    }
}

@Composable
private fun LibraryTile(item: LibraryItem, now: Long, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val ratio = when (item.aspect) { "9:16" -> 9f / 16f; "1:1", "" -> 1f; else -> 16f / 10f }
    Column(Modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (item.status == Status.DONE) ratio else 1f), contentAlignment = Alignment.Center) {
            when {
                item.status == Status.DONE && item.resultUrl != null -> {
                    AsyncImage(item.resultUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    if (item.kind.isVideo) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Outlined.PlayArrow, null, tint = Color.White) }
                    }
                }
                item.status == Status.FAILED -> {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
                else -> CreatingPlaceholder(item.kind.isVideo)
            }
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.prompt, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (item.kind.isVideo) Icons.Outlined.VideoLibrary else Icons.Outlined.Image, null,
                    Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    statusText(item, now), style = MaterialTheme.typography.labelSmall,
                    color = if (item.status == Status.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.status == Status.RUNNING || item.status == Status.QUEUED) {
                LinearProgressIndicator(
                    progress = { estimatedProgress(item, now) },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)),
                )
            }
        }
    }
}

/** Animated aurora shown while a creation is being made. */
@Composable
fun CreatingPlaceholder(video: Boolean, modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "aurora").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse), label = "t",
    )
    Box(
        modifier.fillMaxSize().background(
            Brush.linearGradient(
                listOf(Brand.Violet.copy(alpha = 0.55f), Brand.Pink.copy(alpha = 0.35f), Brand.Cyan.copy(alpha = 0.45f)),
                start = androidx.compose.ui.geometry.Offset(0f, 600f * t),
                end = androidx.compose.ui.geometry.Offset(600f, 600f * (1 - t)),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.AutoAwesome, null,
            Modifier.size(36.dp).graphicsLayer { rotationZ = 360f * t; alpha = 0.6f + 0.4f * t },
            tint = Color.White,
        )
    }
}
