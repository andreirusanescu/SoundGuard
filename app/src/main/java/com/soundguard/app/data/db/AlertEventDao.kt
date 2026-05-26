package com.soundguard.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertEventDao {
    @Insert
    suspend fun insert(event: AlertEventEntity): Long

    @Query("SELECT * FROM alert_events ORDER BY timestampMs DESC LIMIT 200")
    fun observeRecent(): Flow<List<AlertEventEntity>>

    @Query("DELETE FROM alert_events")
    suspend fun clear()
}
