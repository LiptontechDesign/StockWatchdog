package com.stockwatchdog.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.stockwatchdog.app.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ApiProvider { AUTO, FINNHUB, TWELVE_DATA, YAHOO, ALPHA_VANTAGE }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UserSettings(
    val provider: ApiProvider = ApiProvider.AUTO,
    val twelveDataKey: String = "",
    val alphaVantageKey: String = "",
    val finnhubKey: String = "",
    val platformFeePercent: Double = 0.0,
    val intervalMinutes: Int = 30,
    val notificationsEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
) {
    /**
     * Key for a specific provider. Only meaningful when the user has forced
     * a single provider; AUTO mode uses every configured key through the
     * fallback chain.
     */
    val activeKey: String
        get() = when (provider) {
            ApiProvider.TWELVE_DATA -> twelveDataKey
            ApiProvider.ALPHA_VANTAGE -> alphaVantageKey
            ApiProvider.FINNHUB -> finnhubKey
            ApiProvider.YAHOO -> ""
            ApiProvider.AUTO -> ""
        }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val PROVIDER = stringPreferencesKey("provider")
        val TWELVE = stringPreferencesKey("td_key")
        val ALPHA = stringPreferencesKey("av_key")
        val FINNHUB = stringPreferencesKey("fh_key")
        val PLATFORM_FEE_PERCENT = doublePreferencesKey("platform_fee_percent")
        val INTERVAL = intPreferencesKey("interval_minutes")
        val NOTIFS = booleanPreferencesKey("notifications")
        val THEME = stringPreferencesKey("theme")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            provider = prefs[Keys.PROVIDER]?.let(::runCatchingProvider) ?: ApiProvider.AUTO,
            twelveDataKey = prefs[Keys.TWELVE] ?: BuildConfig.TWELVE_DATA_API_KEY,
            alphaVantageKey = prefs[Keys.ALPHA] ?: BuildConfig.ALPHA_VANTAGE_API_KEY,
            finnhubKey = prefs[Keys.FINNHUB] ?: BuildConfig.FINNHUB_API_KEY,
            platformFeePercent = prefs[Keys.PLATFORM_FEE_PERCENT] ?: 0.0,
            intervalMinutes = prefs[Keys.INTERVAL] ?: 30,
            notificationsEnabled = prefs[Keys.NOTIFS] ?: true,
            themeMode = prefs[Keys.THEME]?.let(::runCatchingTheme) ?: ThemeMode.SYSTEM
        )
    }

    suspend fun setProvider(provider: ApiProvider) =
        context.dataStore.edit { it[Keys.PROVIDER] = provider.name }

    suspend fun setTwelveDataKey(key: String) =
        context.dataStore.edit { it[Keys.TWELVE] = key.trim() }

    suspend fun setAlphaVantageKey(key: String) =
        context.dataStore.edit { it[Keys.ALPHA] = key.trim() }

    suspend fun setFinnhubKey(key: String) =
        context.dataStore.edit { it[Keys.FINNHUB] = key.trim() }

    suspend fun setPlatformFeePercent(percent: Double) =
        context.dataStore.edit { it[Keys.PLATFORM_FEE_PERCENT] = percent.coerceAtLeast(0.0) }

    suspend fun setIntervalMinutes(minutes: Int) =
        context.dataStore.edit { it[Keys.INTERVAL] = minutes }

    suspend fun setNotificationsEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.NOTIFS] = enabled }

    suspend fun setThemeMode(mode: ThemeMode) =
        context.dataStore.edit { it[Keys.THEME] = mode.name }

    private fun runCatchingProvider(raw: String): ApiProvider? =
        runCatching { ApiProvider.valueOf(raw) }.getOrNull()

    private fun runCatchingTheme(raw: String): ThemeMode? =
        runCatching { ThemeMode.valueOf(raw) }.getOrNull()
}
