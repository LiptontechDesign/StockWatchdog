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
}
