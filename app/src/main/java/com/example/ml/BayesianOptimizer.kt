package com.example.ml

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sqrt

enum class AcquisitionType {
    EI,  // Expected Improvement
    UCB, // Upper Confidence Bound
    PI   // Probability of Improvement
}

enum class OptimizationGoal {
    MAXIMIZE,
    MINIMIZE
}

class BayesianOptimizer {

    // PDF of standard Normal distribution
    fun pdf(z: Double): Double {
        return exp(-0.5 * z * z) / sqrt(2.0 * PI)
    }

    // CDF of standard Normal distribution
    fun cdf(z: Double): Double {
        if (z < -8.0) return 0.0
        if (z > 8.0) return 1.0

        val absZ = kotlin.math.abs(z)
        val p = 0.2316419
        val b1 = 0.319381530
        val b2 = -0.356563782
        val b3 = 1.781477937
        val b4 = -1.821255978
        val b5 = 1.330274429

        val t = 1.0 / (1.0 + p * absZ)
        val poly = b1 * t + b2 * t.pow(2) + b3 * t.pow(3) + b4 * t.pow(4) + b5 * t.pow(5)
        val value = 1.0 - pdf(absZ) * poly

        return if (z >= 0) value else 1.0 - value
    }

    // Calculate acquisition score for a given prediction
    fun calculateAcquisition(
        mean: Double,
        stdDev: Double,
        bestObservedY: Double,
        acquisitionType: AcquisitionType,
        goal: OptimizationGoal,
        xi: Double = 0.01,
        kappa: Double = 2.0
    ): Double {
        val s = max(1e-9, stdDev)

        return when (goal) {
            OptimizationGoal.MAXIMIZE -> {
                when (acquisitionType) {
                    AcquisitionType.PI -> {
                        val z = (mean - bestObservedY - xi) / s
                        cdf(z)
                    }
                    AcquisitionType.EI -> {
                        val z = (mean - bestObservedY - xi) / s
                        (mean - bestObservedY - xi) * cdf(z) + s * pdf(z)
                    }
                    AcquisitionType.UCB -> {
                        mean + kappa * s
                    }
                }
            }
            OptimizationGoal.MINIMIZE -> {
                when (acquisitionType) {
                    AcquisitionType.PI -> {
                        val z = (bestObservedY - mean - xi) / s
                        cdf(z)
                    }
                    AcquisitionType.EI -> {
                        val z = (bestObservedY - mean - xi) / s
                        (bestObservedY - mean - xi) * cdf(z) + s * pdf(z)
                    }
                    AcquisitionType.UCB -> {
                        // For minimization, we want low mean.
                        // So a high score corresponds to a low value and/or high exploration (uncertainty)
                        // Score = - (mean - kappa * s) = -mean + kappa * s
                        -mean + kappa * s
                    }
                }
            }
        }
    }

    // Structures to define experiment factors
    data class FactorBounds(
        val name: String,
        val minVal: Double,
        val maxVal: Double
    )

    data class Recommendation(
        val values: DoubleArray,
        val predictedMean: Double,
        val predictedStdDev: Double,
        val acquisitionScore: Double,
        val explanation: String
    )

    // Suggest top recommendations using Monte Carlo grid search over parameter space
    fun suggestNext(
        gp: GaussianProcess,
        factors: List<FactorBounds>,
        historicalX: List<DoubleArray>,
        historicalY: DoubleArray,
        acquisitionType: AcquisitionType,
        goal: OptimizationGoal,
        count: Int = 10,
        xi: Double = 0.01,
        kappa: Double = 2.0
    ): List<Recommendation> {
        if (factors.isEmpty() || historicalY.isEmpty() || !gp.isFit) {
            // Either model is not trained/fitted or parameters empty
            // Create random samples within bounds
            val random = java.util.Random()
            val initialRecs = ArrayList<Recommendation>()
            for (i in 0 until count) {
                val candidate = DoubleArray(factors.size) { idx ->
                    val bounds = factors[idx]
                    bounds.minVal + random.nextDouble() * (bounds.maxVal - bounds.minVal)
                }
                initialRecs.add(
                    Recommendation(
                        values = candidate,
                        predictedMean = 0.0,
                        predictedStdDev = 1.0,
                        acquisitionScore = 0.0,
                        explanation = "Initial explorative run. Randomly sampled to cover parameter space evenly."
                    )
                )
            }
            return initialRecs
        }

        // Get the best observed Y value
        val bestObserved = if (goal == OptimizationGoal.MAXIMIZE) {
            historicalY.maxOrNull() ?: 0.0
        } else {
            historicalY.minOrNull() ?: 0.0
        }

        // Standard sample candidates (Random Search / Monte Carlo)
        val numCandidates = 4000
        val candidatesList = ArrayList<DoubleArray>(numCandidates)
        val random = java.util.Random()
        
        // Dynamically scale xi based on the target variable's standard deviation
        val scaledXi = xi * gp.stdY

        for (i in 0 until numCandidates) {
            val candidate = DoubleArray(factors.size) { idx ->
                val bounds = factors[idx]
                bounds.minVal + random.nextDouble() * (bounds.maxVal - bounds.minVal)
            }
            candidatesList.add(candidate)
        }

        // Core score calculations
        val scoresList = ArrayList<Recommendation>()
        for (candidate in candidatesList) {
            // Predict
            val pred = gp.predict(candidate)

            // Dynamic explainability rules
            val score = calculateAcquisition(pred.mean, pred.stdDev, bestObserved, acquisitionType, goal, scaledXi, kappa)

            // Measure minimum Euclidean distance to run experiments in normalized scale
            var minDistance = Double.MAX_VALUE
            for (hist in historicalX) {
                var sumDistSq = 0.0
                for (j in factors.indices) {
                    val range = factors[j].maxVal - factors[j].minVal
                    val den = if (range < 1e-9) 1.0 else range
                    val valDiff = (candidate[j] - hist[j]) / den
                    sumDistSq += valDiff * valDiff
                }
                val dist = sqrt(sumDistSq)
                if (dist < minDistance) {
                    minDistance = dist
                }
            }

            // Diversity penalty check to avoid redundant/over-sampled space
            val adjustedScore = if (minDistance < 0.05) {
                // Heavily penalize close recommendations to enforce new exploration
                val penaltyRatio = minDistance / 0.05
                if (score > 0) {
                    score * penaltyRatio
                } else {
                    // For negative scores, we shift them downwards proportional to closeness
                    score - (1.0 - penaltyRatio) * kotlin.math.max(1.0, kotlin.math.abs(score))
                }
            } else {
                score
            }

            // Build explanation narrative
            val explanation = when {
                minDistance < 0.05 -> {
                    "Highly penalized because it is extremely close to an already completed experiment of similar parameters."
                }
                pred.stdDev > 0.4 -> {
                    "Selected for HIGH EXPLORATION value. This region has high uncertainty (±${String.format("%.2f", pred.stdDev * 1.96)}), which helps the model fill critical gaps in knowledge."
                }
                goal == OptimizationGoal.MAXIMIZE && pred.mean > bestObserved -> {
                    "Selected for high PREDECTIED EXCELLENCE. This condition is forecasted to yield ${String.format("%.2f", pred.mean)}, exceeding our current best result of ${String.format("%.2f", bestObserved)}."
                }
                goal == OptimizationGoal.MINIMIZE && pred.mean < bestObserved -> {
                    "Selected for high MINIMIZATION performance. This condition is forecasted to yield ${String.format("%.2f", pred.mean)}, which is lower than our current best limit of ${String.format("%.2f", bestObserved)}."
                }
                else -> {
                    "Selected because of a balanced trade-off between predicted target outcome (${String.format("%.1f", pred.mean)}) and localized exploration value."
                }
            }

            scoresList.add(
                Recommendation(
                    values = candidate,
                    predictedMean = pred.mean,
                    predictedStdDev = pred.stdDev,
                    acquisitionScore = adjustedScore,
                    explanation = explanation
                )
            )
        }

        // Sort descending by acquisitionScore
        scoresList.sortByDescending { it.acquisitionScore }

        // Take top configurations
        return scoresList.take(count)
    }
}
