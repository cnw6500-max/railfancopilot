package com.railfancopilot.app.`data`.repository

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class RailFanDatabase_Impl : RailFanDatabase() {
  private val _savedLocationDao: Lazy<SavedLocationDao> = lazy {
    SavedLocationDao_Impl(this)
  }

  private val _communityReportDao: Lazy<CommunityReportDao> = lazy {
    CommunityReportDao_Impl(this)
  }

  private val _photoMetadataDao: Lazy<PhotoMetadataDao> = lazy {
    PhotoMetadataDao_Impl(this)
  }

  private val _locoIdEntryDao: Lazy<LocoIdEntryDao> = lazy {
    LocoIdEntryDao_Impl(this)
  }

  private val _symbolDecodeEntryDao: Lazy<SymbolDecodeEntryDao> = lazy {
    SymbolDecodeEntryDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(6,
        "b39121fc15d70431435307c944263d1f", "ab2597293ddc18a7c284cac5a8ac566b") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `saved_locations` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `notes` TEXT, `subdivision` TEXT, `scannerFrequency` TEXT, `photoTips` TEXT, `createdMs` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `community_reports` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `userName` TEXT NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `text` TEXT NOT NULL, `trainSymbol` TEXT, `railroad` TEXT, `tags` TEXT NOT NULL, `timestampMs` INTEGER NOT NULL, `upvotes` INTEGER NOT NULL, `isVerified` INTEGER NOT NULL, `localPhotoPath` TEXT, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `tagged_photos` (`id` TEXT NOT NULL, `railroad` TEXT, `trainSymbol` TEXT, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `locationName` TEXT, `timestampMs` INTEGER NOT NULL, `locoModel` TEXT, `notes` TEXT, `localPath` TEXT, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `loco_id_history` (`id` TEXT NOT NULL, `resultText` TEXT NOT NULL, `thumbnailPath` TEXT, `timestampMs` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `symbol_decode_history` (`id` TEXT NOT NULL, `symbol` TEXT NOT NULL, `railroad` TEXT NOT NULL, `type` TEXT NOT NULL, `origin` TEXT NOT NULL, `destination` TEXT NOT NULL, `timestampMs` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'b39121fc15d70431435307c944263d1f')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `saved_locations`")
        connection.execSQL("DROP TABLE IF EXISTS `community_reports`")
        connection.execSQL("DROP TABLE IF EXISTS `tagged_photos`")
        connection.execSQL("DROP TABLE IF EXISTS `loco_id_history`")
        connection.execSQL("DROP TABLE IF EXISTS `symbol_decode_history`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection):
          RoomOpenDelegate.ValidationResult {
        val _columnsSavedLocations: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsSavedLocations.put("id", TableInfo.Column("id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSavedLocations.put("name", TableInfo.Column("name", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSavedLocations.put("latitude", TableInfo.Column("latitude", "REAL", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSavedLocations.put("longitude", TableInfo.Column("longitude", "REAL", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSavedLocations.put("notes", TableInfo.Column("notes", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSavedLocations.put("subdivision", TableInfo.Column("subdivision", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSavedLocations.put("scannerFrequency", TableInfo.Column("scannerFrequency", "TEXT",
            false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSavedLocations.put("photoTips", TableInfo.Column("photoTips", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSavedLocations.put("createdMs", TableInfo.Column("createdMs", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysSavedLocations: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesSavedLocations: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoSavedLocations: TableInfo = TableInfo("saved_locations", _columnsSavedLocations,
            _foreignKeysSavedLocations, _indicesSavedLocations)
        val _existingSavedLocations: TableInfo = read(connection, "saved_locations")
        if (!_infoSavedLocations.equals(_existingSavedLocations)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |saved_locations(com.railfancopilot.app.data.models.SavedLocation).
              | Expected:
              |""".trimMargin() + _infoSavedLocations + """
              |
              | Found:
              |""".trimMargin() + _existingSavedLocations)
        }
        val _columnsCommunityReports: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsCommunityReports.put("id", TableInfo.Column("id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCommunityReports.put("userId", TableInfo.Column("userId", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCommunityReports.put("userName", TableInfo.Column("userName", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCommunityReports.put("latitude", TableInfo.Column("latitude", "REAL", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCommunityReports.put("longitude", TableInfo.Column("longitude", "REAL", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCommunityReports.put("text", TableInfo.Column("text", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCommunityReports.put("trainSymbol", TableInfo.Column("trainSymbol", "TEXT", false,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCommunityReports.put("railroad", TableInfo.Column("railroad", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCommunityReports.put("tags", TableInfo.Column("tags", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsCommunityReports.put("timestampMs", TableInfo.Column("timestampMs", "INTEGER", true,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCommunityReports.put("upvotes", TableInfo.Column("upvotes", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCommunityReports.put("isVerified", TableInfo.Column("isVerified", "INTEGER", true,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCommunityReports.put("localPhotoPath", TableInfo.Column("localPhotoPath", "TEXT",
            false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysCommunityReports: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesCommunityReports: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoCommunityReports: TableInfo = TableInfo("community_reports",
            _columnsCommunityReports, _foreignKeysCommunityReports, _indicesCommunityReports)
        val _existingCommunityReports: TableInfo = read(connection, "community_reports")
        if (!_infoCommunityReports.equals(_existingCommunityReports)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |community_reports(com.railfancopilot.app.data.models.CommunityReport).
              | Expected:
              |""".trimMargin() + _infoCommunityReports + """
              |
              | Found:
              |""".trimMargin() + _existingCommunityReports)
        }
        val _columnsTaggedPhotos: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsTaggedPhotos.put("id", TableInfo.Column("id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsTaggedPhotos.put("railroad", TableInfo.Column("railroad", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsTaggedPhotos.put("trainSymbol", TableInfo.Column("trainSymbol", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTaggedPhotos.put("latitude", TableInfo.Column("latitude", "REAL", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsTaggedPhotos.put("longitude", TableInfo.Column("longitude", "REAL", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsTaggedPhotos.put("locationName", TableInfo.Column("locationName", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTaggedPhotos.put("timestampMs", TableInfo.Column("timestampMs", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTaggedPhotos.put("locoModel", TableInfo.Column("locoModel", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsTaggedPhotos.put("notes", TableInfo.Column("notes", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsTaggedPhotos.put("localPath", TableInfo.Column("localPath", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysTaggedPhotos: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesTaggedPhotos: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoTaggedPhotos: TableInfo = TableInfo("tagged_photos", _columnsTaggedPhotos,
            _foreignKeysTaggedPhotos, _indicesTaggedPhotos)
        val _existingTaggedPhotos: TableInfo = read(connection, "tagged_photos")
        if (!_infoTaggedPhotos.equals(_existingTaggedPhotos)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |tagged_photos(com.railfancopilot.app.data.models.PhotoMetadata).
              | Expected:
              |""".trimMargin() + _infoTaggedPhotos + """
              |
              | Found:
              |""".trimMargin() + _existingTaggedPhotos)
        }
        val _columnsLocoIdHistory: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsLocoIdHistory.put("id", TableInfo.Column("id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsLocoIdHistory.put("resultText", TableInfo.Column("resultText", "TEXT", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLocoIdHistory.put("thumbnailPath", TableInfo.Column("thumbnailPath", "TEXT", false,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLocoIdHistory.put("timestampMs", TableInfo.Column("timestampMs", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysLocoIdHistory: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesLocoIdHistory: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoLocoIdHistory: TableInfo = TableInfo("loco_id_history", _columnsLocoIdHistory,
            _foreignKeysLocoIdHistory, _indicesLocoIdHistory)
        val _existingLocoIdHistory: TableInfo = read(connection, "loco_id_history")
        if (!_infoLocoIdHistory.equals(_existingLocoIdHistory)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |loco_id_history(com.railfancopilot.app.data.models.LocoIdEntry).
              | Expected:
              |""".trimMargin() + _infoLocoIdHistory + """
              |
              | Found:
              |""".trimMargin() + _existingLocoIdHistory)
        }
        val _columnsSymbolDecodeHistory: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsSymbolDecodeHistory.put("id", TableInfo.Column("id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSymbolDecodeHistory.put("symbol", TableInfo.Column("symbol", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSymbolDecodeHistory.put("railroad", TableInfo.Column("railroad", "TEXT", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSymbolDecodeHistory.put("type", TableInfo.Column("type", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSymbolDecodeHistory.put("origin", TableInfo.Column("origin", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSymbolDecodeHistory.put("destination", TableInfo.Column("destination", "TEXT", true,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSymbolDecodeHistory.put("timestampMs", TableInfo.Column("timestampMs", "INTEGER",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysSymbolDecodeHistory: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesSymbolDecodeHistory: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoSymbolDecodeHistory: TableInfo = TableInfo("symbol_decode_history",
            _columnsSymbolDecodeHistory, _foreignKeysSymbolDecodeHistory,
            _indicesSymbolDecodeHistory)
        val _existingSymbolDecodeHistory: TableInfo = read(connection, "symbol_decode_history")
        if (!_infoSymbolDecodeHistory.equals(_existingSymbolDecodeHistory)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |symbol_decode_history(com.railfancopilot.app.data.models.SymbolDecodeEntry).
              | Expected:
              |""".trimMargin() + _infoSymbolDecodeHistory + """
              |
              | Found:
              |""".trimMargin() + _existingSymbolDecodeHistory)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "saved_locations",
        "community_reports", "tagged_photos", "loco_id_history", "symbol_decode_history")
  }

  public override fun clearAllTables() {
    super.performClear(false, "saved_locations", "community_reports", "tagged_photos",
        "loco_id_history", "symbol_decode_history")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(SavedLocationDao::class, SavedLocationDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(CommunityReportDao::class,
        CommunityReportDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(PhotoMetadataDao::class, PhotoMetadataDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(LocoIdEntryDao::class, LocoIdEntryDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(SymbolDecodeEntryDao::class,
        SymbolDecodeEntryDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override
      fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>):
      List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun savedLocationDao(): SavedLocationDao = _savedLocationDao.value

  public override fun communityReportDao(): CommunityReportDao = _communityReportDao.value

  public override fun photoMetadataDao(): PhotoMetadataDao = _photoMetadataDao.value

  public override fun locoIdEntryDao(): LocoIdEntryDao = _locoIdEntryDao.value

  public override fun symbolDecodeEntryDao(): SymbolDecodeEntryDao = _symbolDecodeEntryDao.value
}
