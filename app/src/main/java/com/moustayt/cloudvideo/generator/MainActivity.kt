package com.moustayt.cloudvideo.generator

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moustayt.cloudvideo.generator.data.AppViewModel
import com.moustayt.cloudvideo.generator.data.Settings
import com.moustayt.cloudvideo.generator.ui.App
import com.moustayt.cloudvideo.generator.ui.NeoTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Settings.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AppViewModel = viewModel()
            val theme by vm.theme.collectAsState()
            NeoTheme(theme) {
                App(vm, onRecreate = { recreate() })
            }
        }
    }
}
