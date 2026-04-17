package com.stockwatchdog.app.domain

import com.stockwatchdog.app.data.db.entities.AlertEntity
import com.stockwatchdog.app.data.db.entities.AlertType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertEvaluatorTest {

    private fun quote(symbol: String = "AAPL", price: Double, pct: Double? = null): Quote =
        Quote(
            symbol = symbol, name = null, price = price, previousClose = null,
            change = null, percentChange = pct, open = null, high = null, low = null,
            volume = null, marketIsOpen = null, currency = null,
            fetchedAtMillis = 0L
        )

    @Test
    fun priceAbove_firesOnceOnCrossing() {
        val alert = AlertEntity(
            id = 1L, symbol = "AAPL",
            type = AlertType.PRICE_ABOVE, threshold = 200.0,
            lastCrossingState = false
        )
        val first = AlertEvaluator.evaluate(alert, quote(price = 210.0))
        assertTrue(first.shouldNotify)
        assertEquals(true, first.updated.lastCrossingState)

        // Second tick while still above: must NOT re-fire.
        val second = AlertEvaluator.evaluate(first.updated, quote(price = 215.0))
        assertFalse(second.shouldNotify)
        assertEquals(true, second.updated.lastCrossingState)

        // Price falls back below: re-arms the trigger.
        val third = AlertEvaluator.evaluate(second.updated, quote(price = 190.0))
        assertFalse(third.shouldNotify)
        assertEquals(false, third.updated.lastCrossingState)

        // Crosses back above: fires again.
        val fourth = AlertEvaluator.evaluate(third.updated, quote(price = 205.0))
        assertTrue(fourth.shouldNotify)
    }

    @Test
    fun priceBelow_firesOnceOnCrossing() {
        val alert = AlertEntity(
            id = 2L, symbol = "AAPL",
            type = AlertType.PRICE_BELOW, threshold = 100.0,
            lastCrossingState = false
        )
        val first = AlertEvaluator.evaluate(alert, quote(price = 95.0))
        assertTrue(first.shouldNotify)
        val second = AlertEvaluator.evaluate(first.updated, quote(price = 94.0))
        assertFalse(second.shouldNotify)
    }

    @Test
    fun percentChange_firesAtMostOncePerDay() {
        val alert = AlertEntity(
            id = 3L, symbol = "AAPL",
            type = AlertType.PERCENT_CHANGE_DAY, threshold = 3.0
        )
        val now = System.currentTimeMillis()
        val hit = AlertEvaluator.evaluate(alert, quote(price = 10.0, pct = -4.2), now)
        assertTrue(hit.shouldNotify)
        assertNotNull(hit.updated.lastPercentTriggerDate)

        val again = AlertEvaluator.evaluate(hit.updated, quote(price = 10.0, pct = -5.0), now)
        assertFalse(again.shouldNotify)
    }

    @Test
    fun disabledAlert_neverFires() {
        val alert = AlertEntity(
            id = 4L, symbol = "AAPL",
            type = AlertType.PRICE_ABOVE, threshold = 10.0,
            enabled = false, lastCrossingState = false
        )
        val result = AlertEvaluator.evaluate(alert, quote(price = 999.0))
        assertFalse(result.shouldNotify)
    }

    @Test
    fun gainVsEntry_firesOnceOnCrossingAndReArms() {
        val entry = 100.0
        val alert = AlertEntity(
            id = 5L, symbol = "AAPL",
            type = AlertType.PERCENT_ABOVE_ENTRY, threshold = 10.0,
            lastCrossingState = false
        )

        // Price at +5% vs entry: below threshold.
        val below = AlertEvaluator.evaluate(alert, quote(price = 105.0), entryPrice = entry)
        assertFalse(below.shouldNotify)

        // Price at +12% vs entry: crosses threshold, fires.
        val cross = AlertEvaluator.evaluate(below.updated, quote(price = 112.0), entryPrice = entry)
        assertTrue(cross.shouldNotify)

        // Still above threshold: must not re-fire.
        val held = AlertEvaluator.evaluate(cross.updated, quote(price = 120.0), entryPrice = entry)
        assertFalse(held.shouldNotify)

        // Drops back to +3%: disarms.
        val reset = AlertEvaluator.evaluate(held.updated, quote(price = 103.0), entryPrice = entry)
        assertFalse(reset.shouldNotify)

        // Climbs above again: fires again.
        val again = AlertEvaluator.evaluate(reset.updated, quote(price = 115.0), entryPrice = entry)
        assertTrue(again.shouldNotify)
    }

    @Test
    fun lossVsEntry_firesWhenDropThresholdReached() {
        val entry = 200.0
        val alert = AlertEntity(
            id = 6L, symbol = "AAPL",
            type = AlertType.PERCENT_BELOW_ENTRY, threshold = 5.0,
            lastCrossingState = false
        )
        // -4% vs entry: not yet.
        val not = AlertEvaluator.evaluate(alert, quote(price = 192.0), entryPrice = entry)
        assertFalse(not.shouldNotify)

        // -6% vs entry: fires.
        val hit = AlertEvaluator.evaluate(not.updated, quote(price = 188.0), entryPrice = entry)
        assertTrue(hit.shouldNotify)

        // Still below threshold: no re-fire.
        val held = AlertEvaluator.evaluate(hit.updated, quote(price = 180.0), entryPrice = entry)
        assertFalse(held.shouldNotify)
    }

    @Test
    fun entryBasedAlert_noopWhenEntryMissing() {
        val alert = AlertEntity(
            id = 7L, symbol = "AAPL",
            type = AlertType.PERCENT_ABOVE_ENTRY, threshold = 1.0,
            lastCrossingState = false
        )
        val result = AlertEvaluator.evaluate(alert, quote(price = 9999.0), entryPrice = null)
        assertFalse(result.shouldNotify)
        // State left untouched so the alert cleanly resumes once entry is added.
        assertEquals(alert, result.updated)
    }
}
