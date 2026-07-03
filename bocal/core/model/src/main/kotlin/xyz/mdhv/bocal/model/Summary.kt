package xyz.mdhv.bocal.model

/**
 * Structured, already-computed session facts handed to a summarizer. Pure
 * primitives (no engine types beyond [TrendDirection]) so the interface can
 * live in :core:model without pulling in the analysis layer.
 */
data class SessionSummaryInput(
    val attemptCount: Int,
    /** Mean absolute intonation error in cents; lower is better. */
    val meanAbsCents: Double,
    /** Share of held time within the in-tune window, 0..100. */
    val percentInTune: Double,
    val meanHoldSeconds: Double,
    /** Signed overall bias: positive = tends sharp, negative = tends flat. */
    val intonationBiasCents: Double,
    /** Longitudinal read from the trend engine, or null when there's no history yet. */
    val trendDirection: TrendDirection? = null,
    val isPersonalBest: Boolean = false,
    val currentStreakDays: Int = 0,
    /** Note the player ran sharpest on this session, e.g. "F#5", or null. */
    val sharpestNote: String? = null,
    /** Note the player ran flattest on this session, or null. */
    val flattestNote: String? = null,
)

/**
 * The Urbana seam (strategic analysis §4): a session summary provider. The
 * shipped default is a fully-offline, deterministic template summarizer — no
 * INTERNET permission, no dependency on another running project. An
 * Urbana-backed implementation can slot in behind this interface later as an
 * opt-in power-user add-on without disturbing the backendless guarantee.
 */
interface SummaryProvider {
    fun summarize(input: SessionSummaryInput): String
}
