package com.stockwatchdog.app.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stockwatchdog.app.data.db.AlertDao
import com.stockwatchdog.app.data.db.entities.AlertEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlertsViewModel(
    private val dao: AlertDao
) : ViewModel() {

    val alerts: StateFlow<List<AlertEntity>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun toggle(id: Long, enabled: Boolean) = viewModelScope.launch { dao.setEnabled(id, enabled) }
    fun delete(id: Long) = viewModelScope.launch { dao.delete(id) }
}
