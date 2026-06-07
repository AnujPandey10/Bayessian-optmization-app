package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.ui.screens.ProjectListScreen
import com.example.ui.screens.StudyDashboardScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.BioOptViewModel

enum class ScreenState {
    PROJECT_LIST,
    STUDY_DASHBOARD
}

class MainActivity : ComponentActivity() {
    private val viewModel: BioOptViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                var currentScreen by remember { mutableStateOf(ScreenState.PROJECT_LIST) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (currentScreen) {
                        ScreenState.PROJECT_LIST -> {
                            ProjectListScreen(
                                viewModel = viewModel,
                                onProjectSelected = {
                                    currentScreen = ScreenState.STUDY_DASHBOARD
                                }
                            )
                        }
                        ScreenState.STUDY_DASHBOARD -> {
                            StudyDashboardScreen(
                                viewModel = viewModel,
                                onBackToProjects = {
                                    currentScreen = ScreenState.PROJECT_LIST
                                    viewModel.selectProject(null)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
