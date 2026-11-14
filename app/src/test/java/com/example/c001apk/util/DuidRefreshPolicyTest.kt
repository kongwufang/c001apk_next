package com.example.c001apk.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuidRefreshPolicyTest {
    private val acquiredAt = 1800000000000L
    private val day = 86400000L

    @Test fun waitsAFull24HoursFromLastSuccess() {
        assertFalse(DuidRefreshPolicy.shouldRefresh(true, true, true, acquiredAt, acquiredAt))
        assertFalse(DuidRefreshPolicy.shouldRefresh(true, true, true, acquiredAt, acquiredAt + day - 1))
        assertTrue(DuidRefreshPolicy.shouldRefresh(true, true, true, acquiredAt, acquiredAt + day))
        assertTrue(DuidRefreshPolicy.shouldRefresh(true, true, true, acquiredAt, acquiredAt + 2 * day))
    }

    @Test fun disabledOrUnconfirmedOnboardingNeverFetches() {
        assertFalse(DuidRefreshPolicy.shouldRefresh(false, true, true, acquiredAt, acquiredAt + 2 * day))
        assertFalse(DuidRefreshPolicy.shouldRefresh(true, false, false, 0, acquiredAt))
        assertFalse(DuidRefreshPolicy.shouldRefresh(false, false, false, 0, acquiredAt))
    }

    @Test fun missingIdOrMissingAcquisitionRecordIsDue() {
        assertTrue(DuidRefreshPolicy.shouldRefresh(true, true, false, acquiredAt, acquiredAt))
        assertTrue(DuidRefreshPolicy.shouldRefresh(true, true, true, 0, acquiredAt))
    }

    @Test fun clockRollbackDoesNotCauseAnEarlyRefresh() {
        assertFalse(DuidRefreshPolicy.shouldRefresh(true, true, true, acquiredAt, acquiredAt - day))
    }

    @Test fun recordingAnotherSuccessStartsANewWindow() {
        val nextSuccess = acquiredAt + day + 12345
        assertFalse(DuidRefreshPolicy.shouldRefresh(true, true, true, nextSuccess, nextSuccess + day - 1))
        assertTrue(DuidRefreshPolicy.shouldRefresh(true, true, true, nextSuccess, nextSuccess + day))
    }
}
