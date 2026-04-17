package com.stockwatchdog.app.data.api

import com.stockwatchdog.app.data.api.models.AlphaGlobalQuoteEnvelope
import com.stockwatchdog.app.data.api.models.AlphaSymbolSearch
import com.stockwatchdog.app.data.api.models.AlphaTimeSeriesDaily
import com.stockwatchdog.app.data.api.models.AlphaTimeSeriesIntraday
import retrofit2.http.GET
import retrofit2.http.Query

interface AlphaVantageApi {

    @GET("query?function=GLOBAL_QUOTE")
    suspend fun globalQuote(
        @Query("symbol") symbol: String,
        @Query("apikey") apiKey: String
    ): AlphaGlobalQuoteEnvelope

    @GET("query?function=SYMBOL_SEARCH")
    suspend fun search(
        @Query("keywords") query: String,
        @Query("apikey") apiKey: String
    ): AlphaSymbolSearch

    @GET("query?function=TIME_SERIES_INTRADAY")
    suspend fun intraday(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("outputsize") outputSize: String,
        @Query("apikey") apiKey: String
    ): AlphaTimeSeriesIntraday

    @GET("query?function=TIME_SERIES_DAILY")
    suspend fun daily(
        @Query("symbol") symbol: String,
        @Query("outputsize") outputSize: String,
        @Query("apikey") apiKey: String
    ): AlphaTimeSeriesDaily
}
