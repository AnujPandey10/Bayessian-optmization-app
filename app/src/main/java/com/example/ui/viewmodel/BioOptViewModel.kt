package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.ml.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BioOptViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = BioOptRepository(
        database.projectDao(),
        database.studyDao(),
        database.experimentDao()
    )

    private val converters = Converters()

    // Exposed Reactive State list
    val projects: StateFlow<List<Project>> = repository.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentProject = MutableStateFlow<Project?>(null)

    // Studies list for current project
    val studies: StateFlow<List<Study>> = currentProject
        .flatMapLatest { project ->
            if (project != null) {
                repository.getStudies(project.id)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentStudy = MutableStateFlow<Study?>(null)

    // Historical experiments of current study
    val currentExperiments: StateFlow<List<Experiment>> = currentStudy
        .flatMapLatest { study ->
            if (study != null) {
                repository.getExperiments(study.id)
            } else {
                flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active ML Models & Recommendation engine states
    var activeGP: GaussianProcess? = null
        private set

    val gpMetrics = MutableStateFlow<GPMetrics?>(null)
    val recommendations = MutableStateFlow<List<BayesianOptimizer.Recommendation>>(emptyList())
    val isTraining = MutableStateFlow(false)
    val trainingError = MutableStateFlow<String?>(null)

    // Initiate ViewModel triggers
    init {
        // Trigger model retrains when current experiments list or configuration parameter of the study changes
        viewModelScope.launch {
            combine(currentStudy, currentExperiments) { study, experiments ->
                Pair(study, experiments)
            }.collectLatest { (study, experiments) ->
                if (study != null && experiments.isNotEmpty()) {
                    retrainGPRModel(study, experiments)
                } else {
                    activeGP = null
                    gpMetrics.value = null
                    recommendations.value = emptyList()
                }
            }
        }
    }

    // Retrain Gaussian Process Regressor and calculate metrics
    private suspend fun retrainGPRModel(study: Study, experiments: List<Experiment>) {
        isTraining.value = true
        trainingError.value = null

        withContext(Dispatchers.Default) {
            try {
                val factors = converters.stringToFactors(study.factorsJson)
                val responses = converters.stringToResponses(study.responsesJson)
                val optIndex = study.targetResponseIndex

                // Extract training data
                val validExp = experiments.filter { exp ->
                    val yList = converters.stringToDoubleList(exp.responseValuesJson ?: "")
                    yList.isNotEmpty() && optIndex < yList.size
                }

                if (validExp.size < 2) {
                    gpMetrics.value = null
                    // Still generates explorative recommendations even if size is small (e.g. random grid initial sampling)
                    val unusedGP = GaussianProcess(
                        kernelType = KernelType.valueOf(study.kernelType),
                        lengthScale = 1.0,
                        signalVariance = 1.0,
                        noiseVariance = 1e-4
                    )
                    activeGP = unusedGP

                    val bo = BayesianOptimizer()
                    val bounds = factors.map { BayesianOptimizer.FactorBounds(it.name, it.minVal, it.maxVal) }
                    val recs = bo.suggestNext(
                        gp = unusedGP,
                        factors = bounds,
                        historicalX = emptyList(),
                        historicalY = doubleArrayOf(),
                        acquisitionType = AcquisitionType.valueOf(study.acquisitionFunction),
                        goal = OptimizationGoal.valueOf(study.targetGoal),
                        count = 10
                    )
                    recommendations.value = recs
                    isTraining.value = false
                    return@withContext
                }

                // Construct matrices
                val X = ArrayList<DoubleArray>()
                val yList = ArrayList<Double>()

                for (exp in validExp) {
                    val xArr = converters.stringToDoubleList(exp.factorValuesJson).toDoubleArray()
                    val yArr = converters.stringToDoubleList(exp.responseValuesJson ?: "")
                    if (xArr.size == factors.size && yArr.size > optIndex) {
                        X.add(xArr)
                        yList.add(yArr[optIndex])
                    }
                }

                val y = yList.toDoubleArray()

                // Fit GP model
                val gp = GaussianProcess(
                    kernelType = KernelType.valueOf(study.kernelType),
                    lengthScale = 1.2, // robust length scale default
                    signalVariance = 1.0,
                    noiseVariance = 1e-3 // process observation standard noise
                )

                val success = gp.fit(X, y)
                if (success) {
                    activeGP = gp

                    // Calculate predictions on training data to generate metrics
                    val predictions = DoubleArray(X.size)
                    for (i in X.indices) {
                        predictions[i] = gp.predict(X[i]).mean
                    }

                    // Compute metrics
                    val metrics = gp.calculateMetrics(y, predictions)
                    gpMetrics.value = metrics

                    // Suggest next experiments
                    val bo = BayesianOptimizer()
                    val bounds = factors.map { BayesianOptimizer.FactorBounds(it.name, it.minVal, it.maxVal) }
                    val recs = bo.suggestNext(
                        gp = gp,
                        factors = bounds,
                        historicalX = X,
                        historicalY = y,
                        acquisitionType = AcquisitionType.valueOf(study.acquisitionFunction),
                        goal = OptimizationGoal.valueOf(study.targetGoal),
                        count = 10
                    )
                    recommendations.value = recs
                } else {
                    trainingError.value = "Failed to decompose covariance matrix. Please ensure input variables are distinct."
                }
            } catch (e: Exception) {
                trainingError.value = "Error compiling models: ${e.message}"
            } finally {
                isTraining.value = false
            }
        }
    }

    // Force run training
    fun forceRetrain() {
        viewModelScope.launch {
            val study = currentStudy.value
            val exps = currentExperiments.value
            if (study != null) {
                retrainGPRModel(study, exps)
            }
        }
    }

    // ------------------------------------------------------------------------
    // DB Modifiers
    // ------------------------------------------------------------------------

    fun selectProject(project: Project?) {
        currentProject.value = project
        currentStudy.value = null
    }

    fun selectStudy(study: Study?) {
        currentStudy.value = study
    }

    fun addProject(name: String, desc: String) {
        viewModelScope.launch {
            val project = Project(name = name, description = desc)
            val pId = repository.insertProject(project)
            selectProject(project.copy(id = pId.toInt()))
        }
    }

    fun deleteProject(project: Project) {
        viewModelScope.launch {
            repository.deleteProject(project)
            if (currentProject.value?.id == project.id) {
                selectProject(null)
            }
        }
    }

    fun addStudy(
        name: String,
        desc: String,
        targetGoal: String,
        targetRepIdx: Int,
        factors: List<Factor>,
        responses: List<Response>,
        kernel: String = "RBF",
        acq: String = "EI"
    ) {
        val proj = currentProject.value ?: return
        viewModelScope.launch {
            val study = Study(
                projectId = proj.id,
                name = name,
                description = desc,
                targetGoal = targetGoal,
                targetResponseIndex = targetRepIdx,
                factorsJson = converters.factorsToString(factors),
                responsesJson = converters.responsesToString(responses),
                kernelType = kernel,
                acquisitionFunction = acq
            )
            val sId = repository.insertStudy(study)
            selectStudy(study.copy(id = sId.toInt()))
        }
    }

    fun updateStudyConfig(kernel: String, acq: String, targetGoal: String, optIndex: Int) {
        val study = currentStudy.value ?: return
        viewModelScope.launch {
            val updated = study.copy(
                kernelType = kernel,
                acquisitionFunction = acq,
                targetGoal = targetGoal,
                targetResponseIndex = optIndex
            )
            repository.updateStudy(updated)
            currentStudy.value = updated
        }
    }

    fun deleteStudy(study: Study) {
        viewModelScope.launch {
            repository.deleteStudy(study)
            if (currentStudy.value?.id == study.id) {
                selectStudy(null)
            }
        }
    }

    fun addExperiment(factorValues: List<Double>, responseValues: List<Double>?) {
        val study = currentStudy.value ?: return
        viewModelScope.launch {
            val exp = Experiment(
                studyId = study.id,
                factorValuesJson = converters.doubleListToString(factorValues),
                responseValuesJson = converters.doubleListToString(responseValues ?: emptyList()),
                isRecommendation = false
            )
            repository.insertExperiment(exp)
        }
    }

    // Convert recommended experiment from Queue into run historical entry
    fun recordRecommendationRun(values: DoubleArray, actualResponses: List<Double>) {
        val study = currentStudy.value ?: return
        viewModelScope.launch {
            val exp = Experiment(
                studyId = study.id,
                factorValuesJson = converters.doubleListToString(values.toList()),
                responseValuesJson = converters.doubleListToString(actualResponses),
                isRecommendation = true
            )
            repository.insertExperiment(exp)
        }
    }

    fun deleteExperiment(experiment: Experiment) {
        viewModelScope.launch {
            repository.deleteExperiment(experiment)
        }
    }

    fun clearExperiments() {
        val study = currentStudy.value ?: return
        viewModelScope.launch {
            repository.clearExperiments(study.id)
        }
    }

    // Helper: Import templates easily
    fun importTemplateProject(type: String) {
        viewModelScope.launch {
            val (projName, pDesc) = when (type) {
                "refolding" -> "Insulin Bio-Refolding" to "Industrial process optimization of downstream insulin refolding."
                "digestion" -> "Insulin Degludec Digestion" to "High-purity enzymatic cleavage optimization experiments."
                else -> "Recombinant Proinsulin Fermentation" to "Upstream bioreactor yield and biomass optimization project."
            }

            val pId = repository.insertProject(Project(name = projName, description = pDesc))

            val study = when (type) {
                "refolding" -> {
                    val factors = listOf(
                        Factor("pH", 7.5, 11.0, "pH"),
                        Factor("Redox Ratio (GSH/GSSG)", 1.0, 15.0, "ratio"),
                        Factor("Protein Conc", 0.1, 5.0, "mg/mL")
                    )
                    val responses = listOf(
                        Response("Folding Yield", "%"),
                        Response("Aggr Formation", "%"),
                        Response("Total Recovery", "%")
                    )
                    Study(
                        projectId = pId.toInt(),
                        name = "Refolding Phase-II Optimization",
                        description = "Optimizing refolding yield while minimizing aggregation and stabilizing protein concentration.",
                        targetGoal = "MAXIMIZE",
                        targetResponseIndex = 0,
                        factorsJson = converters.factorsToString(factors),
                        responsesJson = converters.responsesToString(responses),
                        kernelType = "RBF",
                        acquisitionFunction = "EI"
                    )
                }
                "digestion" -> {
                    val factors = listOf(
                        Factor("Trypsin Ratio", 0.5, 5.0, "% w/w"),
                        Factor("Temperature", 25.0, 45.0, "°C"),
                        Factor("Digestion Time", 2.0, 24.0, "hrs"),
                        Factor("pH", 6.5, 8.5, "pH")
                    )
                    val responses = listOf(
                        Response("Conversion", "%"),
                        Response("Product Purity", "%")
                    )
                    Study(
                        projectId = pId.toInt(),
                        name = "Trypsin Cleavage Optimization",
                        description = "Achieve maximum target conversion and purity profiles.",
                        targetGoal = "MAXIMIZE",
                        targetResponseIndex = 0,
                        factorsJson = converters.factorsToString(factors),
                        responsesJson = converters.responsesToString(responses),
                        kernelType = "MATERN_5_2",
                        acquisitionFunction = "UCB"
                    )
                }
                else -> {
                    val factors = listOf(
                        Factor("Feed Rate", 5.0, 50.0, "mL/hr"),
                        Factor("Temperature", 28.0, 39.0, "°C"),
                        Factor("Dissolved Oxygen (DO)", 10.0, 60.0, "%"),
                        Factor("pH", 6.6, 7.4, "pH"),
                        Factor("Induction Time", 4.0, 20.0, "hrs")
                    )
                    val responses = listOf(
                        Response("Proinsulin Conc", "g/L"),
                        Response("Biomass", "g/L"),
                        Response("Product Yield", "g/g-feed")
                    )
                    Study(
                        projectId = pId.toInt(),
                        name = "Fermentation Batch Tuning",
                        description = "Increasing proinsulin volumetric concentration and biomass efficiency.",
                        targetGoal = "MAXIMIZE",
                        targetResponseIndex = 0,
                        factorsJson = converters.factorsToString(factors),
                        responsesJson = converters.responsesToString(responses),
                        kernelType = "MATERN_3_2",
                        acquisitionFunction = "EI"
                    )
                }
            }

            val sId = repository.insertStudy(study)

            // Feed pre-installed points so model runs right away!
            val experiments = when (type) {
                "refolding" -> listOf(
                    Experiment(studyId = sId.toInt(), factorValuesJson = converters.doubleListToString(listOf(8.5, 3.0, 1.0)), responseValuesJson = converters.doubleListToString(listOf(45.0, 8.0, 88.0))),
                    Experiment(studyId = sId.toInt(), factorValuesJson = converters.doubleListToString(listOf(9.5, 5.0, 1.5)), responseValuesJson = converters.doubleListToString(listOf(72.0, 12.0, 84.0))),
                    Experiment(studyId = sId.toInt(), factorValuesJson = converters.doubleListToString(listOf(10.5, 10.0, 3.0)), responseValuesJson = converters.doubleListToString(listOf(82.0, 25.0, 71.0))),
                    Experiment(studyId = sId.toInt(), factorValuesJson = converters.doubleListToString(listOf(9.0, 8.0, 2.0)), responseValuesJson = converters.doubleListToString(listOf(68.0, 14.0, 80.0))),
                    Experiment(studyId = sId.toInt(), factorValuesJson = converters.doubleListToString(listOf(11.0, 2.0, 4.5)), responseValuesJson = converters.doubleListToString(listOf(30.0, 42.0, 55.0)))
                )
                "digestion" -> listOf(
                    Experiment(studyId = sId.toInt(), factorValuesJson = converters.doubleListToString(listOf(1.0, 30.0, 4.0, 7.0)), responseValuesJson = converters.doubleListToString(listOf(35.0, 82.0))),
                    Experiment(studyId = sId.toInt(), factorValuesJson = converters.doubleListToString(listOf(2.5, 37.0, 12.0, 7.5)), responseValuesJson = converters.doubleListToString(listOf(78.0, 91.0))),
                    Experiment(studyId = sId.toInt(), factorValuesJson = converters.doubleListToString(listOf(5.0, 42.0, 18.0, 8.0)), responseValuesJson = converters.doubleListToString(listOf(94.0, 88.0))),
                    Experiment(studyId = sId.toInt(), factorValuesJson = converters.doubleListToString(listOf(0.5, 37.0, 24.0, 7.5)), responseValuesJson = converters.doubleListToString(listOf(52.0, 93.0))),
                    Experiment(studyId = sId.toInt(), factorValuesJson = converters.doubleListToString(listOf(4.0, 25.0, 8.0, 8.5)), responseValuesJson = converters.doubleListToString(listOf(42.0, 79.0)))
                )
                else -> listOf(
                    Experiment(studyId = sId.toInt(), factorValuesJson = converters.doubleListToString(listOf(10.0, 30.0, 20.0, 6.8, 6.0)), responseValuesJson = converters.doubleListToString(listOf(0.8, 12.0, 0.15))),
                    Experiment(studyId = sId.toInt(), factorValuesJson = converters.doubleListToString(listOf(30.0, 37.0, 40.0, 7.0, 12.0)), responseValuesJson = converters.doubleListToString(listOf(2.4, 32.0, 0.28))),
                    Experiment(studyId = sId.toInt(), factorValuesJson = converters.doubleListToString(listOf(45.0, 39.0, 50.0, 7.2, 18.0)), responseValuesJson = converters.doubleListToString(listOf(3.6, 45.0, 0.32))),
                    Experiment(studyId = sId.toInt(), factorValuesJson = converters.doubleListToString(listOf(20.0, 35.0, 30.0, 7.0, 10.0)), responseValuesJson = converters.doubleListToString(listOf(1.8, 24.0, 0.22))),
                    Experiment(studyId = sId.toInt(), factorValuesJson = converters.doubleListToString(listOf(50.0, 28.0, 15.0, 7.4, 20.0)), responseValuesJson = converters.doubleListToString(listOf(1.1, 18.0, 0.12)))
                )
            }

            for (exp in experiments) {
                repository.insertExperiment(exp)
            }

            // Set as current selection
            selectProject(Project(id = pId.toInt(), name = projName, description = pDesc))
            selectStudy(study.copy(id = sId.toInt()))
        }
    }
}
