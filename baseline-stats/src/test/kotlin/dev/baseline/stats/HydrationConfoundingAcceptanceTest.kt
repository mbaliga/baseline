package dev.baseline.stats

import org.junit.Assert.assertTrue
import org.junit.Test

class HydrationConfoundingAcceptanceTest {
    @Test
    fun exertionAndHeatDemoteNaiveHydrationConclusion() {
        val count = 180
        val exertion = DoubleArray(count) { 1.0 + (it % 30) }
        val heat = DoubleArray(count) { 18.0 + ((it * 7) % 16) }
        val independentHydrationVariation = DoubleArray(count) { ((it * 17) % 11 - 5).toDouble() }
        val water = DoubleArray(count) {
            20.0 + 2.0 * exertion[it] + 1.5 * heat[it] + independentHydrationVariation[it]
        }
        val nextDayRecovery = DoubleArray(count) {
            100.0 - 2.8 * exertion[it] - 1.7 * heat[it]
        }

        val naive = DescriptiveStats.pearson(water, nextDayRecovery)
        val adjusted = OrdinaryLeastSquares.fit(
            outcome = nextDayRecovery,
            predictors = listOf(water, exertion, heat),
        )
        val adjustedWaterCoefficient = adjusted.coefficients[1]

        assertTrue("naive association should look harmful", naive < -0.8)
        assertTrue(
            "hydration effect should disappear after confounder adjustment",
            kotlin.math.abs(adjustedWaterCoefficient) < 1e-8,
        )
    }
}
