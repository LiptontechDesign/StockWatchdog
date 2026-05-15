package com.stockwatchdog.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stockwatchdog.app.data.prefs.ApiProvider
import com.stockwatchdog.app.data.prefs.SettingsRepository
import com.stockwatchdog.app.data.prefs.ThemeMode
import com.stockwatchdog.app.data.prefs.UserSettings
import com.stockwatchdog.app.firebase.FirebaseServices
import com.stockwatchdog.app.notifications.NotificationHelper
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
    fun setFinnhubKey(k: String) = viewModelScope.launch { repo.setFinnhubKey(k) }
    fun setFmpKey(k: String) = viewModelScope.launch { repo.setFmpKey(k) }
    fun setPlatformFeePercent(percent: Double) = viewModelScope.launch { repo.setPlatformFeePercent(percent) }

    fun setInterval(minutes: Int) = viewModelScope.launch {
        repo.setIntervalMinutes(minutes)
        AlertWorkScheduler.scheduleFromSettings(appContext)
    }

    fun setNotificationsEnabled(enabled: Boolean) = viewModelScope.launch {
        repo.setNotificationsEnabled(enabled)
        AlertWorkScheduler.scheduleFromSettings(appContext)
    }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { repo.setThemeMode(mode) }

    fun setQuietHoursEnabled(enabled: Boolean) =
        viewModelScope.launch { repo.setQuietHoursEnabled(enabled) }

    fun setQuietHoursRange(startMinutes: Int, endMinutes: Int) =
        viewModelScope.launch { repo.setQuietHoursRange(startMinutes, endMinutes) }

    fun setMarketHoursOnly(enabled: Boolean) =
        viewModelScope.launch { repo.setMarketHoursOnly(enabled) }

    fun setFirebasePushEnabled(enabled: Boolean) = viewModelScope.launch {
        repo.setFirebasePushEnabled(enabled)
        if (enabled) FirebaseServices.refreshMessaging(appContext)
    }

    fun refreshFirebasePush() {
        FirebaseServices.refreshMessaging(appContext)
    }

    fun sendTestNotification() {
        NotificationHelper.show(
            context = appContext,
            notificationId = (System.currentTimeMillis() and 0x7fffffff).toInt(),
            symbol = "",
            title = "Stock Watchdog test",
            body = "Notifications are working. Firebase push can use this same channel.",
            route = "settings"
        )
    }
}
