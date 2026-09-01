package xyz.mdhv.bocal.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.mdhv.bocal.data.bundle.NoteAttempt

class SessionAnalyzerTest {

    private val analyzer = SessionAnalyzer(inTuneToleranceCents = 5.0)

    private fun attempt(
        midi: Int = 69,
        holdMs: Long = 1000,
        meanCents: Double = 0.0,
        confidence: Double = 0.9,
        centroid: Double? = null,
    ) = NoteAttempt(
        midi = midi, startOffsetMs = 0, holdMs = holdMs, meanCents = meanCents,
        centsStdDev = 0.0, confidence = confidence, meanCentroidHz = centroid,
    )

    @Test
    fun `empty session is the empty metrics`() {
        assertEquals(SessionMetrics.EMPTY, analyzer.analyze(emptyList()))
        assertEquals(100.0, SessionMetrics.EMPTY.accuracyScore(), 1e-9)
    }

    @Test
    fun `perfect session scores 100 and reads fully in tune`() {
        val metrics = analyzer.analyze(listOf(attempt(meanCents = 0.0), attempt(meanCents = 2.0)))
        assertEquals(100.0, metrics.percentInTune, 1e-9)
        assertEquals(1.0, metrics.meanAbsCents, 1e-9) // (0 + 2) / 2
        assertEquals(99.0, metrics.accuracyScore(), 1e-9)
    }

    @Test
    fun `percent in tune is hold-time weighted`() {
        // A long in-tune note and a short out-of-tune one: mostly in tune by time.
        val metrics = analyzer.analyze(
            listOf(
                attempt(holdMs = 9000, meanCents = 1.0),   // in tune, long
                attempt(holdMs = 1000, meanCents = 30.0),  // out, short
            ),
        )
        assertEquals(90.0, metrics.percentInTune, 1e-9)
        // Weighted mean abs cents = (9000*1 + 1000*30) / 10000 = 3.9
        assertEquals(3.9, metrics.meanAbsCents, 1e-9)
    }

    @Test
    fun `signed bias distinguishes sharp from flat`() {
        val sharp = analyzer.analyze(listOf(attempt(meanCents = 8.0), attempt(meanCents = 12.0)))
        assertTrue(sharp.intonationBiasCents > 0)
        val flat = analyzer.analyze(listOf(attempt(meanCents = -8.0), attempt(meanCents = -12.0)))
        assertTrue(flat.intonationBiasCents < 0)
    }

    @Test
    fun `centroid averaged only over attempts that have it`() {
        val metrics = analyzer.analyze(
            listOf(
                attempt(holdMs = 1000, centroid = 1000.0),
                attempt(holdMs = 1000, centroid = null),
                attempt(holdMs = 1000, centroid = 3000.0),
            ),
        )
        assertEquals(2000.0, metrics.meanCentroidHz!!, 1e-9)
    }

    @Test
    fun `no centroid data yields null brightness`() {
        assertNull(analyzer.analyze(listOf(attempt(), attempt())).meanCentroidHz)
    }

    @Test
    fun `score is clamped and monotonic in error`() {
        val good = analyzer.analyze(listOf(attempt(meanCents = 2.0))).accuracyScore()
        val bad = analyzer.analyze(listOf(attempt(meanCents = 40.0))).accuracyScore()
        val awful = analyzer.analyze(listOf(attempt(meanCents = 200.0))).accuracyScore()
        assertTrue(good > bad)
        assertTrue(bad > awful)
        assertEquals(0.0, awful, 1e-9) // clamped, never negative
    }

    @Test
    fun `zero-hold attempts fall back to per-attempt weighting`() {
        val metrics = analyzer.analyze(listOf(attempt(holdMs = 0, meanCents = 0.0), attempt(holdMs = 0, meanCents = 10.0)))
        assertEquals(2, metrics.attemptCount)
        assertEquals(5.0, metrics.meanAbsCents, 1e-9)
        assertEquals(50.0, metrics.percentInTune, 1e-9)
    }

    @Test
    fun `toSessionScore carries the accuracy score with identity fields`() {
        val score = analyzer.analyze(listOf(attempt(meanCents = 5.0))).toSessionScore(index = 3, epochDay = 20_100)
        assertEquals(3, score.index)
        assertEquals(20_100L, score.epochDay)
        assertEquals(95.0, score.value, 1e-9)
    }
}
