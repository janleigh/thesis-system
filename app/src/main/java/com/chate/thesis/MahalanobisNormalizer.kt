package com.chate.thesis

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Minimum number of samples required to compute a full covariance matrix.
 * Below this threshold, a diagonal (variance-only) approximation is used.
 */
const val MIN_SAMPLES_FOR_COVARIANCE = 50

/**
 * MahalanobisNormalizer — computes Mahalanobis distance from a fitted distribution.
 *
 * Ported from the Python MahalanobisNormalizer class. All linear algebra is
 * implemented in pure Kotlin using DoubleArray / Array<DoubleArray>.
 */
class MahalanobisNormalizer {
    @SerializedName("mean")
    var mean: DoubleArray? = null
        private set

    @SerializedName("cov_inv")
    var covInv: Array<DoubleArray>? = null
        private set

    @SerializedName("is_fitted")
    var isFitted: Boolean = false
        private set

    @SerializedName("cold_start")
    var coldStart: Boolean = true
        private set

    // ------------------------------------------------------------------ //
    //  Fit the normalizer to a collection of feature vectors              //
    // ------------------------------------------------------------------ //

    /**
     * Fit the normalizer on [vectors] (each element is a feature vector).
     *
     * When fewer than [MIN_SAMPLES_FOR_COVARIANCE] samples are available the
     * inverse covariance is approximated by `diag(1 / variance)`.
     */
    fun fit(vectors: List<DoubleArray>): MahalanobisNormalizer {
        require(vectors.isNotEmpty()) { "Cannot fit on empty data" }

        val n = vectors.size
        val d = vectors[0].size
        // Build matrix (n × d)
        val x = Array(n) { i -> vectors[i].copyOf() }

        mean = matMean(x)

        if (n < MIN_SAMPLES_FOR_COVARIANCE) {
            // Cold-start path: diagonal approximation
            val variances = matVariance(x, mean!!)
            // Replace near-zero variances with 1.0 to avoid division by zero
            for (i in variances.indices) {
                if (variances[i] < 1e-9) variances[i] = 1.0
            }
            covInv = matDiag(DoubleArray(d) { 1.0 / variances[it] })
            coldStart = true
        } else {
            // Full covariance path (sample covariance, ddof=1)
            val cov = matCovariance(x, mean!!)
            covInv =
                try {
                    val inv = matInverse(cov)
                    coldStart = false
                    inv
                } catch (_: Exception) {
                    // Regularisation fallback: cov + 1e-6 * I
                    val reg = Array(d) { i -> DoubleArray(d) { j -> cov[i][j] + if (i == j) 1e-6 else 0.0 } }
                    coldStart = true
                    matInverse(reg)
                }
        }

        isFitted = true
        return this
    }

    // ------------------------------------------------------------------ //
    //  Distance / scoring                                                 //
    // ------------------------------------------------------------------ //

    /**
     * Compute Mahalanobis distance of [vector] from the fitted distribution.
     *
     *   d = sqrt( (x - μ)ᵀ Σ⁻¹ (x - μ) )
     */
    fun distance(vector: DoubleArray): Double {
        check(isFitted) { "MahalanobisNormalizer has not been fitted" }
        val diff = DoubleArray(vector.size) { vector[it] - mean!![it] }
        val tmp = matVecMul(covInv!!, diff) // Σ⁻¹ · diff
        var distSq = 0.0
        for (i in diff.indices) distSq += diff[i] * tmp[i]
        return sqrt(max(distSq, 0.0))
    }

    /**
     * Return a score in [0, 1] by dividing the raw distance by [maxDist].
     * In a 6-dimensional feature space, legitimate typing distances typically
     * range between 1.5 - 3.5 (mean sqrt(6) ≈ 2.45), mapping to 0.15 - 0.35 (ALLOW).
     * Impostor distances (typically 8.0 - 25.0) scale to high-risk (0.80 - 1.00).
     */
    fun normalizedScore(
        vector: DoubleArray,
        maxDist: Double = 10.0,
    ): Double {
        val raw = distance(vector)
        return min(raw / maxDist, 1.0)
    }

    // ------------------------------------------------------------------ //
    //  JSON serialization via Gson                                        //
    // ------------------------------------------------------------------ //

    fun toJson(): String = Gson().toJson(this)

    companion object {
        fun fromJson(json: String): MahalanobisNormalizer = Gson().fromJson(json, MahalanobisNormalizer::class.java)
    }
}

// ====================================================================== //
//  Pure-Kotlin linear algebra helpers                                     //
// ====================================================================== //

/**
 * Column-wise mean of matrix [x] (n × d) → DoubleArray of length d.
 */
internal fun matMean(x: Array<DoubleArray>): DoubleArray {
    val n = x.size
    val d = x[0].size
    val mean = DoubleArray(d)
    for (row in x) for (j in 0 until d) mean[j] += row[j]
    for (j in 0 until d) mean[j] /= n
    return mean
}

/**
 * Column-wise population variance of [x] around the given [mean].
 */
internal fun matVariance(
    x: Array<DoubleArray>,
    mean: DoubleArray,
): DoubleArray {
    val n = x.size
    val d = mean.size
    val v = DoubleArray(d)
    for (row in x) for (j in 0 until d) {
        val diff = row[j] - mean[j]
        v[j] += diff * diff
    }
    for (j in 0 until d) v[j] /= n
    return v
}

/**
 * Sample covariance matrix (ddof = 1, matching `np.cov(rowvar=False)`).
 *
 * Returns a d × d matrix where `cov[i][j] = Σ (xₖᵢ - μᵢ)(xₖⱼ - μⱼ) / (n - 1)`.
 */
internal fun matCovariance(
    x: Array<DoubleArray>,
    mean: DoubleArray,
): Array<DoubleArray> {
    val n = x.size
    val d = mean.size
    val cov = Array(d) { DoubleArray(d) }

    for (row in x) {
        for (i in 0 until d) {
            val di = row[i] - mean[i]
            for (j in i until d) {
                val dj = row[j] - mean[j]
                cov[i][j] += di * dj
            }
        }
    }
    // Divide by (n - 1) and mirror
    val denom = (n - 1).toDouble()
    for (i in 0 until d) {
        for (j in i until d) {
            cov[i][j] /= denom
            if (j != i) cov[j][i] = cov[i][j]
        }
    }
    return cov
}

/**
 * Matrix inverse via Gauss-Jordan elimination with partial pivoting.
 *
 * @throws ArithmeticException if the matrix is singular.
 */
internal fun matInverse(matrix: Array<DoubleArray>): Array<DoubleArray> {
    val n = matrix.size
    // Augmented matrix [A | I]
    val aug =
        Array(n) { i ->
            DoubleArray(2 * n) { j ->
                if (j < n) {
                    matrix[i][j]
                } else if (j - n == i) {
                    1.0
                } else {
                    0.0
                }
            }
        }

    for (col in 0 until n) {
        // Partial pivoting: find the row with the largest absolute value in the column
        var maxRow = col
        var maxVal = kotlin.math.abs(aug[col][col])
        for (row in col + 1 until n) {
            val v = kotlin.math.abs(aug[row][col])
            if (v > maxVal) {
                maxVal = v
                maxRow = row
            }
        }
        if (maxVal < 1e-12) throw ArithmeticException("Singular matrix in matInverse")

        // Swap rows
        if (maxRow != col) {
            val tmp = aug[col]
            aug[col] = aug[maxRow]
            aug[maxRow] = tmp
        }

        // Scale pivot row
        val pivot = aug[col][col]
        for (j in 0 until 2 * n) aug[col][j] /= pivot

        // Eliminate column in all other rows
        for (row in 0 until n) {
            if (row == col) continue
            val factor = aug[row][col]
            for (j in 0 until 2 * n) aug[row][j] -= factor * aug[col][j]
        }
    }

    // Extract the right half (the inverse)
    return Array(n) { i -> DoubleArray(n) { j -> aug[i][j + n] } }
}

/**
 * Build a diagonal matrix from a vector.
 */
internal fun matDiag(v: DoubleArray): Array<DoubleArray> {
    val n = v.size
    return Array(n) { i -> DoubleArray(n) { j -> if (i == j) v[i] else 0.0 } }
}

/**
 * Matrix-vector multiplication: A (n×n) · v (n) → result (n).
 */
internal fun matVecMul(
    a: Array<DoubleArray>,
    v: DoubleArray,
): DoubleArray {
    val n = a.size
    val result = DoubleArray(n)
    for (i in 0 until n) {
        var s = 0.0
        for (j in v.indices) s += a[i][j] * v[j]
        result[i] = s
    }
    return result
}
