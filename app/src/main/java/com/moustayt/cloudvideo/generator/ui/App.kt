package com.moustayt.cloudvideo.generator.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.moustayt.cloudvideo.generator.R
import com.moustayt.cloudvideo.generator.data.AppViewModel
import kotlinx.coroutines.launch

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

@Composable
fun App(vm: AppViewModel, onRecreate: () -> Unit) {
    var onboarded by remember { mutableStateOf(vm.settings.onboarded) }
    AnimatedContent(onboarded, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "onboarding") { done ->
        if (!done) {
            NeoTheme("dark") { Onboarding(vm, onRecreate) { vm.settings.onboarded = true; onboarded = true } }
        } else {
            MainShell(vm, onRecreate)
        }
    }
}

@Composable
private fun MainShell(vm: AppViewModel, onRecreate: () -> Unit) {
    // Ask once for permission to notify when a creation is ready (Android 13+).
    val ctx = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    val tabs = listOf(
        Tab("create", R.string.tab_create, Icons.Outlined.AutoAwesome),
        Tab("library", R.string.tab_library, Icons.Outlined.VideoLibrary),
        Tab("editor", R.string.tab_editor, Icons.Outlined.ContentCut),
        Tab("settings", R.string.tab_settings, Icons.Outlined.Settings),
    )
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    fun go(r: String) = nav.navigate(r) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    // "Remix" from the detail screen opens Create with the prompt filled in.
    val draft by vm.draft.collectAsState()
    LaunchedEffect(draft) { if (draft != null && route != "create") go("create") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (route in tabs.map { it.route }) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = { go(t.route) },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(stringResource(t.label)) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = "create", modifier = Modifier.padding(pad)) {
            composable("create") { CreateScreen(vm) { go("library") } }
            composable("library") { LibraryScreen(vm, onOpen = { id -> nav.navigate("detail/$id") }, onCreate = { go("create") }) }
            composable("editor") { EditorScreen(vm) { go("library") } }
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
    val icons = listOf(Icons.Outlined.Movie, Icons.Outlined.AutoAwesome, Icons.Outlined.ContentCut)
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF1A1145), Color(0xFF0B0A17))),
        ),
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { LanguageChips(vm, onRecreate) }
                TextButton(onClick = onDone) { Text(stringResource(R.string.skip), color = Color.White.copy(alpha = 0.8f)) }
            }
            HorizontalPager(pager, Modifier.weight(1f)) { i ->
                Column(
                    Modifier.fillMaxSize().brandGlow(0.45f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (i == 0) {
                        BrandMark(128.dp)
                        Spacer(Modifier.height(20.dp))
                        Wordmark(34.sp)
                        Text(stringResource(R.string.tagline), color = Color.White.copy(alpha = 0.7f))
                        Spacer(Modifier.height(36.dp))
                    } else {
                        Box(
                            Modifier.size(120.dp).clip(CircleShape).background(Brand.gradient),
                            contentAlignment = Alignment.Center,
                        ) { Icon(icons[i], null, Modifier.size(56.dp), tint = Color.White) }
                        Spacer(Modifier.height(36.dp))
                    }
                    Text(
                        stringResource(pages[i].first), style = MaterialTheme.typography.headlineMedium,
                        color = Color.White, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(pages[i].second), textAlign = TextAlign.Center,
                        color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
                repeat(pages.size) { i ->
                    val w by animateDpAsState(if (pager.currentPage == i) 28.dp else 8.dp, label = "dot")
                    Box(
                        Modifier.padding(horizontal = 4.dp).height(8.dp).width(w).clip(CircleShape)
                            .background(if (pager.currentPage == i) Brand.horizontal else Brush.linearGradient(listOf(Color.White.copy(0.25f), Color.White.copy(0.25f)))),
                    )
                }
            }
            val last = pager.currentPage == pages.lastIndex
            GradientButton(
                stringResource(if (last) R.string.start else R.string.next),
                onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
