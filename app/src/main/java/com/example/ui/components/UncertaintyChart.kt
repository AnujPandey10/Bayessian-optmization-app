package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Experiment
import com.example.data.Factor
import com.example.ml.AcquisitionType
import com.example.ml.BayesianOptimizer
import com.example.ml.GaussianProcess
import com.example.ml.OptimizationGoal
import kotlin.math.max
import kotlin.math.min

@Composable
fun UncertaintyChart(
    gp: GaussianProcess,
    factors: List<Factor>,
    sweepFactorIdx: Int,
    otherFactorValues: Map<Int, Double>,
    experiments: List<Experiment>,
    targetResponseIdx: Int,
    targetGoal: String,
    acquisitionType: String,
    modifier: Modifier = Modifier
) {
    if (factors.isEmpty() || sweepFactorIdx >= factors.size) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text("Please configure factor parameters.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val sweepFactor = factors[sweepFactorIdx]
    val textMeasurer = rememberTextMeasurer()
    val axisStyle = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)

    // Parse valid historical coordinates
    val scatterPoints = remember(experiments, sweepFactorIdx, targetResponseIdx) {
        val scatter = ArrayList<Offset>()
        for (exp in experiments) {
            val yArr = com.example.data.Converters().stringToDoubleList(exp.responseValuesJson ?: "")
            if (yArr.isNotEmpty() && targetResponseIdx < yArr.size) {
                val xArr = com.example.data.Converters().stringToDoubleList(exp.factorValuesJson)
                if (sweepFactorIdx < xArr.size) {
                    scatter.add(Offset(xArr[sweepFactorIdx].toFloat(), yArr[targetResponseIdx].toFloat()))
                }
            }
        }
        scatter
    }

    val optGoal = try { OptimizationGoal.valueOf(targetGoal) } catch(e: Exception) { OptimizationGoal.MAXIMIZE }
    val acqType = try { AcquisitionType.valueOf(acquisitionType) } catch(e: Exception) { AcquisitionType.EI }

    val bestObservedY = remember(scatterPoints, optGoal) {
        if (scatterPoints.isNotEmpty()) {
            if (optGoal == OptimizationGoal.MAXIMIZE) scatterPoints.maxOf { it.y.toDouble() }
            else scatterPoints.minOf { it.y.toDouble() }
        } else {
            0.0
        }
    }

    // Generate 1D sweep points
    val resolution = 80
    val sweepPoints = remember(gp, sweepFactorIdx, otherFactorValues, targetResponseIdx, bestObservedY, optGoal, acqType) {
        val list = ArrayList<SweepPoint>()
        val range = sweepFactor.maxVal - sweepFactor.minVal
        val optimizer = BayesianOptimizer()

        for (i in 0 until resolution) {
            val pct = i.toDouble() / (resolution - 1)
            val currentVal = sweepFactor.minVal + pct * (if (range < 1e-9) 1.0 else range)

            // Formulate composite factor array
            val candidate = DoubleArray(factors.size)
            for (j in factors.indices) {
                candidate[j] = when (j) {
                    sweepFactorIdx -> currentVal
                    else -> otherFactorValues[j] ?: ((factors[j].maxVal + factors[j].minVal) / 2.0)
                }
            }

            // Predict with GPR
            val pred = gp.predict(candidate)
            val acqScore = optimizer.calculateAcquisition(pred.mean, pred.stdDev, bestObservedY, acqType, optGoal, 0.01 * gp.stdY)
            list.add(SweepPoint(currentVal, pred.mean, pred.stdDev, acqScore))
        }
        list
    }

    // Work out coordinate scales
    val maxYVal = remember(sweepPoints, scatterPoints) {
        val maxSweep = sweepPoints.map { it.mean + 1.96 * it.stdDev }.maxOrNull() ?: 1.0
        val maxScatter = scatterPoints.map { it.y.toDouble() }.maxOrNull() ?: 1.0
        max(maxSweep, maxScatter)
    }

    val minYVal = remember(sweepPoints, scatterPoints) {
        val minSweep = sweepPoints.map { it.mean - 1.96 * it.stdDev }.minOrNull() ?: 0.0
        val minScatter = scatterPoints.map { it.y.toDouble() }.minOrNull() ?: 0.0
        min(minSweep, minScatter)
    }

    val minAcq = sweepPoints.minOfOrNull { it.acq } ?: 0.0
    val maxAcq = sweepPoints.maxOfOrNull { it.acq } ?: 1.0
    val safeAcqRange = max(1e-9, maxAcq - minAcq)

    val yRange = maxYVal - minYVal
    val yPadding = if (yRange < 1e-9) 1.0 else yRange * 0.12
    val chartMinY = minYVal - yPadding
    val chartMaxY = maxYVal + yPadding

    Column(modifier = modifier) {
        Text(
            text = "Gaussian Process Kernel Fit (Mean and ±95% Confidence Interval)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            val leftMargin = 38.dp.toPx()
            val rightMargin = 16.dp.toPx()
            val topMargin = 16.dp.toPx()
            val bottomMargin = 28.dp.toPx()

            val chartW = size.width - leftMargin - rightMargin
            val chartH = size.height - topMargin - bottomMargin

            if (chartW <= 0 || chartH <= 0) return@Canvas

            // Map sweep X coordinate to Float position
            fun getXPos(valX: Double): Float {
                val span = sweepFactor.maxVal - sweepFactor.minVal
                val ratio = if (span < 1e-9) 0.5 else (valX - sweepFactor.minVal) / span
                return (leftMargin + ratio * chartW).toFloat()
            }

            // Map Y coordinate to position
            fun getYPos(valY: Double): Float {
                val span = chartMaxY - chartMinY
                val ratio = if (span < 1e-9) 0.5 else (valY - chartMinY) / span
                return (topMargin + chartH - ratio * chartH).toFloat()
            }

            // Map Acq coordinate to position overlay at bottom
            fun getAcqYPos(valAcq: Double): Float {
                val ratio = (valAcq - minAcq) / safeAcqRange
                val acqHeight = chartH * 0.35 // 35% height
                return (topMargin + chartH - ratio * acqHeight).toFloat()
            }

            // Y ticks & helper gridlines
            val yTicks = 4
            for (i in 0 until yTicks) {
                val pct = i.toDouble() / (yTicks - 1)
                val curY = chartMinY + pct * (chartMaxY - chartMinY)
                val yPos = getYPos(curY)

                drawLine(
                    color = Color.LightGray.copy(alpha = 0.4f),
                    start = Offset(leftMargin, yPos),
                    end = Offset(leftMargin + chartW, yPos),
                    strokeWidth = 1.dp.toPx()
                )

                drawText(
                    textMeasurer = textMeasurer,
                    text = String.format("%.1f", curY),
                    topLeft = Offset(leftMargin - 32.dp.toPx(), yPos - 6.dp.toPx()),
                    style = axisStyle
                )
            }

            // X ticks
            val xTicks = 5
            for (i in 0 until xTicks) {
                val pct = i.toDouble() / (xTicks - 1)
                val curX = sweepFactor.minVal + pct * (sweepFactor.maxVal - sweepFactor.minVal)
                val xPos = getXPos(curX)

                drawLine(
                    color = Color.DarkGray,
                    start = Offset(xPos, topMargin + chartH),
                    end = Offset(xPos, topMargin + chartH + 5.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx()
                )

                drawText(
                    textMeasurer = textMeasurer,
                    text = String.format("%.2f", curX),
                    topLeft = Offset(xPos - 12.dp.toPx(), topMargin + chartH + 7.dp.toPx()),
                    style = axisStyle
                )
            }

            // 1. Draw ±95% Shaded Confidence Interval Envelope (Gaussian bounds)
            val intervalPath = Path()
            intervalPath.moveTo(getXPos(sweepPoints[0].x), getYPos(sweepPoints[0].mean + 1.96 * sweepPoints[0].stdDev))
            for (i in 1 until sweepPoints.size) {
                intervalPath.lineTo(getXPos(sweepPoints[i].x), getYPos(sweepPoints[i].mean + 1.96 * sweepPoints[i].stdDev))
            }
            for (i in sweepPoints.size - 1 downTo 0) {
                intervalPath.lineTo(getXPos(sweepPoints[i].x), getYPos(sweepPoints[i].mean - 1.96 * sweepPoints[i].stdDev))
            }
            intervalPath.close()

            drawPath(
                path = intervalPath,
                color = Color(0x3000B4D8)
            )

            // 2. Draw Acquisition Landscape Fill (D3-style area chart)
            val acqPath = Path()
            acqPath.moveTo(getXPos(sweepPoints[0].x), topMargin + chartH)
            for (i in 0 until sweepPoints.size) {
                acqPath.lineTo(getXPos(sweepPoints[i].x), getAcqYPos(sweepPoints[i].acq))
            }
            acqPath.lineTo(getXPos(sweepPoints.last().x), topMargin + chartH)
            acqPath.close()

            drawPath(
                path = acqPath,
                color = Color(0x258B5CF6) // Translucent purple for acquisition
            )

            // Draw Acquisition line
            val acqLinePath = Path()
            acqLinePath.moveTo(getXPos(sweepPoints[0].x), getAcqYPos(sweepPoints[0].acq))
            for (i in 1 until sweepPoints.size) {
                acqLinePath.lineTo(getXPos(sweepPoints[i].x), getAcqYPos(sweepPoints[i].acq))
            }
            drawPath(
                path = acqLinePath,
                color = Color(0xFF8B5CF6),
                style = Stroke(width = 2.dp.toPx())
            )

            // 3. Draw Mean prediction Curve (Vivid cyan-indigo stroke)
            val meanPath = Path()
            meanPath.moveTo(getXPos(sweepPoints[0].x), getYPos(sweepPoints[0].mean))
            for (i in 1 until sweepPoints.size) {
                meanPath.lineTo(getXPos(sweepPoints[i].x), getYPos(sweepPoints[i].mean))
            }

            drawPath(
                path = meanPath,
                color = Color(0xFF0077B6),
                style = Stroke(width = 2.5.dp.toPx())
            )

            // 4. Draw Scatter Points of Historical/Completed runs
            for (pt in scatterPoints) {
                val xPos = getXPos(pt.x.toDouble())
                val yPos = getYPos(pt.y.toDouble())

                // Draw outer dark aura
                drawCircle(
                    color = Color(0xFF030712),
                    radius = 5.dp.toPx(),
                    center = Offset(xPos, yPos)
                )
                // Draw inner bright scientific scarlet dot
                drawCircle(
                    color = Color(0xFFEF4444),
                    radius = 3.5.dp.toPx(),
                    center = Offset(xPos, yPos)
                )
            }

            // Base border lines
            drawLine(
                color = Color.DarkGray,
                start = Offset(leftMargin, topMargin),
                end = Offset(leftMargin, topMargin + chartH),
                strokeWidth = 1.5.dp.toPx()
            )
            drawLine(
                color = Color.DarkGray,
                start = Offset(leftMargin, topMargin + chartH),
                end = Offset(leftMargin + chartW, topMargin + chartH),
                strokeWidth = 1.5.dp.toPx()
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(10.dp).background(Color(0xFFEF4444)))
            Text(
                "Observed runs",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, end = 12.dp)
            )

            Box(modifier = Modifier.size(10.dp).background(Color(0xFF0077B6)))
            Text(
                "GP Mean Fit",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, end = 12.dp)
            )

            Box(modifier = Modifier.size(14.dp, 8.dp).background(Color(0x3000B4D8)))
            Text(
                "±95% CI Uncertainty",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, end = 12.dp)
            )

            Box(modifier = Modifier.size(14.dp, 8.dp).background(Color(0x508B5CF6)))
            Text(
                "Acq Function",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

private data class SweepPoint(val x: Double, val mean: Double, val stdDev: Double, val acq: Double)
