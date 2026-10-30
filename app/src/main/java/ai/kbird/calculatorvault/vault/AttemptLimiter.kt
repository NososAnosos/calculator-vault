package ai.kbird.calculatorvault.vault

/**
 * Exponential backoff for failed unlock attempts.
 *
 * The first [FREE_FAILURES] consecutive failures are free; each failure after that
 * locks attempts out for BASE * 2^(n - FREE_FAILURES), capped at [MAX_DELAY_MS].
 * During a lockout, candidate PINs are not checked at all — the calculator just
 * computes, so a locked-out vault is indistinguishable from no vault.
 *
 * State is plain data so the caller can persist it. Times are wall-clock ms;
 * moving the system clock forward shortens a lockout (documented v1 limitation).
 */
data class AttemptState(val consecutiveFailures: Int = 0, val lockedUntil: Long = 0L)

object AttemptLimiter {
    const val FREE_FAILURES = 5
    const val BASE_DELAY_MS = 30_000L
    const val MAX_DELAY_MS = 24 * 60 * 60 * 1000L

    fun canAttempt(state: AttemptState, now: Long): Boolean = now >= state.lockedUntil

    fun onFailure(state: AttemptState, now: Long): AttemptState {
        val n = state.consecutiveFailures + 1
        val over = n - FREE_FAILURES
        val lockedUntil = if (over <= 0) 0L else now + delayFor(over)
        return AttemptState(n, lockedUntil)
    }

    fun onSuccess(): AttemptState = AttemptState()

    /**
     * An internal error after (or during) authentication is NOT a failed
     * attempt. Only a verifier mismatch — a genuinely wrong PIN — may call
     * [onFailure]. If the PIN was correct but opening the vault then threw
     * downstream (e.g. the notes database failed to open, as on 2026-10-28),
     * or key derivation itself threw, the attempt count must stay exactly
     * as it was: burning an attempt there punishes the right PIN and can
     * lock the owner out for an app bug. Callers use this so that rule is
     * explicit (and tested) instead of an accidental omission.
     */
    fun onInternalError(state: AttemptState): AttemptState = state

    internal fun delayFor(over: Int): Long {
        val shift = (over - 1).coerceAtMost(30)
        return (BASE_DELAY_MS shl shift).coerceAtMost(MAX_DELAY_MS)
    }
}
