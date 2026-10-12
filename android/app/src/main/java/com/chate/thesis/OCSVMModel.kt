package com.chate.thesis

import com.google.gson.Gson
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs

// ====================================================================== //
//  Default hyper-parameters                                               //
// ====================================================================== //
private const val DEFAULT_NU = 0.1
private const val DEFAULT_GAMMA_SCALE = "scale"

/**
 * OCSVMModel — a pure-Kotlin One-Class SVM using an RBF kernel.
 *
 * Ported from the Python OCSVMModel. Training uses a simplified SMO
 * (Sequential Minimal Optimisation) procedure suitable for small datasets
 * (5–50 vectors, 6 dimensions).
 *
 * The model stores everything needed for inference and can be serialised to
 * / deserialised from JSON via Gson.
 */
class OCSVMModel(
    private val nu: Double = DEFAULT_NU,
    private val gammaMode: String = DEFAULT_GAMMA_SCALE
) {
    // -- Trained model state ------------------------------------------------
    var isFitted: Boolean = false
        private set

    private var supportVectors: Array<DoubleArray> = emptyArray()
    private var dualCoefficients: DoubleArray = doubleArrayOf()
    private var intercept: Double = 0.0               // ρ (rho)
    private var gamma: Double = 0.0

    // StandardScaler parameters
    private var scalerMean: DoubleArray = doubleArrayOf()
    private var scalerStd: DoubleArray = doubleArrayOf()

    // Score normalisation
    private var trainScoreMin: Double = 0.0
    private var trainScoreMax: Double = 0.0

    // ====================================================================== //
    //  StandardScaler (inner helper)                                          //
    // ====================================================================== //

    /**
     * StandardScaler — zero-mean, unit-variance feature scaling.
     *
     * Uses population standard deviation (ddof = 0) to match
     * sklearn's `StandardScaler`.
     */
    private inner class StandardScaler {
        var mean: DoubleArray = doubleArrayOf()
        var std: DoubleArray = doubleArrayOf()

        /**
         * Compute per-feature mean and population std, then return the
         * scaled copy of [x].
         */
        fun fitTransform(x: Array<DoubleArray>): Array<DoubleArray> {
            val n = x.size
            val d = x[0].size
            mean = DoubleArray(d)
            std = DoubleArray(d)

            // Mean
            for (row in x) for (j in 0 until d) mean[j] += row[j]
            for (j in 0 until d) mean[j] /= n

            // Variance (population, ddof=0)
            for (row in x) for (j in 0 until d) {
                val diff = row[j] - mean[j]
                std[j] += diff * diff
            }
            for (j in 0 until d) {
                std[j] = sqrt(std[j] / n)
                if (std[j] < 1e-12) std[j] = 1.0   // avoid division by zero
            }

            return transform(x)
        }

        /** Scale a matrix using already-fitted mean/std. */
        fun transform(x: Array<DoubleArray>): Array<DoubleArray> =
            Array(x.size) { i ->
                DoubleArray(x[i].size) { j -> (x[i][j] - mean[j]) / std[j] }
            }

        /** Scale a single vector using already-fitted mean/std. */
        fun transform(x: DoubleArray): DoubleArray =
            DoubleArray(x.size) { j -> (x[j] - mean[j]) / std[j] }
    }

    // ====================================================================== //
    //  RBF Kernel                                                             //
    // ====================================================================== //

    /** RBF (Gaussian) kernel: K(a, b) = exp(-γ · ||a - b||²) */
    private fun rbfKernel(a: DoubleArray, b: DoubleArray, g: Double): Double {
        var sq = 0.0
        for (i in a.indices) {
            val d = a[i] - b[i]
            sq += d * d
        }
        return exp(-g * sq)
    }

    // ====================================================================== //
    //  Training (Simplified SMO)                                              //
    // ====================================================================== //

    /**
     * Train the One-Class SVM on [vectors] (list of raw feature arrays).
     *
     * Steps:
     * 1. Scale features (StandardScaler).
     * 2. Compute gamma ('scale' → 1 / (nFeatures * totalVariance)).
     * 3. Build kernel matrix.
     * 4. Run simplified SMO to find dual variables α.
     * 5. Extract support vectors, dual coefficients, and ρ.
     * 6. Record min/max training decision scores for normalisation.
     */
    fun train(vectors: List<DoubleArray>) {
        require(vectors.size >= 2) { "Need at least 2 training vectors" }

        val n = vectors.size
        val d = vectors[0].size

        // 1. Scale
        val scaler = StandardScaler()
        val xScaled = scaler.fitTransform(Array(n) { vectors[it].copyOf() })
        scalerMean = scaler.mean
        scalerStd = scaler.std

        // 2. Gamma
        gamma = if (gammaMode == DEFAULT_GAMMA_SCALE) {
            // For small enrollment samples (10-25 vectors in 6D), gamma = 1 / (2*d) prevents
            // isolated RBF support vector spikes while maintaining a distinct decision boundary.
            1.0 / (2.0 * d)
        } else {
            gammaMode.toDoubleOrNull() ?: (1.0 / (2.0 * d))
        }

        // 3. Kernel matrix (symmetric)
        val K = Array(n) { DoubleArray(n) }
        for (i in 0 until n) {
            K[i][i] = 1.0   // K(x,x) = 1 for RBF
            for (j in i + 1 until n) {
                val v = rbfKernel(xScaled[i], xScaled[j], gamma)
                K[i][j] = v
                K[j][i] = v
            }
        }

        // 4. Simplified SMO
        //    Objective: min 0.5 αᵀKα,  s.t. 0 ≤ αᵢ ≤ C,  Σαᵢ = 1
        //    where C = 1 / (n * ν)
        val C = 1.0 / (n * nu)
        val alpha = DoubleArray(n) { 1.0 / n }   // uniform initialisation (satisfies Σα=1)

        // Gradient: g_i = ∂/∂α_i (0.5 αᵀKα) = Σ_j K_ij α_j
        val grad = DoubleArray(n)
        for (i in 0 until n) {
            var s = 0.0
            for (j in 0 until n) s += K[i][j] * alpha[j]
            grad[i] = s
        }

        val maxIter = 1000 * n
        val tol = 1e-6

        for (iter in 0 until maxIter) {
            // Find most violating pair (i = argmin grad among α < C, j = argmax grad among α > 0)
            var iUp = -1; var minGrad = Double.MAX_VALUE
            var jLow = -1; var maxGrad = -Double.MAX_VALUE

            for (k in 0 until n) {
                if (alpha[k] < C - tol && grad[k] < minGrad) { minGrad = grad[k]; iUp = k }
                if (alpha[k] > tol && grad[k] > maxGrad) { maxGrad = grad[k]; jLow = k }
            }

            if (iUp == -1 || jLow == -1 || maxGrad - minGrad < tol) break   // converged

            val i = iUp
            val j = jLow

            // Analytic update
            val kii = K[i][i]; val kjj = K[j][j]; val kij = K[i][j]
            val denom = kii + kjj - 2.0 * kij
            if (denom < 1e-12) continue   // skip degenerate pair

            val delta = (grad[j] - grad[i]) / denom

            // Clip so that both αᵢ and αⱼ stay in [0, C] and Σα is preserved
            val maxDelta = min(C - alpha[i], alpha[j])
            val clippedDelta = max(0.0, min(delta, maxDelta))
            if (clippedDelta < 1e-12) continue

            alpha[i] += clippedDelta
            alpha[j] -= clippedDelta

            // Update gradients
            for (k in 0 until n) {
                grad[k] += clippedDelta * (K[i][k] - K[j][k])
            }
        }

        // 5. Extract support vectors (α > tolerance)
        val svTol = 1e-7
        val svIndices = (0 until n).filter { alpha[it] > svTol }

        supportVectors = Array(svIndices.size) { xScaled[svIndices[it]].copyOf() }
        dualCoefficients = DoubleArray(svIndices.size) { alpha[svIndices[it]] }

        // ρ = mean of decision values over support vectors on the margin
        // For SVs with 0 < α < C, f(x) should equal ρ.  We average decision
        // values of *all* SVs for robustness on tiny datasets.
        var rhoSum = 0.0
        for (idx in svIndices) {
            var fVal = 0.0
            for (k in 0 until n) fVal += alpha[k] * K[k][idx]
            rhoSum += fVal
        }
        intercept = rhoSum / svIndices.size

        // 6. Training scores for normalisation
        val trainScores = DoubleArray(n) { idx ->
            decisionFunctionInternal(xScaled[idx])
        }
        trainScoreMin = trainScores.min()
        trainScoreMax = trainScores.max()

        isFitted = true
    }

    // ====================================================================== //
    //  Inference                                                              //
    // ====================================================================== //

    /**
     * Raw decision function value: f(x) = Σ αᵢ · K(svᵢ, x) − ρ
     */
    private fun decisionFunctionInternal(xScaled: DoubleArray): Double {
        var sum = 0.0
        for (i in supportVectors.indices) {
            sum += dualCoefficients[i] * rbfKernel(supportVectors[i], xScaled, gamma)
        }
        return sum - intercept
    }

    /**
     * Decision function on a raw (unscaled) input vector.
     */
    fun decisionFunction(vector: DoubleArray): Double {
        check(isFitted) { "Model is not fitted" }
        val xScaled = DoubleArray(vector.size) { j -> (vector[j] - scalerMean[j]) / scalerStd[j] }
        return decisionFunctionInternal(xScaled)
    }

    /**
     * Anomaly score in [0, 1].
     *
     * The score is normalised such that the maximum training decision value maps to 0.0,
     * the decision boundary (0.0) maps to 0.5, and negative values scale smoothly to 1.0.
     */
    fun anomalyScore(vector: DoubleArray): Double {
        check(isFitted) { "Model is not fitted" }
        val xScaled = DoubleArray(vector.size) { j -> (vector[j] - scalerMean[j]) / scalerStd[j] }
        val rawScore = decisionFunctionInternal(xScaled)

        return if (rawScore >= 0) {
            val scale = kotlin.math.max(trainScoreMax, 1e-9)
            0.28 * (1.0 - (rawScore / scale).coerceIn(0.0, 1.0))
        } else {
            val scale = kotlin.math.max(intercept, 1e-9)
            0.28 + 0.72 * (-rawScore / scale).coerceIn(0.0, 1.0)
        }
    }

    /**
     * Predict: +1 = inlier, -1 = outlier.
     */
    fun predict(vector: DoubleArray): Int {
        return if (decisionFunction(vector) >= 0.0) 1 else -1
    }

    // ====================================================================== //
    //  JSON Serialization via Gson                                            //
    // ====================================================================== //

    /** Serialisable snapshot of the trained model. */
    private data class ModelSnapshot(
        val supportVectors: Array<DoubleArray>,
        val dualCoefficients: DoubleArray,
        val intercept: Double,
        val gamma: Double,
        val nu: Double,
        val gammaMode: String,
        val scalerMean: DoubleArray,
        val scalerStd: DoubleArray,
        val trainScoreMin: Double,
        val trainScoreMax: Double
    )

    fun toJson(): String {
        val snapshot = ModelSnapshot(
            supportVectors = supportVectors,
            dualCoefficients = dualCoefficients,
            intercept = intercept,
            gamma = gamma,
            nu = nu,
            gammaMode = gammaMode,
            scalerMean = scalerMean,
            scalerStd = scalerStd,
            trainScoreMin = trainScoreMin,
            trainScoreMax = trainScoreMax
        )
        return Gson().toJson(snapshot)
    }

    companion object {
        fun fromJson(json: String): OCSVMModel {
            val snapshot = Gson().fromJson(json, ModelSnapshot::class.java)
            return OCSVMModel(nu = snapshot.nu, gammaMode = snapshot.gammaMode).apply {
                supportVectors = snapshot.supportVectors
                dualCoefficients = snapshot.dualCoefficients
                intercept = snapshot.intercept
                gamma = snapshot.gamma
                scalerMean = snapshot.scalerMean
                scalerStd = snapshot.scalerStd
                trainScoreMin = snapshot.trainScoreMin
                trainScoreMax = snapshot.trainScoreMax
                isFitted = true
            }
        }
    }
}
