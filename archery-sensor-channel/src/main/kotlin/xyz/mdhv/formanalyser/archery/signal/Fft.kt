package xyz.mdhv.formanalyser.archery.signal

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.PI

/**
 * Minimal radix-2 iterative Cooley–Tukey FFT, enough for archery tremor/release spectral
 * analysis (handoff §6: "tremor/steadiness = FFT on accel/gyro"). No external DSP dep so the
 * same code can later cross-compile for on-device use.
 */
object Fft {

    fun nextPow2(n: Int): Int {
        var p = 1
        while (p < n) p = p shl 1
        return p
    }

    /** In-place complex FFT. [re] and [im] must be the same length and a power of two. */
    fun transform(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        require(n == im.size) { "re/im length mismatch" }
        require(n > 0 && (n and (n - 1)) == 0) { "length must be a power of two, was $n" }
        if (n == 1) return

        // Bit-reversal permutation.
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wReal = cos(ang)
            val wImag = sin(ang)
            var i = 0
            while (i < n) {
                var curReal = 1.0
                var curImag = 0.0
                for (k in 0 until len / 2) {
                    val aIdx = i + k
                    val bIdx = i + k + len / 2
                    val bReal = re[bIdx] * curReal - im[bIdx] * curImag
                    val bImag = re[bIdx] * curImag + im[bIdx] * curReal
                    re[bIdx] = re[aIdx] - bReal
                    im[bIdx] = im[aIdx] - bImag
                    re[aIdx] += bReal
                    im[aIdx] += bImag
                    val nextReal = curReal * wReal - curImag * wImag
                    curImag = curReal * wImag + curImag * wReal
                    curReal = nextReal
                }
                i += len
            }
            len = len shl 1
        }
    }

    /**
     * One-sided magnitude spectrum of a real signal (DC..Nyquist). The input is mean-removed
     * and zero-padded to the next power of two. Returns bins[0..N/2].
     */
    fun magnitudeSpectrum(signal: DoubleArray): DoubleArray {
        if (signal.isEmpty()) return DoubleArray(0)
        val mean = signal.average()
        val n = nextPow2(signal.size)
        val re = DoubleArray(n) { if (it < signal.size) signal[it] - mean else 0.0 }
        val im = DoubleArray(n)
        transform(re, im)
        return DoubleArray(n / 2 + 1) { hypot(re[it], im[it]) }
    }

    /**
     * Dominant (peak-power) frequency in Hz, excluding the DC bin. Returns 0.0 for a signal
     * with no AC content. Used for tremor frequency and release-vibration analysis.
     */
    fun dominantFrequency(signal: DoubleArray, sampleRateHz: Double): Double {
        val mag = magnitudeSpectrum(signal)
        if (mag.size <= 1) return 0.0
        var bestBin = 1
        var bestMag = -1.0
        for (k in 1 until mag.size) {
            if (mag[k] > bestMag) { bestMag = mag[k]; bestBin = k }
        }
        val n = (mag.size - 1) * 2 // FFT length used
        return bestBin * sampleRateHz / n
    }
}
