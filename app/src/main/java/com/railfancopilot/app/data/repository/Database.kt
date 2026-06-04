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
import com.railfancopilot.app.data.models.TimetableCacheEntry
import com.railfancopilot.app.data.models.TrainTrailWaypoint
import com.railfancopilot.app.data.models.TripLog
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

@Dao
interface TrainTrailWaypointDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(waypoint: TrainTrailWaypoint)

    /** All waypoints for a train since [sinceMs], oldest-first. */
    @Query("SELECT * FROM train_trail_waypoints WHERE trainId = :trainId AND timestampMs >= :sinceMs ORDER BY timestampMs ASC")
    suspend fun getTrailSince(trainId: String, sinceMs: Long): List<TrainTrailWaypoint>

    /** Latest waypoint for a train (used to check minimum movement before writing). */
    @Query("SELECT * FROM train_trail_waypoints WHERE trainId = :trainId ORDER BY timestampMs DESC LIMIT 1")
    suspend fun getLatestWaypoint(trainId: String): TrainTrailWaypoint?

    /** All waypoints across all trains since [sinceMs] — used for startup trail seed. */
    @Query("SELECT * FROM train_trail_waypoints WHERE timestampMs >= :sinceMs ORDER BY trainId, timestampMs ASC")
    suspend fun getAllSince(sinceMs: Long): List<TrainTrailWaypoint>

    /** Prune waypoints older than [beforeMs] — called on startup to enforce 14-day window. */
    @Query("DELETE FROM train_trail_waypoints WHERE timestampMs < :beforeMs")
    suspend fun pruneOlderThan(beforeMs: Long)
}

@Dao
interface TripLogDao {

    @Query("SELECT * FROM trip_logs ORDER BY startMs DESC")
    fun getAllFlow(): Flow<List<TripLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trip: TripLog)

    @Update
    suspend fun update(trip: TripLog)

    @Delete
    suspend fun delete(trip: TripLog)

    /** Returns the in-progress trip if one exists (endMs == 0). */
    @Query("SELECT * FROM trip_logs WHERE endMs = 0 LIMIT 1")
    suspend fun getActiveTrip(): TripLog?

    /** Totals for the stats header — excludes the active trip. */
    @Query("SELECT COALESCE(SUM(distanceMiles),0) FROM trip_logs WHERE endMs > 0")
    suspend fun totalMiles(): Double

    @Query("SELECT COUNT(*) FROM trip_logs WHERE endMs > 0")
    suspend fun completedTripCount(): Int
}

// ── Migrations ───────────────────────────────────────────────────────────────

/** v5 → v6: added localPhotoPath column to community_reports. */
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE community_reports ADD COLUMN localPhotoPath TEXT")
    }
}

/** v6 → v7: added consist, weather, locationName columns to community_reports. */
private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE community_reports ADD COLUMN consist TEXT")
        db.execSQL("ALTER TABLE community_reports ADD COLUMN weather TEXT")
        db.execSQL("ALTER TABLE community_reports ADD COLUMN locationName TEXT NOT NULL DEFAULT ''")
    }
}

@Dao
interface TimetableCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TimetableCacheEntry)

    @Query("SELECT * FROM timetable_cache WHERE trainId = :trainId LIMIT 1")
    suspend fun get(trainId: String): TimetableCacheEntry?

    @Query("DELETE FROM timetable_cache WHERE fetchedMs < :beforeMs")
    suspend fun pruneOlderThan(beforeMs: Long)
}

/** v8 → v9: added timetable_cache table. */
private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS timetable_cache (
                trainId TEXT NOT NULL PRIMARY KEY,
                stopsJson TEXT NOT NULL,
                fetchedMs INTEGER NOT NULL
            )
        """.trimIndent())
    }
}

/** v7 → v8: added train_trail_waypoints and trip_logs tables. */
private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS train_trail_waypoints (
                id TEXT NOT NULL PRIMARY KEY,
                trainId TEXT NOT NULL,
                trainSymbol TEXT NOT NULL,
                railroad TEXT NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                speedMph INTEGER NOT NULL,
                timestampMs INTEGER NOT NULL
            )
        """.trimIndent())
        // Index names must match Room's auto-generated convention: index_{tableName}_{columnName}
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_train_trail_waypoints_trainId` ON `train_trail_waypoints`(`trainId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_train_trail_waypoints_timestampMs` ON `train_trail_waypoints`(`timestampMs`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS trip_logs (
                id TEXT NOT NULL PRIMARY KEY,
                trainId TEXT NOT NULL,
                trainSymbol TEXT NOT NULL,
                railroad TEXT NOT NULL,
                startMs INTEGER NOT NULL,
                endMs INTEGER NOT NULL DEFAULT 0,
                distanceMiles REAL NOT NULL DEFAULT 0.0,
                boardingStation TEXT,
                alightingStation TEXT,
                notes TEXT
            )
        """.trimIndent())
    }
}

// ── Database ─────────────────────────────────────────────────────────────────

@Database(
    entities = [
        SavedLocation::class,
        CommunityReport::class,
        PhotoMetadata::class,
        LocoIdEntry::class,
        SymbolDecodeEntry::class,
        TrainTrailWaypoint::class,
        TripLog::class,
        TimetableCacheEntry::class
    ],
    version = 9,
    exportSchema = false
)
abstract class RailFanDatabase : RoomDatabase() {
    abstract fun savedLocationDao(): SavedLocationDao
    abstract fun communityReportDao(): CommunityReportDao
    abstract fun photoMetadataDao(): PhotoMetadataDao
    abstract fun locoIdEntryDao(): LocoIdEntryDao
    abstract fun symbolDecodeEntryDao(): SymbolDecodeEntryDao
    abstract fun trainTrailWaypointDao(): TrainTrailWaypointDao
    abstract fun tripLogDao(): TripLogDao
    abstract fun timetableCacheDao(): TimetableCacheDao

    companion object {
        @Volatile private var INSTANCE: RailFanDatabase? = null

        fun getInstance(context: Context): RailFanDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    RailFanDatabase::class.java,
                    "railfan_db"
                )
                .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build().also { INSTANCE = it }
            }
    }
}
