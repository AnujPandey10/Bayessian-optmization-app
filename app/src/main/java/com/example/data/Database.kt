package com.example.data

import android.content.Context
import androidx.room.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow

// ------------------------------------------------------------------------
// Support Structures
// ------------------------------------------------------------------------

data class Factor(
    val name: String,
    val minVal: Double,
    val maxVal: Double,
    val unit: String
)

data class Response(
    val name: String,
    val unit: String
)

// ------------------------------------------------------------------------
// Room Entities
// ------------------------------------------------------------------------

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String,
    val createdTimestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "studies",
    foreignKeys = [
        ForeignKey(
            entity = Project::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class Study(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val projectId: Int,
    val name: String,
    val description: String,
    val targetGoal: String, // "MAXIMIZE" or "MINIMIZE"
    val targetResponseIndex: Int = 0, // Primary output index to optimize
    val factorsJson: String, // List<Factor>
    val responsesJson: String, // List<Response>
    val kernelType: String = "RBF",
    val acquisitionFunction: String = "EI",
    val createdTimestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "experiments",
    foreignKeys = [
        ForeignKey(
            entity = Study::class,
            parentColumns = ["id"],
            childColumns = ["studyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["studyId"])]
)
data class Experiment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val studyId: Int,
    val factorValuesJson: String, // DoubleArray -> List<Double>
    val responseValuesJson: String, // DoubleArray? -> List<Double>? (null means recommended but not yet evaluated)
    val timestamp: Long = System.currentTimeMillis(),
    val isRecommendation: Boolean = false,
    val predictedMean: Double? = null,
    val predictedStdDev: Double? = null
)

// ------------------------------------------------------------------------
// JSON Type Converters
// ------------------------------------------------------------------------

class Converters {
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val factorListType = Types.newParameterizedType(List::class.java, Factor::class.java)
    val factorAdapter = moshi.adapter<List<Factor>>(factorListType)

    private val responseListType = Types.newParameterizedType(List::class.java, Response::class.java)
    val responseAdapter = moshi.adapter<List<Response>>(responseListType)

    private val doubleListType = Types.newParameterizedType(List::class.java, Double::class.javaObjectType)
    val doubleListAdapter = moshi.adapter<List<Double>>(doubleListType)

    // Helper utilities for manual serialize / deserialize outside Room too
    fun factorsToString(factors: List<Factor>): String {
        return factorAdapter.toJson(factors) ?: "[]"
    }

    fun stringToFactors(str: String): List<Factor> {
        return try {
            factorAdapter.fromJson(str) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun responsesToString(responses: List<Response>): String {
        return responseAdapter.toJson(responses) ?: "[]"
    }

    fun stringToResponses(str: String): List<Response> {
        return try {
            responseAdapter.fromJson(str) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun doubleListToString(values: List<Double>?): String {
        if (values == null) return "[]"
        return doubleListAdapter.toJson(values) ?: "[]"
    }

    fun stringToDoubleList(str: String): List<Double> {
        return try {
            doubleListAdapter.fromJson(str) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

// ------------------------------------------------------------------------
// Room DAOs
// ------------------------------------------------------------------------

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY createdTimestamp DESC")
    fun getAllProjects(): Flow<List<Project>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: Project): Long

    @Delete
    suspend fun deleteProject(project: Project)
}

@Dao
interface StudyDao {
    @Query("SELECT * FROM studies WHERE projectId = :projectId ORDER BY createdTimestamp DESC")
    fun getStudiesForProject(projectId: Int): Flow<List<Study>>

    @Query("SELECT * FROM studies WHERE id = :studyId LIMIT 1")
    suspend fun getStudyById(studyId: Int): Study?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudy(study: Study): Long

    @Update
    suspend fun updateStudy(study: Study)

    @Delete
    suspend fun deleteStudy(study: Study)
}

@Dao
interface ExperimentDao {
    @Query("SELECT * FROM experiments WHERE studyId = :studyId ORDER BY timestamp ASC")
    fun getExperimentsForStudy(studyId: Int): Flow<List<Experiment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExperiment(experiment: Experiment): Long

    @Delete
    suspend fun deleteExperiment(experiment: Experiment)

    @Query("DELETE FROM experiments WHERE studyId = :studyId")
    suspend fun clearExperimentsForStudy(studyId: Int)
}

// ------------------------------------------------------------------------
// AppDatabase
// ------------------------------------------------------------------------

@Database(entities = [Project::class, Study::class, Experiment::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun studyDao(): StudyDao
    abstract fun experimentDao(): ExperimentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bioopt_ai_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
