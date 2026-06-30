package xyz.mdhv.baseline.engine.model

/**
 * Core, sport-agnostic data model for the Baseline engine.
 *
 * The engine knows nothing about archery (or fencing, or hangboarding). It knows about
 * athletes, sessions, and "reps" — the smallest scored unit of a sport (a shot, a touch,
 * a hang). Everything sport-specific lives behind the interfaces in the `sport` package
 * and is surfaced to the engine as a [FeatureVector] plus an optional outcome [score].
 */

/** A named scalar feature extracted from one rep, e.g. "steadiness" -> 78.0. */
typealias FeatureVector = Map<String, Double>

data class Athlete(
    val id: String,
    val displayName: String,
)

/**
 * A continuous capture period: one practice session / one end / one range visit.
 * Carries the context the engine needs to keep baselines comparable (you don't compare
 * a 50 lb-draw session against a 30 lb one).
 */
data class Session(
    val id: String,
    val athleteId: String,
    val startedAtEpochMs: Long,
    /** Free-form, sport-supplied context used to bucket comparable baselines. */
    val context: SessionContext = SessionContext(),
    val reps: List<Rep> = emptyList(),
)

/**
 * Opaque, sport-agnostic context tags. Archery fills this with draw weight, bow, distance;
 * the engine only uses it to decide which reps share a baseline (see BaselineKey).
 */
data class SessionContext(
    val tags: Map<String, String> = emptyMap(),
) {
    operator fun get(key: String): String? = tags[key]
}

/**
 * The core unit. A rep is one scored attempt: its extracted [features], an optional
 * ground-truth [score] (manual entry or target-face CV), and its ordinal [indexInSession]
 * (needed for fatigue trajectories — fatigue is a function of position within a session).
 *
 * Raw time-series (IMU/pose windows) are NOT held here; they live in storage. The engine
 * operates on the extracted feature vector so it stays sport-agnostic and lightweight.
 */
data class Rep(
    val id: String,
    val sessionId: String,
    val indexInSession: Int,
    val features: FeatureVector,
    /** Ground-truth outcome on the sport's scale, or null if not yet logged. */
    val score: Double? = null,
)
