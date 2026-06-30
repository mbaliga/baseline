package xyz.mdhv.baseline.engine.sport

import xyz.mdhv.baseline.engine.model.Rep
import xyz.mdhv.baseline.engine.stats.linearFit

/**
 * THE DIFFERENTIATOR (handoff §1.2, §3.5): tie each signal to actual on-target score.
 *
 * Given reps that have BOTH a feature vector and a logged outcome score, this fits each
 * feature against the score and reports the relationship — enabling statements like
 * "this much pin-drift costs you ~X points". This is sport-agnostic: it works for any
 * module that logs a ground-truth score per rep.
 *
 * Honesty guardrails baked in: a correlation is only reported for features with at least
 * [minSamples] scored reps and non-degenerate variance, and every result carries its n and
 * R² so the UI can refuse to over-claim on thin data.
 */
object SignalScoreCorrelation {

    fun correlate(reps: List<Rep>, minSamples: Int = MIN_SAMPLES): List<FeatureScoreRelation> {
        val scored = reps.filter { it.score != null }
        if (scored.size < minSamples) return emptyList()

        val featureNames = scored.flatMap { it.features.keys }.toSortedSet()
        val out = ArrayList<FeatureScoreRelation>(featureNames.size)
        for (name in featureNames) {
            val xs = ArrayList<Double>()
            val ys = ArrayList<Double>()
            for (rep in scored) {
                val v = rep.features[name] ?: continue
                xs.add(v)
                ys.add(rep.score!!)
            }
            if (xs.size < minSamples) continue
            val fit = linearFit(xs, ys) ?: continue
            out.add(
                FeatureScoreRelation(
                    feature = name,
                    n = xs.size,
                    r = fit.r,
                    rSquared = fit.rSquared,
                    pointsPerUnit = fit.slope,
                )
            )
        }
        // Strongest relationships first.
        return out.sortedByDescending { it.rSquared }
    }

    const val MIN_SAMPLES = 6
}

/**
 * One feature's measured relationship to score.
 * @param pointsPerUnit OLS slope: change in score per +1 unit of the feature. Negative
 *        means "more of this feature loses points" (e.g. more drift -> lower score).
 */
data class FeatureScoreRelation(
    val feature: String,
    val n: Int,
    val r: Double,
    val rSquared: Double,
    val pointsPerUnit: Double,
) {
    /** Coarse confidence gate for the UI: enough samples AND a non-trivial trend. */
    fun isTrustworthy(minN: Int = 10, minR2: Double = 0.1): Boolean =
        n >= minN && rSquared >= minR2
}
