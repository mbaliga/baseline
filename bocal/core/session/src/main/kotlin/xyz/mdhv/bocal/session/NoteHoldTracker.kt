package xyz.mdhv.bocal.session

import kotlin.math.sqrt
import xyz.mdhv.bocal.data.bundle.NoteAttempt

/**
 * Turns a stream of per-frame pitch readings into discrete held-note
 * [NoteAttempt]s — the atom the metrics, tendency maps, and trend engine all
 * aggregate over.
 *
 * A "hold" is a run of voiced frames resolving to the same note. It ends when
 * the note changes, when the signal goes unvoiced for longer than
 * [gapToleranceMs] (a short catch-breath is tolerated), or on [flush] at
 * session end. Holds shorter than [minHoldMs] are discarded as blips.
 *
 * Single-threaded: feed it from the same analysis worker that runs the
 * detector. Timestamps must be monotonic (e.g. SystemClock.elapsedRealtime).
 */
class NoteHoldTracker(
    private val minHoldMs: Long = 250,
    private val gapToleranceMs: Long = 120,
) {
    init {
        require(minHoldMs >= 0 && gapToleranceMs >= 0)
    }

    private var sessionStartMs: Long = UNSET
    private var holdMidi: Int? = null
    private var holdStartMs: Long = 0
    private var lastVoicedMs: Long = 0

    // Welford accumulators for the current hold.
    private var n = 0
    private var centsMean = 0.0
    private var centsM2 = 0.0
    private var confSum = 0.0
    private var centroidSum = 0.0
    private var centroidCount = 0

    /**
     * Feed one frame. Returns a finalized [NoteAttempt] when this frame closed
     * the previous hold (note change or gap), otherwise null.
     */
    fun onSample(
        midi: Int,
        cents: Double,
        confidence: Double,
        isVoiced: Boolean,
        centroidHz: Double?,
        timestampMs: Long,
    ): NoteAttempt? {
        if (sessionStartMs == UNSET) sessionStartMs = timestampMs

        if (!isVoiced) {
            // Close the hold only once the silence exceeds tolerance; end it at
            // the last voiced frame so trailing silence isn't counted as hold.
            return if (holdMidi != null && timestampMs - lastVoicedMs > gapToleranceMs) {
                finalize()
            } else {
                null
            }
        }

        // A note change or an over-tolerance gap ends the current hold and
        // starts a fresh one on this frame.
        val gapExceeded = holdMidi != null && timestampMs - lastVoicedMs > gapToleranceMs
        if (holdMidi != null && (midi != holdMidi || gapExceeded)) {
            val finished = finalize()
            startHold(midi, timestampMs)
            accumulate(cents, confidence, centroidHz, timestampMs)
            return finished
        }

        if (holdMidi == null) startHold(midi, timestampMs)
        accumulate(cents, confidence, centroidHz, timestampMs)
        return null
    }

    /** Finalize any dangling hold at session end. Call once when stopping. */
    fun flush(): NoteAttempt? = if (holdMidi != null) finalize() else null

    fun reset() {
        sessionStartMs = UNSET
        resetHold()
    }

    private fun startHold(midi: Int, timestampMs: Long) {
        holdMidi = midi
        holdStartMs = timestampMs
    }

    private fun accumulate(cents: Double, confidence: Double, centroidHz: Double?, timestampMs: Long) {
        n++
        val delta = cents - centsMean
        centsMean += delta / n
        centsM2 += delta * (cents - centsMean)
        confSum += confidence
        if (centroidHz != null) {
            centroidSum += centroidHz
            centroidCount++
        }
        lastVoicedMs = timestampMs
    }

    private fun finalize(): NoteAttempt? {
        val midi = holdMidi
        val holdMs = lastVoicedMs - holdStartMs
        val attempt = if (midi != null && n > 0 && holdMs >= minHoldMs) {
            NoteAttempt(
                midi = midi,
                startOffsetMs = holdStartMs - sessionStartMs,
                holdMs = holdMs,
                meanCents = centsMean,
                centsStdDev = if (n >= 2) sqrt(centsM2 / (n - 1)) else 0.0,
                confidence = confSum / n,
                meanCentroidHz = if (centroidCount > 0) centroidSum / centroidCount else null,
            )
        } else {
            null
        }
        resetHold()
        return attempt
    }

    private fun resetHold() {
        holdMidi = null
        holdStartMs = 0
        lastVoicedMs = 0
        n = 0
        centsMean = 0.0
        centsM2 = 0.0
        confSum = 0.0
        centroidSum = 0.0
        centroidCount = 0
    }

    private companion object {
        const val UNSET = Long.MIN_VALUE
    }
}
