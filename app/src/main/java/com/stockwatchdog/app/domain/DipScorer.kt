package com.stockwatchdog.app.domain

/**
 * Pure scoring function — converts raw [DipSignals] into a 0..100 score, a
 * plain-English [DipLabel], a [DipConfidence] level, and a short reason
 * string. No side effects, no I/O — easy to unit-test.
 *
 * Scoring is deliberately conservative and explainable, not statistically
 * tuned. It gives:
 *
 *  - up to 50 pts from price signals (drop from high, near 52w low, below MA200)
 *  - up to 50 pts from health signals (revenue, profitability, debt, earnings)
 *
 * The label is then picked from a small decision table that combines the
 * total score with the size of the drop and the share of health signals
 * that came back positive.
 */
object DipScorer {

    /** Minimum drop from 52w high we treat as "in a dip" at all. */
    private const val MIN_DIP_PCT = 15.0

    /** Window (percent above 52w low) that we still consider "near low". */
    private const val NEAR_LOW_WINDOW = 10.0

    /**
     * Run the analysis. The resulting [DipAnalysis] always has a label,
     * a score and a reason — even when input data is sparse. Sparse data
     * produces a [DipConfidence.LOW] result rather than crashing.
     */
    fun analyze(
        symbol: String,
        name: String?,
        signals: DipSignals,
        computedAtMillis: Long
    ): DipAnalysis {
        val priceScore = scorePrice(signals)
        val (healthScore, healthMax, healthPositives) = scoreHealth(signals)
        val total = (priceScore + healthScore).coerceIn(0, 100)
        val confidence = computeConfidence(signals, healthMax)
        val nearLow = isNearLow(signals)
        val label = pickLabel(
            pctFromHigh = signals.pctFromHigh,
            total = total,
            healthScore = healthScore,
            healthMax = healthMax,
            healthPositives = healthPositives
        )
        val reason = buildReason(symbol, name, signals, label, nearLow)
        return DipAnalysis(
            symbol = symbol,
            name = name,
            currentPrice = signals.currentPrice,
            pctFromHigh = signals.pctFromHigh,
            nearLow = nearLow,
            score = total,
            label = label,
            confidence = confidence,
            reason = reason,
            computedAtMillis = computedAtMillis,
            signals = signals
        )
    }

    // --- Sub-scorers -----------------------------------------------------

    private fun scorePrice(s: DipSignals): Int {
        var score = 0

        // Drop from 52w high — the biggest single signal of "in a dip".
        s.pctFromHigh?.let { drop ->
            score += when {
                drop >= 60 -> 30
                drop >= 45 -> 25
                drop >= 30 -> 20
                drop >= 20 -> 12
                drop >= 10 -> 6
                else -> 0
            }
        }

        // Near 52w low → adds urgency to the dip.
        s.pctFromLow?.let { aboveLow ->
            score += when {
                aboveLow <= 5 -> 12
                aboveLow <= 10 -> 8
                aboveLow <= 20 -> 4
                else -> 0
            }
        }

        // Below 200-day moving average → confirms it's a real downtrend, not noise.
        if (s.currentPrice != null && s.ma200 != null && s.ma200 > 0) {
            val belowMaPct = (s.ma200 - s.currentPrice) / s.ma200 * 100.0
            score += when {
                belowMaPct >= 15 -> 8
                belowMaPct >= 5 -> 5
                belowMaPct > 0 -> 2
                else -> 0
            }
        }

        return score.coerceAtMost(50)
    }

    /**
     * Returns (score, maxAvailable, positivesCount).
     * `maxAvailable` reflects how many points we *could* have awarded given
     * the data we actually had — used to compute confidence and to detect
     * "value trap" situations where most health signals came back negative.
     */
    private fun scoreHealth(s: DipSignals): Triple<Int, Int, Int> {
        var score = 0
        var max = 0
        var positives = 0

        // Revenue growth: stable or growing = good.
        s.revenueGrowthYoYPct?.let { g ->
            max += 15
            if (g > 0) {
                score += 15
                positives += 1
            } else if (g > -5) {
                score += 7 // mild decline still tolerable
            }
        }

        // Profit margin: company is making money.
        s.profitMarginPct?.let { m ->
            max += 15
            if (m > 10) {
                score += 15
                positives += 1
            } else if (m > 3) {
                score += 9
                positives += 1
            } else if (m > 0) {
                score += 4
            }
        }

        // Debt: not too aggressive.
        s.debtToEquity?.let { d ->
            max += 10
            if (d < 0.8) {
                score += 10
                positives += 1
            } else if (d < 1.5) {
                score += 6
                positives += 1
            } else if (d < 3.0) {
                score += 2
            }
        }

        // EPS: company is profitable on a per-share basis.
        s.epsTtm?.let { e ->
            max += 10
            if (e > 1.0) {
                score += 10
                positives += 1
            } else if (e > 0) {
                score += 5
                positives += 1
            }
        }

        return Triple(score.coerceAtMost(50), max, positives)
    }

    private fun computeConfidence(s: DipSignals, healthMax: Int): DipConfidence {
        val hasPrice = s.currentPrice != null && s.high52w != null && s.low52w != null
        return when {
            hasPrice && healthMax >= 35 -> DipConfidence.HIGH
            hasPrice && healthMax >= 15 -> DipConfidence.MEDIUM
            else -> DipConfidence.LOW
        }
    }

    private fun isNearLow(s: DipSignals): Boolean {
        val above = s.pctFromLow ?: return false
        return above <= NEAR_LOW_WINDOW
    }

    private fun pickLabel(
        pctFromHigh: Double?,
        total: Int,
        healthScore: Int,
        healthMax: Int,
        healthPositives: Int
    ): DipLabel {
        // Not enough drop → not in a dip at all, regardless of health.
        if (pctFromHigh == null || pctFromHigh < MIN_DIP_PCT) {
            return DipLabel.NOT_IN_DIP
        }

        // Compute share of health signals that were positive (0..1).
        // If we got NO health data, we treat it as "missing" → WATCH_DIP.
        val healthShare = if (healthMax > 0) healthScore.toDouble() / healthMax else -1.0

        return when {
            // Big drop with strong fundamentals → opportunity.
            total >= 70 && healthShare >= 0.65 -> DipLabel.STRONG_DIP

            // Big drop, fundamentals look broken → classic value trap.
            pctFromHigh >= 25 && healthMax >= 25 && healthShare < 0.20 ->
                DipLabel.VALUE_TRAP

            // Drop with weakening fundamentals (but not catastrophic).
            healthMax >= 25 && healthShare < 0.40 -> DipLabel.RISKY_DIP

            // Anything else with a meaningful drop is a Watch Dip
            // (data is mixed or partially missing).
            else -> DipLabel.WATCH_DIP
        }
    }

    // --- Plain-English reason ------------------------------------------

    private fun buildReason(
        symbol: String,
        name: String?,
        s: DipSignals,
        label: DipLabel,
        nearLow: Boolean
    ): String {
        val who = name?.takeIf { it.isNotBlank() } ?: symbol
        val drop = s.pctFromHigh
        val parts = mutableListOf<String>()

        when {
            drop == null -> parts += "$who has limited price history available."
            drop < MIN_DIP_PCT -> parts += "$who is only ${"%.0f".format(drop)}% off its recent high — not a major dip yet."
            else -> parts += "$who is down ${"%.0f".format(drop)}% from its recent high"
                .let { if (nearLow) "$it and is close to its 52-week low." else "$it." }
        }

        when (label) {
            DipLabel.STRONG_DIP ->
                parts += "The business still appears healthy based on available data."
            DipLabel.WATCH_DIP ->
                parts += "Some company-health information is mixed or missing, so be cautious."
            DipLabel.RISKY_DIP ->
                parts += "There are warning signs in the company's health — be careful."
            DipLabel.VALUE_TRAP ->
                parts += "Several health signals look weak — this may be a value trap."
            DipLabel.NOT_IN_DIP ->
                parts += "It is not currently in a meaningful dip."
        }

        if (label != DipLabel.NOT_IN_DIP) {
            parts += "This may be a ${label.displayName}."
        }

        return parts.joinToString(" ")
    }

    // --- Helpers used by the repository to build [DipSignals] ----------

    /**
     * Compute a 200-day simple moving average from a list of closing prices,
     * ordered oldest -> newest. Returns null if we have fewer than 200 points.
     */
    fun simpleMovingAverage200(closes: List<Double>): Double? {
        if (closes.size < 200) return null
        return closes.takeLast(200).average()
    }

    /** Percent below the 52w high (positive when current is below high). */
    fun pctFromHigh(current: Double?, high: Double?): Double? {
        if (current == null || high == null || high <= 0) return null
        if (current >= high) return 0.0
        return ((high - current) / high) * 100.0
    }

    /** Percent above the 52w low (positive when current is above low). */
    fun pctFromLow(current: Double?, low: Double?): Double? {
        if (current == null || low == null || low <= 0) return null
        if (current <= low) return 0.0
        return ((current - low) / low) * 100.0
    }
}
