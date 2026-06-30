package xyz.mdhv.baseline.engine.fatigue

import xyz.mdhv.baseline.engine.stats.linearFit

/**
 * Fatigue is a *trajectory across a session*, not a per-rep property (handoff §3.3:
 * "Strength is a fatigue story, not a per-shot one"). Given a metric sampled once per rep
 * in session order — typically steadiness, or a holding-moment proxy — this fits the trend
 * and reports the decay.
 */
object FatigueTracker {

    /**
     * @param valuesInOrder one value per rep, in session order (index 0 = first rep).
     * @param higherIsBetter true for metrics where larger == fresher (e.g. steadiness);
     *        false for metrics where larger == more fatigued (e.g. tremor amplitude).
     */
    fun analyze(valuesInOrder: List<Double>, higherIsBetter: Boolean = true): FatigueTrajectory? {
        val clean = valuesInOrder.filter { !it.isNaN() }
        if (clean.size < MIN_REPS) return null
        val xs = clean.indices.map { it.toDouble() }
        val fit = linearFit(xs, clean) ?: return null

        val firstFit = fit.predict(0.0)
        val lastFit = fit.predict((clean.size - 1).toDouble())
        // Positive decayFraction == got worse over the session, regardless of metric polarity.
        val rawChange = lastFit - firstFit
        val worsening = if (higherIsBetter) -rawChange else rawChange
        val base = if (kotlin.math.abs(firstFit) < 1e-9) 1e-9 else kotlin.math.abs(firstFit)
        val decayFraction = worsening / base

        return FatigueTrajectory(
            repCount = clean.size,
            slopePerRep = fit.slope,
            startFitted = firstFit,
            endFitted = lastFit,
            decayFraction = decayFraction,
            trendStrength = fit.rSquared,
        )
    }

    const val MIN_REPS = 4
}

data class FatigueTrajectory(
    val repCount: Int,
    /** Change in the metric per rep (signed, in the metric's own units). */
    val slopePerRep: Double,
    val startFitted: Double,
    val endFitted: Double,
    /**
     * Fraction the athlete degraded from start to end, polarity-normalised so positive
     * always means "fatigued": e.g. 0.18 == ended ~18% worse than they started.
     */
    val decayFraction: Double,
    /** R² of the linear trend in [0,1] — how cleanly fatigue explains the metric drift. */
    val trendStrength: Double,
) {
    val fatigued: Boolean get() = decayFraction > 0.0
}
