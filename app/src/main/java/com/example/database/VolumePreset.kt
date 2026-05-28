package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "volume_presets")
data class VolumePreset(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val mediaVol: Int,         // Percentage from 0 to 100
    val ringVol: Int,          // Percentage from 0 to 100
    val alarmVol: Int,         // Percentage from 0 to 100
    val notificationVol: Int,  // Percentage from 0 to 100
    val isSystemPreset: Boolean = false
)
