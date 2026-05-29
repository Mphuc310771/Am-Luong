package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.database.AppDatabase
import com.example.database.VolumePreset
import com.example.database.VolumePresetRepository
import com.example.service.VolumeControlService
import com.example.service.VolumeSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VolumeViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = VolumePresetRepository(database.volumePresetDao())
    val settings = VolumeSettings.getInstance(application)
    private val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val allPresets: StateFlow<List<VolumePreset>> = repository.allPresets
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Expose active background service state
    val isServiceRunning: StateFlow<Boolean> = VolumeControlService.isRunning

    fun startVolumeService() {
        settings.isServiceRunning = true
        val intent = Intent(getApplication(), VolumeControlService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getApplication<Application>().startForegroundService(intent)
        } else {
            getApplication<Application>().startService(intent)
        }
    }

    fun stopVolumeService() {
        settings.isServiceRunning = false
        val intent = Intent(getApplication(), VolumeControlService::class.java)
        getApplication<Application>().stopService(intent)
    }

    fun addNewPreset(name: String, mediaVol: Int, ringVol: Int, alarmVol: Int, notificationVol: Int) {
        viewModelScope.launch {
            repository.insert(
                VolumePreset(
                    name = name,
                    mediaVol = mediaVol,
                    ringVol = ringVol,
                    alarmVol = alarmVol,
                    notificationVol = notificationVol,
                    isSystemPreset = false
                )
            )
        }
    }

    fun deletePreset(preset: VolumePreset) {
        viewModelScope.launch {
            repository.delete(preset)
        }
    }

    // Direct helper to fetch and set current stream percentages
    fun getStreamPercentage(streamType: Int): Int {
        val current = audioManager.getStreamVolume(streamType)
        val max = audioManager.getStreamMaxVolume(streamType)
        if (max == 0) return 0
        return ((current.toFloat() / max.toFloat()) * 100).toInt()
    }

    fun setStreamPercentage(streamType: Int, percent: Int, playSound: Boolean = false) {
        val current = audioManager.getStreamVolume(streamType)
        val max = audioManager.getStreamMaxVolume(streamType)
        val target = ((percent.toFloat() / 100f) * max).toInt().coerceIn(0, max)
        if (target != current) {
            val flags = if (playSound) AudioManager.FLAG_PLAY_SOUND else 0
            audioManager.setStreamVolume(streamType, target, flags)
        }
    }

    fun applyPreset(preset: VolumePreset) {
        val mediaMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val ringMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
        val alarmMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val notifMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)

        val mVol = ((preset.mediaVol.toFloat() / 100f) * mediaMax).toInt()
        val rVol = ((preset.ringVol.toFloat() / 100f) * ringMax).toInt()
        val aVol = ((preset.alarmVol.toFloat() / 100f) * alarmMax).toInt()
        val nVol = ((preset.notificationVol.toFloat() / 100f) * notifMax).toInt()

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mVol, AudioManager.FLAG_PLAY_SOUND)
        audioManager.setStreamVolume(AudioManager.STREAM_RING, rVol, 0)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, aVol, 0)
        audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, nVol, 0)
    }

    override fun onCleared() {
        super.onCleared()
        try {
            AppDatabase.closeDatabase()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        System.runFinalization()
        System.gc()
    }
}
