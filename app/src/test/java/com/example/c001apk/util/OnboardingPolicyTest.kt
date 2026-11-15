package com.example.c001apk.util

import org.junit.Assert.*
import org.junit.Test

class OnboardingPolicyTest {
    @Test fun legacyCompletionDoesNotReplayBefore10011() {
        assertTrue(OnboardingPolicy.isComplete(10010, true, 0))
        assertTrue(OnboardingPolicy.isComplete(10010, true, 1))
        assertEquals(1, OnboardingPolicy.requiredRevision(10010))
    }

    @Test fun completed10010MustConfirmTheNewFlowFrom10011() {
        assertFalse(OnboardingPolicy.isComplete(10011, true, 0))
        assertFalse(OnboardingPolicy.isComplete(10011, true, 1))
        assertEquals(2, OnboardingPolicy.requiredRevision(10011))
    }

    @Test fun completionStopsReplayingOnRestartAndSubsequentVersions() {
        val revision = OnboardingPolicy.revisionAfterCompletion(10011, 0)
        assertEquals(2, revision)
        assertTrue(OnboardingPolicy.isComplete(10011, true, revision))
        assertTrue(OnboardingPolicy.isComplete(10012, true, revision))
    }

    @Test fun unfinishedOrExplicitlyResetFlowRemainsRequired() {
        assertFalse(OnboardingPolicy.isComplete(10010, false, 0))
        assertFalse(OnboardingPolicy.isComplete(10011, false, 0))
        assertFalse(OnboardingPolicy.isComplete(10011, false, 2))
    }

    @Test fun oldVersionCompletionCannotConsumeTheFutureRevision() {
        assertEquals(1, OnboardingPolicy.revisionAfterCompletion(10010, 0))
        assertFalse(OnboardingPolicy.isComplete(10011, true,
            OnboardingPolicy.revisionAfterCompletion(10010, 0)))
    }

    @Test fun rollbackDoesNotErasePreviouslyCompletedNewContent() {
        assertEquals(2, OnboardingPolicy.revisionAfterCompletion(10010, 2))
        assertTrue(OnboardingPolicy.isComplete(10010, true, 2))
        assertTrue(OnboardingPolicy.isComplete(10011, true, 2))
    }
}
