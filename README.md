<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# BioOpt AI

BioOpt AI is an Android application designed for AI-driven bioprocess optimization using Bayesian optimization and Gaussian Process regression. The application helps in optimizing studies and tracking project progression through intelligent insights and data analysis.

View your app in AI Studio: https://ai.studio/apps/1aeaf345-4286-469f-b594-f3789fddcfc5

## Features

- **Project Management:** Create, manage, and view different projects and studies.
- **Study Dashboard:** Monitor and analyze active studies with comprehensive optimization insights.
- **Machine Learning Integration:** Uses Gaussian Process regression and Bayesian Optimizer for data-driven decisions.
- **Interactive UI:** Built with Jetpack Compose for a reactive, smooth, and modern user experience.
- **Data Persistence:** Uses Room database for efficient and secure local data storage.

## Architecture

The project follows a standard modern Android architecture:
- **UI Layer:** Jetpack Compose components (`ProjectListScreen`, `StudyDashboardScreen`, etc.)
- **ViewModel:** `BioOptViewModel` acts as the bridge between UI and data layer, managing state using `StateFlow`.
- **Data Layer:** Room database (`AppDatabase`, `BioOptRepository`) for local data handling.
- **Machine Learning Layer:** Contains `GaussianProcess` and `BayesianOptimizer` to implement the AI logic.

## Run Locally

**Prerequisites:** [Android Studio](https://developer.android.com/studio)

1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device
