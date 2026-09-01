package xyz.mdhv.bocal.fixtures

import kotlin.math.PI
import kotlin.math.sin

/**
 * Deterministic synthetic test signals. Everything is generated in memory so
 * Tier-1 tests never depend on files or platform audio.
 */
object Signals {

    fun sine(
        frequencyHz: Double,
        sampleRate: Int,
        length: Int,
        amplitude: Double = 0.5,
        phase: Double = 0.0,
    ): FloatArray = FloatArray(length) { i ->
        (amplitude * sin(2.0 * PI * frequencyHz * i / sampleRate + phase)).toFloat()
    }

    /** Ideal sawtooth (not band-limited — fine for detector tests, aliased HF is weak). */
    fun sawtooth(
        frequencyHz: Double,
        sampleRate: Int,
        length: Int,
        amplitude: Double = 0.5,
    ): FloatArray = FloatArray(length) { i ->
        val t = i * frequencyHz / sampleRate
        (amplitude * (2.0 * (t - kotlin.math.floor(t + 0.5)))).toFloat()
    }

    /**
     * Sum of harmonics k=1..amps.size at relative amplitudes [amps], normalized
     * so the peak stays inside ±[amplitude].
     */
    fun harmonicTone(
        f0Hz: Double,
        sampleRate: Int,
        length: Int,
        amps: DoubleArray,
        amplitude: Double = 0.5,
    ): FloatArray {
        val raw = DoubleArray(length)
        for (k in amps.indices) {
            val f = f0Hz * (k + 1)
            if (f >= sampleRate / 2.0) continue
            for (i in 0 until length) {
                raw[i] += amps[k] * sin(2.0 * PI * f * i / sampleRate)
            }
        }
        val peak = raw.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1e-12)
        val scale = amplitude / peak
        return FloatArray(length) { (raw[it] * scale).toFloat() }
    }

    fun twoTone(
        f1Hz: Double,
        f2Hz: Double,
        sampleRate: Int,
        length: Int,
        a1: Double = 0.5,
        a2: Double = 0.25,
    ): FloatArray = FloatArray(length) { i ->
        (a1 * sin(2.0 * PI * f1Hz * i / sampleRate) +
            a2 * sin(2.0 * PI * f2Hz * i / sampleRate)).toFloat()
    }

    fun silence(length: Int): FloatArray = FloatArray(length)

    /** Deterministic white noise via a linear congruential generator. */
    fun whiteNoise(length: Int, amplitude: Double = 0.5, seed: Long = 0x5EED): FloatArray {
        var state = seed
        return FloatArray(length) {
            state = state * 6364136223846793005L + 1442695040888963407L
            val unit = ((state ushr 11).toDouble() / (1L shl 53).toDouble()) * 2.0 - 1.0
            (unit * amplitude).toFloat()
        }
    }
}
