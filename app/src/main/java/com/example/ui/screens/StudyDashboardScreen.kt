package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Converters
import com.example.data.Experiment
import com.example.data.Factor
import com.example.data.Response
import com.example.data.Study
import com.example.ml.AcquisitionType
import com.example.ml.KernelType
import com.example.ui.components.OptimizationProgressChart
import com.example.ui.components.ReportGenerator
import com.example.ui.components.ResponseSurfaceCanvas
import com.example.ui.components.UncertaintyChart
import com.example.ui.theme.*
import com.example.ui.viewmodel.BioOptViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyDashboardScreen(
    viewModel: BioOptViewModel,
    onBackToProjects: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentProject by viewModel.currentProject.collectAsStateWithLifecycle()
    val studies by viewModel.studies.collectAsStateWithLifecycle()
    val currentStudy by viewModel.currentStudy.collectAsStateWithLifecycle()
    val experiments by viewModel.currentExperiments.collectAsStateWithLifecycle()

    val gpMetrics by viewModel.gpMetrics.collectAsStateWithLifecycle()
    val recommendations by viewModel.recommendations.collectAsStateWithLifecycle()
    val isTraining by viewModel.isTraining.collectAsStateWithLifecycle()
    val trainingError by viewModel.trainingError.collectAsStateWithLifecycle()

    val converters = remember { Converters() }

    // Navigation and section tab state
    var selectedTabIdx by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Models", "Recs Queue", "History Logs", "Visuals", "Export PDF")

    // Modals visibility states
    var showAddStudyDialog by remember { mutableStateOf(false) }
    var showAddExpDialog by remember { mutableStateOf(false) }
    var showCSVImportDialog by remember { mutableStateOf(false) }
    var showActualResultDialog by remember { mutableStateOf<com.example.ml.BayesianOptimizer.Recommendation?>(null) }

    // Non-plotted factors dynamic slider map
    val sliderValues = remember { mutableStateMapOf<Int, Double>() }

    // Trigger state reset when study selection changes
    LaunchedEffect(currentStudy) {
        val study = currentStudy
        if (study != null) {
            val factors = converters.stringToFactors(study.factorsJson)
            sliderValues.clear()
            factors.forEachIndexed { idx, fac ->
                sliderValues[idx] = (fac.minVal + fac.maxVal) / 2.0
            }
        }
    }

    if (currentProject == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No active model project workspace is selected.")
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentProject?.name ?: "Workspace Dashboard",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        currentStudy?.let {
                            Text(
                                text = "Active Study: ${it.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } ?: Text(
                            text = "Please select or create a process study.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackToProjects) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Return home")
                    }
                },
                actions = {
                    // Study dropdown switcher
                    Box {
                        var expandedStudyDropdown by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { expandedStudyDropdown = true },
                            modifier = Modifier.testTag("study_switcher_btn")
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Switch Study")
                        }
                        DropdownMenu(
                            expanded = expandedStudyDropdown,
                            onDismissRequest = { expandedStudyDropdown = false }
                        ) {
                            studies.forEach { study ->
                                DropdownMenuItem(
                                    text = { Text(study.name) },
                                    onClick = {
                                        viewModel.selectStudy(study)
                                        expandedStudyDropdown = false
                                    }
                                )
                            }
                            Divider()
                            DropdownMenuItem(
                                text = {
                                    Row {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Create Process Study")
                                    }
                                },
                                onClick = {
                                    showAddStudyDialog = true
                                    expandedStudyDropdown = false
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Divider(color = Slate200, thickness = 1.dp)
            // Horizontal Selection Tabs
            TabRow(
                selectedTabIndex = selectedTabIdx,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIdx == index,
                        onClick = { selectedTabIdx = index },
                        text = {
                            Text(
                                title,
                                fontSize = 11.sp,
                                maxLines = 1,
                                fontWeight = if (selectedTabIdx == index) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.testTag("tab_button_$index")
                    )
                }
            }

            if (currentStudy == null) {
                // Empty state block
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Empty studies logo",
                            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "No Bio-Process Study Configured",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Please establish factor and response configurations for this project to trigger Gaussian modeling.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showAddStudyDialog = true },
                            modifier = Modifier.testTag("create_first_study_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Configure New Study")
                        }
                    }
                }
            } else {
                val study = currentStudy!!
                val factors = converters.stringToFactors(study.factorsJson)
                val responses = converters.stringToResponses(study.responsesJson)

                if (isTraining) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                trainingError?.let { err ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp)) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(err, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp)
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (selectedTabIdx) {
                        0 -> {
                            // --- T1: MACHINE LEARNING ENGINE & MODELING CONFIGS ---
                            val modelingScrollState = rememberScrollState()
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(modelingScrollState)
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Model setup configurations
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, Slate200),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            "Gaussian Process Prior Configurations",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Kernel type Row
                                        Text("Statistical Interpolation Kernel", style = MaterialTheme.typography.labelMedium)
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            KernelType.values().forEach { kt ->
                                                val isSel = study.kernelType == kt.name
                                                FilterChip(
                                                    selected = isSel,
                                                    onClick = {
                                                        viewModel.updateStudyConfig(
                                                            kernel = kt.name,
                                                            acq = study.acquisitionFunction,
                                                            targetGoal = study.targetGoal,
                                                            optIndex = study.targetResponseIndex
                                                        )
                                                    },
                                                    label = { Text(kt.name.replace("_", " "), fontSize = 10.sp) },
                                                    modifier = Modifier.testTag("kernel_chip_${kt.name}")
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Acquisition selector
                                        Text("Bayesian Optimizer Acquisition Strategy", style = MaterialTheme.typography.labelMedium)
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            AcquisitionType.values().forEach { acq ->
                                                val isSel = study.acquisitionFunction == acq.name
                                                FilterChip(
                                                    selected = isSel,
                                                    onClick = {
                                                        viewModel.updateStudyConfig(
                                                            kernel = study.kernelType,
                                                            acq = acq.name,
                                                            targetGoal = study.targetGoal,
                                                            optIndex = study.targetResponseIndex
                                                        )
                                                    },
                                                    label = { Text(acq.name, fontSize = 10.sp) },
                                                    modifier = Modifier.testTag("acq_chip_${acq.name}")
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Primary Goal Target", style = MaterialTheme.typography.labelMedium)
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    RadioButton(
                                                        selected = study.targetGoal == "MAXIMIZE",
                                                        onClick = {
                                                            viewModel.updateStudyConfig(
                                                                kernel = study.kernelType,
                                                                acq = study.acquisitionFunction,
                                                                targetGoal = "MAXIMIZE",
                                                                optIndex = study.targetResponseIndex
                                                            )
                                                        }
                                                    )
                                                    Text("MAXIMIZE", fontSize = 11.sp)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    RadioButton(
                                                        selected = study.targetGoal == "MINIMIZE",
                                                        onClick = {
                                                            viewModel.updateStudyConfig(
                                                                kernel = study.kernelType,
                                                                acq = study.acquisitionFunction,
                                                                targetGoal = "MINIMIZE",
                                                                optIndex = study.targetResponseIndex
                                                            )
                                                        }
                                                    )
                                                    Text("MINIMIZE", fontSize = 11.sp)
                                                }
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Optimized Factor Index", style = MaterialTheme.typography.labelMedium)
                                                var resDropdownExpanded by remember { mutableStateOf(false) }
                                                Box {
                                                    OutlinedButton(
                                                        onClick = { resDropdownExpanded = true },
                                                        modifier = Modifier.fillMaxWidth().testTag("response_index_btn")
                                                    ) {
                                                        Text(
                                                            responses.getOrNull(study.targetResponseIndex)?.name ?: "Select Output",
                                                            fontSize = 11.sp,
                                                            maxLines = 1
                                                        )
                                                    }
                                                    DropdownMenu(
                                                        expanded = resDropdownExpanded,
                                                        onDismissRequest = { resDropdownExpanded = false }
                                                    ) {
                                                        responses.forEachIndexed { idx, response ->
                                                            DropdownMenuItem(
                                                                text = { Text(response.name) },
                                                                onClick = {
                                                                    viewModel.updateStudyConfig(
                                                                        kernel = study.kernelType,
                                                                        acq = study.acquisitionFunction,
                                                                        targetGoal = study.targetGoal,
                                                                        optIndex = idx
                                                                    )
                                                                    resDropdownExpanded = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Button(
                                            onClick = { viewModel.forceRetrain() },
                                            modifier = Modifier.fillMaxWidth().testTag("retrain_btn")
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Recalculate Bayesian Model")
                                        }
                                    }
                                }

                                // Diagnostics Card
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, Slate200),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            "Model Statistical Diagnostics",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))

                                        if (gpMetrics == null) {
                                            Text(
                                                "Statistical indicators require at least 2 evaluating runs to compile. Model is currently executing prior distribution sweeps.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontStyle = FontStyle.Italic
                                            )
                                        } else {
                                            val metrics = gpMetrics!!
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                DiagnosticValueCard(
                                                    title = "Coefficient (R²)",
                                                    value = String.format("%.4f", metrics.r2),
                                                    desc = "Proportion of target variance model explains",
                                                    modifier = Modifier.weight(1f)
                                                )
                                                DiagnosticValueCard(
                                                    title = "Error (RMSE)",
                                                    value = String.format("%.4f", metrics.rmse),
                                                    desc = "Standard error deviation of residuals",
                                                    modifier = Modifier.weight(1f)
                                                )
                                                DiagnosticValueCard(
                                                    title = "Mean Disp (MAE)",
                                                    value = String.format("%.4f", metrics.mae),
                                                    desc = "Average absolute prediction error",
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Top Recommendation summary
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, Slate200),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column {
                                        // Header Row with modern slate background representing high density parameters
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Slate100)
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Star,
                                                    contentDescription = null,
                                                    tint = HighDensityBlue,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Next Recommended Experiment".uppercase(),
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = Slate600
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .background(Amber100, shape = RoundedCornerShape(12.dp))
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = (study.acquisitionFunction + " Policy").uppercase(),
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                                    color = Amber800
                                                )
                                            }
                                        }

                                        Column(modifier = Modifier.padding(16.dp)) {

                                        if (recommendations.isEmpty()) {
                                            Text(
                                                "Analyzing model configurations to formulate optimal recipes...",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontStyle = FontStyle.Italic
                                            )
                                        } else {
                                            val rec = recommendations.first()
                                            Text(
                                                "Suggested Factor Conditions:",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )

                                            factors.forEachIndexed { i, f ->
                                                val value = rec.values.getOrNull(i) ?: 0.0
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(f.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text(
                                                        "${String.format("%.3f", value)} ${f.unit}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }

                                            Divider(modifier = Modifier.padding(vertical = 8.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column {
                                                    Text("Predicted Output Yield", style = MaterialTheme.typography.labelSmall)
                                                    Text(
                                                        "${String.format("%.2f", rec.predictedMean)} ${responses[study.targetResponseIndex].unit}",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }

                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text("Uncertainty Interval (95% CI)", style = MaterialTheme.typography.labelSmall)
                                                    Text(
                                                        "±${String.format("%.2f", rec.predictedStdDev * 1.96)} ${responses[study.targetResponseIndex].unit}",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Motivation: ${rec.explanation}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontStyle = FontStyle.Italic
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Button(
                                                onClick = { showActualResultDialog = rec },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                modifier = Modifier.fillMaxWidth().testTag("execute_first_rec_btn")
                                            ) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Execute Recommended Recipe")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                        1 -> {
                            // --- T2: RECOMMENDATION QUEUE ---
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "Bayesian Suggested Experiments Queue",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Process recipes sorted dynamically by acquisition scores. Running these systematically maximizes data value.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                if (recommendations.isEmpty()) {
                                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        Text("Queue rendering... Waiting on regression fitting results.", fontStyle = FontStyle.Italic)
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        itemsIndexed(recommendations) { idx, rec ->
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surface
                                                ),
                                                border = BorderStroke(1.dp, Slate200),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(14.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(24.dp)
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(MaterialTheme.colorScheme.primary),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                "${idx + 1}",
                                                                color = MaterialTheme.colorScheme.onPrimary,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            "Suggested Chemistry Profile",
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Spacer(modifier = Modifier.weight(1f))
                                                        Text(
                                                            "Acq Val: ${String.format("%.3f", rec.acquisitionScore)}",
                                                            fontSize = 10.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.height(10.dp))

                                                    // Lists factors
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        factors.forEachIndexed { i, f ->
                                                            val v = rec.values.getOrNull(i) ?: 0.0
                                                            Card(
                                                                colors = CardDefaults.cardColors(containerColor = Slate100),
                                                                border = BorderStroke(1.dp, Slate200),
                                                                shape = RoundedCornerShape(8.dp),
                                                                modifier = Modifier.weight(1f)
                                                            ) {
                                                                Column(
                                                                    modifier = Modifier.padding(6.dp),
                                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                                ) {
                                                                    Text(f.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                                                    Text(
                                                                        "${String.format("%.2f", v)} ${f.unit}",
                                                                        fontSize = 10.sp,
                                                                        fontWeight = FontWeight.Bold
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(10.dp))

                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Column {
                                                            Text("Forecast Value", fontSize = 9.sp, color = Color.Gray)
                                                            Text(
                                                                "${String.format("%.2f", rec.predictedMean)} ${responses[study.targetResponseIndex].unit}",
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                        Column(horizontalAlignment = Alignment.End) {
                                                            Text("Confidence (95%)", fontSize = 9.sp, color = Color.Gray)
                                                            Text(
                                                                "±${String.format("%.2f", rec.predictedStdDev * 1.96)} ${responses[study.targetResponseIndex].unit}",
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Text(
                                                        rec.explanation,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontStyle = FontStyle.Italic
                                                    )

                                                    Spacer(modifier = Modifier.height(10.dp))
                                                    Button(
                                                        onClick = { showActualResultDialog = rec },
                                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                                        modifier = Modifier.fillMaxWidth().testTag("execute_rec_btn_$idx")
                                                    ) {
                                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text("Run Run & Register Output", fontSize = 11.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        2 -> {
                            // --- T3: EXPERIMENTAL DATA HISTORY LOGS & IMPORT ---
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            "Historical Process Runs Logger",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            "Total Runs Registered: ${experiments.size}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(onClick = { showCSVImportDialog = true }) {
                                            Icon(Icons.Default.Share, contentDescription = "Paste CSV Data")
                                        }
                                        Button(
                                            onClick = { showAddExpDialog = true },
                                            modifier = Modifier.testTag("add_custom_exp_btn")
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Add", fontSize = 11.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                if (experiments.isEmpty()) {
                                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.LightGray)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("No experimental records mapped yet.", fontStyle = FontStyle.Italic, color = Color.Gray)
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(experiments) { exp ->
                                            val xVals = converters.stringToDoubleList(exp.factorValuesJson)
                                            val yVals = converters.stringToDoubleList(exp.responseValuesJson ?: "")

                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                                border = BorderStroke(0.5.dp, Color.LightGray)
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Icon(
                                                            imageVector = if (exp.isRecommendation) Icons.Default.Star else Icons.Default.List,
                                                            contentDescription = null,
                                                            tint = if (exp.isRecommendation) MaterialTheme.colorScheme.primary else Color.Gray,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = if (exp.isRecommendation) "Guided Bayesian Run" else "Custom Operator Entry",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (exp.isRecommendation) MaterialTheme.colorScheme.primary else Color.Gray
                                                        )
                                                        Spacer(modifier = Modifier.weight(1f))
                                                        Text(
                                                            text = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(exp.timestamp)),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontSize = 9.sp,
                                                            color = Color.Gray
                                                        )
                                                        IconButton(
                                                            onClick = { viewModel.deleteExperiment(exp) },
                                                            modifier = Modifier.size(24.dp).padding(start = 2.dp)
                                                        ) {
                                                            Icon(Icons.Default.Close, contentDescription = "Delete run", modifier = Modifier.size(14.dp), tint = Color.Red.copy(alpha = 0.7f))
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(6.dp))

                                                    // Render values
                                                    Column {
                                                        Text("Factors Setpoints:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            factors.forEachIndexed { i, f ->
                                                                val value = xVals.getOrNull(i) ?: 0.0
                                                                Text("${f.name}: ${String.format("%.2f", value)}", fontSize = 10.sp)
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text("Observed Response Metrology:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                                        if (yVals.isEmpty()) {
                                                            Text("Pending execution metrology...", fontSize = 10.sp, fontStyle = FontStyle.Italic, color = Color.Red)
                                                        } else {
                                                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                                responses.forEachIndexed { i, r ->
                                                                    val value = yVals.getOrNull(i) ?: 0.0
                                                                    Text("${r.name}: ${String.format("%.2f", value)} ${r.unit}", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = { viewModel.clearExperiments() },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                                    ) {
                                        Text("Wipe Experimental History Logs")
                                    }
                                }
                            }
                        }

                        3 -> {
                            // --- T4: SCIENTIFIC ANALYTICAL CHARTS DISPLAY ---
                            var chartViewIndex by remember { mutableIntStateOf(0) } // 0 = 2D Surface, 1 = 1D Uncertainty, 2 = Progress Line
                            var xAxisSel by remember { mutableIntStateOf(0) }
                            var yAxisSel by remember { mutableIntStateOf(1) }

                            var sweepFactorIdx by remember { mutableIntStateOf(0) }

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("2D Response Surface", "1D GP Uncertainty", "Optimization Progress").forEachIndexed { index, title ->
                                        val isSel = chartViewIndex == index
                                        ElevatedButton(
                                            onClick = { chartViewIndex = index },
                                            colors = ButtonDefaults.elevatedButtonColors(
                                                containerColor = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                            ),
                                            modifier = Modifier.weight(1f).testTag("chart_tab_btn_$index")
                                        ) {
                                            Text(
                                                title,
                                                fontSize = 9.sp,
                                                maxLines = 1,
                                                color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                if (viewModel.activeGP == null) {
                                    Box(
                                        modifier = Modifier.height(300.dp).fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Visualization requires at least 2 evaluating trials to execute priors.", fontStyle = FontStyle.Italic)
                                    }
                                } else {
                                    val gp = viewModel.activeGP!!

                                    when (chartViewIndex) {
                                        0 -> {
                                            // --- 2D Surface ---
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("X-Axis Parameter", style = MaterialTheme.typography.labelSmall)
                                                    var xEx by remember { mutableStateOf(false) }
                                                    Box {
                                                        OutlinedButton(onClick = { xEx = true }, modifier = Modifier.fillMaxWidth()) {
                                                            Text(factors.getOrNull(xAxisSel)?.name ?: "X Axis", fontSize = 10.sp, maxLines = 1)
                                                        }
                                                        DropdownMenu(expanded = xEx, onDismissRequest = { xEx = false }) {
                                                            factors.forEachIndexed { idx, fac ->
                                                                DropdownMenuItem(text = { Text(fac.name) }, onClick = { xAxisSel = idx; xEx = false })
                                                            }
                                                        }
                                                    }
                                                }

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("Y-Axis Parameter", style = MaterialTheme.typography.labelSmall)
                                                    var yEx by remember { mutableStateOf(false) }
                                                    Box {
                                                        OutlinedButton(onClick = { yEx = true }, modifier = Modifier.fillMaxWidth()) {
                                                            Text(factors.getOrNull(yAxisSel)?.name ?: "Y Axis", fontSize = 10.sp, maxLines = 1)
                                                        }
                                                        DropdownMenu(expanded = yEx, onDismissRequest = { yEx = false }) {
                                                            factors.forEachIndexed { idx, fac ->
                                                                DropdownMenuItem(text = { Text(fac.name) }, onClick = { yAxisSel = idx; yEx = false })
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // Draw Mean Pred Map
                                            ResponseSurfaceCanvas(
                                                gp = gp,
                                                factors = factors,
                                                xAxisIdx = xAxisSel,
                                                yAxisIdx = yAxisSel,
                                                otherFactorValues = sliderValues,
                                                drawUncertainty = false,
                                                modifier = Modifier.fillMaxWidth().height(260.dp)
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // Draw Uncertainty Map
                                            ResponseSurfaceCanvas(
                                                gp = gp,
                                                factors = factors,
                                                xAxisIdx = xAxisSel,
                                                yAxisIdx = yAxisSel,
                                                otherFactorValues = sliderValues,
                                                drawUncertainty = true,
                                                modifier = Modifier.fillMaxWidth().height(260.dp)
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // Dynamic sliders for non-axis factors
                                            if (factors.size > 2) {
                                                Text(
                                                    "Interactive Non-Axis Sliders:",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.labelLarge
                                                )
                                                factors.forEachIndexed { i, f ->
                                                    if (i != xAxisSel && i != yAxisSel) {
                                                        val valCurrent = sliderValues[i] ?: ((f.maxVal + f.minVal) / 2.0)
                                                        Column {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween
                                                            ) {
                                                                Text("${f.name}: ${String.format("%.2f", valCurrent)} ${f.unit}", style = MaterialTheme.typography.bodySmall)
                                                                Row {
                                                                    Text("${f.minVal}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                                                    Spacer(modifier = Modifier.width(100.dp))
                                                                    Text("${f.maxVal}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                                                }
                                                            }
                                                            Slider(
                                                                value = valCurrent.toFloat(),
                                                                onValueChange = { sliderValues[i] = it.toDouble() },
                                                                valueRange = f.minVal.toFloat()..f.maxVal.toFloat()
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        1 -> {
                                            // --- 1D GP Uncertainty Band ---
                                            Column {
                                                Text("Select Sweeping Parameter", style = MaterialTheme.typography.labelSmall)
                                                var sweepEx by remember { mutableStateOf(false) }
                                                Box {
                                                    OutlinedButton(onClick = { sweepEx = true }, modifier = Modifier.fillMaxWidth()) {
                                                        Text(factors.getOrNull(sweepFactorIdx)?.name ?: "Sweep Axis", fontSize = 10.sp)
                                                    }
                                                    DropdownMenu(expanded = sweepEx, onDismissRequest = { sweepEx = false }) {
                                                        factors.forEachIndexed { idx, fac ->
                                                            DropdownMenuItem(text = { Text(fac.name) }, onClick = { sweepFactorIdx = idx; sweepEx = false })
                                                        }
                                                    }
                                                }
                                            }

                                            UncertaintyChart(
                                                gp = gp,
                                                factors = factors,
                                                sweepFactorIdx = sweepFactorIdx,
                                                otherFactorValues = sliderValues,
                                                experiments = experiments,
                                                targetResponseIdx = study.targetResponseIndex,
                                                modifier = Modifier.fillMaxWidth().height(290.dp)
                                            )
                                        }

                                        2 -> {
                                            // --- Optimization Progress Chart ---
                                            OptimizationProgressChart(
                                                experiments = experiments,
                                                targetResponseIdx = study.targetResponseIndex,
                                                goal = study.targetGoal,
                                                modifier = Modifier.fillMaxWidth().height(290.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        4 -> {
                            // --- T5: EXPORT SCIENTIFIC REPORTS ---
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "PDF Report Logo",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(80.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Compile Diagnostic Report PDF",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Converts active metadata, experimental logs, GP kernel diagnostics, and Bayesian suggestions into a multi-page physical printout or shareable PDF document.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                                Spacer(modifier = Modifier.height(20.dp))

                                Button(
                                    onClick = {
                                        ReportGenerator.generateAndPrintReport(
                                            context = context,
                                            project = currentProject!!,
                                            study = study,
                                            experiments = experiments,
                                            metrics = gpMetrics,
                                            recommendations = recommendations
                                        )
                                        Toast.makeText(context, "Assembling report and launching print spooler...", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("export_pdf_btn")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Compile Diagnostics PDF")
                                }
                            }
                        }
                    }
                }

// Modal Create Study has been moved to the end of the Composable body to support empty studies configuration

    // Modal Execute / Register Output Dialog
    showActualResultDialog?.let { rec ->
        val outputTextValues = remember { mutableStateMapOf<Int, String>() }

        AlertDialog(
            onDismissRequest = { showActualResultDialog = null },
            title = {
                Row {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 6.dp))
                    Text("Conduct Recommended Run", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "Set up physical equipment using the following coordinates:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Target values display
                    factors.forEachIndexed { idx, fac ->
                        val targetVal = rec.values.getOrNull(idx) ?: 0.0
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(fac.name, fontSize = 12.sp, color = Color.Gray)
                            Text("${String.format("%.3f", targetVal)} ${fac.unit}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        "Enter observed evaluation results:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )

                    responses.forEachIndexed { idx, resp ->
                        val curVal = outputTextValues[idx] ?: ""
                        OutlinedTextField(
                            value = curVal,
                            onValueChange = { outputTextValues[idx] = it },
                            label = { Text("${resp.name} (${resp.unit})") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Slate900,
                                unfocusedTextColor = Slate900,
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = HighDensityBlue,
                                unfocusedBorderColor = Slate200,
                                focusedLabelColor = HighDensityBlue,
                                unfocusedLabelColor = Slate500
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().testTag("metrology_input_$idx")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedResponses = ArrayList<Double>()
                        var valid = true
                        for (i in responses.indices) {
                            val text = outputTextValues[i] ?: ""
                            val doubleVal = text.toDoubleOrNull()
                            if (doubleVal != null) {
                                parsedResponses.add(doubleVal)
                            } else {
                                valid = false
                                break
                            }
                        }

                        if (valid && parsedResponses.size == responses.size) {
                            viewModel.recordRecommendationRun(rec.values, parsedResponses)
                            showActualResultDialog = null
                            Toast.makeText(context, "Experiment compiled into history logs. Prior retuned.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Please supply valid decimal values for all responses.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("save_metrology_btn")
                ) {
                    Text("Register Run Outcomes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showActualResultDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Modal Add Custom/Historical Experiment
    if (showAddExpDialog) {
        val factorValInputs = remember { mutableStateMapOf<Int, String>() }
        val responseValInputs = remember { mutableStateMapOf<Int, String>() }

        AlertDialog(
            onDismissRequest = { showAddExpDialog = false },
            title = { Text("Log Custom Process Run Trials", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().height(350.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val inputColors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Slate900,
                        unfocusedTextColor = Slate900,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = HighDensityBlue,
                        unfocusedBorderColor = Slate200,
                        focusedLabelColor = HighDensityBlue,
                        unfocusedLabelColor = Slate500
                    )
                    Text("Factors Setpoints:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    factors.forEachIndexed { idx, fac ->
                        val text = factorValInputs[idx] ?: ""
                        OutlinedTextField(
                            value = text,
                            onValueChange = { factorValInputs[idx] = it },
                            label = { Text("${fac.name} (${fac.minVal}..${fac.maxVal} ${fac.unit})") },
                            colors = inputColors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().testTag("manual_factor_input_$idx")
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text("Observed Outcomes Métrologie:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    responses.forEachIndexed { idx, resp ->
                        val text = responseValInputs[idx] ?: ""
                        OutlinedTextField(
                            value = text,
                            onValueChange = { responseValInputs[idx] = it },
                            label = { Text("${resp.name} (${resp.unit})") },
                            colors = inputColors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().testTag("manual_response_input_$idx")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val xDouble = ArrayList<Double>()
                        val yDouble = ArrayList<Double>()
                        var valid = true

                        for (i in factors.indices) {
                            val fVal = factorValInputs[i]?.toDoubleOrNull()
                            if (fVal != null) {
                                xDouble.add(fVal)
                            } else {
                                valid = false
                                break
                            }
                        }

                        for (i in responses.indices) {
                            val rVal = responseValInputs[i]?.toDoubleOrNull()
                            if (rVal != null) {
                                yDouble.add(rVal)
                            } else {
                                valid = false
                                break
                            }
                        }

                        if (valid && xDouble.size == factors.size && yDouble.size == responses.size) {
                            viewModel.addExperiment(xDouble, yDouble)
                            showAddExpDialog = false
                        } else {
                            Toast.makeText(context, "Please enter correct decimal metrics for all cells.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("submit_manual_exp_btn")
                ) {
                    Text("Add Row")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddExpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Modal CSV Paste Import
    if (showCSVImportDialog) {
        var csvText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCSVImportDialog = false },
            title = {
                Row {
                    Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import Raw CSV Data Logs")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Paste comma-separated rows matching factors and response ordering.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Text(
                        "Expected column order:\n" +
                                factors.joinToString(" | ") { it.name } + " | " +
                                responses.joinToString(" | ") { it.name },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = csvText,
                        onValueChange = { csvText = it },
                        placeholder = { Text("e.g. 9.5, 4.2, 1.8, 65.5, 12.0, 84.0\n9.2, 6.0, 2.0, 72.0, 10.0, 88.0") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Slate900,
                            unfocusedTextColor = Slate900,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = HighDensityBlue,
                            unfocusedBorderColor = Slate200,
                            focusedLabelColor = HighDensityBlue,
                            unfocusedLabelColor = Slate500
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .testTag("csv_pasted_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val numTotalCols = factors.size + responses.size
                        val lines = csvText.trim().split("\n")
                        var successCount = 0
                        var parseFailure = false

                        for (line in lines) {
                            if (line.isBlank()) continue
                            val parts = line.split(",").map { it.trim() }
                            if (parts.size == numTotalCols) {
                                val doubleValues = parts.mapNotNull { it.toDoubleOrNull() }
                                if (doubleValues.size == numTotalCols) {
                                    val fVals = doubleValues.take(factors.size)
                                    val rVals = doubleValues.takeLast(responses.size)
                                    viewModel.addExperiment(fVals, rVals)
                                    successCount++
                                } else {
                                    parseFailure = true
                                }
                            } else {
                                parseFailure = true
                            }
                        }

                        showCSVImportDialog = false
                        if (successCount > 0) {
                            Toast.makeText(context, "$successCount rows parsed and logged.", Toast.LENGTH_SHORT).show()
                        }
                        if (parseFailure) {
                            Toast.makeText(context, "Some rows failed to import due to formatting.", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.testTag("submit_csv_import_btn")
                ) {
                    Text("Import Lines")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCSVImportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
            } // closes active study else block

            // Modal Create Study
            if (showAddStudyDialog) {
                var studyName by remember { mutableStateOf("Insulin Yield Optimization Sweep") }
                var studyDesc by remember { mutableStateOf("Optimize bio-process factors to maximize insulin refolding yield") }
                var studyGoal by remember { mutableStateOf("MAXIMIZE") }

                // Managed Factoring List Entry
                val factorsList = remember {
                    mutableStateListOf<Factor>(
                        Factor("pH Level", 8.5, 11.0, "pH"),
                        Factor("Protein Conc.", 0.5, 5.0, "mg/mL"),
                        Factor("Redox Ratio", 1.0, 20.0, "GSH/GSSG")
                    )
                }
                val responsesList = remember {
                    mutableStateListOf<Response>(
                        Response("Yield", "%"),
                        Response("Purity", "%")
                    )
                }

                // Local dynamic entry row
                var nextFKey by remember { mutableStateOf("") }
                var nextFMin by remember { mutableStateOf("") }
                var nextFMax by remember { mutableStateOf("") }
                var nextFUnit by remember { mutableStateOf("") }

                var nextRKey by remember { mutableStateOf("") }
                var nextRUnit by remember { mutableStateOf("") }

                val inputColors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Slate900,
                    unfocusedTextColor = Slate900,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = HighDensityBlue,
                    unfocusedBorderColor = Slate200,
                    focusedLabelColor = HighDensityBlue,
                    unfocusedLabelColor = Slate500
                )

                AlertDialog(
                    onDismissRequest = { showAddStudyDialog = false },
                    title = { Text("Configure Process Study Parameters", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth().height(420.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = studyName,
                                onValueChange = { studyName = it },
                                label = { Text("Study Name (e.g., Conversion Sweep)") },
                                colors = inputColors,
                                modifier = Modifier.fillMaxWidth().testTag("study_name_input")
                            )
                            OutlinedTextField(
                                value = studyDesc,
                                onValueChange = { studyDesc = it },
                                label = { Text("Brief scientific scope description") },
                                colors = inputColors,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Target Goal:", style = MaterialTheme.typography.labelMedium)
                                Spacer(modifier = Modifier.width(12.dp))
                                RadioButton(selected = studyGoal == "MAXIMIZE", onClick = { studyGoal = "MAXIMIZE" })
                                Text("MAX", fontSize = 11.sp)
                                RadioButton(selected = studyGoal == "MINIMIZE", onClick = { studyGoal = "MINIMIZE" })
                                Text("MIN", fontSize = 11.sp)
                            }

                            Divider()

                            // --- Mapped Factors Section ---
                            Text("1. Process Factors Bounds Definitions", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            factorsList.forEachIndexed { idx, f ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${idx + 1}. ${f.name} (Range: ${f.minVal} to ${f.maxVal} ${f.unit})", fontSize = 11.sp)
                                    IconButton(onClick = { factorsList.removeAt(idx) }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Red)
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedTextField(value = nextFKey, onValueChange = { nextFKey = it }, label = { Text("Factor Key", fontSize = 10.sp) }, colors = inputColors, modifier = Modifier.weight(1.5f).testTag("factor_key_input"))
                                OutlinedTextField(value = nextFMin, onValueChange = { nextFMin = it }, label = { Text("Min", fontSize = 10.sp) }, colors = inputColors, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                                OutlinedTextField(value = nextFMax, onValueChange = { nextFMax = it }, label = { Text("Max", fontSize = 10.sp) }, colors = inputColors, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                                OutlinedTextField(value = nextFUnit, onValueChange = { nextFUnit = it }, label = { Text("Unit", fontSize = 10.sp) }, colors = inputColors, modifier = Modifier.weight(1f))
                            }
                            Button(
                                onClick = {
                                    val mn = nextFMin.toDoubleOrNull() ?: 0.0
                                    val mx = nextFMax.toDoubleOrNull() ?: 1.0
                                    if (nextFKey.isNotBlank()) {
                                        factorsList.add(Factor(nextFKey, mn, mx, nextFUnit))
                                        nextFKey = ""
                                        nextFMin = ""
                                        nextFMax = ""
                                        nextFUnit = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                modifier = Modifier.fillMaxWidth().testTag("add_factor_btn")
                            ) {
                                Text("Register Factor", fontSize = 11.sp)
                            }

                            Divider()

                            // --- Mapped Responses Section ---
                            Text("2. Metrology Responses Definitions", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            responsesList.forEachIndexed { idx, r ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${idx + 1}. ${r.name} (${r.unit})", fontSize = 11.sp)
                                    IconButton(onClick = { responsesList.removeAt(idx) }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Red)
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedTextField(value = nextRKey, onValueChange = { nextRKey = it }, label = { Text("Response Key", fontSize = 10.sp) }, colors = inputColors, modifier = Modifier.weight(2f).testTag("response_key_input"))
                                OutlinedTextField(value = nextRUnit, onValueChange = { nextRUnit = it }, label = { Text("Unit", fontSize = 10.sp) }, colors = inputColors, modifier = Modifier.weight(1f))
                            }
                            Button(
                                onClick = {
                                    if (nextRKey.isNotBlank()) {
                                        responsesList.add(Response(nextRKey, nextRUnit))
                                        nextRKey = ""
                                        nextRUnit = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                modifier = Modifier.fillMaxWidth().testTag("add_response_btn")
                            ) {
                                Text("Register Response", fontSize = 11.sp)
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (studyName.isNotBlank() && factorsList.isNotEmpty() && responsesList.isNotEmpty()) {
                                    viewModel.addStudy(
                                        name = studyName,
                                        desc = studyDesc,
                                        targetGoal = studyGoal,
                                        targetRepIdx = 0,
                                        factors = factorsList,
                                        responses = responsesList
                                    )
                                    showAddStudyDialog = false
                                } else {
                                    Toast.makeText(context, "Please configure at least one Factor and Response.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.testTag("confirm_create_study_btn")
                        ) {
                            Text("Lock Configurations")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddStudyDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        } // closes main Column block
    } // closes Scaffold content lambda
} // closes StudyDashboardScreen Composable

@Composable
fun DiagnosticValueCard(
    title: String,
    value: String,
    desc: String,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Slate200),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Slate500,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                ),
                color = HighDensityBlue
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = desc,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = Slate400,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}
