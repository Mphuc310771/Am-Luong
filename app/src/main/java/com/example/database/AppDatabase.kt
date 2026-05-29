package com.example.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [VolumePreset::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun volumePresetDao(): VolumePresetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "volume_control_database"
                )
                .addCallback(AppDatabaseCallback())
                .build()
                INSTANCE = instance
                instance
            }
        }

        fun closeDatabase() {
            synchronized(this) {
                INSTANCE?.let {
                    try {
                        if (it.isOpen) {
                            it.close()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    INSTANCE = null
                }
            }
        }

        private class AppDatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Use a lightweight, isolated one-shot IO scope to populate default presets
                CoroutineScope(Dispatchers.IO).launch {
                    INSTANCE?.let { database ->
                        val dao = database.volumePresetDao()
                        // Insert standard profiles immediately
                        dao.insertPreset(VolumePreset(name = "Mặc định ☕", mediaVol = 50, ringVol = 60, alarmVol = 50, notificationVol = 50, isSystemPreset = true))
                        dao.insertPreset(VolumePreset(name = "Im lặng 🤫", mediaVol = 0, ringVol = 0, alarmVol = 30, notificationVol = 0, isSystemPreset = true))
                        dao.insertPreset(VolumePreset(name = "Khán phòng / Học tập 📖", mediaVol = 15, ringVol = 0, alarmVol = 20, notificationVol = 10, isSystemPreset = true))
                        dao.insertPreset(VolumePreset(name = "Giải trí / Chơi game 🎧", mediaVol = 85, ringVol = 40, alarmVol = 50, notificationVol = 50, isSystemPreset = true))
                        dao.insertPreset(VolumePreset(name = "Ngoài trời 🔊", mediaVol = 100, ringVol = 100, alarmVol = 100, notificationVol = 100, isSystemPreset = true))
                    }
                }
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                // Optimize SQLite caching to use minimal memory
                try {
                    db.execSQL("PRAGMA cache_size = 30;")
                    db.execSQL("PRAGMA temp_store = 2;") // MEMORY
                    db.execSQL("PRAGMA journal_mode = TRUNCATE;")
                    db.execSQL("PRAGMA shrink_memory;") // Free any unused native database memory back to Android instantly
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
