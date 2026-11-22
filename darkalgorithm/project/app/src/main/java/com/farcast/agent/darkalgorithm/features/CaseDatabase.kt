package com.farcast.agent.darkalgorithm.features

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery

/**
 * #p-db. The website compiles SQLite to WASM (sql.js) because a browser has
 * no native database — it's working around a platform limit. Android has
 * real SQLite built in, and Room is the standard typed layer over it, so
 * this is a straight upgrade rather than a port: same "every finding is a
 * real queryable row" idea, backed by the platform's actual database engine
 * instead of a WASM build of one. `btn-db-run`'s raw-SQL box maps to
 * Room's @RawQuery for the same "let the user type SQL" behavior if that's
 * worth keeping; the common queries (`SELECT category, COUNT(*)...`) are
 * exposed as typed DAO methods below instead, which is safer by default.
 */
@Entity(tableName = "findings")
data class FindingRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val caseId: String,
    val category: String,      // matches CaseFile/Lead.branch
    val text: String,
    val lat: Double?,
    val lon: Double?,
    val createdAtEpochMs: Long
)

@Dao
interface FindingDao {
    @Insert
    suspend fun insert(row: FindingRow): Long

    @Query("SELECT * FROM findings WHERE caseId = :caseId ORDER BY createdAtEpochMs DESC")
    suspend fun forCase(caseId: String): List<FindingRow>

    @Query("SELECT category, COUNT(*) as count FROM findings WHERE caseId = :caseId GROUP BY category")
    suspend fun countsByCategory(caseId: String): List<CategoryCount>

    @Query("DELETE FROM findings WHERE caseId = :caseId")
    suspend fun clearCase(caseId: String)

    @Query("DELETE FROM findings")
    suspend fun clearAll()

    /** Equivalent of the site's free-text SQL box — kept for parity, but a
     *  RawQuery still only runs SELECTs Room considers safe against this
     *  table; it's not an open SQL console onto the whole app DB. */
    @RawQuery(observedEntities = [FindingRow::class])
    suspend fun rawQuery(query: SupportSQLiteQuery): List<FindingRow>
}

data class CategoryCount(val category: String, val count: Int)

/**
 * Moment Finder's index: one row per YOLO detection, tying a label back to
 * the exact Lead/image/timestamp it came from. "Search a label, get the
 * moment" is literally `findingDao... ` — er, `detectionDao().byLabel(...)`.
 */
@Entity(tableName = "detections")
data class DetectionRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val caseId: String,
    val leadId: String,          // which Lead's image this came from (e.g. a cam snapshot)
    val label: String,           // YoloLabels entry, e.g. "car", "person"
    val confidence: Float,
    val boxX: Float, val boxY: Float, val boxW: Float, val boxH: Float,  // normalized, see BoundingBox
    val imagePath: String,       // local file path to the frame/snapshot this detection is from
    val detectedAtEpochMs: Long
)

@Dao
interface DetectionDao {
    @Insert
    suspend fun insert(row: DetectionRow): Long

    @Insert
    suspend fun insertAll(rows: List<DetectionRow>)

    /** The actual "Moment Finder" query: every moment a given label showed up in this case. */
    @Query("SELECT * FROM detections WHERE caseId = :caseId AND label = :label ORDER BY detectedAtEpochMs DESC")
    suspend fun byLabel(caseId: String, label: String): List<DetectionRow>

    @Query("SELECT DISTINCT label FROM detections WHERE caseId = :caseId ORDER BY label")
    suspend fun distinctLabels(caseId: String): List<String>

    @Query("SELECT label, COUNT(*) as count FROM detections WHERE caseId = :caseId GROUP BY label ORDER BY count DESC")
    suspend fun labelCounts(caseId: String): List<LabelCount>

    @Query("DELETE FROM detections WHERE caseId = :caseId")
    suspend fun clearCase(caseId: String)
}

data class LabelCount(val label: String, val count: Int)

@Database(entities = [FindingRow::class, DetectionRow::class], version = 2)
abstract class CaseDatabase : RoomDatabase() {
    abstract fun findingDao(): FindingDao
    abstract fun detectionDao(): DetectionDao
}
