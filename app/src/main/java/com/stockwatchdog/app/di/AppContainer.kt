package com.stockwatchdog.app.di

import android.content.Context
import androidx.room.Room
import com.stockwatchdog.app.data.api.AlphaVantageApi
import com.stockwatchdog.app.data.api.MarketDataRepository
import com.stockwatchdog.app.data.api.TwelveDataApi
import com.stockwatchdog.app.data.db.AppDatabase
import com.stockwatchdog.app.data.prefs.SettingsRepository
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Lightweight manual DI container. Intentionally avoids Hilt/Dagger to keep
 * cold-start fast and build times short for a single-user app.
 */
class AppContainer(private val context: Context) {

    private val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .apply {
            // Keep logging quiet on release; enable verbose only for debug builds.
            if (com.stockwatchdog.app.BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                })
            }
        }
        .build()

    private val converter = json.asConverterFactory("application/json".toMediaType())

    private val twelveData: TwelveDataApi = Retrofit.Builder()
        .baseUrl("https://api.twelvedata.com/")
        .client(httpClient)
        .addConverterFactory(converter)
        .build()
        .create(TwelveDataApi::class.java)

    private val alphaVantage: AlphaVantageApi = Retrofit.Builder()
        .baseUrl("https://www.alphavantage.co/")
        .client(httpClient)
        .addConverterFactory(converter)
        .build()
        .create(AlphaVantageApi::class.java)

    val database: AppDatabase = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "stockwatchdog.db"
    )
        .addMigrations(AppDatabase.MIGRATION_1_2)
        .fallbackToDestructiveMigration()
        .build()

    val settingsRepository: SettingsRepository = SettingsRepository(context.applicationContext)

    val marketDataRepository: MarketDataRepository = MarketDataRepository(
        twelveData = twelveData,
        alphaVantage = alphaVantage,
        settings = settingsRepository,
        priceCacheDao = database.priceCacheDao()
    )
}
