package xyz.mdhv.bocal.session

import kotlin.math.abs
import xyz.mdhv.bocal.data.bundle.NoteAttempt
import xyz.mdhv.bocal.model.SessionScore

/**
 * Aggregate quality read of one practice session. All metrics are hold-time
 * weighted where it matters, so a long, steady tone counts more than a
 * momentary blip.
 */
data class SessionMetrics(
    val attemptCount: Int,
    val totalHoldMs: Long,
    val meanHoldMs: Double,
    /** Hold-weighted mean absolute intonation error, in cents. Lower is better. */
    val meanAbsCents: Double,
    /** Share of held time within the in-tune window, 0..100. */
    val percentInTune: Double,
    /** Hold-weighted signed bias: positive = tends sharp, negative = tends flat. */
    val intonationBiasCents: Double,
    /** Hold-weighted mean detector confidence, 0..1. */
    val meanConfidence: Double,
    /** Hold-weighted mean tone brightness, or null if no timbre data. */
    val meanCentroidHz: Double?,
) {
    /**
     * A single higher-is-better score for the longitudinal trend engine.
     * Chosen to be intuitive and monotonic in accuracy: 100 when perfectly in
     * tune, dropping one point per cent of mean absolute error.
     */
    fun accuracyScore(): Double = (100.0 - meanAbsCents).coerceIn(0.0, 100.0)

    fun toSessionScore(index: Int, epochDay: Long): SessionScore =
        SessionScore(index = index, epochDay = epochDay, value = accuracyScore())

    companion object {
        val EMPTY = SessionMetrics(
            attemptCount = 0, totalHoldMs = 0, meanHoldMs = 0.0, meanAbsCents = 0.0,
            percentInTune = 0.0, intonationBiasCents = 0.0, meanConfidence = 0.0,
            meanCentroidHz = null,
        )
    }
}

class SessionAnalyzer(
    /** |cents| at or below which a note counts as in tune (matches the tuner). */
    private val inTuneToleranceCents: Double = 5.0,
) {
    fun analyze(attempts: List<NoteAttempt>): SessionMetrics {
        if (attempts.isEmpty()) return SessionMetrics.EMPTY

        val totalHoldMs = attempts.sumOf { it.holdMs }
        // Weight by hold time; guard the degenerate all-zero-hold case.
        val weightTotal = totalHoldMs.toDouble().takeIf { it > 0.0 } ?: attempts.size.toDouble()
        fun weightOf(a: NoteAttempt): Double =
            if (totalHoldMs > 0) a.holdMs.toDouble() else 1.0

        var wAbs = 0.0
        var wBias = 0.0
        var wConf = 0.0
        var wCentroid = 0.0
        var centroidWeight = 0.0
        var inTuneHold = 0.0
        for (a in attempts) {
            val w = weightOf(a)
            wAbs += w * abs(a.meanCents)
            wBias += w * a.meanCents
            wConf += w * a.confidence
            val centroid = a.meanCentroidHz
            if (centroid != null) {
                wCentroid += w * centroid
                centroidWeight += w
            }
            if (abs(a.meanCents) <= inTuneToleranceCents) inTuneHold += w
        }

        return SessionMetrics(
            attemptCount = attempts.size,
            totalHoldMs = totalHoldMs,
            meanHoldMs = totalHoldMs.toDouble() / attempts.size,
            meanAbsCents = wAbs / weightTotal,
            percentInTune = 100.0 * inTuneHold / weightTotal,
            intonationBiasCents = wBias / weightTotal,
            meanConfidence = wConf / weightTotal,
            meanCentroidHz = if (centroidWeight > 0) wCentroid / centroidWeight else null,
        )
    }
}
