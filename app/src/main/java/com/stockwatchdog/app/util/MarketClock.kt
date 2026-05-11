package com.stockwatchdog.app.util

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Lightweight market-hours clock + Kenya-time conversions.
 *
 * Designed for a user based in Nairobi (Africa/Nairobi, UTC+3) who tracks
 * mostly US stocks. We display every market state in **local Kenya time**
 * so the user never has to convert from New York time mentally.
 *
 * No external deps; uses `java.time` (API 26+, which this app already
 * targets per `compileSdk` >= 33).
 */
object MarketClock {

    val KENYA: ZoneId = ZoneId.of("Africa/Nairobi")

    /** Supported markets we model. Hours are regular session only. */
    enum class Market(
        val label: String,
        val zoneId: ZoneId,
        val open: LocalTime,
        val close: LocalTime
    ) {
        US_NYSE(
            label = "US (NYSE/NASDAQ)",
            zoneId = ZoneId.of("America/New_York"),
            open = LocalTime.of(9, 30),
            close = LocalTime.of(16, 0)
        ),
        UK_LSE(
            label = "London (LSE)",
            zoneId = ZoneId.of("Europe/London"),
            open = LocalTime.of(8, 0),
            close = LocalTime.of(16, 30)
        )
    }

    enum class State { OPEN, CLOSED_PRE, CLOSED_POST, CLOSED_WEEKEND }

    data class MarketStatus(
        val market: Market,
        val state: State,
        /** True iff the market is currently open. */
        val isOpen: Boolean,
        /**
         * Duration until the next state transition (the next open if
         * closed, the next close if open). Always positive.
         */
        val until: Duration,
        /** ZonedDateTime of that next transition, in Kenya time. */
        val nextChangeAtKenya: ZonedDateTime
    )

    /**
     * Compute the current status of [market] relative to the given
     * instant (defaults to "now"). The result's countdown is in Kenya
     * time so it's safe to display directly.
     */
    fun status(market: Market, nowUtcMillis: Long = System.currentTimeMillis()): MarketStatus {
        val nowLocal = ZonedDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(nowUtcMillis),
            market.zoneId
        )
        val dow = nowLocal.dayOfWeek
        val isWeekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY

        val todayOpen = nowLocal.with(market.open).withSecond(0).withNano(0)
        val todayClose = nowLocal.with(market.close).withSecond(0).withNano(0)

        val (state, nextLocal) = when {
            isWeekend -> State.CLOSED_WEEKEND to nextMondayOpen(nowLocal, market)
            nowLocal.isBefore(todayOpen) -> State.CLOSED_PRE to todayOpen
            nowLocal.isAfter(todayClose) -> State.CLOSED_POST to nextWeekdayOpen(nowLocal, market)
            else -> State.OPEN to todayClose
        }

        val nextKenya = nextLocal.withZoneSameInstant(KENYA)
        val until = Duration.between(nowLocal, nextLocal).abs()
        return MarketStatus(
            market = market,
            state = state,
            isOpen = state == State.OPEN,
            until = until,
            nextChangeAtKenya = nextKenya
        )
    }

    private fun nextMondayOpen(from: ZonedDateTime, market: Market): ZonedDateTime {
        var d = from.with(market.open).withSecond(0).withNano(0)
        while (d.dayOfWeek == DayOfWeek.SATURDAY ||
            d.dayOfWeek == DayOfWeek.SUNDAY ||
            !d.isAfter(from)
        ) {
            d = d.plusDays(1)
        }
        return d
    }

    private fun nextWeekdayOpen(from: ZonedDateTime, market: Market): ZonedDateTime {
        var d = from.plusDays(1).with(market.open).withSecond(0).withNano(0)
        while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) {
            d = d.plusDays(1)
        }
        return d
    }

    /**
     * Format a Duration as a short human countdown.
     * - `>= 1 day` → `"3d 4h"`
     * - `>= 1 hour` → `"4h 12m"`
     * - else → `"12m 30s"`
     */
    fun formatCountdown(d: Duration): String {
        val total = d.seconds.coerceAtLeast(0)
        val days = total / 86_400
        val hours = (total % 86_400) / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    /** Format a ZonedDateTime in Kenya time like `"Wed 16:30 EAT"`. */
    fun formatKenyaShort(dt: ZonedDateTime): String {
        val dt2 = dt.withZoneSameInstant(KENYA)
        val day = dt2.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercaseChar() }
        val hh = dt2.hour.toString().padStart(2, '0')
        val mm = dt2.minute.toString().padStart(2, '0')
        return "$day $hh:$mm EAT"
    }

    /** Format an epoch-seconds timestamp as `"Thu, 15 Jan 16:30 EAT"`. */
    fun formatKenyaLong(epochSeconds: Long): String {
        val dt = ZonedDateTime.ofInstant(
            java.time.Instant.ofEpochSecond(epochSeconds),
            KENYA
        )
        val day = dt.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercaseChar() }
        val month = dt.month.name.take(3).lowercase().replaceFirstChar { it.uppercaseChar() }
        val hh = dt.hour.toString().padStart(2, '0')
        val mm = dt.minute.toString().padStart(2, '0')
        return "$day, ${dt.dayOfMonth} $month $hh:$mm EAT"
    }

    /** Days remaining until an epoch-seconds future timestamp. Negative if past. */
    fun daysUntil(epochSeconds: Long, nowMillis: Long = System.currentTimeMillis()): Long {
        val diffMs = epochSeconds * 1000L - nowMillis
        return diffMs / 86_400_000L
    }
}
