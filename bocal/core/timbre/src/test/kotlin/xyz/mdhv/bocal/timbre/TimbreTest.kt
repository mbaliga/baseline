package xyz.mdhv.bocal.timbre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.mdhv.bocal.fixtures.Signals

private const val SR = 48_000
private const val FRAME = 4096

class FftTest {

    @Test
    fun `sine concentrates energy at its bin`() {
        // Pick a frequency exactly on a bin: bin 64 → 64 * 48000/4096 = 750 Hz.
        val freq = 64.0 * SR / FRAME
        val spectrum = Fft.magnitudeSpectrum(Signals.sine(freq, SR, FRAME, amplitude = 0.9))
        val peakBin = spectrum.indices.maxBy { spectrum[it] }
        assertEquals(64, peakBin)
        // Energy at the peak dwarfs a far-away bin.
        assertTrue(spectrum[64] > 100 * spectrum[200])
    }

    @Test
    fun `rejects non power of two`() {
        try {
            Fft.transform(DoubleArray(1000), DoubleArray(1000))
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
        }
    }
}

class TimbreAnalyzerTest {

    private val analyzer = TimbreAnalyzer()

    @Test
    fun `bright tone has higher centroid than dull tone at same f0`() {
        val dull = Signals.sine(220.0, SR, FRAME)
        val bright = Signals.sawtooth(220.0, SR, FRAME)
        val dullFrame = analyzer.analyze(dull, SR, 220.0)
        val brightFrame = analyzer.analyze(bright, SR, 220.0)
        assertTrue(
            "expected saw centroid ${brightFrame.spectralCentroidHz} > sine ${dullFrame.spectralCentroidHz}",
            brightFrame.spectralCentroidHz > dullFrame.spectralCentroidHz * 1.5,
        )
    }

    @Test
    fun `tristimulus sums to one for a harmonic tone`() {
        val tone = Signals.harmonicTone(261.63, SR, FRAME, doubleArrayOf(1.0, 0.6, 0.4, 0.3, 0.2, 0.1))
        val t = analyzer.analyze(tone, SR, 261.63).tristimulus
        assertEquals(1.0, t.t1 + t.t2 + t.t3, 1e-9)
        // Fundamental-dominant signal: T1 is the largest component.
        assertTrue(t.t1 > t.t2 && t.t1 > t.t3)
    }

    @Test
    fun `pure sine is fundamental-dominated`() {
        val t = analyzer.analyze(Signals.sine(440.0, SR, FRAME), SR, 440.0).tristimulus
        assertTrue("t1 was ${t.t1}", t.t1 > 0.9)
    }

    @Test
    fun `odd-only spectrum yields high odd-even ratio, even-heavy yields low`() {
        // Odd harmonics only (square-wave-like): 1, 3, 5.
        val oddTone = Signals.harmonicTone(
            220.0, SR, FRAME, doubleArrayOf(1.0, 0.0, 0.33, 0.0, 0.2),
        )
        // Even-heavy: strong 2nd and 4th.
        val evenTone = Signals.harmonicTone(
            220.0, SR, FRAME, doubleArrayOf(1.0, 0.8, 0.05, 0.5),
        )
        val oddRatio = analyzer.analyze(oddTone, SR, 220.0).oddEvenRatio
        val evenRatio = analyzer.analyze(evenTone, SR, 220.0).oddEvenRatio
        assertTrue("odd $oddRatio should exceed even $evenRatio", oddRatio > 1.0 && evenRatio < 1.0)
    }

    @Test
    fun `harmonic frequencies land near integer multiples`() {
        val tone = Signals.harmonicTone(200.0, SR, FRAME, doubleArrayOf(1.0, 0.5, 0.3, 0.2))
        val harmonics = analyzer.analyze(tone, SR, 200.0).harmonics
        for (h in harmonics.take(4)) {
            val expected = 200.0 * h.index
            assertTrue(
                "harmonic ${h.index} at ${h.frequencyHz}, expected ~$expected",
                kotlin.math.abs(h.frequencyHz - expected) < 30.0,
            )
        }
    }
}

class ToneStabilityTrackerTest {

    @Test
    fun `steady tone is steadier than wavering tone`() {
        val steady = ToneStabilityTracker()
        val wavering = ToneStabilityTracker()
        for (i in 0 until 10) {
            steady.add(Signals.sine(440.0, SR, FRAME), SR)
            // Alternate between dull and bright frames — unstable timbre.
            val frame = if (i % 2 == 0) {
                Signals.sine(440.0, SR, FRAME)
            } else {
                Signals.sawtooth(440.0, SR, FRAME)
            }
            wavering.add(frame, SR)
        }
        assertTrue(steady.centroidStdDevHz < wavering.centroidStdDevHz)
        assertTrue(steady.meanFlux < wavering.meanFlux)
    }

    @Test
    fun `reset clears state`() {
        val tracker = ToneStabilityTracker()
        tracker.add(Signals.sine(440.0, SR, FRAME), SR)
        tracker.reset()
        assertEquals(0, tracker.frameCount)
        assertEquals(0.0, tracker.meanCentroidHz, 0.0)
    }
}
