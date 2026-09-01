package xyz.mdhv.bocal.baseline

import kotlin.math.abs
import kotlin.math.sqrt
import xyz.mdhv.bocal.model.SessionScore
import xyz.mdhv.bocal.model.TrendAnalyzer
import xyz.mdhv.bocal.model.TrendDirection
import xyz.mdhv.bocal.model.TrendReport

/**
 * Deterministic longitudinal trend engine (proprietary).
 *
 * Design goals, in order: (1) no false "improving" praise — direction claims
 * require statistical support, (2) fully deterministic for a given series,
 * (3) domain-agnostic — nothing in here knows about music or archery.
 */
class BaselineEngine(
    /** Sessions in the "recent" window used for slope/direction. */
    private val recentWindow: Int = 8,
    /** Sessions in the rolling baseline window preceding the recent window. */
    private val baselineWindow: Int = 10,
    /** t-statistic magnitude required to call a slope a real trend. */
    private val slopeSignificance: Double = 2.0,
) : TrendAnalyzer {

    override fun analyze(series: List<SessionScore>): TrendReport {
        if (series.isEmpty()) {
            return TrendReport(
                baselineMean = 0.0, baselineStdDev = 0.0, recentMean = 0.0,
                slopePerSession = 0.0, direction = TrendDirection.STABLE,
                plateau = false, changePoints = emptyList(), personalBestIndex = -1,
            )
        }
        val values = DoubleArray(series.size) { series[it].value }
        val n = values.size

        val recentCount = minOf(recentWindow, n)
        val recent = values.copyOfRange(n - recentCount, n)
        val recentMean = recent.average()

        // Baseline = the window immediately preceding the recent window; when
        // history is short, fall back to the whole series.
        val baselineEnd = n - recentCount
        val baselineStart = (baselineEnd - baselineWindow).coerceAtLeast(0)
        val baselineSlice =
            if (baselineEnd > baselineStart) values.copyOfRange(baselineStart, baselineEnd)
            else values
        val baselineMean = baselineSlice.average()
        val baselineStdDev = stdDev(baselineSlice)

        val (slope, direction) = slopeAndDirection(recent)

        val firstHalf = values.copyOfRange(0, n / 2)
        val (_, earlyDirection) =
            if (firstHalf.size >= 3) slopeAndDirection(firstHalf)
            else 0.0 to TrendDirection.STABLE
        val plateau = direction == TrendDirection.STABLE &&
            earlyDirection == TrendDirection.IMPROVING &&
            recentMean >= baselineMean

        return TrendReport(
            baselineMean = baselineMean,
            baselineStdDev = baselineStdDev,
            recentMean = recentMean,
            slopePerSession = slope,
            direction = direction,
            plateau = plateau,
            changePoints = changePoints(values),
            personalBestIndex = values.indices.maxBy { values[it] },
        )
    }

    /** Least-squares slope plus a significance-gated direction call. */
    private fun slopeAndDirection(window: DoubleArray): Pair<Double, TrendDirection> {
        val k = window.size
        if (k < 2) return 0.0 to TrendDirection.STABLE

        val xMean = (k - 1) / 2.0
        val yMean = window.average()
        var sxx = 0.0
        var sxy = 0.0
        for (i in 0 until k) {
            val dx = i - xMean
            sxx += dx * dx
            sxy += dx * (window[i] - yMean)
        }
        val slope = sxy / sxx

        // Residual standard error of the fit.
        var ssRes = 0.0
        for (i in 0 until k) {
            val fitted = yMean + slope * (i - xMean)
            val r = window[i] - fitted
            ssRes += r * r
        }
        val direction = if (k <= 2 || ssRes < 1e-18) {
            // Perfectly collinear (synthetic or 2 points): sign decides.
            when {
                slope > 1e-12 -> TrendDirection.IMPROVING
                slope < -1e-12 -> TrendDirection.DECLINING
                else -> TrendDirection.STABLE
            }
        } else {
            val se = sqrt(ssRes / (k - 2)) / sqrt(sxx)
            val t = slope / se
            when {
                t > slopeSignificance -> TrendDirection.IMPROVING
                t < -slopeSignificance -> TrendDirection.DECLINING
                else -> TrendDirection.STABLE
            }
        }
        return slope to direction
    }

    /**
     * Change points via outliers in the first-difference series, judged
     * against the median absolute deviation. Robust to steady ramps (their
     * differences are constant, so nothing flags) and to modest noise.
     */
    private fun changePoints(values: DoubleArray): List<Int> {
        val n = values.size
        if (n < 4) return emptyList()
        val diffs = DoubleArray(n - 1) { values[it + 1] - values[it] }
        val median = median(diffs.clone())
        val absDevs = DoubleArray(diffs.size) { abs(diffs[it] - median) }
        val mad = median(absDevs.clone())
        // 1.4826 scales MAD to a normal-equivalent sigma; 3.5 sigma flags.
        // Alternating noise can drive MAD to zero, so a plain-stddev floor
        // backs it up before the numeric epsilon does.
        val threshold = maxOf(3.5 * 1.4826 * mad, 2.0 * stdDev(diffs), 1e-9)
        val points = ArrayList<Int>()
        for (i in diffs.indices) {
            if (abs(diffs[i] - median) > threshold) points.add(i + 1)
        }
        return points
    }

    private fun stdDev(values: DoubleArray): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        var sum = 0.0
        for (v in values) sum += (v - mean) * (v - mean)
        return sqrt(sum / (values.size - 1))
    }

    private fun median(values: DoubleArray): Double {
        values.sort()
        val mid = values.size / 2
        return if (values.size % 2 == 1) values[mid] else (values[mid - 1] + values[mid]) / 2.0
    }
}
