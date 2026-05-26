package com.soundguard.app.data

import com.soundguard.app.data.db.AlertEventDao
import com.soundguard.app.data.db.AlertEventEntity
import com.soundguard.app.ml.SoundCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class HealthSummary(
    val total24h: Int,
    val criticalCount24h: Int,
    val lastAlertMs: Long?,
    val topCategory: SoundCategory?,
    val sevenDayBuckets: List<Int>  // index 0 = 6 days ago … index 6 = today
)

private const val DAY_MS = 24L * 60L * 60L * 1000L

@Singleton
class HealthRepository @Inject constructor(
    private val dao: AlertEventDao
) {
    val summary: Flow<HealthSummary> = dao.observeRecent().map { rows ->
        val now = System.currentTimeMillis()
        val last24 = rows.filter { it.timestampMs > now - DAY_MS }

        val critical = last24.count { row ->
            categoryOf(row)?.severity?.name == "CRITICAL"
        }

        val top = last24
            .groupingBy { it.category }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?.let { runCatching { SoundCategory.valueOf(it) }.getOrNull() }

        val buckets = IntArray(7).apply {
            for (row in rows) {
                val daysAgo = ((now - row.timestampMs) / DAY_MS).toInt()
                if (daysAgo in 0..6) this[6 - daysAgo]++
            }
        }.toList()

        HealthSummary(
            total24h = last24.size,
            criticalCount24h = critical,
            lastAlertMs = rows.firstOrNull()?.timestampMs,
            topCategory = top,
            sevenDayBuckets = buckets
        )
    }

    private fun categoryOf(row: AlertEventEntity): SoundCategory? =
        runCatching { SoundCategory.valueOf(row.category) }.getOrNull()
}
