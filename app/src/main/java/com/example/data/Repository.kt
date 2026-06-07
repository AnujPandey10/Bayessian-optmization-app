package com.example.data

import kotlinx.coroutines.flow.Flow

class BioOptRepository(
    private val projectDao: ProjectDao,
    private val studyDao: StudyDao,
    private val experimentDao: ExperimentDao
) {
    val allProjects: Flow<List<Project>> = projectDao.getAllProjects()

    fun getStudies(projectId: Int): Flow<List<Study>> = studyDao.getStudiesForProject(projectId)

    suspend fun getStudyById(studyId: Int): Study? = studyDao.getStudyById(studyId)

    fun getExperiments(studyId: Int): Flow<List<Experiment>> = experimentDao.getExperimentsForStudy(studyId)

    suspend fun insertProject(project: Project): Long = projectDao.insertProject(project)

    suspend fun deleteProject(project: Project) = projectDao.deleteProject(project)

    suspend fun insertStudy(study: Study): Long = studyDao.insertStudy(study)

    suspend fun updateStudy(study: Study) = studyDao.updateStudy(study)

    suspend fun deleteStudy(study: Study) = studyDao.deleteStudy(study)

    suspend fun insertExperiment(experiment: Experiment): Long = experimentDao.insertExperiment(experiment)

    suspend fun deleteExperiment(experiment: Experiment) = experimentDao.deleteExperiment(experiment)

    suspend fun clearExperiments(studyId: Int) = experimentDao.clearExperimentsForStudy(studyId)
}
