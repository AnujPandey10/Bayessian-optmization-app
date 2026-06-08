

# BioOpt AI

BioOpt AI is an Android application designed for AI-driven bioprocess optimization using Bayesian optimization and Gaussian Process regression. The application helps in optimizing studies and tracking project progression through intelligent insights and data analysis.


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
