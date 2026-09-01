package xyz.mdhv.bocal.pitch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.mdhv.bocal.fixtures.Signals
import xyz.mdhv.bocal.model.NoteMath
import xyz.mdhv.bocal.model.PitchEstimate

private const val SAMPLE_RATE = 48_000
private const val FRAME = 4096

/**
 * Tier-1 accuracy contract shared by every DSP detector: ±5 cents on clean
 * synthetic tones across the playable range (brief §6).
 */
abstract class PitchDetectorContract {

    abstract fun detector(): PitchDetector

    // Concert-pitch fundamentals across the sax family range: baritone low
    // Bb1-ish up through C7 (SwiftF0's ceiling, brief §2.3).
    private val testFrequencies = doubleArrayOf(
        55.0, 82.41, 110.0, 146.83, 196.0, 261.63, 349.23,
        440.0, 554.37, 698.46, 880.0, 1174.66, 1567.98, 2093.0,
    )

    @Test
    fun `clean sines within 5 cents across the range`() {
        val detector = detector()
        for (freq in testFrequencies) {
            val frame = Signals.sine(freq, SAMPLE_RATE, FRAME)
            val estimate = detector.analyze(frame, SAMPLE_RATE)
            assertTrue("expected voiced at $freq Hz", estimate.isVoiced)
            val cents = NoteMath.centsBetween(freq, estimate.frequencyHz)
            assertTrue(
                "at $freq Hz: got ${estimate.frequencyHz} Hz (${"%.2f".format(cents)} cents off)",
                kotlin.math.abs(cents) <= 5.0,
            )
        }
    }

    @Test
    fun `a440 detected with high confidence`() {
        val estimate = detector().analyze(Signals.sine(440.0, SAMPLE_RATE, FRAME), SAMPLE_RATE)
        assertTrue(estimate.isVoiced)
        assertTrue("confidence was ${estimate.confidence}", estimate.confidence > 0.8)
    }

    @Test
    fun `sawtooth 220 finds the fundamental not a harmonic`() {
        val frame = Signals.sawtooth(220.0, SAMPLE_RATE, FRAME)
        val estimate = detector().analyze(frame, SAMPLE_RATE)
        assertTrue(estimate.isVoiced)
        val cents = NoteMath.centsBetween(220.0, estimate.frequencyHz)
        assertTrue(
            "sawtooth: got ${estimate.frequencyHz} Hz (${"%.2f".format(cents)} cents off 220)",
            kotlin.math.abs(cents) <= 5.0,
        )
    }

    @Test
    fun `strong second harmonic does not cause octave error`() {
        // Fundamental 330 Hz with a 2nd harmonic at 80% amplitude — a shape a
        // bright sax reed easily produces.
        val frame = Signals.harmonicTone(
            330.0, SAMPLE_RATE, FRAME, amps = doubleArrayOf(1.0, 0.8, 0.4, 0.2),
        )
        val estimate = detector().analyze(frame, SAMPLE_RATE)
        assertTrue(estimate.isVoiced)
        val cents = NoteMath.centsBetween(330.0, estimate.frequencyHz)
        assertTrue(
            "got ${estimate.frequencyHz} Hz (${"%.2f".format(cents)} cents off 330)",
            kotlin.math.abs(cents) <= 5.0,
        )
    }

    @Test
    fun `silence is unvoiced`() {
        val estimate = detector().analyze(Signals.silence(FRAME), SAMPLE_RATE)
        assertFalse(estimate.isVoiced)
    }

    @Test
    fun `near-silence below rms threshold is unvoiced`() {
        val frame = Signals.sine(440.0, SAMPLE_RATE, FRAME, amplitude = 1e-5)
        assertFalse(detector().analyze(frame, SAMPLE_RATE).isVoiced)
    }

    @Test
    fun `white noise is not reported as a confident pitch`() {
        val estimate = detector().analyze(Signals.whiteNoise(FRAME), SAMPLE_RATE)
        assertFalse(
            "noise came back voiced at ${estimate.frequencyHz} Hz conf=${estimate.confidence}",
            estimate.isVoiced && estimate.confidence > 0.8,
        )
    }

    @Test
    fun `unvoiced constant is well-formed`() {
        assertFalse(PitchEstimate.UNVOICED.isVoiced)
    }
}

class YinPitchDetectorTest : PitchDetectorContract() {
    override fun detector(): PitchDetector = YinPitchDetector()
}

class MpmPitchDetectorTest : PitchDetectorContract() {
    override fun detector(): PitchDetector = MpmPitchDetector()
}
