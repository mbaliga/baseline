package xyz.mdhv.bocal.model

/**
 * One session's scalar score on some metric, higher = better.
 * The unit is whatever the metric defines (e.g. mean pitch accuracy in
 * negative-mean-absolute-cents, note hold seconds, percent in tune).
 */
data class SessionScore(
    /** Monotonic session ordinal (0-based); ties broken by this, not by day. */
    val index: Int,
    /** Local calendar day of the session as epoch day, for gap/recency handling. */
    val epochDay: Long,
    val value: Double,
)

enum class TrendDirection { IMPROVING, STABLE, DECLINING }

/**
 * Longitudinal read of a score series. Produced by a [TrendAnalyzer]; consumed
 * by journal/Pro surfaces. This contract is OPEN and versioned — implementations
 * may be proprietary (see :core:baseline).
 */
data class TrendReport(
    /** Rolling "your normal" level over the baseline window. */
    val baselineMean: Double,
    /** Dispersion of the baseline window; 0 when fewer than 2 sessions. */
    val baselineStdDev: Double,
    /** Mean of the recent window. */
    val recentMean: Double,
    /** Least-squares slope of the recent window, in score units per session. */
    val slopePerSession: Double,
    val direction: TrendDirection,
    /** True when a prior improving stretch has flattened out at a higher level. */
    val plateau: Boolean,
    /** Session indices where the level shifted abruptly (change points). */
    val changePoints: List<Int>,
    /** Index into the input series of the best session, or -1 if empty. */
    val personalBestIndex: Int,
)

/**
 * The versioned seam between open surfaces and the (possibly closed)
 * longitudinal engine. Keep this interface stable: two products consume it.
 */
interface TrendAnalyzer {
    fun analyze(series: List<SessionScore>): TrendReport
}
