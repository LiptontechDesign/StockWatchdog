package com.stockwatchdog.app.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stockwatchdog.app.data.db.AlertDao
import com.stockwatchdog.app.data.db.entities.AlertEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlertsUiState(
    val confirmDeleteId: Long? = null,
    val undoDeleteEntity: AlertEntity? = null
)

class AlertsViewModel(
    private val dao: AlertDao
) : ViewModel() {

    private val _ui = MutableStateFlow(AlertsUiState())
    val ui: StateFlow<AlertsUiState> = _ui.asStateFlow()

    val alerts: StateFlow<List<AlertEntity>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun toggle(id: Long, enabled: Boolean) = viewModelScope.launch { dao.setEnabled(id, enabled) }

    fun confirmDelete(id: Long) = _ui.update { it.copy(confirmDeleteId = id) }
    fun cancelDelete() = _ui.update { it.copy(confirmDeleteId = null) }

    fun delete(id: Long) {
        viewModelScope.launch {
            val entity = alerts.value.firstOrNull { it.id == id }
            dao.delete(id)
            _ui.update { it.copy(confirmDeleteId = null, undoDeleteEntity = entity) }
        }
    }

    fun undoDelete() {
        val entity = _ui.value.undoDeleteEntity ?: return
        viewModelScope.launch {
            dao.insert(entity)
            _ui.update { it.copy(undoDeleteEntity = null) }
        }
    }

    fun dismissUndoSnackbar() = _ui.update { it.copy(undoDeleteEntity = null) }
}
