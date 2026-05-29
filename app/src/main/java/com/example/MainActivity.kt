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
  private var isContentSet = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setAppContent()
  }

  private fun setAppContent() {
    if (!isContentSet) {
      setContent {
        MyApplicationTheme {
          val viewModel: VolumeViewModel = viewModel()
          VolumeScreen(viewModel = viewModel)
        }
      }
      isContentSet = true
    }
  }

  override fun onStart() {
    super.onStart()
    setAppContent()
  }

  override fun onStop() {
    super.onStop()
    // RAM Purge: Release entire Compose tree, dispose compositions, and disconnect database
    try {
      val contentView = findViewById<android.view.ViewGroup>(android.R.id.content)
      contentView?.let { container ->
        // Recursively find and dispose of any Compose composition to clean up all Compose runtime objects
        for (i in 0 until container.childCount) {
          val child = container.getChildAt(i)
          if (child is androidx.compose.ui.platform.ComposeView) {
            child.disposeComposition()
          }
        }
        container.removeAllViews()
      }
      isContentSet = false
    } catch (e: Exception) {
      e.printStackTrace()
    }
    
    // Close SQLite and Room caches to ensure zero background holding of database memory
    try {
      com.example.database.AppDatabase.closeDatabase()
    } catch (e: Exception) {
      e.printStackTrace()
    }
    
    // Call finish to completely destroy activity window, view caches and release full activity heap
    finish()
    
    // Request instant JVM execution of garbage collection to sweep all freed memory
    System.gc()
  }

  override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    System.gc()
  }

  override fun onDestroy() {
    super.onDestroy()
    try {
      com.example.database.AppDatabase.closeDatabase()
    } catch (e: Exception) {
      e.printStackTrace()
    }
    System.runFinalization()
    System.gc()
  }
}
