package com.stockwatchdog.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stockwatchdog.app.data.prefs.ApiProvider
import com.stockwatchdog.app.data.prefs.SettingsRepository
import com.stockwatchdog.app.data.prefs.ThemeMode
import com.stockwatchdog.app.data.prefs.UserSettings
import com.stockwatchdog.app.work.AlertWorkScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repo: SettingsRepository,
    private val appContext: Context
) : ViewModel() {

    val state: StateFlow<UserSettings> = repo.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = UserSettings()
    )

    fun setProvider(p: ApiProvider) = viewModelScope.launch { repo.setProvider(p) }
    fun setTwelveKey(k: String) = viewModelScope.launch { repo.setTwelveDataKey(k) }
    fun setAlphaKey(k: String) = viewModelScope.launch { repo.setAlphaVantageKey(k) }

    fun setInterval(minutes: Int) = viewModelScope.launch {
        repo.setIntervalMinutes(minutes)
        AlertWorkScheduler.schedule(appContext, minutes)
    }

    fun setNotificationsEnabled(enabled: Boolean) = viewModelScope.launch {
        repo.setNotificationsEnabled(enabled)
        if (enabled) {
            AlertWorkScheduler.schedule(appContext, state.value.intervalMinutes)
        } else {
            AlertWorkScheduler.cancel(appContext)
        }
    }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { repo.setThemeMode(mode) }
}
