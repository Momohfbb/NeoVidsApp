package com.moustayt.cloudvideo.generator.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moustayt.cloudvideo.generator.R
import com.moustayt.cloudvideo.generator.data.ApiException
import com.moustayt.cloudvideo.generator.data.AppViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipRow(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { o ->
            FilterChip(
                selected = o == selected,
                onClick = { onSelect(o) },
                label = { Text(label(o)) },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

@Composable
fun LanguageChips(vm: AppViewModel, onRecreate: () -> Unit) {
    val current = vm.settings.language
    ChipRow(
        options = listOf("system", "ar", "en"),
        selected = current,
        label = {
            when (it) {
                "ar" -> stringResource(R.string.arabic)
                "en" -> stringResource(R.string.english)
                else -> stringResource(R.string.system)
            }
        },
    ) { v ->
        if (v != current) {
            vm.settings.language = v
            onRecreate()
        }
    }
}

/** The app mark (rounded-square icon). */
@Composable
fun BrandMark(size: Dp, modifier: Modifier = Modifier) {
    Image(
        painterResource(R.drawable.brand_mark), contentDescription = null,
        modifier = modifier.size(size).shadow(size / 4, RoundedCornerShape(size * 0.26f), ambientColor = Brand.Violet, spotColor = Brand.Violet),
    )
}

/** "NeoVids AI" wordmark with the gradient "Vids". */
@Composable
fun Wordmark(fontSize: TextUnit = 26.sp, modifier: Modifier = Modifier) {
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.ExtraBold)) { append("Neo") }
        withStyle(SpanStyle(brush = Brand.gradient, fontWeight = FontWeight.ExtraBold)) { append("Vids") }
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Light)) { append(" AI") }
    }
    Text(text, fontSize = fontSize, modifier = modifier, letterSpacing = (-0.5).sp)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** Primary call to action: a glowing brand-gradient pill. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leading: (@Composable RowScope.() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(50)
    val shimmer by rememberInfiniteTransition(label = "btn").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "shift",
    )
    val brush = Brush.linearGradient(
        listOf(Brand.Violet, Brand.Pink, Brand.Cyan, Brand.Violet),
        start = androidx.compose.ui.geometry.Offset(-600f + 1200f * shimmer, 0f),
        end = androidx.compose.ui.geometry.Offset(600f + 1200f * shimmer, 200f),
    )
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.5f)
            .height(58.dp)
            .shadow(if (enabled) 18.dp else 0.dp, shape, ambientColor = Brand.Violet, spotColor = Brand.Violet)
            .clip(shape)
            .background(if (loading) brush else Brand.horizontal)
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            } else if (leading != null) {
                leading.invoke(this)
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

/** Soft brand glow used behind headers. */
fun Modifier.brandGlow(alpha: Float = 0.35f): Modifier = this.background(
    Brush.radialGradient(listOf(Brand.Violet.copy(alpha = alpha), Color.Transparent), radius = 900f),
)

/** Turns backend / network failures into a friendly, localized sentence. */
@Composable
fun friendlyError(e: Throwable?): String = friendlyError((e as? ApiException)?.code, e?.message)

@Composable
fun friendlyError(code: String?, raw: String?): String = when {
    code == "BALANCE" || (raw ?: "").contains("balance", ignoreCase = true) -> stringResource(R.string.err_balance)
    code == "QUOTA" -> stringResource(R.string.err_quota)
    code == "NO_PROVIDER" -> stringResource(R.string.err_no_provider)
    code == "UNAUTHORIZED" -> stringResource(R.string.err_auth)
    code == "NETWORK" -> stringResource(R.string.err_network)
    code == "TIMEOUT" -> stringResource(R.string.err_timeout)
    code == "PRO_LOCKED" -> stringResource(R.string.err_pro_locked)
    else -> stringResource(R.string.error_generic, raw ?: "")
}

@Composable
fun ErrorCard(message: String, details: String?, modifier: Modifier = Modifier) {
    androidx.compose.material3.Card(
        modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        androidx.compose.foundation.layout.Column(Modifier.padding(14.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
            if (!details.isNullOrBlank() && details != message) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "${stringResource(R.string.err_details)}: $details",
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                )
            }
        }
    }
}
