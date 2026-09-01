package xyz.mdhv.bocal.haptics

import kotlin.math.abs
import kotlin.math.min

/**
 * Turns intonation into feel, so the player never has to look: drift ticks
 * whose strength grows with |cents| (sharper feel when sharp, duller when
 * flat) and a distinct "lock" pulse on entering tune. Hysteresis keeps the
 * lock from chattering at the boundary; a rate limiter keeps the actuator
 * from buzzing continuously.
 *
 * Pure logic — time is injected, output is IR. The renderer decides what the
 * device can actually play.
 */
class TunerHapticPlanner(
    /** |cents| at or below which we consider the note in tune. */
    private val lockEnterCents: Double = 5.0,
    /** |cents| above which we consider the lock lost (must exceed enter). */
    private val lockExitCents: Double = 8.0,
    /** Minimum gap between drift ticks. */
    private val tickIntervalMs: Long = 150,
    /** Cents deviation that maps to a full-strength drift tick. */
    private val fullScaleCents: Double = 50.0,
) {
    init {
        require(lockExitCents > lockEnterCents) { "hysteresis requires exit > enter" }
    }

    private var locked = false
    private var lastTickAtMs = Long.MIN_VALUE / 2

    /**
     * Feed one pitch reading; returns the IR to play right now, or null for
     * silence. [nowMs] must be monotonic (e.g. SystemClock.elapsedRealtime).
     */
    fun onReading(cents: Double, isVoiced: Boolean, nowMs: Long): HapticScore? {
        if (!isVoiced) {
            locked = false
            return null
        }
        val magnitude = abs(cents)

        if (locked) {
            if (magnitude > lockExitCents) locked = false
        } else if (magnitude <= lockEnterCents) {
            locked = true
            lastTickAtMs = nowMs
            return lockPulse()
        }

        if (locked) return null // resting in tune: no buzz

        if (nowMs - lastTickAtMs < tickIntervalMs) return null
        lastTickAtMs = nowMs
        return driftTick(cents)
    }

    fun reset() {
        locked = false
        lastTickAtMs = Long.MIN_VALUE / 2
    }

    /** Crisp, unmistakable arrival: rise into a click. */
    private fun lockPulse() = HapticScore(
        events = listOf(
            HapticEvent.Primitive(PrimitiveKind.QUICK_RISE, scale = 0.6f),
            HapticEvent.Primitive(PrimitiveKind.CLICK, scale = 1f, delayMs = 30),
        ),
    )

    /**
     * Drift feedback: intensity tracks how far off, sharpness tracks the
     * direction — sharp pitch feels sharp (high sharpness tick), flat pitch
     * feels blunt (low-sharpness low tick).
     */
    private fun driftTick(cents: Double): HapticScore {
        val strength = min(1.0, abs(cents) / fullScaleCents).toFloat()
        val kind = if (cents > 0) PrimitiveKind.TICK else PrimitiveKind.LOW_TICK
        val sharpness = if (cents > 0) 0.5f + 0.5f * strength else 0.15f
        return HapticScore(
            events = listOf(
                HapticEvent.Primitive(kind, scale = (0.2f + 0.8f * strength)),
                HapticEvent.Envelope(
                    breakpoints = listOf(
                        Breakpoint(timeMs = 0, intensity = 0.2f * strength, sharpness = sharpness),
                        Breakpoint(timeMs = 40, intensity = 0f, sharpness = sharpness),
                    ),
                ),
            ),
        )
    }
}
