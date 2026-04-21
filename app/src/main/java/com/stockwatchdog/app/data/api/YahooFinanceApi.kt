package com.stockwatchdog.app.data.api

import com.stockwatchdog.app.data.api.models.YahooChartEnvelope
import com.stockwatchdog.app.data.api.models.YahooSearchResponse
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Yahoo Finance public JSON endpoints. No API key required.
 * Used as the no-auth fallback layer in the provider chain so the app
 * keeps serving quotes + charts + search even when keyed providers are
 * rate-limited.
 *
 * Note: these are undocumented endpoints. A reasonable User-Agent is
 * required or Yahoo may reject the request.
 */
interface YahooFinanceApi {

    /**
     * Combined quote + history response. Covers stocks, ETFs and indices.
     *
     * @param interval Yahoo intervals, e.g. "5m", "30m", "1d".
     * @param range Yahoo ranges, e.g. "1d", "5d", "1mo", "3mo".
     */
    @Headers(
        "User-Agent: Mozilla/5.0 (Linux; Android 10) StockWatchdog/1.0",
        "Accept: application/json"
    )
    @GET("v8/finance/chart/{symbol}")
    suspend fun chart(
        @Path("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("range") range: String,
        @Query("includePrePost") includePrePost: Boolean = false
    ): YahooChartEnvelope

    /**
     * Symbol search. Returns stocks, ETFs, indices, currencies etc.
     */
    @Headers(
        "User-Agent: Mozilla/5.0 (Linux; Android 10) StockWatchdog/1.0",
        "Accept: application/json"
    )
    @GET("v1/finance/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("quotesCount") quotesCount: Int = 10,
        @Query("newsCount") newsCount: Int = 0
    ): YahooSearchResponse
}
