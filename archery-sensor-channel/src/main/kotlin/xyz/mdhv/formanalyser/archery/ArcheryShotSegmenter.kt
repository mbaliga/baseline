package xyz.mdhv.formanalyser.archery

import xyz.mdhv.baseline.engine.model.TimeSeries
import xyz.mdhv.baseline.engine.sport.RepSegmenter
import xyz.mdhv.formanalyser.archery.signal.Butterworth
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Tunable thresholds for shot segmentation, in the IMU's units (gyro deg/s). Defaults are
 * starting points to validate side-by-side against the Steady Aim A1 Pro (handoff §4b), not
 * gospel — expose these in the app so they can be tuned per archer/bow.
 */
data class SegmenterConfig(
    /** Below this smoothed angular speed, the bow is "holding". */
    val holdThresholdDegPerSec: Double = 18.0,
    /** A spike above this marks the release transient. */
    val releaseThresholdDegPerSec: Double = 80.0,
    /** A hold must last at least this long to count (rejects momentary stillness). */
    val minHoldSeconds: Double = 0.6,
    /** How far before the hold to look for the set-up (draw) motion. */
    val maxSetUpSeconds: Double = 2.5,
    /** Length of the release window captured after the spike. */
    val releaseWindowSeconds: Double = 0.25,
)

/**
 * Segments a continuous bow-IMU stream into discrete shots by finding quasi-static HOLD
 * plateaus and the RELEASE spike that ends each (handoff §3.3: "the hold is quasi-static").
 * Implements the engine's [RepSegmenter] seam.
 */
class ArcheryShotSegmenter(
    private val config: SegmenterConfig = SegmenterConfig(),
) : RepSegmenter<ArcheryShot> {

    override fun segment(stream: TimeSeries): List<ArcheryShot> {
        val n = stream.size
        if (n < 8) return emptyList()
        val fs = stream.sampleRateHz

        val mag = angularSpeedMagnitude(stream)
        // Smooth to an envelope so a single noisy sample doesn't break a hold.
        val cutoff = min(8.0, fs / 2.0 * 0.8)
        val env = if (cutoff > 0.5) Butterworth.lowPassZeroPhase(mag, cutoff, fs) else mag

        val minHoldSamples = (config.minHoldSeconds * fs).toInt().coerceAtLeast(1)
        val maxSetUpSamples = (config.maxSetUpSeconds * fs).toInt().coerceAtLeast(1)
        val releaseWindowSamples = (config.releaseWindowSeconds * fs).toInt().coerceAtLeast(1)

        val shots = ArrayList<ArcheryShot>()
        var i = 0
        while (i < n) {
            // Find the next run below the hold threshold.
            if (env[i] >= config.holdThresholdDegPerSec) { i++; continue }
            var holdStart = i
            var j = i
            while (j < n && env[j] < config.holdThresholdDegPerSec) j++
            val holdEnd = j // exclusive: first sample at/above threshold

            if (holdEnd - holdStart < minHoldSamples) { i = holdEnd + 1; continue }

            // Release: first spike above releaseThreshold at/after holdEnd, within a short reach.
            val searchLimit = min(n, holdEnd + releaseWindowSamples * 3)
            var releaseStart = holdEnd
            var found = false
            var k = holdEnd
            while (k < searchLimit) {
                if (env[k] >= config.releaseThresholdDegPerSec) { releaseStart = k; found = true; break }
                k++
            }
            if (!found) {
                // A hold with no clean release spike (e.g. let-down). Skip it.
                i = holdEnd + 1
                continue
            }
            val releaseEnd = min(n, releaseStart + releaseWindowSamples)

            // Set-up: walk back from holdStart while there is motion, bounded.
            var setUpStart = holdStart
            val backLimit = (holdStart - maxSetUpSamples).coerceAtLeast(0)
            var b = holdStart
            while (b > backLimit && env[b - 1] >= config.holdThresholdDegPerSec) b--
            setUpStart = b

            shots.add(ArcheryShot(stream, setUpStart, holdStart, releaseStart, releaseEnd))
            i = releaseEnd + 1
        }
        return shots
    }

    private fun angularSpeedMagnitude(stream: TimeSeries): DoubleArray {
        val gx = stream.channel(ArcheryChannels.GYRO_X)
        val gy = stream.channel(ArcheryChannels.GYRO_Y)
        val gz = stream.channel(ArcheryChannels.GYRO_Z)
        return DoubleArray(stream.size) { sqrt(gx[it] * gx[it] + gy[it] * gy[it] + gz[it] * gz[it]) }
    }
}
