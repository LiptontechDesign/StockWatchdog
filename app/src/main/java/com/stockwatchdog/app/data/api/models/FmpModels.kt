package com.stockwatchdog.app.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FmpIncomeStatement(
    val date: String? = null,
    val symbol: String? = null,
    val reportedCurrency: String? = null,
    val fiscalYear: String? = null,
    val period: String? = null,
    val revenue: Double? = null,
    val grossProfit: Double? = null,
    val operatingIncome: Double? = null,
    val netIncome: Double? = null,
    val eps: Double? = null,
    val epsDiluted: Double? = null
)

@Serializable
data class FmpBalanceSheet(
    val date: String? = null,
    val symbol: String? = null,
    val reportedCurrency: String? = null,
    val fiscalYear: String? = null,
    val period: String? = null,
    val cashAndCashEquivalents: Double? = null,
    val cashAndShortTermInvestments: Double? = null,
    val totalCurrentAssets: Double? = null,
    val totalCurrentLiabilities: Double? = null,
    val totalDebt: Double? = null,
    val netDebt: Double? = null,
    val totalStockholdersEquity: Double? = null,
    val totalEquity: Double? = null
)

@Serializable
data class FmpCashFlowStatement(
    val date: String? = null,
    val symbol: String? = null,
    val reportedCurrency: String? = null,
    val fiscalYear: String? = null,
    val period: String? = null,
    val operatingCashFlow: Double? = null,
    val freeCashFlow: Double? = null
)

@Serializable
data class FmpEarningsEvent(
    val symbol: String? = null,
    val date: String? = null,
    val epsActual: Double? = null,
    val epsEstimated: Double? = null,
    val revenueActual: Double? = null,
    val revenueEstimated: Double? = null,
    val lastUpdated: String? = null
)

@Serializable
data class FmpQuote(
    val symbol: String? = null,
    val name: String? = null,
    val price: Double? = null,
    val yearHigh: Double? = null,
    val yearLow: Double? = null,
    val priceAvg50: Double? = null,
    val priceAvg200: Double? = null,
    val volume: Long? = null,
    val marketCap: Double? = null,
    val timestamp: Long? = null
)

@Serializable
data class FmpProfile(
    val symbol: String? = null,
    val companyName: String? = null,
    val sector: String? = null,
    val industry: String? = null,
    val exchange: String? = null,
    val isEtf: Boolean? = null,
    val isActivelyTrading: Boolean? = null
)

@Serializable
data class FmpAnalystRecommendation(
    val symbol: String? = null,
    val date: String? = null,
    val analystRatingsStrongBuy: Int? = null,
    @SerialName("analystRatingsbuy") val analystRatingsBuy: Int? = null,
    val analystRatingsHold: Int? = null,
    val analystRatingsSell: Int? = null,
    val analystRatingsStrongSell: Int? = null
)
