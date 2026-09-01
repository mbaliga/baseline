package xyz.mdhv.bocal.session

import kotlin.math.sqrt
import xyz.mdhv.bocal.data.bundle.NoteAttempt
import xyz.mdhv.bocal.model.NoteMath

/** A note's intonation tendency: does the player sit sharp or flat on it, how consistently. */
data class NoteTendency(
    /** Pitch class 0..11 (C..B) this tendency is for. */
    val pitchClass: Int,
    /** Hold-weighted signed mean cents: positive = sharp, negative = flat. */
    val meanCents: Double,
    /** Hold-weighted spread of the per-attempt bias, in cents. */
    val stdDevCents: Double,
    val attemptCount: Int,
    val totalHoldMs: Long,
) {
    val noteName: String get() = NoteMath.noteName(pitchClass)
}

/**
 * Builds per-pitch-class intonation tendency ("you tend to play F# sharp") from
 * a set of attempts — MVP §4.3's "per-note sharp/flat bias" map. Aggregating
 * by pitch class rather than absolute note pools octaves together, which is
 * what a wind player wants: the fingering, not the register, drives the bias.
 */
class IntonationTendencyMap {

    fun fromAttempts(attempts: List<NoteAttempt>): Map<Int, NoteTendency> {
        // Accumulate both hold-weighted and unweighted sums per pitch class, so
        // the degenerate "all attempts on this class had zero hold" case falls
        // back to a count average *for that class alone*. Deciding it globally
        // would let one held note on some other note erase this class's real
        // bias (its weights would all be zero → a false 0.0 centering).
        data class Acc(
            var sumW: Double = 0.0,
            var sumWX: Double = 0.0,
            var sumWX2: Double = 0.0,
            var sumX: Double = 0.0,
            var sumX2: Double = 0.0,
            var count: Int = 0,
            var holdMs: Long = 0,
        )

        val byClass = HashMap<Int, Acc>()
        for (a in attempts) {
            val pc = Math.floorMod(a.midi, 12)
            val w = a.holdMs.toDouble()
            val mc = a.meanCents
            val acc = byClass.getOrPut(pc) { Acc() }
            acc.sumW += w
            acc.sumWX += w * mc
            acc.sumWX2 += w * mc * mc
            acc.sumX += mc
            acc.sumX2 += mc * mc
            acc.count++
            acc.holdMs += a.holdMs
        }

        return byClass.mapValues { (pc, acc) ->
            val useWeighted = acc.sumW > 0
            val mean = if (useWeighted) acc.sumWX / acc.sumW else acc.sumX / acc.count
            val meanSq = if (useWeighted) acc.sumWX2 / acc.sumW else acc.sumX2 / acc.count
            val variance = (meanSq - mean * mean).coerceAtLeast(0.0)
            NoteTendency(
                pitchClass = pc,
                meanCents = mean,
                stdDevCents = sqrt(variance),
                attemptCount = acc.count,
                totalHoldMs = acc.holdMs,
            )
        }
    }

    /** The note the player runs sharpest on (most positive bias), or null. */
    fun sharpest(attempts: List<NoteAttempt>): NoteTendency? =
        fromAttempts(attempts).values.maxByOrNull { it.meanCents }?.takeIf { it.meanCents > 0 }

    /** The note the player runs flattest on (most negative bias), or null. */
    fun flattest(attempts: List<NoteAttempt>): NoteTendency? =
        fromAttempts(attempts).values.minByOrNull { it.meanCents }?.takeIf { it.meanCents < 0 }
}
