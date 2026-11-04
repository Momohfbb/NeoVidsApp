package com.moustayt.cloudvideo.generator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.moustayt.cloudvideo.generator.R
import com.moustayt.cloudvideo.generator.data.AppViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipRow(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { o ->
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) })
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
