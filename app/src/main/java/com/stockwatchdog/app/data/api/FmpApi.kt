package com.stockwatchdog.app.data.api

import com.stockwatchdog.app.data.api.models.FmpBalanceSheet
import com.stockwatchdog.app.data.api.models.FmpAnalystRecommendation
import com.stockwatchdog.app.data.api.models.FmpCashFlowStatement
import com.stockwatchdog.app.data.api.models.FmpEarningsEvent
import com.stockwatchdog.app.data.api.models.FmpIncomeStatement
import com.stockwatchdog.app.data.api.models.FmpProfile
import com.stockwatchdog.app.data.api.models.FmpQuote
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface FmpApi {

    @GET("income-statement")
    suspend fun incomeStatements(
        @Query("symbol") symbol: String,
        @Query("period") period: String,
        @Query("limit") limit: Int,
        @Query("apikey") apiKey: String
    ): List<FmpIncomeStatement>

    @GET("balance-sheet-statement")
    suspend fun balanceSheets(
        @Query("symbol") symbol: String,
        @Query("period") period: String,
        @Query("limit") limit: Int,
        @Query("apikey") apiKey: String
    ): List<FmpBalanceSheet>

    @GET("cash-flow-statement")
    suspend fun cashFlowStatements(
        @Query("symbol") symbol: String,
        @Query("period") period: String,
        @Query("limit") limit: Int,
        @Query("apikey") apiKey: String
    ): List<FmpCashFlowStatement>

    @GET("earnings")
    suspend fun earnings(
        @Query("symbol") symbol: String,
        @Query("limit") limit: Int,
        @Query("apikey") apiKey: String
    ): List<FmpEarningsEvent>

    @GET("quote")
    suspend fun quote(
        @Query("symbol") symbol: String,
        @Query("apikey") apiKey: String
    ): List<FmpQuote>

    @GET("profile")
    suspend fun profile(
        @Query("symbol") symbol: String,
        @Query("apikey") apiKey: String
    ): List<FmpProfile>

    @GET("/api/v3/analyst-stock-recommendations/{symbol}")
    suspend fun analystRecommendations(
        @Path("symbol") symbol: String,
        @Query("apikey") apiKey: String
    ): List<FmpAnalystRecommendation>
}
