package xyz.mdhv.baseline.engine.deviation

import xyz.mdhv.baseline.engine.baseline.BaselineModel
import xyz.mdhv.baseline.engine.model.FeatureVector
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Scores how far a rep deviates from the athlete's baseline.
 *
 * Two outputs, deliberately separated:
 *  - [DeviationResult.perFeature]: signed z per feature — the *diagnostic* ("your cant was
 *    +2.1σ today"). Sign is preserved so the app can say which direction you drifted.
 *  - [DeviationResult.stability]: a single 0–100 *summary* where 100 == on-baseline. This
 *    is the number the athlete watches shot-to-shot.
 *
 * The summary is built from a weighted RMS of per-feature |z|, squashed through a smooth
 * exponential so that small deviations barely move the score and large ones saturate near 0.
 */
class DeviationScorer(
    private val baseline: BaselineModel,
    /**
     * Optional per-feature weights. A feature absent from the map defaults to weight 1.0.
     * Use this to weight score-critical signals (release, drift) above postural ones, per
     * the archery theory's "the sensor owns the shot-to-shot signals" principle.
     */
    private val weights: Map<String, Double> = emptyMap(),
    /**
     * Controls how quickly stability falls off with deviation. With k = 0.5, a weighted-RMS
     * of 1σ → ~78, 2σ → ~37, 3σ → ~11. Tunable per sport.
     */
    private val falloff: Double = 0.5,
) {
    fun score(features: FeatureVector): DeviationResult {
        val z = baseline.zScores(features)
        if (z.isEmpty()) {
            return DeviationResult(perFeature = emptyMap(), rms = 0.0, stability = 100.0)
        }
        var weightedSqSum = 0.0
        var weightSum = 0.0
        for ((name, zi) in z) {
            val w = weights[name] ?: 1.0
            weightedSqSum += w * zi * zi
            weightSum += w
        }
        val rms = if (weightSum <= 0.0) 0.0 else sqrt(weightedSqSum / weightSum)
        // Smooth, monotonic 0..100; 0σ -> 100, grows-> 0.
        val stability = 100.0 * exp(-falloff * rms)
        return DeviationResult(perFeature = z, rms = rms, stability = stability)
    }
}

data class DeviationResult(
    /** Signed z per feature (the diagnostic detail). */
    val perFeature: Map<String, Double>,
    /** Weighted RMS of |z| across features (the raw deviation magnitude). */
    val rms: Double,
    /** 0–100 summary, 100 == on-baseline. */
    val stability: Double,
) {
    /** The single feature that deviated most in magnitude, or null if none. */
    val topDeviation: Map.Entry<String, Double>?
        get() = perFeature.maxByOrNull { abs(it.value) }
}
