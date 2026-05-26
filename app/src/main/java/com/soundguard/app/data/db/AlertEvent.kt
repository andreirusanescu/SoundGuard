package com.soundguard.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alert_events")
data class AlertEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val score: Float,
    val timestampMs: Long
)
