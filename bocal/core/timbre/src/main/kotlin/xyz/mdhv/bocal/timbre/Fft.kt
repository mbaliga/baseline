package xyz.mdhv.bocal.timbre

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Clean-room iterative radix-2 Cooley–Tukey FFT. In-place, power-of-two only.
 * Small and dependency-free on purpose: this module must stay pure JVM.
 */
object Fft {

    /** In-place complex FFT over parallel [re]/[im] arrays. */
    fun transform(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        require(n == im.size) { "re/im length mismatch" }
        require(n > 0 && n and (n - 1) == 0) { "FFT size must be a power of two: $n" }

        // Bit-reversal permutation.
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                re[i] = re[j].also { re[j] = re[i] }
                im[i] = im[j].also { im[j] = im[i] }
            }
            var m = n shr 1
            while (m in 1..j) {
                j -= m
                m = m shr 1
            }
            j += m
        }

        // Butterflies.
        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wRe = cos(angle)
            val wIm = sin(angle)
            var i = 0
            while (i < n) {
                var curRe = 1.0
                var curIm = 0.0
                for (k in 0 until len / 2) {
                    val a = i + k
                    val b = i + k + len / 2
                    val tRe = re[b] * curRe - im[b] * curIm
                    val tIm = re[b] * curIm + im[b] * curRe
                    re[b] = re[a] - tRe
                    im[b] = im[a] - tIm
                    re[a] += tRe
                    im[a] += tIm
                    val nextRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                }
                i += len
            }
            len = len shl 1
        }
    }

    /**
     * Magnitude spectrum of a real signal after a Hann window: bins 0..n/2
     * inclusive. Input length must be a power of two.
     */
    fun magnitudeSpectrum(frame: FloatArray): DoubleArray {
        val n = frame.size
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        for (i in 0 until n) {
            val hann = 0.5 * (1.0 - cos(2.0 * PI * i / (n - 1)))
            re[i] = frame[i] * hann
        }
        transform(re, im)
        return DoubleArray(n / 2 + 1) { k -> sqrt(re[k] * re[k] + im[k] * im[k]) }
    }
}
