package com.example.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class VolumeTileService : TileService() {
    
    override fun onClick() {
        super.onClick()
        // Open the custom floating volume panel
        val serviceIntent = Intent(this, VolumeControlService::class.java).apply {
            action = VolumeControlService.ACTION_SHOW_OVERLAY
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // Close/collapse the quick settings notifications panel so the overlay is instantly visible
        try {
            @Suppress("DEPRECATION")
            val closeIntent = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            sendBroadcast(closeIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.label = "Đ.Khiển Âm Lượng"
        tile.state = Tile.STATE_ACTIVE
        tile.updateTile()
    }
}
