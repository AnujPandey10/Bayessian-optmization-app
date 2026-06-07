package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Factor
import com.example.ml.GaussianProcess
import kotlin.math.abs

@Composable
fun ResponseSurfaceCanvas(
    gp: GaussianProcess,
    factors: List<Factor>,
    xAxisIdx: Int,
    yAxisIdx: Int,
    otherFactorValues: Map<Int, Double>,
    drawUncertainty: Boolean,
    modifier: Modifier = Modifier
) {
    if (factors.size < 2 || xAxisIdx >= factors.size || yAxisIdx >= factors.size || xAxisIdx == yAxisIdx) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Please select two distinct factors to view the response surface.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val xFactor = factors[xAxisIdx]
    val yFactor = factors[yAxisIdx]

    val textMeasurer = rememberTextMeasurer()
    val axisLabelStyle = TextStyle(
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 11.sp
    )

    // Calculate prediction grid
    val gridSize = 45
    val gridValues = remember(gp, xAxisIdx, yAxisIdx, otherFactorValues, drawUncertainty) {
        val mat = Array(gridSize) { DoubleArray(gridSize) }
        var maxVal = Double.NEGATIVE_INFINITY
        var minVal = Double.POSITIVE_INFINITY

        val xRange = xFactor.maxVal - xFactor.minVal
        val yRange = yFactor.maxVal - yFactor.minVal

        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                // Determine raw factor coordinates
                val pctX = c.toDouble() / (gridSize - 1)
                val pctY = (gridSize - 1 - r).toDouble() / (gridSize - 1) // Invert Y axis for standard cartesian coordinate plot

                val xVal = xFactor.minVal + pctX * (if (xRange < 1e-9) 1.0 else xRange)
                val yVal = yFactor.minVal + pctY * (if (yRange < 1e-9) 1.0 else yRange)

                // Fill custom factor array
                val candidateValues = DoubleArray(factors.size)
                for (i in factors.indices) {
                    candidateValues[i] = when (i) {
                        xAxisIdx -> xVal
                        yAxisIdx -> yVal
                        else -> otherFactorValues[i] ?: ((factors[i].maxVal + factors[i].minVal) / 2.0)
                    }
                }

                // Predict GP
                val pred = gp.predict(candidateValues)
                val valToPlot = if (drawUncertainty) pred.stdDev else pred.mean
                mat[r][c] = valToPlot

                if (valToPlot > maxVal) maxVal = valToPlot
                if (valToPlot < minVal) minVal = valToPlot
            }
        }
        PredictionGrid(mat, minVal, maxVal)
    }

    // Modern Industrial Scientific Color Palette Map (Sci-Colormap)
    fun getScientificColor(value: Double, min: Double, max: Double): Color {
        val den = max - min
        val norm = if (abs(den) < 1e-9) 0.5 else (value - min) / den

        return if (drawUncertainty) {
            // High uncertainty: Warm golden amber / red (indicating high exploration value)
            // Low uncertainty: Deep cool Indigo (high confidence)
            val r = (norm * 240).toInt().coerceIn(0, 255)
            val g = (norm * 140).toInt().coerceIn(0, 255)
            val b = (255 - (norm * 180)).toInt().coerceIn(0, 255)
            Color(r, g, b)
        } else {
            // Temperature Teal-to-Teal-to-Yellow-Viridis Colormap representing optimum regions
            if (norm < 0.25) {
                // Deep Navy Blue to Indigo
                val sub = norm / 0.25
                Color(
                    red = (13 * (1.0 - sub) + 26 * sub).toInt(),
                    green = (27 * (1.0 - sub) + 84 * sub).toInt(),
                    blue = (42 * (1.0 - sub) + 160 * sub).toInt()
                )
            } else if (norm < 0.7) {
                // Indigo/Teal to Turquoise
                val sub = (norm - 0.25) / 0.45
                Color(
                    red = (26 * (1.0 - sub) + 0 * sub).toInt(),
                    green = (84 * (1.0 - sub) + 150 * sub).toInt(),
                    blue = (160 * (1.0 - sub) + 180 * sub).toInt()
                )
            } else {
                // Turquoise to Vivid Gold
                val sub = (norm - 0.7) / 0.3
                Color(
                    red = (0 * (1.0 - sub) + 241 * sub).toInt(),
                    green = (150 * (1.0 - sub) + 196 * sub).toInt(),
                    blue = (180 * (1.0 - sub) + 15 * sub).toInt()
                )
            }
        }
    }

    Column(modifier = modifier) {
        Text(
            text = if (drawUncertainty) {
                "Model Uncertainty (Standard Deviation): ${xFactor.name} vs ${yFactor.name}"
            } else {
                "Predicted Surface Value (Mean): ${xFactor.name} vs ${yFactor.name}"
            },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Y-Axis label (pH units, C degree, etc.)
            Box(
                modifier = Modifier
                    .width(28.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${yFactor.name} (${yFactor.unit})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.offset(x = (-4).dp)
                )
            }

            // Canvas drawing
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color.White)
            ) {
                val horizontalMargin = 30.dp.toPx()
                val verticalMargin = 30.dp.toPx()

                val drawWidth = size.width - 2 * horizontalMargin
                val drawHeight = size.height - 2 * verticalMargin

                if (drawWidth <= 0 || drawHeight <= 0) return@Canvas

                val cellW = drawWidth / gridSize
                val cellH = drawHeight / gridSize

                // 1. Draw Surface grid mesh cells
                for (r in 0 until gridSize) {
                    for (c in 0 until gridSize) {
                        val valAt = gridValues.matrix[r][c]
                        val color = getScientificColor(valAt, gridValues.min, gridValues.max)

                        drawRect(
                            color = color,
                            topLeft = Offset(
                                horizontalMargin + c * cellW,
                                verticalMargin + r * cellH
                            ),
                            size = Size(cellW + 0.5f, cellH + 0.5f) // overlapping fraction to avoid gaps
                        )
                    }
                }

                // 2. Draw border lines
                drawRect(
                    color = Color.DarkGray,
                    topLeft = Offset(horizontalMargin, verticalMargin),
                    size = Size(drawWidth, drawHeight),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                )

                // 3. Draw Axis tick marks and numerals
                // X Axis ticks
                val xTicks = 5
                for (i in 0 until xTicks) {
                    val pct = i.toDouble() / (xTicks - 1)
                    val xVal = xFactor.minVal + pct * (xFactor.maxVal - xFactor.minVal)
                    val xPos = (horizontalMargin + pct * drawWidth).toFloat()

                    drawLine(
                        color = Color.DarkGray,
                        start = Offset(xPos, verticalMargin + drawHeight),
                        end = Offset(xPos, verticalMargin + drawHeight + 6.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )

                    drawText(
                        textMeasurer = textMeasurer,
                        text = String.format("%.2f", xVal),
                        topLeft = Offset(xPos - 12.dp.toPx(), verticalMargin + drawHeight + 8.dp.toPx()),
                        style = axisLabelStyle
                    )
                }

                // Y Axis ticks
                val yTicks = 5
                for (i in 0 until yTicks) {
                    val pct = i.toDouble() / (yTicks - 1)
                    val yVal = yFactor.minVal + pct * (yFactor.maxVal - yFactor.minVal)
                    // Canvas Y-axis is inverted
                    val yPos = (verticalMargin + drawHeight - pct * drawHeight).toFloat()

                    drawLine(
                        color = Color.DarkGray,
                        start = Offset(horizontalMargin - 6.dp.toPx(), yPos),
                        end = Offset(horizontalMargin, yPos),
                        strokeWidth = 2.dp.toPx()
                    )

                    drawText(
                        textMeasurer = textMeasurer,
                        text = String.format("%.2f", yVal),
                        topLeft = Offset(horizontalMargin - 32.dp.toPx(), yPos - 6.dp.toPx()),
                        style = axisLabelStyle
                    )
                }
            }

            // Continuous Scientific Color-Legend Bar
            Column(
                modifier = Modifier
                    .width(55.dp)
                    .fillMaxHeight()
                    .padding(start = 8.dp, top = 28.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = String.format("%.1f", gridValues.max),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Gradient box
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .width(14.dp)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = if (drawUncertainty) {
                                    listOf(Color(240, 140, 75), Color(120, 70, 165), Color(0, 0, 75))
                                } else {
                                    listOf(Color(241, 196, 15), Color(0, 150, 180), Color(13, 27, 42))
                                }
                            )
                        )
                )

                Text(
                    text = String.format("%.1f", gridValues.min),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // X-Axis Title label
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${xFactor.name} (${xFactor.unit})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class PredictionGrid(
    val matrix: Array<DoubleArray>,
    val min: Double,
    val max: Double
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PredictionGrid) return false
        if (!matrix.contentDeepEquals(other.matrix)) return false
        if (min != other.min) return false
        if (max != other.max) return false
        return true
    }

    override fun hashCode(): Int {
        var result = matrix.contentDeepHashCode()
        result = 31 * result + min.hashCode()
        result = 31 * result + max.hashCode()
        return result
    }
}
