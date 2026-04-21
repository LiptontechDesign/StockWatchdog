package com.stockwatchdog.app.data.api

import java.util.concurrent.ConcurrentHashMap

/**
 * Per-provider circuit breaker.
 *
 * When a provider fails with a rate-limit / auth / quota error we place it
 * in cooldown so subsequent calls within the cooldown window skip it and
 * try the next provider in the chain. A single successful call clears the
 * cooldown for that provider.
 *
 * The state lives in-process only; a process restart starts with no
 * cooldowns active. That is fine for a mobile app and keeps behaviour
 * predictable across launches.
 */
class ProviderCooldown {

    private val cooldownUntilMillis = ConcurrentHashMap<String, Long>()

    /** Returns true if [provider] is currently cooling down. */
    fun isCoolingDown(provider: String, now: Long = System.currentTimeMillis()): Boolean {
        val until = cooldownUntilMillis[provider] ?: return false
        if (now >= until) {
            cooldownUntilMillis.remove(provider)
            return false
        }
        return true
    }

    /** Mark [provider] as cooling down for [durationMillis]. */
    fun trip(
        provider: String,
        durationMillis: Long,
        now: Long = System.currentTimeMillis()
    ) {
        cooldownUntilMillis[provider] = now + durationMillis
    }

    /** Clear cooldown for [provider] (called on success). */
    fun clear(provider: String) {
        cooldownUntilMillis.remove(provider)
    }

    companion object {
        /** Typical per-minute rate-limit cooldown. */
        const val PER_MINUTE_CAP_MS: Long = 60_000L
        /** Per-day rate-limit cooldown (don't hammer the key again today). */
        const val PER_DAY_CAP_MS: Long = 15 * 60_000L
    }
}
