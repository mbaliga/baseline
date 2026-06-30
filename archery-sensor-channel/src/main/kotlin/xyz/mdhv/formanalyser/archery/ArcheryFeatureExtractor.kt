package xyz.mdhv.formanalyser.archery

import xyz.mdhv.baseline.engine.model.FeatureVector
import xyz.mdhv.baseline.engine.model.TimeSeries
import xyz.mdhv.baseline.engine.sport.FeatureExtractor
import xyz.mdhv.formanalyser.archery.signal.Fft
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Extracts the per-shot signals the archery theory ties to score (handoff §3.2) from a
 * segmented [ArcheryShot]'s bow-IMU window.
 *
 * Axis convention (documented because it's load-bearing and must be validated against real
 * hardware per §4): body frame X = right (lateral), Y = forward (toward target, along the
 * arrow), Z = up. Therefore pitch (vertical aim) = gyroX, yaw (horizontal aim) = gyroZ,
 * roll (cant) = gyroY, and a level upright bow reads accelerometer ≈ (0, 0, +1 g).
 */
class ArcheryFeatureExtractor : FeatureExtractor<ArcheryShot> {

    override val featureNames = listOf(
        STEADINESS, PIN_DRIFT_DEG, CANT_DEG, HOLD_DURATION_S,
        TREMOR_HZ, RELEASE_PEAK, RELEASE_DOMINANT_HZ,
    )

    override fun extract(rep: ArcheryShot): FeatureVector {
        val fs = rep.sampleRateHz
        val hold = rep.phaseSlice(ShotPhase.HOLD)
        val release = rep.phaseSlice(ShotPhase.RELEASE)

        val holdMag = angularSpeedMagnitude(hold)
        val rms = rms(holdMag)

        return linkedMapOf(
            // Steadiness 0–100: lower hold angular speed == steadier. Mirrors the A1 Pro's
            // 0–100 score so the two can be compared side-by-side.
            STEADINESS to 100.0 * exp(-rms / STEADINESS_SCALE_DEG_PER_S),
            PIN_DRIFT_DEG to pinDrift(hold, fs),
            CANT_DEG to meanCant(hold),
            HOLD_DURATION_S to rep.holdDurationSeconds,
            TREMOR_HZ to dominantTremorHz(hold, fs),
            RELEASE_PEAK to (angularSpeedMagnitude(release).maxOrNull() ?: 0.0),
            RELEASE_DOMINANT_HZ to releaseDominantHz(release, fs),
        )
    }

    /** Integrated angular displacement range about the two aiming axes (pitch + yaw). */
    private fun pinDrift(hold: TimeSeries, fs: Double): Double {
        if (hold.size < 2) return 0.0
        val dt = 1.0 / fs
        val pitch = integrateRange(hold.channel(ArcheryChannels.GYRO_X), dt)
        val yaw = integrateRange(hold.channel(ArcheryChannels.GYRO_Z), dt)
        return hypot(pitch, yaw)
    }

    private fun integrateRange(rate: DoubleArray, dt: Double): Double {
        var angle = 0.0
        var minA = 0.0
        var maxA = 0.0
        for (r in rate) {
            angle += r * dt
            if (angle < minA) minA = angle
            if (angle > maxA) maxA = angle
        }
        return maxA - minA
    }

    /** Mean cant (bow roll) over the hold, from the gravity vector's lateral component. */
    private fun meanCant(hold: TimeSeries): Double {
        if (hold.size == 0) return 0.0
        val ax = hold.channel(ArcheryChannels.ACC_X)
        val az = hold.channel(ArcheryChannels.ACC_Z)
        var sum = 0.0
        for (i in ax.indices) sum += Math.toDegrees(atan2(ax[i], az[i]))
        return sum / ax.size
    }

    /**
     * Dominant tremor frequency over the hold, from the *signed* gyro axes (summed power
     * spectra). Using the angular-speed magnitude here would rectify the signal and double
     * the apparent frequency, so we spectrum each axis and combine.
     */
    private fun dominantTremorHz(hold: TimeSeries, fs: Double): Double {
        if (hold.size < 5) return 0.0
        val mx = Fft.magnitudeSpectrum(hold.channel(ArcheryChannels.GYRO_X))
        val my = Fft.magnitudeSpectrum(hold.channel(ArcheryChannels.GYRO_Y))
        val mz = Fft.magnitudeSpectrum(hold.channel(ArcheryChannels.GYRO_Z))
        if (mx.size <= 1) return 0.0
        var bestBin = 1
        var bestPower = -1.0
        for (k in 1 until mx.size) {
            val p = mx[k] * mx[k] + my[k] * my[k] + mz[k] * mz[k]
            if (p > bestPower) { bestPower = p; bestBin = k }
        }
        val fftLen = (mx.size - 1) * 2
        return bestBin * fs / fftLen
    }

    private fun releaseDominantHz(release: TimeSeries, fs: Double): Double {
        if (release.size < 4) return 0.0
        return Fft.dominantFrequency(angularSpeedMagnitude(release), fs)
    }

    private fun angularSpeedMagnitude(ts: TimeSeries): DoubleArray {
        if (ts.size == 0) return DoubleArray(0)
        val gx = ts.channel(ArcheryChannels.GYRO_X)
        val gy = ts.channel(ArcheryChannels.GYRO_Y)
        val gz = ts.channel(ArcheryChannels.GYRO_Z)
        return DoubleArray(ts.size) { sqrt(gx[it] * gx[it] + gy[it] * gy[it] + gz[it] * gz[it]) }
    }

    private fun rms(a: DoubleArray): Double {
        if (a.isEmpty()) return 0.0
        var s = 0.0
        for (v in a) s += v * v
        return sqrt(s / a.size)
    }

    companion object {
        const val STEADINESS = "steadiness"
        const val PIN_DRIFT_DEG = "pinDriftDeg"
        const val CANT_DEG = "cantDeg"
        const val HOLD_DURATION_S = "holdDurationS"
        const val TREMOR_HZ = "tremorHz"
        const val RELEASE_PEAK = "releasePeakDegPerSec"
        const val RELEASE_DOMINANT_HZ = "releaseDominantHz"

        /** Angular-speed RMS (deg/s) that maps to ~37/100 steadiness. */
        const val STEADINESS_SCALE_DEG_PER_S = 12.0
    }
}
