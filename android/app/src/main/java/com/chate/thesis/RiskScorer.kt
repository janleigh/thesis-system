package com.chate.thesis

import kotlin.math.sqrt
import kotlin.math.max
import kotlin.math.min

// ====================================================================== //
//  Utility                                                                //
// ====================================================================== //

/** Clamp [v] into [lo, hi]. */
fun clip(v: Double, lo: Double, hi: Double): Double = v.coerceIn(lo, hi)

// ====================================================================== //
//  Action enum                                                            //
// ====================================================================== //

/**
 * Possible risk-based actions the system can take after evaluating a
 * keystroke sample.
 */
enum class Action(val displayName: String) {
    ALLOW("Allow"),
    WARN("Warn"),
    STEP_UP_AUTH("Step-Up Authentication"),
    BLOCK_SESSION("Block Session")
}

// ====================================================================== //
//  RiskDecision data class                                                //
// ====================================================================== //

/**
 * Immutable result of [RiskScorer.score].
 *
 * @property ocsvmScore     Raw OCSVM anomaly score [0, 1].
 * @property mahalScore     Normalised Mahalanobis score [0, 1].
 * @property compositeScore Weighted combination of the two scores.
 * @property action         Recommended action based on thresholds.
 * @property isAnomaly      True when the action is STEP_UP_AUTH or BLOCK_SESSION.
 * @property thresholdUsed  The adaptive warn threshold that was in effect.
 * @property keystrokeCount Number of keystrokes in the evaluated sample.
 */
data class RiskDecision(
    val ocsvmScore: Double,
    val mahalScore: Double,
    val compositeScore: Double,
    val action: Action,
    val isAnomaly: Boolean,
    val thresholdUsed: Double,
    val keystrokeCount: Int
)

// ====================================================================== //
//  RiskScorer                                                             //
// ====================================================================== //

/**
 * RiskScorer — fuses OCSVM and Mahalanobis scores into a single composite
 * risk score and maps it to an [Action].
 *
 * The warn threshold adapts over time using a rolling window of recent
 * composite scores (mean + k·std), clamped between [baseWarnThresh] and
 * [stepUpThresh] − 0.05.
 *
 * Ported from the Python `RiskScorer` class.
 */
class RiskScorer(
    ocsvmWeight: Double = 0.6,
    mahalWeight: Double = 0.4,
    private val baseWarnThresh: Double = 0.35,
    private val stepUpThresh: Double = 0.60,
    private val blockThresh: Double = 0.80,
    private val adaptiveWindow: Int = 20,
    private val adaptiveK: Double = 1.5
) {
    // Normalise weights so they sum to 1
    private val ocsvmW: Double
    private val mahalW: Double

    /** Rolling history of composite scores (bounded by [adaptiveWindow]). */
    private val scoreHistory: ArrayDeque<Double> = ArrayDeque()

    init {
        val total = ocsvmWeight + mahalWeight
        ocsvmW = ocsvmWeight / total
        mahalW = mahalWeight / total
    }

    // ------------------------------------------------------------------ //
    //  Public API                                                         //
    // ------------------------------------------------------------------ //

    /**
     * Compute a [RiskDecision] from the raw model outputs.
     *
     * @param ocsvmScore     Anomaly score from [OCSVMModel.anomalyScore].
     * @param mahalScore     Normalised score from [MahalanobisNormalizer.normalizedScore].
     * @param keystrokeCount Number of keystrokes in the sample (informational).
     */
    fun score(
        ocsvmScore: Double,
        mahalScore: Double,
        keystrokeCount: Int = 0
    ): RiskDecision {
        val clampedOcsvm = clip(ocsvmScore, 0.0, 1.0)
        val clampedMahal = clip(mahalScore, 0.0, 1.0)

        val composite = clip(
            ocsvmW * clampedOcsvm + mahalW * clampedMahal,
            0.0, 1.0
        )

        // Compute adaptive threshold *before* appending the new score
        val adaptiveThresh = adaptiveThreshold()

        // Append to history (cap at window size)
        scoreHistory.addLast(composite)
        if (scoreHistory.size > adaptiveWindow) {
            scoreHistory.removeFirst()
        }

        val action = mapAction(composite, adaptiveThresh)
        val isAnomaly = action == Action.STEP_UP_AUTH || action == Action.BLOCK_SESSION

        return RiskDecision(
            ocsvmScore = clampedOcsvm,
            mahalScore = clampedMahal,
            compositeScore = composite,
            action = action,
            isAnomaly = isAnomaly,
            thresholdUsed = adaptiveThresh,
            keystrokeCount = keystrokeCount
        )
    }

    /** Clear the rolling score history (e.g. on re-enrolment). */
    fun resetHistory() {
        scoreHistory.clear()
    }

    // ------------------------------------------------------------------ //
    //  Internal helpers                                                   //
    // ------------------------------------------------------------------ //

    /**
     * Adaptive warn threshold: mean + k·std of recent scores, clamped to
     * [baseWarnThresh, stepUpThresh − 0.05].  Falls back to [baseWarnThresh]
     * when fewer than 5 scores are available.
     */
    private fun adaptiveThreshold(): Double {
        if (scoreHistory.size < 5) return baseWarnThresh

        val hist = scoreHistory.toList()
        val n = hist.size
        val mean = hist.sum() / n

        var variance = 0.0
        for (v in hist) {
            val d = v - mean
            variance += d * d
        }
        // Population std (matching np.std default ddof=0)
        val std = sqrt(variance / n)

        val adaptive = mean + adaptiveK * std
        return clip(adaptive, baseWarnThresh, stepUpThresh - 0.05)
    }

    /**
     * Map a composite score to an [Action] based on the current thresholds.
     */
    private fun mapAction(score: Double, warnThresh: Double): Action {
        return when {
            score >= blockThresh   -> Action.BLOCK_SESSION
            score >= stepUpThresh  -> Action.STEP_UP_AUTH
            score >= warnThresh    -> Action.WARN
            else                   -> Action.ALLOW
        }
    }
}
