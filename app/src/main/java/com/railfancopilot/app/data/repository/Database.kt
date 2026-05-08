package com.railfancopilot.app.data.repository

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.railfancopilot.app.data.models.CommunityReport
import com.railfancopilot.app.data.models.LocoIdEntry
import com.railfancopilot.app.data.models.PhotoMetadata
import com.railfancopilot.app.data.models.SavedLocation
import com.railfancopilot.app.data.models.SymbolDecodeEntry
import kotlinx.coroutines.flow.Flow

// ── DAOs ─────────────────────────────────────────────────────────────────────

@Dao
interface SymbolDecodeEntryDao {
    @Query("SELECT * FROM symbol_decode_history ORDER BY timestampMs DESC LIMIT 50")
    fun getAllFlow(): Flow<List<SymbolDecodeEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: SymbolDecodeEntry)

    @Delete
    suspend fun delete(entry: SymbolDecodeEntry)

    /** Keep only the 50 most recent entries. */
    @Query("""
        DELETE FROM symbol_decode_history
        WHERE id NOT IN (
            SELECT id FROM symbol_decode_history ORDER BY timestampMs DESC LIMIT 50
        )
    """)
    suspend fun prune()

    @Query("DELETE FROM symbol_decode_history")
    suspend fun deleteAll()
}

@Dao
interface LocoIdEntryDao {
    @Query("SELECT * FROM loco_id_history ORDER BY timestampMs DESC LIMIT 50")
    fun getAllFlow(): Flow<List<LocoIdEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: LocoIdEntry)

    @Delete
    suspend fun delete(entry: LocoIdEntry)
}

@Dao
interface PhotoMetadataDao {
    @Query("SELECT * FROM tagged_photos ORDER BY timestampMs DESC")
    fun getAllFlow(): Flow<List<PhotoMetadata>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: PhotoMetadata)

    @Delete
    suspend fun delete(photo: PhotoMetadata)
}

@Dao
interface SavedLocationDao {
    @Query("SELECT * FROM saved_locations ORDER BY createdMs DESC")
    fun getAllFlow(): Flow<List<SavedLocation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: SavedLocation)

    @Delete
    suspend fun delete(location: SavedLocation)

    @Query("SELECT * FROM saved_locations WHERE id = :id")
    suspend fun getById(id: String): SavedLocation?
}

@Dao
interface CommunityReportDao {
    @Query("SELECT * FROM community_reports ORDER BY timestampMs DESC LIMIT 100")
    fun getRecentFlow(): Flow<List<CommunityReport>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: CommunityReport)

    @Query("""
        SELECT * FROM community_reports
        WHERE (latitude BETWEEN :minLat AND :maxLat)
          AND (longitude BETWEEN :minLon AND :maxLon)
        ORDER BY timestampMs DESC
    """)
    suspend fun getNearby(
        minLat: Double, maxLat: Double,
        minLon: Double, maxLon: Double
    ): List<CommunityReport>

    @Query("""
        SELECT * FROM community_reports
        WHERE (latitude BETWEEN :minLat AND :maxLat)
          AND (longitude BETWEEN :minLon AND :maxLon)
        ORDER BY timestampMs DESC
    """)
    fun getNearbyFlow(
        minLat: Double, maxLat: Double,
        minLon: Double, maxLon: Double
    ): Flow<List<CommunityReport>>

    @Query("SELECT * FROM community_reports ORDER BY timestampMs DESC LIMIT 200")
    fun getAllFlow(): Flow<List<CommunityReport>>
}

// ── Migrations ───────────────────────────────────────────────────────────────

/** v5 → v6: added localPhotoPath column to community_reports. */
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE community_reports ADD COLUMN localPhotoPath TEXT")
    }
}

// ── Database ─────────────────────────────────────────────────────────────────

@Database(
    entities = [
        SavedLocation::class,
        CommunityReport::class,
        PhotoMetadata::class,
        LocoIdEntry::class,
        SymbolDecodeEntry::class
    ],
    version = 6,
    exportSchema = false
)
abstract class RailFanDatabase : RoomDatabase() {
    abstract fun savedLocationDao(): SavedLocationDao
    abstract fun communityReportDao(): CommunityReportDao
    abstract fun photoMetadataDao(): PhotoMetadataDao
    abstract fun locoIdEntryDao(): LocoIdEntryDao
    abstract fun symbolDecodeEntryDao(): SymbolDecodeEntryDao

    companion object {
        @Volatile private var INSTANCE: RailFanDatabase? = null

        fun getInstance(context: Context): RailFanDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    RailFanDatabase::class.java,
                    "railfan_db"
                )
                .addMigrations(MIGRATION_5_6)
                .fallbackToDestructiveMigration(dropAllTables = true)   // fallback for pre-v5 installs
                .build().also { INSTANCE = it }
            }
    }
}
