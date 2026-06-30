package xyz.mdhv.formanalyser.archery

import xyz.mdhv.formanalyser.archery.signal.Butterworth
import xyz.mdhv.formanalyser.archery.signal.Fft
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class FftTest {
    @Test fun recoversDominantFrequencyOfPureSine() {
        val fs = 200.0
        val n = 256
        val freq = 10.0
        val sig = DoubleArray(n) { sin(2 * PI * freq * it / fs) }
        val dom = Fft.dominantFrequency(sig, fs)
        assertEquals(freq, dom, fs / n, "dominant freq should be ~$freq, was $dom")
    }

    @Test fun nextPow2IsCorrect() {
        assertEquals(1, Fft.nextPow2(1))
        assertEquals(8, Fft.nextPow2(5))
        assertEquals(1024, Fft.nextPow2(1000))
    }
}

class ButterworthTest {
    private fun rms(a: DoubleArray) = sqrt(a.sumOf { it * it } / a.size)

    @Test fun lowPassRemovesHighFrequencyKeepsLow() {
        val fs = 200.0
        val n = 1024
        val low = DoubleArray(n) { sin(2 * PI * 2.0 * it / fs) }   // 2 Hz, keep
        val high = DoubleArray(n) { sin(2 * PI * 40.0 * it / fs) } // 40 Hz, kill
        val mixed = DoubleArray(n) { low[it] + high[it] }

        // input has both components: RMS ≈ sqrt(0.5 + 0.5) = 1.0
        assertEquals(1.0, rms(mixed), 0.05)

        val filtered = Butterworth.lowPassZeroPhase(mixed, cutoffHz = 10.0, sampleRateHz = fs)
        // output should be ≈ the 2 Hz component alone: RMS ≈ 0.707
        assertTrue(rms(filtered) in 0.6..0.8, "expected ~0.707 RMS after LP, got ${rms(filtered)}")
    }

    @Test fun lowPassHasUnityDcGain() {
        val n = 200
        val dc = DoubleArray(n) { 5.0 }
        val out = Butterworth.lowPass(dc, cutoffHz = 5.0, sampleRateHz = 100.0)
        // after the transient settles, output should track the DC level
        assertEquals(5.0, out.last(), 1e-6)
    }
}
