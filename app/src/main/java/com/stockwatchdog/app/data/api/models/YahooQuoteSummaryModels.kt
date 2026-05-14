package com.stockwatchdog.app.data.api.models

import kotlinx.serialization.Serializable

/**
 * Minimal deserialisers for Yahoo Finance's `v10/finance/quoteSummary`
 * endpoint. We only extract the fields the Dip page needs:
 *
 *  - calendarEvents → next earnings date
 *  - earnings       → last quarter EPS actual vs estimate (beat/miss)
 *  - financialData  → analyst price target & recommendation
 *  - summaryDetail  → 52w high/low, 200d MA, average daily volume
 *
 * Most Yahoo numeric fields come back wrapped as `{ "raw": 1.23, "fmt": "1.23" }`
 * — we deserialise that into [YahooRawNumber] and read `.raw`.
 * The endpoint is **undocumented** and occasionally returns 401 unless a
 * crumb is presented; callers must tolerate failure.
 */
@Serializable
data class YahooQuoteSummaryEnvelope(
    val quoteSummary: YahooQuoteSummary? = null
)

@Serializable
data class YahooQuoteSummary(
    val result: List<YahooQuoteSummaryResult> = emptyList(),
    val error: YahooSummaryError? = null
)

@Serializable
data class YahooSummaryError(
    val code: String? = null,
    val description: String? = null
)

@Serializable
data class YahooQuoteSummaryResult(
    val calendarEvents: YahooCalendarEvents? = null,
    val earnings: YahooEarnings? = null,
    val financialData: YahooFinancialData? = null,
    val summaryDetail: YahooSummaryDetail? = null,
    val defaultKeyStatistics: YahooDefaultKeyStatistics? = null,
    val assetProfile: YahooAssetProfile? = null
)

@Serializable
data class YahooRawNumber(
    val raw: Double? = null,
    val fmt: String? = null,
    val longFmt: String? = null
)

@Serializable
data class YahooRawLong(
    val raw: Long? = null,
    val fmt: String? = null
)

// ---- calendarEvents.earnings ------------------------------------------------

@Serializable
data class YahooCalendarEvents(
    val earnings: YahooCalendarEarnings? = null
)

@Serializable
data class YahooCalendarEarnings(
    /** Usually a list of 1-2 epoch-seconds timestamps for the upcoming report. */
    val earningsDate: List<YahooRawLong> = emptyList(),
    val isEarningsDateEstimate: Boolean? = null,
    val earningsAverage: YahooRawNumber? = null,
    val earningsLow: YahooRawNumber? = null,
    val earningsHigh: YahooRawNumber? = null
)

// ---- earnings.earningsChart -------------------------------------------------

@Serializable
data class YahooEarnings(
    val earningsChart: YahooEarningsChart? = null
)

@Serializable
data class YahooEarningsChart(
    val quarterly: List<YahooEarningsQuarter> = emptyList(),
    val currentQuarterEstimate: YahooRawNumber? = null,
    val currentQuarterEstimateDate: String? = null,
    val currentQuarterEstimateYear: Int? = null,
    val earningsDate: List<YahooRawLong> = emptyList()
)

@Serializable
data class YahooEarningsQuarter(
    /** e.g. `"1Q2024"`. */
    val date: String? = null,
    val actual: YahooRawNumber? = null,
    val estimate: YahooRawNumber? = null
)

// ---- financialData ----------------------------------------------------------

@Serializable
data class YahooFinancialData(
    val targetMeanPrice: YahooRawNumber? = null,
    val targetHighPrice: YahooRawNumber? = null,
    val targetLowPrice: YahooRawNumber? = null,
    val numberOfAnalystOpinions: YahooRawLong? = null,
    val recommendationKey: String? = null,
    val recommendationMean: YahooRawNumber? = null,
    val currentPrice: YahooRawNumber? = null,
    val totalRevenue: YahooRawNumber? = null,
    val revenueGrowth: YahooRawNumber? = null,
    val earningsGrowth: YahooRawNumber? = null,
    val grossMargins: YahooRawNumber? = null,
    val ebitdaMargins: YahooRawNumber? = null,
    val operatingMargins: YahooRawNumber? = null,
    val profitMargins: YahooRawNumber? = null,
    val freeCashflow: YahooRawNumber? = null,
    val operatingCashflow: YahooRawNumber? = null,
    val totalCash: YahooRawNumber? = null,
    val totalDebt: YahooRawNumber? = null,
    val debtToEquity: YahooRawNumber? = null,
    val currentRatio: YahooRawNumber? = null
)

// ---- summaryDetail ----------------------------------------------------------

@Serializable
data class YahooSummaryDetail(
    val fiftyTwoWeekHigh: YahooRawNumber? = null,
    val fiftyTwoWeekLow: YahooRawNumber? = null,
    val twoHundredDayAverage: YahooRawNumber? = null,
    val fiftyDayAverage: YahooRawNumber? = null,
    val trailingPE: YahooRawNumber? = null,
    val forwardPE: YahooRawNumber? = null,
    val dividendYield: YahooRawNumber? = null,
    val averageVolume: YahooRawLong? = null,
    val averageDailyVolume10Day: YahooRawLong? = null,
    val volume: YahooRawLong? = null,
    val regularMarketVolume: YahooRawLong? = null
)

@Serializable
data class YahooDefaultKeyStatistics(
    val trailingEps: YahooRawNumber? = null,
    val forwardEps: YahooRawNumber? = null,
    val pegRatio: YahooRawNumber? = null,
    val priceToBook: YahooRawNumber? = null,
    val enterpriseToRevenue: YahooRawNumber? = null,
    val enterpriseToEbitda: YahooRawNumber? = null
)

@Serializable
data class YahooAssetProfile(
    val sector: String? = null,
    val industry: String? = null
)
