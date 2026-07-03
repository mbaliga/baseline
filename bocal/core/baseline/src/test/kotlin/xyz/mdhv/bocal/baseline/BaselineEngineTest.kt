package xyz.mdhv.bocal.baseline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.mdhv.bocal.model.SessionScore
import xyz.mdhv.bocal.model.TrendDirection

class BaselineEngineTest {

    private val engine = BaselineEngine()

    private fun series(vararg values: Double): List<SessionScore> =
        values.mapIndexed { i, v -> SessionScore(index = i, epochDay = 20_000L + i, value = v) }

    /** Deterministic small "noise" that averages out and is not a trend. */
    private fun wiggle(i: Int): Double = if (i % 2 == 0) 0.3 else -0.3

    @Test
    fun `steadily improving series is IMPROVING with positive slope`() {
        val report = engine.analyze(series(*DoubleArray(20) { it.toDouble() }))
        assertEquals(TrendDirection.IMPROVING, report.direction)
        assertEquals(1.0, report.slopePerSession, 1e-9)
        assertTrue(report.recentMean > report.baselineMean)
        assertEquals(19, report.personalBestIndex)
    }

    @Test
    fun `declining series is DECLINING`() {
        val report = engine.analyze(series(*DoubleArray(20) { 20.0 - it }))
        assertEquals(TrendDirection.DECLINING, report.direction)
        assertEquals(0, report.personalBestIndex)
    }

    @Test
    fun `flat noisy series is STABLE, not praised as improving`() {
        val report = engine.analyze(series(*DoubleArray(20) { 10.0 + wiggle(it) }))
        assertEquals(TrendDirection.STABLE, report.direction)
        assertFalse(report.plateau)
    }

    @Test
    fun `rise then flatten reads as a plateau`() {
        val values = DoubleArray(20) { i -> if (i < 10) i.toDouble() else 9.0 + wiggle(i) / 3.0 }
        val report = engine.analyze(series(*values))
        assertEquals(TrendDirection.STABLE, report.direction)
        assertTrue("expected plateau", report.plateau)
    }

    @Test
    fun `flat series with no prior improvement is not a plateau`() {
        val report = engine.analyze(series(*DoubleArray(20) { 5.0 + wiggle(it) }))
        assertFalse(report.plateau)
    }

    @Test
    fun `abrupt step is reported as a change point near the step`() {
        val values = DoubleArray(20) { i -> (if (i < 10) 2.0 else 12.0) + wiggle(i) }
        val report = engine.analyze(series(*values))
        assertTrue(
            "change points ${report.changePoints} should contain 10",
            report.changePoints.any { it in 9..11 },
        )
    }

    @Test
    fun `steady ramp has no change points`() {
        val report = engine.analyze(series(*DoubleArray(20) { it * 0.5 }))
        assertTrue("ramp flagged ${report.changePoints}", report.changePoints.isEmpty())
    }

    @Test
    fun `empty series yields a safe report`() {
        val report = engine.analyze(emptyList())
        assertEquals(TrendDirection.STABLE, report.direction)
        assertEquals(-1, report.personalBestIndex)
        assertTrue(report.changePoints.isEmpty())
    }

    @Test
    fun `single session yields stable with itself as best`() {
        val report = engine.analyze(series(7.0))
        assertEquals(TrendDirection.STABLE, report.direction)
        assertEquals(0, report.personalBestIndex)
        assertEquals(7.0, report.baselineMean, 1e-9)
        assertEquals(7.0, report.recentMean, 1e-9)
    }

    @Test
    fun `baseline window excludes the recent window when history allows`() {
        // 10 sessions at 2.0, then 8 recent sessions at 5.0: baseline should
        // reflect the old level, recent the new.
        val values = DoubleArray(18) { i -> if (i < 10) 2.0 else 5.0 }
        val report = engine.analyze(series(*values))
        assertEquals(2.0, report.baselineMean, 1e-9)
        assertEquals(5.0, report.recentMean, 1e-9)
    }

    @Test
    fun `deterministic - same input twice gives identical reports`() {
        val input = series(*DoubleArray(15) { it + wiggle(it) })
        assertEquals(engine.analyze(input), engine.analyze(input))
    }
}
