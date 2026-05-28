package com.example.database

import kotlinx.coroutines.flow.Flow

class VolumePresetRepository(private val volumePresetDao: VolumePresetDao) {
    val allPresets: Flow<List<VolumePreset>> = volumePresetDao.getAllPresets()

    suspend fun insert(preset: VolumePreset) {
        volumePresetDao.insertPreset(preset)
    }

    suspend fun delete(preset: VolumePreset) {
        volumePresetDao.deletePreset(preset)
    }

    suspend fun getById(id: Int): VolumePreset? {
        return volumePresetDao.getPresetById(id)
    }
}
