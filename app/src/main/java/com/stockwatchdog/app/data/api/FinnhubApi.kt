package com.stockwatchdog.app.data.api

import com.stockwatchdog.app.data.api.models.FinnhubQuote
import com.stockwatchdog.app.data.api.models.FinnhubEarningsCalendarResponse
import com.stockwatchdog.app.data.api.models.FinnhubSymbolSearch
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Finnhub REST client. Free tier is generous on /quote (~60 calls/min),
 * which we use as the preferred real-time quote source in the fallback chain.
 * Historical candles are premium for most symbols on the free tier, so we
 * deliberately do NOT expose /stock/candle here.
 */
interface FinnhubApi {

    @GET("quote")
    suspend fun quote(
        @Query("symbol") symbol: String,
        @Query("token") apiKey: String
    ): FinnhubQuote

    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("token") apiKey: String
    ): FinnhubSymbolSearch

    @GET("calendar/earnings")
    suspend fun earningsCalendar(
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("symbol") symbol: String,
        @Query("token") apiKey: String
    ): FinnhubEarningsCalendarResponse
}
