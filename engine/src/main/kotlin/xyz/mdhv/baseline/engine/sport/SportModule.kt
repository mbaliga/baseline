package xyz.mdhv.baseline.engine.sport

import xyz.mdhv.baseline.engine.model.FeatureVector
import xyz.mdhv.baseline.engine.model.TimeSeries

/**
 * THE SEAM. Everything sport-specific lives behind this interface so the engine core
 * (baseline, deviation, fatigue, correlation, sync) never imports anything archery.
 * Fencing / pistol / hangboard each ship one implementation of [SportModule] and slot in
 * without touching the core.
 *
 * A module is three things the handoff §2 calls out — a rep-segmenter, a feature extractor,
 * and a scoring rubric — bundled with the metadata the engine needs to score them.
 *
 * @param TRaw the module's own raw-rep type (e.g. an archery shot with phase boundaries).
 *             Opaque to the engine; produced by [segmenter] and consumed by [extractor].
 */
interface SportModule<TRaw> {
    /** Stable id, e.g. "archery". */
    val sportId: String

    val segmenter: RepSegmenter<TRaw>
    val extractor: FeatureExtractor<TRaw>
    val scoring: ScoringRubric

    /**
     * Per-feature weights for the deviation summary. Lets a sport encode the theory's
     * "sensor owns the score-critical signals" by weighting release/drift above posture.
     * Features not listed default to 1.0.
     */
    val deviationWeights: Map<String, Double> get() = emptyMap()

    /**
     * Which features the sport believes degrade with fatigue, and their polarity
     * (true == higher-is-fresher). Drives [xyz.mdhv.baseline.engine.fatigue.FatigueTracker].
     */
    val fatigueMetrics: Map<String, Boolean> get() = emptyMap()
}

/** Splits a continuous capture stream into discrete raw reps. */
fun interface RepSegmenter<TRaw> {
    fun segment(stream: TimeSeries): List<TRaw>
}

/** Turns one raw rep into a named feature vector the engine can baseline & score. */
interface FeatureExtractor<TRaw> {
    /** The features this extractor can produce. Stable, used for UI and baseline keys. */
    val featureNames: List<String>
    fun extract(rep: TRaw): FeatureVector
}

/** Describes a sport's outcome scale (the ground truth correlation is built against). */
interface ScoringRubric {
    /** Best possible single-rep score (e.g. 10 for a 10-ring target, X counts as 10). */
    val maxScore: Double
    /** Whether [score] is a legal value on this scale. */
    fun isValid(score: Double): Boolean
}
