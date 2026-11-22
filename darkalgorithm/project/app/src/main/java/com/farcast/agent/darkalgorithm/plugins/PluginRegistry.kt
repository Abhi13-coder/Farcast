package com.farcast.agent.darkalgorithm.plugins

import androidx.room.*
import kotlinx.serialization.json.Json

@Entity(tableName = "installed_plugins")
data class InstalledPluginRow(
    @PrimaryKey val id: String,
    val manifestJson: String,     // full PluginManifest, so a disabled/updated catalogue entry doesn't orphan an install
    val localFilePath: String,
    val enabled: Boolean = true,
    val installedAtEpochMs: Long
)

@Dao
interface InstalledPluginDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: InstalledPluginRow)

    @Query("SELECT * FROM installed_plugins")
    suspend fun all(): List<InstalledPluginRow>

    @Query("SELECT * FROM installed_plugins WHERE enabled = 1")
    suspend fun allEnabled(): List<InstalledPluginRow>

    @Query("DELETE FROM installed_plugins WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE installed_plugins SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)
}

@Database(entities = [InstalledPluginRow::class], version = 1)
abstract class PluginDatabase : RoomDatabase() {
    abstract fun installedPluginDao(): InstalledPluginDao
}

/** Convenience wrapper so callers work with PluginManifest, not raw rows. */
class PluginRegistry(private val db: PluginDatabase) {
    private val json = Json { ignoreUnknownKeys = true }

    data class InstalledPlugin(val manifest: PluginManifest, val localFilePath: String, val enabled: Boolean)

    suspend fun record(manifest: PluginManifest, localFilePath: String) {
        db.installedPluginDao().insert(
            InstalledPluginRow(
                id = manifest.id,
                manifestJson = json.encodeToString(PluginManifest.serializer(), manifest),
                localFilePath = localFilePath,
                installedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun remove(id: String) = db.installedPluginDao().delete(id)
    suspend fun setEnabled(id: String, enabled: Boolean) = db.installedPluginDao().setEnabled(id, enabled)

    suspend fun listInstalled(onlyEnabled: Boolean = true): List<InstalledPlugin> =
        (if (onlyEnabled) db.installedPluginDao().allEnabled() else db.installedPluginDao().all())
            .map { row ->
                InstalledPlugin(json.decodeFromString(PluginManifest.serializer(), row.manifestJson), row.localFilePath, row.enabled)
            }
}
