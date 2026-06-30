package xyz.mdhv.baseline.engine

import xyz.mdhv.baseline.engine.baseline.BaselineBuilder
import xyz.mdhv.baseline.engine.deviation.DeviationScorer
import xyz.mdhv.baseline.engine.fatigue.FatigueTracker
import xyz.mdhv.baseline.engine.model.Rep
import xyz.mdhv.baseline.engine.sport.SignalScoreCorrelation
import xyz.mdhv.baseline.engine.stats.Welford
import xyz.mdhv.baseline.engine.stats.linearFit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatsTest {
    @Test fun welfordMatchesNaiveMeanAndVariance() {
        val data = listOf(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0)
        val w = Welford().apply { data.forEach { add(it) } }
        assertEquals(5.0, w.mean, 1e-9)
        // population variance of this classic dataset is 4.0
        assertEquals(4.0, w.variance, 1e-9)
        assertEquals(2.0, w.stdDev, 1e-9)
    }

    @Test fun welfordIgnoresNaN() {
        val w = Welford().apply { add(1.0); add(Double.NaN); add(3.0) }
        assertEquals(2L, w.count)
        assertEquals(2.0, w.mean, 1e-9)
    }

    @Test fun linearFitRecoversKnownLine() {
        // y = -2x + 5 exactly
        val xs = (0..9).map { it.toDouble() }
        val ys = xs.map { -2.0 * it + 5.0 }
        val fit = assertNotNull(linearFit(xs, ys))
        assertEquals(-2.0, fit.slope, 1e-9)
        assertEquals(5.0, fit.intercept, 1e-9)
        assertEquals(-1.0, fit.r, 1e-9)
    }

    @Test fun linearFitNullOnNoVariance() {
        assertNull(linearFit(listOf(1.0, 1.0, 1.0), listOf(1.0, 2.0, 3.0)))
    }
}

class BaselineAndDeviationTest {
    @Test fun zeroDeviationOnTheMeanGivesFullStability() {
        val builder = BaselineBuilder()
        repeat(10) { builder.add(mapOf("steadiness" to 80.0 + (it % 3 - 1), "cant" to 0.5)) }
        val baseline = builder.build()
        assertTrue(baseline.isReady())

        val scorer = DeviationScorer(baseline)
        // Score the baseline mean exactly -> ~100 stability, ~0 rms.
        val mean = baseline.metrics["steadiness"]!!.mean
        val result = scorer.score(mapOf("steadiness" to mean, "cant" to baseline.metrics["cant"]!!.mean))
        assertEquals(0.0, result.rms, 1e-9)
        assertEquals(100.0, result.stability, 1e-9)
    }

    @Test fun largeDeviationLowersStabilityAndFlagsTopFeature() {
        val builder = BaselineBuilder()
        // steadiness ~ N(80, ~1), cant ~ N(0, ~0.1)
        listOf(79.0, 80.0, 81.0, 80.0, 79.0, 81.0, 80.0, 80.0).forEach {
            builder.add(mapOf("steadiness" to it, "cant" to 0.0))
        }
        listOf(-0.1, 0.0, 0.1, 0.0, -0.1, 0.1, 0.0, 0.0).forEachIndexed { _, c ->
            // already added steadiness above; add cant samples via a second pass baseline
        }
        val baseline = builder.build()
        val scorer = DeviationScorer(baseline)
        // A steadiness way off baseline.
        val result = scorer.score(mapOf("steadiness" to 60.0))
        assertTrue(result.stability < 50.0, "stability should drop for a big deviation, was ${result.stability}")
        assertEquals("steadiness", result.topDeviation?.key)
        assertTrue(result.topDeviation!!.value < 0, "steadiness dropped, z should be negative")
    }

    @Test fun weightsAmplifyCriticalFeatures() {
        val b = BaselineBuilder()
        repeat(8) { b.add(mapOf("release" to 1.0 + (it % 2) * 0.2, "posture" to 1.0 + (it % 2) * 0.2)) }
        val baseline = b.build()
        val feats = mapOf("release" to 3.0, "posture" to 3.0) // both equally off
        val unweighted = DeviationScorer(baseline).score(feats).stability
        val weighted = DeviationScorer(baseline, weights = mapOf("release" to 5.0, "posture" to 1.0)).score(feats).stability
        // identical deviations, identical weights effect here since both off equally -> equal,
        // so instead make only release off:
        val onlyRelease = mapOf("release" to 3.0, "posture" to baseline.metrics["posture"]!!.mean)
        val plain = DeviationScorer(baseline).score(onlyRelease).stability
        val heavy = DeviationScorer(baseline, weights = mapOf("release" to 5.0)).score(onlyRelease).stability
        assertTrue(heavy < plain, "weighting the off feature should reduce stability ($heavy vs $plain)")
        assertEquals(unweighted, weighted, 1e-9) // sanity: equal deviations unaffected by relative weights
    }
}

class FatigueTest {
    @Test fun detectsSteadinessDecayAcrossSession() {
        // steadiness falls from ~90 to ~70 across 20 shots
        val values = (0 until 20).map { 90.0 - it * 1.0 }
        val traj = assertNotNull(FatigueTracker.analyze(values, higherIsBetter = true))
        assertTrue(traj.fatigued)
        assertTrue(traj.slopePerRep < 0)
        // ended ~21% below start
        assertTrue(traj.decayFraction > 0.15, "decayFraction was ${traj.decayFraction}")
        assertTrue(traj.trendStrength > 0.99) // perfectly linear
    }

    @Test fun risingTremorIsAlsoFatigueWhenHigherIsWorse() {
        val tremor = (0 until 10).map { 1.0 + it * 0.1 } // tremor grows
        val traj = assertNotNull(FatigueTracker.analyze(tremor, higherIsBetter = false))
        assertTrue(traj.fatigued)
    }

    @Test fun tooFewRepsReturnsNull() {
        assertNull(FatigueTracker.analyze(listOf(1.0, 2.0)))
    }
}

class SignalScoreCorrelationTest {
    @Test fun recoversThatMoreDriftCostsPoints() {
        // Build reps where score = 10 - 2*drift (+ tiny wobble), drift in [0,3]
        val reps = (0 until 12).map { i ->
            val drift = (i % 4).toDouble() * 0.8
            Rep(
                id = "r$i", sessionId = "s", indexInSession = i,
                features = mapOf("drift" to drift, "noise" to (i % 2).toDouble()),
                score = 10.0 - 2.0 * drift,
            )
        }
        val rels = SignalScoreCorrelation.correlate(reps)
        val drift = assertNotNull(rels.firstOrNull { it.feature == "drift" })
        assertEquals(-2.0, drift.pointsPerUnit, 1e-6) // ~2 points lost per unit drift
        assertTrue(drift.r < -0.99)
        assertTrue(drift.isTrustworthy())
    }

    @Test fun ignoresUnscoredRepsAndThinData() {
        val reps = (0 until 3).map { Rep("r$it", "s", it, mapOf("x" to it.toDouble()), score = it.toDouble()) }
        assertTrue(SignalScoreCorrelation.correlate(reps).isEmpty()) // below MIN_SAMPLES
    }
}
