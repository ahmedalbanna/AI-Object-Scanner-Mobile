package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "scan_reports")
data class DbReport(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val title: String,
    val category: String,
    val confidence: Double,
    val imageBase64: String?, // Preview base64 stream of scanned image
    val reportMarkdown: String, // Fully styled markdown text for the in-app display
    val language: String = "ar",
    
    // Extracted specific properties requested by FR-003 for direct usage
    val attributes: String = "",
    val dimensions: String = "",
    val weight: String = "",
    val uses: String = "",
    val care: String = "",
    
    // Coordinates overlay if available (for drawing boxes on saved scan)
    val boundingBox: String? = null, // Format: "ymin,xmin,ymax,xmax"
    
    // External knowledge base integration fields
    val wikipediaUrl: String? = null,
    val wikipediaSummary: String? = null,
    
    // Google Search Grounding sources serialized as JSON
    val googleSearchSourcesJson: String? = null,
    
    // Spoken voice notes path associated with report
    val audioPath: String? = null
)

@Dao
interface ReportDao {
    @Query("SELECT * FROM scan_reports ORDER BY timestamp DESC")
    fun getAllReports(): Flow<List<DbReport>>

    @Query("SELECT * FROM scan_reports WHERE title LIKE :searchQuery OR category LIKE :searchQuery OR reportMarkdown LIKE :searchQuery ORDER BY timestamp DESC")
    fun searchReports(searchQuery: String): Flow<List<DbReport>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: DbReport): Long

    @Update
    suspend fun updateReport(report: DbReport)

    @Query("UPDATE scan_reports SET audioPath = :audioPath WHERE id = :id")
    suspend fun updateAudioPath(id: Long, audioPath: String?)

    @Query("DELETE FROM scan_reports WHERE id = :id")
    suspend fun deleteReportById(id: Long)

    @Delete
    suspend fun deleteReport(report: DbReport)
}

@Database(entities = [DbReport::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reportDao(): ReportDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "object_scanner_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class ReportRepository(private val reportDao: ReportDao) {
    val allReports: Flow<List<DbReport>> = reportDao.getAllReports()

    fun searchReports(query: String): Flow<List<DbReport>> {
        return reportDao.searchReports("%$query%")
    }

    suspend fun insertReport(report: DbReport): Long {
        return reportDao.insertReport(report)
    }

    suspend fun updateReport(report: DbReport) {
        reportDao.updateReport(report)
    }

    suspend fun updateAudioPath(id: Long, audioPath: String?) {
        reportDao.updateAudioPath(id, audioPath)
    }

    suspend fun deleteReportById(id: Long) {
        reportDao.deleteReportById(id)
    }
}
