package ai.kbird.calculatorvault.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttemptLimiterTest {

    @Test fun freeFailuresThenExponentialBackoff() {
        var s = AttemptState()
        val now = 1_000_000L
        repeat(AttemptLimiter.FREE_FAILURES) {
            assertTrue(AttemptLimiter.canAttempt(s, now))
            s = AttemptLimiter.onFailure(s, now)
        }
        assertTrue(AttemptLimiter.canAttempt(s, now))

        s = AttemptLimiter.onFailure(s, now)
        assertEquals(now + 30_000L, s.lockedUntil)
        assertFalse(AttemptLimiter.canAttempt(s, now + 29_999L))
        assertTrue(AttemptLimiter.canAttempt(s, now + 30_000L))

        s = AttemptLimiter.onFailure(s, now)
        assertEquals(now + 60_000L, s.lockedUntil)
        s = AttemptLimiter.onFailure(s, now)
        assertEquals(now + 120_000L, s.lockedUntil)
    }

    @Test fun delayIsCapped() {
        assertEquals(AttemptLimiter.MAX_DELAY_MS, AttemptLimiter.delayFor(40))
        assertEquals(AttemptLimiter.MAX_DELAY_MS, AttemptLimiter.delayFor(1000))
    }

    @Test fun successResets() {
        assertEquals(AttemptState(), AttemptLimiter.onSuccess())
    }

    @Test fun internalErrorDoesNotBurnAnAttempt() {
        // Regression test for the 2026-10-28 wart: a correct PIN whose
        // downstream open crashed was counted as a failure and locked
        // the owner out. Internal errors must leave the state untouched,
        // at every failure count, including in/around a lockout window.
        val now = 1_000_000L
        var s = AttemptState()
        assertEquals(s, AttemptLimiter.onInternalError(s))

        repeat(AttemptLimiter.FREE_FAILURES + 2) {
            s = AttemptLimiter.onFailure(s, now)
        }
        val before = s
        assertEquals(before, AttemptLimiter.onInternalError(before))
        assertEquals(before.consecutiveFailures, AttemptLimiter.onInternalError(before).consecutiveFailures)
        assertEquals(before.lockedUntil, AttemptLimiter.onInternalError(before).lockedUntil)
    }

    @Test fun internalErrorAfterSuccessStaysReset() {
        // Correct PIN -> limiter resets -> downstream open fails.
        // The state the caller keeps is the reset, never a new failure.
        val afterSuccess = AttemptLimiter.onSuccess()
        assertEquals(AttemptState(), AttemptLimiter.onInternalError(afterSuccess))
    }
}
