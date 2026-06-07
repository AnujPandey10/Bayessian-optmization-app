package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import kotlin.math.max
import kotlin.math.min

@Composable
fun OptimizationProgressChart(
    experiments: List<Experiment>,
    targetResponseIdx: Int,
    goal: String, // "MAXIMIZE" or "MINIMIZE"
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val axisLabelStyle = TextStyle(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp
    )

    // Parse valid evaluated actual experiments
    val validExps = experiments.filter { exp ->
        val yVals = com.example.data.Converters().stringToDoubleList(exp.responseValuesJson ?: "")
        yVals.isNotEmpty() && targetResponseIdx < yVals.size
    }

    if (validExps.size < 2) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Accumulate at least 2 experiment runs to populate progress metrics.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }

    // Extract actual target responses
    val responses = validExps.map { exp ->
        com.example.data.Converters().stringToDoubleList(exp.responseValuesJson ?: "")[targetResponseIdx]
    }

    // Calculate rolling best (optimal run achieved so far per iteration)
    val runningBest = ArrayList<Double>()
    var currentBest = responses[0]
    for (res in responses) {
        currentBest = if (goal == "MAXIMIZE") {
            max(currentBest, res)
        } else {
            min(currentBest, res)
        }
        runningBest.add(currentBest)
    }

    val maxResponse = max(responses.maxOrNull() ?: 1.0, runningBest.maxOrNull() ?: 1.0)
    val minResponse = min(responses.minOrNull() ?: 0.0, runningBest.minOrNull() ?: 0.0)
    val responseRange = maxResponse - minResponse
    val scalePadding = if (responseRange < 1e-9) 1.0 else responseRange * 0.15

    val chartMinY = minResponse - scalePadding
    val chartMaxY = maxResponse + scalePadding

    Column(modifier = modifier) {
        Text(
            text = "Optimization Progress Curve (Best vs. Actual Yield)",
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

            // Grid lines and ticks
            val numTicksY = 4
            for (i in 0 until numTicksY) {
                val pct = i.toDouble() / (numTicksY - 1)
                val yVal = chartMinY + pct * (chartMaxY - chartMinY)
                val yPos = (topMargin + chartH - pct * chartH).toFloat()

                // Grid line
                drawLine(
                    color = Color.LightGray.copy(alpha = 0.5f),
                    start = Offset(leftMargin, yPos),
                    end = Offset(leftMargin + chartW, yPos),
                    strokeWidth = 1.dp.toPx()
                )

                // Label
                drawText(
                    textMeasurer = textMeasurer,
                    text = String.format("%.1f", yVal),
                    topLeft = Offset(leftMargin - 32.dp.toPx(), yPos - 6.dp.toPx()),
                    style = axisLabelStyle
                )
            }

            // X coordinates generator mapping iteration indices to positions
            fun getXPos(index: Int): Float {
                val denom = if (responses.size <= 1) 1f else (responses.size - 1).toFloat()
                return leftMargin + (index.toFloat() / denom) * chartW
            }

            // Y coordinate mapping
            fun getYPos(value: Double): Float {
                val span = chartMaxY - chartMinY
                val ratio = if (span < 1e-9) 0.5 else (value - chartMinY) / span
                return (topMargin + chartH - ratio * chartH).toFloat()
            }

            // 1. Draw Actual response history line (Grey-Indigo dashed or thin)
            val actualPath = Path()
            actualPath.moveTo(getXPos(0), getYPos(responses[0]))
            for (i in 1 until responses.size) {
                actualPath.lineTo(getXPos(i), getYPos(responses[i]))
            }

            drawPath(
                path = actualPath,
                color = Color(0xFFA2A9B2),
                style = Stroke(width = 2.dp.toPx())
            )

            // Draw Actual nodes
            for (i in responses.indices) {
                drawCircle(
                    color = Color(0xFF6B7280),
                    radius = 4.dp.toPx(),
                    center = Offset(getXPos(i), getYPos(responses[i]))
                )
            }

            // 2. Draw Rolling Optimum progress line (Scientific Teal / Bright Green)
            val bestPath = Path()
            bestPath.moveTo(getXPos(0), getYPos(runningBest[0]))
            for (i in 1 until runningBest.size) {
                bestPath.lineTo(getXPos(i), getYPos(runningBest[i]))
            }

            drawPath(
                path = bestPath,
                color = Color(0xFF00B4D8),
                style = Stroke(width = 3.5.dp.toPx())
            )

            // Draw Optimal highlight nodes (gold starred look or prominent circle)
            for (i in runningBest.indices) {
                drawCircle(
                    color = Color(0xFF1A6B9C),
                    radius = 5.5.dp.toPx(),
                    center = Offset(getXPos(i), getYPos(runningBest[i]))
                )
                drawCircle(
                    color = Color(0xFFF1C40F),
                    radius = 3.dp.toPx(),
                    center = Offset(getXPos(i), getYPos(runningBest[i]))
                )
            }

            // X Axis and ticks
            drawLine(
                color = Color.DarkGray,
                start = Offset(leftMargin, topMargin + chartH),
                end = Offset(leftMargin + chartW, topMargin + chartH),
                strokeWidth = 1.5.dp.toPx()
            )

            for (i in responses.indices) {
                val xPos = getXPos(i)
                drawLine(
                    color = Color.DarkGray,
                    start = Offset(xPos, topMargin + chartH),
                    end = Offset(xPos, topMargin + chartH + 5.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx()
                )

                drawText(
                    textMeasurer = textMeasurer,
                    text = "${i + 1}",
                    topLeft = Offset(xPos - 4.dp.toPx(), topMargin + chartH + 7.dp.toPx()),
                    style = axisLabelStyle
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(Color(0xFF6B7280))
            )
            Text(
                "Actual Results",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, end = 16.dp)
            )

            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(Color(0xFFF1C40F))
            )
            Text(
                "Best Condition Achieved",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}
