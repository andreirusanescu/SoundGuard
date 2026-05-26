package com.soundguard.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soundguard.app.data.db.AlertEventDao
import com.soundguard.app.data.db.AlertEventEntity
import com.soundguard.app.ml.SoundCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryItem(
    val id: Long,
    val category: SoundCategory?,
    val rawCategory: String,
    val score: Float,
    val timestampMs: Long
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val dao: AlertEventDao
) : ViewModel() {

    val items: StateFlow<List<HistoryItem>> = dao.observeRecent()
        .map { rows -> rows.map { it.toHistoryItem() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clear() {
        viewModelScope.launch { dao.clear() }
    }

    private fun AlertEventEntity.toHistoryItem(): HistoryItem {
        val cat = runCatching { SoundCategory.valueOf(category) }.getOrNull()
        return HistoryItem(id, cat, category, score, timestampMs)
    }
}
