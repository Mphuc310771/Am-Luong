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

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "volume_control_database"
                )
                .addCallback(AppDatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class AppDatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
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
        }
    }
}
