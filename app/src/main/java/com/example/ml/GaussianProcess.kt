package com.example.ml

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

enum class KernelType {
    RBF,
    MATERN_3_2,
    MATERN_5_2,
    RATIONAL_QUADRATIC,
    PERIODIC
}

class GaussianProcess(
    val kernelType: KernelType = KernelType.RBF,
    val lengthScale: Double = 1.0,
    val signalVariance: Double = 1.0,
    val noiseVariance: Double = 1e-4
) {
    // Scaled training data
    private var normalizedX: List<DoubleArray> = emptyList()
    private var normalizedY: DoubleArray = doubleArrayOf()

    // Scaling parameters
    private var meanX: DoubleArray = doubleArrayOf()
    private var stdX: DoubleArray = doubleArrayOf()
    private var meanY: Double = 0.0
    var stdY: Double = 1.0
        private set

    var isFit: Boolean = false
        private set

    // Fitting parameters
    private var L: Array<DoubleArray> = emptyArray() // Lower triangular Cholesky factor
    private var alpha: DoubleArray = doubleArrayOf() // L^T \alpha = L^-1 (y_norm - mean)

    private val jitter = 1e-6

    // Compute Kernel function between two points
    fun kernel(x1: DoubleArray, x2: DoubleArray): Double {
        var sqDist = 0.0
        for (i in x1.indices) {
            val diff = x1[i] - if (i < x2.size) x2[i] else 0.0
            sqDist += diff * diff
        }
        val d = sqrt(sqDist)

        return when (kernelType) {
            KernelType.RBF -> {
                signalVariance * signalVariance * exp(-sqDist / (2.0 * lengthScale * lengthScale))
            }
            KernelType.MATERN_3_2 -> {
                val term = sqrt(3.0) * d / lengthScale
                signalVariance * signalVariance * (1.0 + term) * exp(-term)
            }
            KernelType.MATERN_5_2 -> {
                val term = sqrt(5.0) * d / lengthScale
                signalVariance * signalVariance * (1.0 + term + (5.0 * sqDist) / (3.0 * lengthScale * lengthScale)) * exp(-term)
            }
            KernelType.RATIONAL_QUADRATIC -> {
                val alphaVal = 1.0 // typical default
                signalVariance * signalVariance * (1.0 + sqDist / (2.0 * alphaVal * lengthScale * lengthScale)).pow(-alphaVal)
            }
            KernelType.PERIODIC -> {
                val p = 1.0 // period length
                signalVariance * signalVariance * exp(-2.0 * kotlin.math.sin(Math.PI * d / p).pow(2) / (lengthScale * lengthScale))
            }
        }
    }

    // Fit the Gaussian Process model
    fun fit(X: List<DoubleArray>, y: DoubleArray): Boolean {
        isFit = false
        if (X.isEmpty() || y.isEmpty() || X.size != y.size) {
            return false
        }
        val n = X.size
        val d = X[0].size

        // Calculate Mean & Std for X
        meanX = DoubleArray(d)
        stdX = DoubleArray(d)
        for (j in 0 until d) {
            var sum = 0.0
            for (i in 0 until n) {
                sum += X[i][j]
            }
            meanX[j] = sum / n

            var varSum = 0.0
            for (i in 0 until n) {
                val diff = X[i][j] - meanX[j]
                varSum += diff * diff
            }
            val st = sqrt(varSum / n)
            stdX[j] = if (st < 1e-9) 1.0 else st
        }

        // Calculate Mean & Std for Y
        var sumY = 0.0
        for (i in 0 until n) {
            sumY += y[i]
        }
        meanY = sumY / n

        var varSumY = 0.0
        for (i in 0 until n) {
            val diff = y[i] - meanY
            varSumY += diff * diff
        }
        val sY = sqrt(varSumY / n)
        stdY = if (sY < 1e-9) 1.0 else sY

        // Normalize X and Y
        val normXList = ArrayList<DoubleArray>(n)
        for (i in 0 until n) {
            val row = DoubleArray(d)
            for (j in 0 until d) {
                row[j] = (X[i][j] - meanX[j]) / stdX[j]
            }
            normXList.add(row)
        }
        normalizedX = normXList

        val normYArray = DoubleArray(n)
        for (i in 0 until n) {
            normYArray[i] = (y[i] - meanY) / stdY
        }
        normalizedY = normYArray

        // Construct Covariance Matrix K
        val K = Array(n) { DoubleArray(n) }
        for (i in 0 until n) {
            for (j in 0 until n) {
                K[i][j] = kernel(normalizedX[i], normalizedX[j])
                if (i == j) {
                    K[i][j] += noiseVariance
                }
            }
        }

        // Perform Cholesky Decomposition K = L * L^T
        L = Array(n) { DoubleArray(n) }
        for (i in 0 until n) {
            for (j in 0..i) {
                var sum = K[i][j]
                for (k in 0 until j) {
                    sum -= L[i][k] * L[j][k]
                }
                if (i == j) {
                    val dVal = sum
                    L[i][j] = sqrt(max(jitter, dVal))
                } else {
                    L[i][j] = sum / L[j][j]
                }
            }
        }

        // Solve L * z = y_normalized
        val z = DoubleArray(n)
        for (i in 0 until n) {
            var sum = normalizedY[i]
            for (k in 0 until i) {
                sum -= L[i][k] * z[k]
            }
            z[i] = sum / L[i][i]
        }

        // Solve L^T * alpha = z
        alpha = DoubleArray(n)
        for (i in n - 1 downTo 0) {
            var sum = z[i]
            for (k in i + 1 until n) {
                sum -= L[k][i] * alpha[k] // Since L^T[i, k] = L[k, i]
            }
            alpha[i] = sum / L[i][i]
        }

        isFit = true
        return true
    }

    // Predict mean and standard deviation for a single inputs xStar (unnormalized)
    fun predict(xStar: DoubleArray): Prediction {
        if (normalizedX.isEmpty() || alpha.isEmpty()) {
            return Prediction(0.0, 1.0)
        }
        val d = meanX.size
        // Normalize test point
        val normXStar = DoubleArray(d)
        for (j in 0 until d) {
            normXStar[j] = (xStar[j] - meanX[j]) / stdX[j]
        }

        val n = normalizedX.size
        // Compute kStar vector
        val kStar = DoubleArray(n)
        for (i in 0 until n) {
            kStar[i] = kernel(normalizedX[i], normXStar)
        }

        // Compute predicted mean_normalized = kStar^T * alpha
        var meanNorm = 0.0
        for (i in 0 until n) {
            meanNorm += kStar[i] * alpha[i]
        }

        // To solve L * beta = kStar
        val beta = DoubleArray(n)
        for (i in 0 until n) {
            var sum = kStar[i]
            for (k in 0 until i) {
                sum -= L[i][k] * beta[k]
            }
            beta[i] = sum / L[i][i]
        }

        // Compute betaSum = ||beta||^2 = kStar^T * K^-1 * kStar
        var betaSumSq = 0.0
        for (i in 0 until n) {
            betaSumSq += beta[i] * beta[i]
        }

        // k(xStar, xStar)
        val kSelf = kernel(normXStar, normXStar) + noiseVariance

        // normalized variance = kSelf - betaSumSq
        val varNorm = max(0.0, kSelf - betaSumSq)
        val stdNorm = sqrt(varNorm)

        // Rescale mean and std
        val predMean = meanNorm * stdY + meanY
        val predStd = stdNorm * stdY

        return Prediction(predMean, predStd)
    }

    // High level metrics
    fun calculateMetrics(actualY: DoubleArray, predictedY: DoubleArray): GPMetrics {
        if (actualY.isEmpty() || actualY.size != predictedY.size) {
            return GPMetrics(0.0, 0.0, 0.0)
        }
        val n = actualY.size

        // MAE, RMSE
        var maeSum = 0.0
        var mseSum = 0.0
        var meanActual = 0.0
        for (i in 0 until n) {
            val diff = actualY[i] - predictedY[i]
            maeSum += kotlin.math.abs(diff)
            mseSum += diff * diff
            meanActual += actualY[i]
        }
        meanActual /= n

        val mae = maeSum / n
        val rmse = sqrt(mseSum / n)

        // R^2
        var ssTot = 0.0
        var ssRes = 0.0
        for (i in 0 until n) {
            val diffActualMean = actualY[i] - meanActual
            val diffRes = actualY[i] - predictedY[i]
            ssTot += diffActualMean * diffActualMean
            ssRes += diffRes * diffRes
        }

        val r2 = if (ssTot < 1e-9) 1.0 else 1.0 - (ssRes / ssTot)

        return GPMetrics(r2, rmse, mae)
    }
}

data class Prediction(val mean: Double, val stdDev: Double)
data class GPMetrics(val r2: Double, val rmse: Double, val mae: Double)
