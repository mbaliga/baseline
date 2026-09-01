package dev.baseline.stats

import kotlin.math.sqrt

object DescriptiveStats {
    fun pearson(left: DoubleArray, right: DoubleArray): Double {
        require(left.size == right.size && left.size >= 3)
        val leftMean = left.average()
        val rightMean = right.average()
        var covariance = 0.0
        var leftVariance = 0.0
        var rightVariance = 0.0
        for (index in left.indices) {
            val a = left[index] - leftMean
            val b = right[index] - rightMean
            covariance += a * b
            leftVariance += a * a
            rightVariance += b * b
        }
        require(leftVariance > 0.0 && rightVariance > 0.0)
        return covariance / sqrt(leftVariance * rightVariance)
    }
}

data class RegressionResult(val coefficients: DoubleArray, val observations: Int)

/** Small deterministic OLS used for Community acceptance fixtures, not causal promotion. */
object OrdinaryLeastSquares {
    fun fit(outcome: DoubleArray, predictors: List<DoubleArray>): RegressionResult {
        require(outcome.size >= predictors.size + 2)
        require(predictors.all { it.size == outcome.size })
        val columns = predictors.size + 1
        val normal = Array(columns) { DoubleArray(columns) }
        val rhs = DoubleArray(columns)
        for (row in outcome.indices) {
            val values = DoubleArray(columns) { column ->
                if (column == 0) 1.0 else predictors[column - 1][row]
            }
            for (a in 0 until columns) {
                rhs[a] += values[a] * outcome[row]
                for (b in 0 until columns) normal[a][b] += values[a] * values[b]
            }
        }
        return RegressionResult(solve(normal, rhs), outcome.size)
    }

    private fun solve(matrix: Array<DoubleArray>, vector: DoubleArray): DoubleArray {
        val n = vector.size
        val augmented = Array(n) { row -> matrix[row].copyOf() + vector[row] }
        for (pivot in 0 until n) {
            val best = (pivot until n).maxBy { kotlin.math.abs(augmented[it][pivot]) }
            val swap = augmented[pivot]
            augmented[pivot] = augmented[best]
            augmented[best] = swap
            require(kotlin.math.abs(augmented[pivot][pivot]) > 1e-10) { "Singular design matrix" }
            val scale = augmented[pivot][pivot]
            for (column in pivot..n) augmented[pivot][column] /= scale
            for (row in 0 until n) {
                if (row == pivot) continue
                val factor = augmented[row][pivot]
                for (column in pivot..n) augmented[row][column] -= factor * augmented[pivot][column]
            }
        }
        return DoubleArray(n) { augmented[it][n] }
    }
}
