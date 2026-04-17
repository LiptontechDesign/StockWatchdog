package com.stockwatchdog.app.data.api

import com.stockwatchdog.app.data.api.models.TwelvePrice
import com.stockwatchdog.app.data.api.models.TwelveQuote
import com.stockwatchdog.app.data.api.models.TwelveSymbolSearch
import com.stockwatchdog.app.data.api.models.TwelveTimeSeries
import retrofit2.http.GET
import retrofit2.http.Query

interface TwelveDataApi {

    @GET("quote")
    suspend fun quote(
        @Query("symbol") symbol: String,
        @Query("apikey") apiKey: String
    ): TwelveQuote

    @GET("price")
    suspend fun price(
        @Query("symbol") symbol: String,
        @Query("apikey") apiKey: String
    ): TwelvePrice

    /**
     * @param interval e.g. "5min", "15min", "30min", "1h", "1day"
     * @param outputsize number of points to return (max usually 5000)
     */
    @GET("time_series")
    suspend fun timeSeries(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("outputsize") outputSize: Int,
        @Query("apikey") apiKey: String
    ): TwelveTimeSeries

    @GET("symbol_search")
    suspend fun search(
        @Query("symbol") query: String,
        @Query("outputsize") outputSize: Int = 10
    ): TwelveSymbolSearch
}
