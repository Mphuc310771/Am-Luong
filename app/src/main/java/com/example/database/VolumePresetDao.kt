package com.example.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VolumePresetDao {
    @Query("SELECT * FROM volume_presets ORDER BY id ASC")
    fun getAllPresets(): Flow<List<VolumePreset>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: VolumePreset)

    @Delete
    suspend fun deletePreset(preset: VolumePreset)

    @Query("SELECT * FROM volume_presets WHERE id = :id")
    suspend fun getPresetById(id: Int): VolumePreset?
}
