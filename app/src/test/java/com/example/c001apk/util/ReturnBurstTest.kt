package com.example.c001apk.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReturnBurstTest {
    private var now = 1_000L
    private val burst = ReturnBurst<Any> { now }
    private val top = Any()
    private val middle = Any()
    private val bottom = Any()

    @Test fun fastCloseOfRevealedPageSkipsOnlyTheSecondAnimation() {
        burst.onResume(top, 7)
        assertFalse(burst.shouldSkipClose(top, 7))
        burst.onClose(top, 7)
        assertFalse(burst.shouldSkipClose(top, 7))
        burst.onPause(top, 7)
        now += 90
        burst.onResume(middle, 7)
        assertTrue(burst.shouldSkipClose(middle, 7))
        assertFalse(burst.shouldSkipClose(bottom, 7))
        now += 261
        assertFalse(burst.shouldSkipClose(middle, 7))
    }

    @Test fun ordinaryPausesAndOtherTasksDoNotCountAsRapidReturn() {
        burst.onResume(top, 7)
        burst.onPause(top, 7)
        burst.onResume(middle, 7)
        assertFalse(burst.shouldSkipClose(middle, 7))
        burst.onClose(middle, 7)
        burst.onPause(middle, 7)
        burst.onResume(bottom, 8)
        assertFalse(burst.shouldSkipClose(bottom, 8))
    }

    @Test fun aNewPageOrAnInterveningNavigationClearsThePendingClose() {
        burst.onResume(top, 7)
        burst.onClose(top, 7)
        burst.onPause(top, 7)
        burst.onCreate()
        burst.onResume(middle, 7)
        assertFalse(burst.shouldSkipClose(middle, 7))
        burst.onClose(middle, 7)
        burst.onPause(middle, 7)
        burst.onResume(bottom, 7)
        assertTrue(burst.shouldSkipClose(bottom, 7))
        burst.onPause(bottom, 7)
        burst.onResume(bottom, 7)
        assertFalse(burst.shouldSkipClose(bottom, 7))
    }
}
