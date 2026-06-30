package xyz.mdhv.baseline.engine.stats

import kotlin.math.sqrt

/**
 * Online (streaming) mean/variance via Welford's algorithm.
 *
 * Used to accumulate a per-metric baseline incrementally as "good" reps arrive,
 * without holding every sample in memory. Numerically stable for long sessions.
 */
class Welford {
    var count: Long = 0L
        private set
    var mean: Double = 0.0
        private set
    private var m2: Double = 0.0

    fun add(x: Double) {
        if (x.isNaN()) return
        count += 1
        val delta = x - mean
        mean += delta / count
        m2 += delta * (x - mean)
    }

    /** Population variance (divide by n). Returns 0 for fewer than 2 samples. */
    val variance: Double
        get() = if (count < 2) 0.0 else m2 / count

    /** Sample variance (divide by n-1). Returns 0 for fewer than 2 samples. */
    val sampleVariance: Double
        get() = if (count < 2) 0.0 else m2 / (count - 1)

    val stdDev: Double
        get() = sqrt(variance)

    val sampleStdDev: Double
        get() = sqrt(sampleVariance)

    fun snapshot(): MetricStats = MetricStats(count, mean, sampleStdDev)
}

/**
 * An immutable summary of one metric's distribution over the reps that built a baseline.
 * [std] is the sample standard deviation (the natural scale for a z-score).
 */
data class MetricStats(
    val count: Long,
    val mean: Double,
    val std: Double,
) {
    /**
     * Signed z-score of [value] against this metric. When [std] is ~0 (a metric that
     * was perfectly constant across the baseline) we fall back to 0 to avoid div-by-zero
     * blowups; callers treating tiny std as "no information" is the honest behaviour.
     */
    fun z(value: Double): Double {
        if (std <= EPS) return 0.0
        return (value - mean) / std
    }

    companion object {
        const val EPS = 1e-9
    }
}

/** Pearson correlation with a simple ordinary-least-squares fit. */
data class LinearFit(
    val slope: Double,
    val intercept: Double,
    /** Pearson r in [-1, 1]. */
    val r: Double,
    val n: Int,
) {
    val rSquared: Double get() = r * r
    fun predict(x: Double): Double = slope * x + intercept
}

/**
 * Ordinary least squares fit of y on x plus Pearson r.
 * Returns null when there are fewer than 2 points or x has no variance.
 */
fun linearFit(xs: List<Double>, ys: List<Double>): LinearFit? {
    require(xs.size == ys.size) { "x and y must be the same length" }
    val n = xs.size
    if (n < 2) return null
    var sx = 0.0; var sy = 0.0; var sxx = 0.0; var syy = 0.0; var sxy = 0.0
    for (i in 0 until n) {
        val x = xs[i]; val y = ys[i]
        sx += x; sy += y; sxx += x * x; syy += y * y; sxy += x * y
    }
    val nD = n.toDouble()
    val covXY = sxy - sx * sy / nD
    val varX = sxx - sx * sx / nD
    val varY = syy - sy * sy / nD
    if (varX <= MetricStats.EPS) return null
    val slope = covXY / varX
    val intercept = (sy - slope * sx) / nD
    val denom = sqrt(varX * varY)
    val r = if (denom <= MetricStats.EPS) 0.0 else covXY / denom
    return LinearFit(slope, intercept, r, n)
}
