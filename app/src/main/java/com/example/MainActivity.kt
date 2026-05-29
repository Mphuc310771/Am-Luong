package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.VolumeScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.VolumeViewModel
import com.example.service.VolumeControlService

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        val viewModel: VolumeViewModel = viewModel()
        VolumeScreen(viewModel = viewModel)
      }
    }
  }

  override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    if (level >= TRIM_MEMORY_BACKGROUND) {
      System.gc()
    }
  }
}
