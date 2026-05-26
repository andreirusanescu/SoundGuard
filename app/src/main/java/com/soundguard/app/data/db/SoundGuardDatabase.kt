package com.soundguard.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [AlertEventEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SoundGuardDatabase : RoomDatabase() {
    abstract fun alertEventDao(): AlertEventDao
}
