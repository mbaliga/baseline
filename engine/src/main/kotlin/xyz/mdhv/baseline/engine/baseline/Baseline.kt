package xyz.mdhv.baseline.engine.baseline

import xyz.mdhv.baseline.engine.model.FeatureVector
import xyz.mdhv.baseline.engine.stats.MetricStats
import xyz.mdhv.baseline.engine.stats.Welford

/**
 * A per-athlete baseline: the distribution of each feature over the athlete's "good" reps.
 * This is the engine's model of "your normal" — the reference every later rep is scored
 * against. It is intentionally just per-feature mean/std (a diagonal model); a full
 * covariance / Mahalanobis model can replace this behind the same [zScores] surface once
 * there's enough data to estimate covariance honestly.
 */
class BaselineModel internal constructor(
    val metrics: Map<String, MetricStats>,
    /** How many reps contributed to this baseline. */
    val repCount: Long,
) {
    val featureNames: Set<String> get() = metrics.keys

    /** Signed z-score per feature present in both the baseline and [features]. */
    fun zScores(features: FeatureVector): Map<String, Double> {
        val out = LinkedHashMap<String, Double>(metrics.size)
        for ((name, stats) in metrics) {
            val v = features[name] ?: continue
            out[name] = stats.z(v)
        }
        return out
    }

    fun isReady(minReps: Long = MIN_REPS_FOR_BASELINE): Boolean = repCount >= minReps

    companion object {
        /**
         * Below this, a baseline is too thin to score against meaningfully. Surfaced so the
         * app can show "building your baseline (3/8)" rather than scoring against noise.
         */
        const val MIN_REPS_FOR_BASELINE = 8L
    }
}

/**
 * Accumulates "good" reps into a [BaselineModel]. Feed it the reps an athlete (or coach)
 * has marked as representative of their good form; call [build] for a snapshot at any time.
 * Streaming + incremental so the app can show the baseline firming up live.
 */
class BaselineBuilder {
    private val acc = LinkedHashMap<String, Welford>()
    private var reps = 0L

    fun add(features: FeatureVector): BaselineBuilder {
        if (features.isEmpty()) return this
        reps += 1
        for ((name, value) in features) {
            acc.getOrPut(name) { Welford() }.add(value)
        }
        return this
    }

    fun addAll(reps: Iterable<FeatureVector>): BaselineBuilder {
        reps.forEach { add(it) }
        return this
    }

    fun build(): BaselineModel {
        val snapshot = acc.mapValues { (_, w) -> w.snapshot() }
        return BaselineModel(snapshot, reps)
    }
}
