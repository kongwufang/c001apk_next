package com.example.c001apk.util

/** Wall-clock policy shared by foreground refresh and its JVM boundary tests. */
internal object DuidRefreshPolicy {
    const val INTERVAL_MS = 24 * 60 * 60 * 1000L

    fun shouldRefresh(enabled: Boolean, onboarded: Boolean, hasId: Boolean, lastSuccess: Long, now: Long): Boolean {
        if (!enabled || !onboarded) return false
        if (!hasId || lastSuccess <= 0) return true
        // A clock rollback must not cause repeated early requests.
        return now >= lastSuccess && now - lastSuccess >= INTERVAL_MS
    }
}
