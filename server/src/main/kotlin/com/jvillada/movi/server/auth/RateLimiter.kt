package com.jvillada.movi.server.auth

import java.util.concurrent.ConcurrentHashMap

/**
 * Simple in-memory per-key sliding-window rate limiter.
 * Thread-safe; no external dependencies.
 *
 * Each call to [allow] atomically prunes timestamps older than [windowMs],
 * checks the count, and — if under the limit — records the current timestamp.
 */
object RateLimiter {

    private val attempts = ConcurrentHashMap<String, MutableList<Long>>()

    /**
     * Returns `true` if the caller identified by [key] is allowed to proceed
     * (fewer than [maxAttempts] calls in the last [windowMs] milliseconds).
     * Returns `false` when the limit is exceeded — the attempt is NOT recorded.
     */
    fun allow(key: String, maxAttempts: Int, windowMs: Long): Boolean {
        val now = System.currentTimeMillis()
        val cutoff = now - windowMs

        // getOrPut is not atomic across threads, but the synchronized block below
        // ensures correctness for the list itself.
        val list = attempts.getOrPut(key) { mutableListOf() }

        synchronized(list) {
            list.removeAll { it < cutoff }
            if (list.size >= maxAttempts) return false
            list.add(now)
        }
        return true
    }

    /** Clears all state — useful in tests. */
    fun reset() = attempts.clear()
}
