package xyz.mdhv.formanalyser.archery.signal

import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 2nd-order Butterworth low-pass (handoff §6) via the bilinear transform. Used to separate
 * slow sway/drift from high-frequency tremor before computing steadiness metrics.
 */
object Butterworth {

    private class Biquad(
        val b0: Double, val b1: Double, val b2: Double,
        val a1: Double, val a2: Double,
    )

    private fun design(cutoffHz: Double, sampleRateHz: Double): Biquad {
        require(cutoffHz > 0 && cutoffHz < sampleRateHz / 2) {
            "cutoff must be in (0, Nyquist); got $cutoffHz @ fs=$sampleRateHz"
        }
        val c = 1.0 / tan(PI * cutoffHz / sampleRateHz)
        val sqrt2 = sqrt(2.0)
        val a0 = 1.0 + sqrt2 * c + c * c
        return Biquad(
            b0 = 1.0 / a0,
            b1 = 2.0 / a0,
            b2 = 1.0 / a0,
            a1 = (2.0 - 2.0 * c * c) / a0,
            a2 = (1.0 - sqrt2 * c + c * c) / a0,
        )
    }

    private fun applyOnce(data: DoubleArray, bq: Biquad): DoubleArray {
        val out = DoubleArray(data.size)
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        for (i in data.indices) {
            val x0 = data[i]
            val y0 = bq.b0 * x0 + bq.b1 * x1 + bq.b2 * x2 - bq.a1 * y1 - bq.a2 * y2
            out[i] = y0
            x2 = x1; x1 = x0
            y2 = y1; y1 = y0
        }
        return out
    }

    /** Causal 2nd-order low-pass (introduces phase lag). */
    fun lowPass(data: DoubleArray, cutoffHz: Double, sampleRateHz: Double): DoubleArray {
        if (data.size < 3) return data.copyOf()
        return applyOnce(data, design(cutoffHz, sampleRateHz))
    }

    /**
     * Zero-phase low-pass (forward+backward, "filtfilt"). Preferred for offline drift
     * estimation where phase lag would bias where the pin "was". Effective order doubles.
     */
    fun lowPassZeroPhase(data: DoubleArray, cutoffHz: Double, sampleRateHz: Double): DoubleArray {
        if (data.size < 3) return data.copyOf()
        val bq = design(cutoffHz, sampleRateHz)
        val forward = applyOnce(data, bq)
        val reversed = forward.reversedArray()
        val back = applyOnce(reversed, bq)
        return back.reversedArray()
    }
}
