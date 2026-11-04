package com.moustayt.cloudvideo.generator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.moustayt.cloudvideo.generator.R
import com.moustayt.cloudvideo.generator.data.AppViewModel
import kotlinx.coroutines.launch

private data class Tab(val route: String, val label: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun App(vm: AppViewModel, onRecreate: () -> Unit) {
    var onboarded by remember { mutableStateOf(vm.settings.onboarded) }
    if (!onboarded) {
        Onboarding(vm, onRecreate) { vm.settings.onboarded = true; onboarded = true }
        return
    }

    val tabs = listOf(
        Tab("create", R.string.tab_create, Icons.Default.AutoAwesome),
        Tab("library", R.string.tab_library, Icons.Default.VideoLibrary),
        Tab("editor", R.string.tab_editor, Icons.Default.ContentCut),
        Tab("settings", R.string.tab_settings, Icons.Default.Settings),
    )
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route

    Scaffold(
        bottomBar = {
            if (route in tabs.map { it.route }) {
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(stringResource(t.label)) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = "create", modifier = Modifier.padding(pad)) {
            composable("create") { CreateScreen(vm) { nav.navigate("library") } }
            composable("library") { LibraryScreen(vm) { id -> nav.navigate("detail/$id") } }
            composable("editor") { EditorScreen(vm) { nav.navigate("library") } }
            composable("settings") { SettingsScreen(vm, onRecreate) }
            composable("detail/{id}") { entry ->
                DetailScreen(vm, entry.arguments?.getString("id").orEmpty()) { nav.popBackStack() }
            }
        }
    }
}

@Composable
private fun Onboarding(vm: AppViewModel, onRecreate: () -> Unit, onDone: () -> Unit) {
    val pages = listOf(
        R.string.onb_title1 to R.string.onb_body1,
        R.string.onb_title2 to R.string.onb_body2,
        R.string.onb_title3 to R.string.onb_body3,
    )
    val icons = listOf(Icons.Default.AutoAwesome, Icons.Default.Movie, Icons.Default.ContentCut)
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            LanguageChips(vm, onRecreate)
            TextButton(onClick = onDone) { Text(stringResource(R.string.skip)) }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { i ->
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(icons[i], null, Modifier.height(96.dp).fillMaxWidth(), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(24.dp))
                Text(stringResource(pages[i].first), fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(pages[i].second), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val last = pager.currentPage == pages.lastIndex
        Button(
            onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(if (last) R.string.start else R.string.next)) }
    }
}
