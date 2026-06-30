package xyz.mdhv.formanalyser.archery

import xyz.mdhv.baseline.engine.model.TimeSeries

/**
 * Channel names the archery module expects in a bow-mounted IMU [TimeSeries]. Gyro in deg/s,
 * accelerometer in g (gravity units). The capture app is responsible for producing a stream
 * with these channels; the module reads them by name so axis order is not load-bearing.
 */
object ArcheryChannels {
    const val GYRO_X = "gyroX"
    const val GYRO_Y = "gyroY"
    const val GYRO_Z = "gyroZ"
    const val ACC_X = "accX"
    const val ACC_Y = "accY"
    const val ACC_Z = "accZ"

    val GYRO = listOf(GYRO_X, GYRO_Y, GYRO_Z)
    val ACC = listOf(ACC_X, ACC_Y, ACC_Z)
}

/** The phases of a single archery shot (handoff §3.2 / A1 Pro segmentation). */
enum class ShotPhase { SET_UP, HOLD, RELEASE }

/**
 * One segmented shot: the raw window plus the sample indices (into [window]) marking the
 * boundaries Set-Up | Hold | Release. This is the archery module's `TRaw` type — opaque to
 * the engine, produced by [ArcheryShotSegmenter], consumed by [ArcheryFeatureExtractor].
 *
 * Indices are half-open: HOLD is [holdStart, releaseStart), RELEASE is [releaseStart, releaseEnd).
 */
data class ArcheryShot(
    val window: TimeSeries,
    val setUpStart: Int,
    val holdStart: Int,
    val releaseStart: Int,
    val releaseEnd: Int,
) {
    val sampleRateHz: Double get() = window.sampleRateHz

    fun phaseSlice(phase: ShotPhase): TimeSeries = when (phase) {
        ShotPhase.SET_UP -> window.slice(setUpStart, holdStart)
        ShotPhase.HOLD -> window.slice(holdStart, releaseStart)
        ShotPhase.RELEASE -> window.slice(releaseStart, releaseEnd)
    }

    val holdDurationSeconds: Double
        get() = (releaseStart - holdStart).coerceAtLeast(0) / sampleRateHz
}
