package com.arcshield.app.preenv

import java.time.Duration
import java.time.Instant

object ShiftPhaseDetector {

    private val STARTUP_WINDOW  = Duration.ofMinutes(60)
    private val SHUTDOWN_WINDOW = Duration.ofMinutes(60)

    fun detect(
        shiftStartedAt: Instant?,
        shiftDurationHours: Int,
        now: Instant = Instant.now(),
    ): ShiftPhase {
        if (shiftStartedAt == null) return ShiftPhase.UNKNOWN
        val elapsed     = Duration.between(shiftStartedAt, now)
        val shiftLength = Duration.ofHours(shiftDurationHours.toLong())
        return when {
            elapsed.isNegative                       -> ShiftPhase.UNKNOWN
            elapsed < STARTUP_WINDOW                 -> ShiftPhase.STARTUP
            elapsed > shiftLength - SHUTDOWN_WINDOW  -> ShiftPhase.SHUTDOWN
            else                                     -> ShiftPhase.STEADY_STATE
        }
    }
}
