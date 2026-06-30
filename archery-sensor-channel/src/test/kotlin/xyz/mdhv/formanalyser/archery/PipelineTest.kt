package xyz.mdhv.formanalyser.archery

import xyz.mdhv.baseline.engine.baseline.BaselineBuilder
import xyz.mdhv.baseline.engine.deviation.DeviationScorer
import xyz.mdhv.baseline.engine.model.Sample
import xyz.mdhv.baseline.engine.model.TimeSeries
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Helpers to synthesise bow-IMU streams with known phase structure. */
private object Synth {
    const val FS = 100.0
    private val channels = listOf(
        ArcheryChannels.GYRO_X, ArcheryChannels.GYRO_Y, ArcheryChannels.GYRO_Z,
        ArcheryChannels.ACC_X, ArcheryChannels.ACC_Y, ArcheryChannels.ACC_Z,
    )

    /** One shot: set-up (motion) -> hold (low tremor at [tremorAmp]) -> release spike. */
    fun shotSamples(
        tremorAmpDegPerSec: Double,
        cantDeg: Double,
        setUp: Int = 60,
        hold: Int = 200,
        release: Int = 20,
        startIndex: Int = 0,
    ): List<Sample> {
        val out = ArrayList<Sample>(setUp + hold + release)
        var t = startIndex
        // set-up: steady drawing motion well above hold threshold, below release threshold.
        repeat(setUp) {
            out.add(sample(t, gx = 45.0, gy = 0.0, gz = 0.0, cantDeg = 0.0)); t++
        }
        // hold: small tremor at 6 Hz on the pitch axis; bow canted by cantDeg.
        repeat(hold) { i ->
            val g = tremorAmpDegPerSec * sin(2 * PI * 6.0 * i / FS)
            out.add(sample(t, gx = g, gy = 0.0, gz = 0.0, cantDeg = cantDeg)); t++
        }
        // release: short hard spike above the release threshold.
        repeat(release) {
            out.add(sample(t, gx = 200.0, gy = 0.0, gz = 0.0, cantDeg = cantDeg)); t++
        }
        return out
    }

    private fun sample(t: Int, gx: Double, gy: Double, gz: Double, cantDeg: Double): Sample {
        val r = Math.toRadians(cantDeg)
        // upright reads (0,0,1) g; cant about the forward axis tilts gravity into +accX.
        val acc = doubleArrayOf(sin(r), 0.0, cos(r))
        return Sample(
            tNanos = (t * (1e9 / FS)).toLong(),
            values = doubleArrayOf(gx, gy, gz, acc[0], acc[1], acc[2]),
        )
    }

    fun stream(samples: List<Sample>) = TimeSeries(channels, samples, FS)
}

class SegmenterTest {
    @Test fun findsExactlyOneShotWithOrderedPhases() {
        val samples = Synth.shotSamples(tremorAmpDegPerSec = 4.0, cantDeg = 0.0) +
            // trailing low-motion tail that is too short to be its own hold
            List(40) { Sample((1_000_000_000L + it), doubleArrayOf(2.0, 0.0, 0.0, 0.0, 0.0, 1.0)) }
        val shots = ArcheryShotSegmenter().segment(Synth.stream(samples))
        assertEquals(1, shots.size)
        val s = shots[0]
        assertTrue(s.setUpStart <= s.holdStart)
        assertTrue(s.holdStart < s.releaseStart)
        assertTrue(s.releaseStart < s.releaseEnd)
        // hold should be roughly the 2 s we synthesised.
        assertEquals(2.0, s.holdDurationSeconds, 0.4)
    }

    @Test fun findsMultipleShotsInASession() {
        val all = ArrayList<Sample>()
        repeat(5) { all.addAll(Synth.shotSamples(4.0, 0.0, startIndex = all.size)) }
        val shots = ArcheryShotSegmenter().segment(Synth.stream(all))
        assertEquals(5, shots.size)
    }
}

class FeatureExtractorTest {
    private val extractor = ArcheryFeatureExtractor()

    private fun featuresFor(tremor: Double, cant: Double) =
        extractor.extract(ArcheryShotSegmenter().segment(Synth.stream(Synth.shotSamples(tremor, cant)))[0])

    @Test fun steadyHoldScoresHigherThanShakyHold() {
        val steady = featuresFor(tremor = 2.0, cant = 0.0)[ArcheryFeatureExtractor.STEADINESS]!!
        val shaky = featuresFor(tremor = 20.0, cant = 0.0)[ArcheryFeatureExtractor.STEADINESS]!!
        assertTrue(steady > shaky, "steady ($steady) should beat shaky ($shaky)")
        assertTrue(steady in 0.0..100.0 && shaky in 0.0..100.0)
    }

    @Test fun cantIsRecoveredFromGravity() {
        val cant = featuresFor(tremor = 3.0, cant = 12.0)[ArcheryFeatureExtractor.CANT_DEG]!!
        assertEquals(12.0, cant, 0.5)
    }

    @Test fun tremorFrequencyAndReleasePeakAreSensible() {
        val f = featuresFor(tremor = 6.0, cant = 0.0)
        assertEquals(6.0, f[ArcheryFeatureExtractor.TREMOR_HZ]!!, 1.5)
        assertTrue(f[ArcheryFeatureExtractor.RELEASE_PEAK]!! > 100.0)
    }
}

/** Cross-repo integration: archery module features flow through the engine's scoring. */
class EngineIntegrationTest {
    @Test fun baselineFromSteadyShotsFlagsAShakyOne() {
        val extractor = ArcheryFeatureExtractor()
        val segmenter = ArcheryShotSegmenter()

        // Build a baseline from 10 good shots with realistic shot-to-shot variation
        // (a real archer's "good" form is a distribution, not a constant).
        val builder = BaselineBuilder()
        repeat(10) { i ->
            val tremor = 3.0 + (i % 3 - 1) * 0.5   // ~2.5..3.5 deg/s
            val cant = 0.5 + (i % 2) * 0.4         // ~0.5..0.9 deg
            val shot = segmenter.segment(Synth.stream(Synth.shotSamples(tremor, cant)))[0]
            builder.add(extractor.extract(shot))
        }
        val baseline = builder.build()
        assertTrue(baseline.isReady())

        val scorer = DeviationScorer(baseline, weights = ArcheryModule.deviationWeights)

        // A good shot scores near baseline; a shaky, canted shot deviates.
        val good = extractor.extract(segmenter.segment(Synth.stream(Synth.shotSamples(3.0, 0.7)))[0])
        val bad = extractor.extract(segmenter.segment(Synth.stream(Synth.shotSamples(25.0, 8.0)))[0])

        val goodStability = scorer.score(good).stability
        val badResult = scorer.score(bad)
        assertTrue(goodStability > badResult.stability,
            "good ($goodStability) should be steadier than bad (${badResult.stability})")
        assertTrue(badResult.stability < 80.0)
    }
}
