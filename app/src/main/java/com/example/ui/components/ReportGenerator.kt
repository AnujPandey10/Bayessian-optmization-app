package com.example.ui.components

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.Converters
import com.example.data.Experiment
import com.example.data.Project
import com.example.data.Study
import com.example.ml.BayesianOptimizer
import com.example.ml.GPMetrics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportGenerator {

    fun generateAndPrintReport(
        context: Context,
        project: Project,
        study: Study,
        experiments: List<Experiment>,
        metrics: GPMetrics?,
        recommendations: List<BayesianOptimizer.Recommendation>
    ) {
        val converters = Converters()
        val factors = converters.stringToFactors(study.factorsJson)
        val responses = converters.stringToResponses(study.responsesJson)
        val optIndex = study.targetResponseIndex
        val primaryResponse = responses.getOrNull(optIndex)?.name ?: "Target Output"

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val generatedDate = dateFormat.format(Date())

        val evaluatedExps = experiments.filter {
            val yArr = converters.stringToDoubleList(it.responseValuesJson ?: "")
            yArr.isNotEmpty() && optIndex < yArr.size
        }

        val bestExp = if (study.targetGoal == "MAXIMIZE") {
            evaluatedExps.maxByOrNull {
                converters.stringToDoubleList(it.responseValuesJson ?: "")[optIndex]
            }
        } else {
            evaluatedExps.minByOrNull {
                converters.stringToDoubleList(it.responseValuesJson ?: "")[optIndex]
            }
        }

        val bestYVal = bestExp?.let {
            converters.stringToDoubleList(it.responseValuesJson ?: "")[optIndex]
        }

        // --- HTML Template Generation ---
        val htmlContent = buildString {
            append("""
                <!DOCTYPE html>
                <html>
                <head>
                <meta charset="utf-8">
                <title>BioOpt AI - Process Optimization Report</title>
                <style>
                    body {
                        font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif;
                        color: #2c3e50;
                        line-height: 1.5;
                        padding: 25px;
                        background-color: #ffffff;
                    }
                    .header {
                        border-bottom: 2px solid #0077b6;
                        padding-bottom: 12px;
                        margin-bottom: 25px;
                    }
                    .header h1 {
                        color: #0077b6;
                        margin: 0;
                        font-size: 24px;
                    }
                    .header p {
                        margin: 5px 0 0 0;
                        color: #7f8c8d;
                        font-size: 13px;
                    }
                    .section {
                        margin-bottom: 25px;
                    }
                    .section h2 {
                        color: #1d3557;
                        border-left: 4px solid #00b4d8;
                        padding-left: 8px;
                        font-size: 16px;
                        margin-top: 0;
                        margin-bottom: 12px;
                    }
                    .meta-grid {
                        display: grid;
                        grid-template-columns: 1fr 1fr;
                        gap: 15px;
                        margin-bottom: 20px;
                        background-color: #f8f9fa;
                        padding: 12px;
                        border-radius: 6px;
                    }
                    .meta-item {
                        font-size: 13px;
                    }
                    .meta-item strong {
                        color: #1d3557;
                    }
                    table {
                        width: 100%;
                        border-collapse: collapse;
                        margin-top: 10px;
                        margin-bottom: 15px;
                        font-size: 12px;
                    }
                    th {
                        background-color: #f1faee;
                        color: #1d3557;
                        text-align: left;
                        padding: 8px;
                        border: 1px solid #d3d3d3;
                        font-weight: bold;
                    }
                    td {
                        padding: 8px;
                        border: 1px solid #e0e0e0;
                    }
                    tr:nth-child(even) {
                        background-color: #fafafa;
                    }
                    .badge-max {
                        background-color: #e8f5e9;
                        color: #2e7d32;
                        padding: 3px 6px;
                        border-radius: 4px;
                        font-weight: bold;
                    }
                    .badge-min {
                        background-color: #ffebee;
                        color: #c62828;
                        padding: 3px 6px;
                        border-radius: 4px;
                        font-weight: bold;
                    }
                    .exec-summary {
                        background-color: #e2f0d9;
                        border: 1px solid #c5e0b4;
                        padding: 15px;
                        border-radius: 6px;
                        font-size: 13px;
                        margin-bottom: 25px;
                    }
                    .badge {
                        background-color: #e5e7eb;
                        color: #374151;
                        padding: 2px 5px;
                        border-radius: 3px;
                        font-size: 11px;
                    }
                </style>
                </head>
                <body>
                    <div class="header">
                        <h1>BioOpt AI Process Optimization Report</h1>
                        <p>Generated on: $generatedDate | Platform: Android BioOpt AI Engine</p>
                    </div>

                    <div class="section">
                        <h2>1. Project & Study Metadata</h2>
                        <div class="meta-grid">
                            <div class="meta-item"><strong>Project Name:</strong> ${project.name}</div>
                            <div class="meta-item"><strong>Study Name:</strong> ${study.name}</div>
                            <div class="meta-item"><strong>Optimization Objective:</strong> 
                                <span class="${if (study.targetGoal == "MAXIMIZE") "badge-max" else "badge-min"}">
                                    ${study.targetGoal} (${primaryResponse})
                                </span>
                            </div>
                            <div class="meta-item"><strong>Statistical Kernel:</strong> <span class="badge">${study.kernelType}</span></div>
                            <div class="meta-item"><strong>Acquisition Strategy:</strong> <span class="badge">${study.acquisitionFunction}</span></div>
                            <div class="meta-item"><strong>Total Processed Iterations:</strong> ${evaluatedExps.size} runs</div>
                        </div>
                    </div>

                    <div class="exec-summary">
                        <strong>Executive Optimization Summary:</strong><br>
                        ${
                            if (bestYVal != null && evaluatedExps.size > 2) {
                                """The process development study was analyzed using Gaussian Process Regression models. 
                                Based on ${evaluatedExps.size} executed experimental conditions, the optimal result achieved is 
                                <strong>$bestYVal ${responses[optIndex].unit}</strong>. 
                                Model diagnostics indicates a regression fit (R²) of <strong>${String.format("%.4f", metrics?.r2 ?: 0.0)}</strong>. 
                                To proceed, we suggest referencing the top generated Bayesian recommendations listed in Section 4."""
                            } else {
                                "The study is currently in its exploratory characterization phase. Initial configurations are randomly sampled within set factor boundaries to partition high-dimensional parameter space evenly before tuning predictive priors."
                            }
                        }
                    </div>
            """.trimIndent())

            // Section 2: Model Diagnostics
            if (metrics != null) {
                append("""
                    <div class="section">
                        <h2>2. Gaussian Process Regression Diagnostics</h2>
                        <table>
                            <thead>
                                <tr>
                                    <th>Model Parameter Metric</th>
                                    <th>Observed Value</th>
                                    <th>Methodological Interpretation</th>
                                </tr>
                            </thead>
                            <tbody>
                                <tr>
                                    <td>Coefficient of Determination (R²)</td>
                                    <td><strong>${String.format("%.4f", metrics.r2)}</strong></td>
                                    <td>Represents the proportion of variance in processing outcomes explained by factor settings. Target > 0.70.</td>
                                </tr>
                                <tr>
                                    <td>Root Mean Square Error (RMSE)</td>
                                    <td><strong>${String.format("%.4f", metrics.rmse)}</strong></td>
                                    <td>Standard deviation of processing residuals (variance between model predictions and experimental metrics).</td>
                                </tr>
                                <tr>
                                    <td>Mean Absolute Error (MAE)</td>
                                    <td><strong>${String.format("%.4f", metrics.mae)}</strong></td>
                                    <td>Average magnitude of processing deviations. Represents average forecasting uncertainty.</td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                """.trimIndent())
            }

            // Section 3: Completed Runs Table
            append("""
                <div class="section">
                    <h2>3. Historical Experimental Logs</h2>
                    <table>
                        <thead>
                            <tr>
                                <th>#</th>
            """.trimIndent())

            // Factor column headers
            for (f in factors) {
                append("<th>${f.name} (${f.unit})</th>")
            }
            // Response column headers
            for (r in responses) {
                append("<th>${r.name} (${r.unit})</th>")
            }
            append("""
                            </tr>
                        </thead>
                        <tbody>
            """.trimIndent())

            for ((idx, exp) in evaluatedExps.withIndex()) {
                val fVals = converters.stringToDoubleList(exp.factorValuesJson)
                val rVals = converters.stringToDoubleList(exp.responseValuesJson ?: "")

                append("<tr>")
                append("<td><strong>${idx + 1}</strong></td>")
                // Factor values
                for (j in factors.indices) {
                    val value = fVals.getOrNull(j) ?: 0.0
                    append("<td>${String.format("%.3f", value)}</td>")
                }
                // Response values
                for (j in responses.indices) {
                    val value = rVals.getOrNull(j) ?: 0.0
                    append("<td>${String.format("%.3f", value)}</td>")
                }
                append("</tr>")
            }

            append("""
                        </tbody>
                    </table>
                </div>
            """.trimIndent())

            // Section 4: Recommendations
            if (recommendations.isNotEmpty()) {
                append("""
                    <div class="section">
                        <h2>4. Bayesian Optimization Recommendations (Next Best Steps)</h2>
                        <p style="font-size: 11px; margin-top: -5px; color: #7f8c8d;">
                            Suggested configurations generated using Monte-Carlo search over the parameter bounds. 
                            Ranked according to expected acquisition value.
                        </p>
                        <table>
                            <thead>
                                <tr>
                                    <th>Rank</th>
                """.trimIndent())

                // Factor column fields
                for (f in factors) {
                    append("<th>${f.name} (${f.unit})</th>")
                }

                // Predictions & Scores
                append("""
                                    <th>Predicted Outcome</th>
                                    <th>95% Confidence Interval</th>
                                    <th>Acquisition Score</th>
                                    <th>Recommendation Decision Explanation</th>
                                </tr>
                            </thead>
                            <tbody>
                """.trimIndent())

                for ((idx, rec) in recommendations.withIndex()) {
                    append("<tr>")
                    append("<td><strong>${idx + 1}</strong></td>")
                    // Factor coordinates
                    for (j in factors.indices) {
                        val value = rec.values.getOrNull(j) ?: 0.0
                        append("<td>${String.format("%.3f", value)}</td>")
                    }
                    // Predictions
                    val ciSpan = rec.predictedStdDev * 1.96
                    append("<td><strong>${String.format("%.3f", rec.predictedMean)}</strong></td>")
                    append("<td>±${String.format("%.3f", ciSpan)}</td>")
                    append("<td>${String.format("%.4f", rec.acquisitionScore)}</td>")
                    append("<td><em style='font-size: 11px; color: #555;'>${rec.explanation}</em></td>")
                    append("</tr>")
                }

                append("""
                            </tbody>
                        </table>
                    </div>
                """.trimIndent())
            }

            append("""
                </body>
                </html>
            """.trimIndent())
        }

        // --- Execute Prints using Android Printing Pipeline ---
        val mainExecutor = context.mainExecutor
        mainExecutor.execute {
            val webView = WebView(context)
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // Initialize print services once layout finishes loading
                    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                    val jobName = "BioOptAI_${study.name.replace(" ", "_")}_Report"
                    val printAdapter = webView.createPrintDocumentAdapter(jobName)

                    printManager.print(
                        jobName,
                        printAdapter,
                        PrintAttributes.Builder().build()
                    )
                }
            }
            webView.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
        }
    }
}
