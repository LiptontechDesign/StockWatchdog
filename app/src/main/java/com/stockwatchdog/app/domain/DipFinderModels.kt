package com.stockwatchdog.app.domain

/**
 * Plain-English label assigned to a ticker after we look at price + health.
 * Order matters for sorting (STRONG_DIP first; NOT_IN_DIP last).
 */
enum class DipLabel(val displayName: String, val tagline: String) {
    STRONG_DIP(
        "Strong Dip",
        "Down a lot, but the company still looks healthy."
    ),
    WATCH_DIP(
        "Watch Dip",
        "Down — some information is mixed or missing."
    ),
    RISKY_DIP(
        "Risky Dip",
        "Down — but the company has warning signs."
    ),
    VALUE_TRAP(
        "Value Trap",
        "Down because the business may be getting weaker."
    ),
    NOT_IN_DIP(
        "Not in Dip",
        "Not currently in a major dip."
    )
}

/** How much we trust this analysis given the data we managed to gather. */
enum class DipConfidence(val displayName: String) {
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low")
}

/**
 * Raw signals we extract from price + fundamentals before scoring. Keeping
 * them separate from the score makes the reason text easier to compose and
 * the scoring logic easier to test in isolation.
 *
 * Any field can be null if the underlying provider didn't return that data
 * point — the scorer tolerates partial input by only counting the signals
 * it can actually evaluate.
 */
data class DipSignals(
    // --- Price signals ---
    /** Latest price the analysis ran on. */
    val currentPrice: Double?,
    /** Highest close in the last 12 months. */
    val high52w: Double?,
    /** Lowest close in the last 12 months. */
    val low52w: Double?,
    /** Simple moving average of the last 200 trading days. */
    val ma200: Double?,
    /** % below 52w high (0..100, larger = bigger drop). */
    val pctFromHigh: Double?,
    /** % above 52w low (0..100, smaller = closer to low). */
    val pctFromLow: Double?,

    // --- Company-health signals ---
    /** Year-over-year revenue growth, percent (e.g. 12.4 means +12.4%). */
    val revenueGrowthYoYPct: Double?,
    /** Net profit margin, percent. */
    val profitMarginPct: Double?,
    /** Debt-to-equity ratio. */
    val debtToEquity: Double?,
    /** Earnings per share (TTM). Used as a rough proxy for profitability. */
    val epsTtm: Double?
)

/**
 * Final result of a dip analysis, persisted to the `dip_finder_results`
 * cache table and rendered directly in the UI. The reason field is plain
 * English so a non-financial user can understand it at a glance.
 */
data class DipAnalysis(
    val symbol: String,
    val name: String?,
    val currentPrice: Double?,
    val pctFromHigh: Double?,
    val nearLow: Boolean,
    val score: Int,
    val label: DipLabel,
    val confidence: DipConfidence,
    val reason: String,
    val computedAtMillis: Long,
    val signals: DipSignals
)
