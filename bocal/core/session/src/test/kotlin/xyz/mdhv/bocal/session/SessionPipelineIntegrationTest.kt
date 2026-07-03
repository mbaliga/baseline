package xyz.mdhv.bocal.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.mdhv.bocal.baseline.BaselineEngine
import xyz.mdhv.bocal.data.bundle.NoteAttempt
import xyz.mdhv.bocal.fixtures.Signals
import xyz.mdhv.bocal.model.NoteMath
import xyz.mdhv.bocal.model.TrendDirection
import xyz.mdhv.bocal.pitch.YinPitchDetector

/**
 * The whole MVP analysis vertical, end to end across module seams:
 * synthetic tone → YinPitchDetector → NoteMath → NoteHoldTracker →
 * SessionAnalyzer → SessionScore → BaselineEngine. If any seam's contract
 * drifts, this breaks.
 */
class SessionPipelineIntegrationTest {

    private val sampleRate = 48_000
    private val frame = 4096
    private val detector = YinPitchDetector()

    /** Run a held [freq] tone through detect→resolve→track and return the attempt. */
    private fun holdToneAttempt(freq: Double, frames: Int = 40, stepMs: Long = 20): NoteAttempt? {
        val tracker = NoteHoldTracker(minHoldMs = 100)
        val samples = Signals.sine(freq, sampleRate, frame)
        for (i in 0 until frames) {
            val estimate = detector.analyze(samples, sampleRate)
            if (estimate.isVoiced) {
                val resolved = NoteMath.resolve(estimate.frequencyHz)
                tracker.onSample(
                    midi = resolved.midi,
                    cents = resolved.cents,
                    confidence = estimate.confidence,
                    isVoiced = true,
                    centroidHz = null,
                    timestampMs = i * stepMs,
                )
            }
        }
        return tracker.flush()
    }

    @Test
    fun `a held A4 flows through to an in-tune attempt`() {
        val attempt = holdToneAttempt(440.0)
        assertTrue("expected an attempt from a sustained tone", attempt != null)
        attempt!!
        assertEquals(69, attempt.midi) // A4
        assertTrue("A4 should read in tune, got ${attempt.meanCents} cents", kotlin.math.abs(attempt.meanCents) <= 5.0)
    }

    @Test
    fun `a sharp tone produces a positive-bias session that scores below a clean one`() {
        // ~20 cents sharp of A4.
        val sharpFreq = 440.0 * Math.pow(2.0, 20.0 / 1200.0)
        val sharpAttempt = holdToneAttempt(sharpFreq)!!
        val cleanAttempt = holdToneAttempt(440.0)!!

        val analyzer = SessionAnalyzer()
        val sharpMetrics = analyzer.analyze(listOf(sharpAttempt))
        val cleanMetrics = analyzer.analyze(listOf(cleanAttempt))

        assertTrue("sharp session should show positive bias", sharpMetrics.intonationBiasCents > 5.0)
        assertTrue(
            "clean session should score higher (${cleanMetrics.accuracyScore()} vs ${sharpMetrics.accuracyScore()})",
            cleanMetrics.accuracyScore() > sharpMetrics.accuracyScore(),
        )
    }

    @Test
    fun `improving session scores drive an IMPROVING trend`() {
        // Simulate sessions getting steadily more accurate (error shrinking),
        // scored through the analyzer, read by the trend engine.
        val analyzer = SessionAnalyzer()
        val scores = (0 until 12).map { i ->
            val meanCents = (24 - i * 2).toDouble() // 24 → 2 cents error
            val attempt = NoteAttempt(
                midi = 69, startOffsetMs = 0, holdMs = 3000, meanCents = meanCents,
                centsStdDev = 1.0, confidence = 0.9,
            )
            analyzer.analyze(listOf(attempt)).toSessionScore(index = i, epochDay = 20_000L + i)
        }
        val report = BaselineEngine().analyze(scores)
        assertEquals(TrendDirection.IMPROVING, report.direction)
        assertTrue(report.slopePerSession > 0)
    }
}
