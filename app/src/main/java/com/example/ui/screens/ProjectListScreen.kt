package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Project
import com.example.ui.theme.*
import com.example.ui.viewmodel.BioOptViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
    viewModel: BioOptViewModel,
    onProjectSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    var newProjectDesc by remember { mutableStateOf("") }

    var showTemplateSelector by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "BioOpt AI Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Column {
                            Text(
                                "BioOpt AI",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                "AI-Driven Bioprocess Optimization Workspace",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showTemplateSelector = true },
                        modifier = Modifier.testTag("import_template_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Import scientific templates"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = "Create Project") },
                text = { Text("New Project") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .padding(bottom = 16.dp, end = 8.dp)
                    .testTag("floating_add_project_btn")
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp)
        ) {
            // High Density Top Divider for Edge-to-Edge feel
            Divider(color = Slate200, thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // Welcome Card/Dashboard Title
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Scientific Process Modeling",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "A robust Bayesian suite designed to characterization, optimize and guide downstream/upstream chemical process development.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Timeline Analytics",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Text(
                "My Projects",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            if (projects.isEmpty()) {
                // Empty state configuration placeholder
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Empty Projects Icon",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No Active Projects Available",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Import a pre-configured biological model template to start optimizing right away, or build a custom project.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showTemplateSelector = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.testTag("import_first_template_btn")
                        ) {
                            Icon(Icons.Default.Star, contentDescription = "Template", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Load Scientific Template")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(projects) { project ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                Slate200
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectProject(project)
                                    onProjectSelected()
                                }
                                .testTag("project_item_card_${project.id}")
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Home,
                                        contentDescription = "Project logo",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 12.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = project.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Created ${java.text.DateFormat.getDateInstance().format(java.util.Date(project.createdTimestamp))}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                        IconButton(
                                            onClick = { viewModel.deleteProject(project) },
                                            modifier = Modifier.testTag("delete_proj_${project.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete project",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = project.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Create Project Dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create Optimization Project") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = newProjectName,
                        onValueChange = { newProjectName = it },
                        label = { Text("Project Name (e.g., Insulin Refolding)") },
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
                            .testTag("project_name_input")
                    )
                    OutlinedTextField(
                        value = newProjectDesc,
                        onValueChange = { newProjectDesc = it },
                        label = { Text("Executive Description") },
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
                            .height(110.dp)
                            .testTag("project_desc_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newProjectName.isNotBlank()) {
                            viewModel.addProject(newProjectName, newProjectDesc)
                            newProjectName = ""
                            newProjectDesc = ""
                            showCreateDialog = false
                            onProjectSelected()
                        }
                    },
                    modifier = Modifier.testTag("confirm_create_project_btn")
                ) {
                    Text("Create Workspace")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Modal Template Selector Dialog
    if (showTemplateSelector) {
        AlertDialog(
            onDismissRequest = { showTemplateSelector = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Template Choice",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Load Scientific Template")
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Templates include preloaded multi-factor configurations and authentic historical test runs. Ideal for checking GP modeling instantly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.importTemplateProject("refolding")
                                showTemplateSelector = false
                                onProjectSelected()
                            }
                            .testTag("template_refolding_card")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("1. Insulin Bio-Refolding Downstream Model", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Factors: pH, Redox Ratio (GSH/GSSG), Protein Conc. Target Yield & Aggregates optimization.", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.importTemplateProject("digestion")
                                showTemplateSelector = false
                                onProjectSelected()
                            }
                            .testTag("template_digestion_card")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("2. Enzymatic Cleavage (Digestion) Optimization", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Factors: Trypsin Ratio, Temperature, pH, Digest Time. Conversion Yield & Purity targets.", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.importTemplateProject("fermentation")
                                showTemplateSelector = false
                                onProjectSelected()
                            }
                            .testTag("template_fermentation_card")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("3. Recombinant Fermentation Batch Model", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Factors: Feed Rate, DO, Temperature, pH, Induction duration. Optimizing volumetric concentration.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTemplateSelector = false }) {
                    Text("Close")
                }
            }
        )
    }
}
