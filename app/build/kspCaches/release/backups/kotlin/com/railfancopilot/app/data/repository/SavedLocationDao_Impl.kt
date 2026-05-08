package com.railfancopilot.app.`data`.repository

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.railfancopilot.app.`data`.models.SavedLocation
import javax.`annotation`.processing.Generated
import kotlin.Double
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class SavedLocationDao_Impl(
  __db: RoomDatabase,
) : SavedLocationDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfSavedLocation: EntityInsertAdapter<SavedLocation>

  private val __deleteAdapterOfSavedLocation: EntityDeleteOrUpdateAdapter<SavedLocation>
  init {
    this.__db = __db
    this.__insertAdapterOfSavedLocation = object : EntityInsertAdapter<SavedLocation>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `saved_locations` (`id`,`name`,`latitude`,`longitude`,`notes`,`subdivision`,`scannerFrequency`,`photoTips`,`createdMs`) VALUES (?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: SavedLocation) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindDouble(3, entity.latitude)
        statement.bindDouble(4, entity.longitude)
        val _tmpNotes: String? = entity.notes
        if (_tmpNotes == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpNotes)
        }
        val _tmpSubdivision: String? = entity.subdivision
        if (_tmpSubdivision == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpSubdivision)
        }
        val _tmpScannerFrequency: String? = entity.scannerFrequency
        if (_tmpScannerFrequency == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpScannerFrequency)
        }
        val _tmpPhotoTips: String? = entity.photoTips
        if (_tmpPhotoTips == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpPhotoTips)
        }
        statement.bindLong(9, entity.createdMs)
      }
    }
    this.__deleteAdapterOfSavedLocation = object : EntityDeleteOrUpdateAdapter<SavedLocation>() {
      protected override fun createQuery(): String = "DELETE FROM `saved_locations` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: SavedLocation) {
        statement.bindText(1, entity.id)
      }
    }
  }

  public override suspend fun insert(location: SavedLocation): Unit = performSuspending(__db, false,
      true) { _connection ->
    __insertAdapterOfSavedLocation.insert(_connection, location)
  }

  public override suspend fun delete(location: SavedLocation): Unit = performSuspending(__db, false,
      true) { _connection ->
    __deleteAdapterOfSavedLocation.handle(_connection, location)
  }

  public override fun getAllFlow(): Flow<List<SavedLocation>> {
    val _sql: String = "SELECT * FROM saved_locations ORDER BY createdMs DESC"
    return createFlow(__db, false, arrayOf("saved_locations")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfLatitude: Int = getColumnIndexOrThrow(_stmt, "latitude")
        val _columnIndexOfLongitude: Int = getColumnIndexOrThrow(_stmt, "longitude")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _columnIndexOfSubdivision: Int = getColumnIndexOrThrow(_stmt, "subdivision")
        val _columnIndexOfScannerFrequency: Int = getColumnIndexOrThrow(_stmt, "scannerFrequency")
        val _columnIndexOfPhotoTips: Int = getColumnIndexOrThrow(_stmt, "photoTips")
        val _columnIndexOfCreatedMs: Int = getColumnIndexOrThrow(_stmt, "createdMs")
        val _result: MutableList<SavedLocation> = mutableListOf()
        while (_stmt.step()) {
          val _item: SavedLocation
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpLatitude: Double
          _tmpLatitude = _stmt.getDouble(_columnIndexOfLatitude)
          val _tmpLongitude: Double
          _tmpLongitude = _stmt.getDouble(_columnIndexOfLongitude)
          val _tmpNotes: String?
          if (_stmt.isNull(_columnIndexOfNotes)) {
            _tmpNotes = null
          } else {
            _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          }
          val _tmpSubdivision: String?
          if (_stmt.isNull(_columnIndexOfSubdivision)) {
            _tmpSubdivision = null
          } else {
            _tmpSubdivision = _stmt.getText(_columnIndexOfSubdivision)
          }
          val _tmpScannerFrequency: String?
          if (_stmt.isNull(_columnIndexOfScannerFrequency)) {
            _tmpScannerFrequency = null
          } else {
            _tmpScannerFrequency = _stmt.getText(_columnIndexOfScannerFrequency)
          }
          val _tmpPhotoTips: String?
          if (_stmt.isNull(_columnIndexOfPhotoTips)) {
            _tmpPhotoTips = null
          } else {
            _tmpPhotoTips = _stmt.getText(_columnIndexOfPhotoTips)
          }
          val _tmpCreatedMs: Long
          _tmpCreatedMs = _stmt.getLong(_columnIndexOfCreatedMs)
          _item =
              SavedLocation(_tmpId,_tmpName,_tmpLatitude,_tmpLongitude,_tmpNotes,_tmpSubdivision,_tmpScannerFrequency,_tmpPhotoTips,_tmpCreatedMs)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: String): SavedLocation? {
    val _sql: String = "SELECT * FROM saved_locations WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfLatitude: Int = getColumnIndexOrThrow(_stmt, "latitude")
        val _columnIndexOfLongitude: Int = getColumnIndexOrThrow(_stmt, "longitude")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _columnIndexOfSubdivision: Int = getColumnIndexOrThrow(_stmt, "subdivision")
        val _columnIndexOfScannerFrequency: Int = getColumnIndexOrThrow(_stmt, "scannerFrequency")
        val _columnIndexOfPhotoTips: Int = getColumnIndexOrThrow(_stmt, "photoTips")
        val _columnIndexOfCreatedMs: Int = getColumnIndexOrThrow(_stmt, "createdMs")
        val _result: SavedLocation?
        if (_stmt.step()) {
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpLatitude: Double
          _tmpLatitude = _stmt.getDouble(_columnIndexOfLatitude)
          val _tmpLongitude: Double
          _tmpLongitude = _stmt.getDouble(_columnIndexOfLongitude)
          val _tmpNotes: String?
          if (_stmt.isNull(_columnIndexOfNotes)) {
            _tmpNotes = null
          } else {
            _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          }
          val _tmpSubdivision: String?
          if (_stmt.isNull(_columnIndexOfSubdivision)) {
            _tmpSubdivision = null
          } else {
            _tmpSubdivision = _stmt.getText(_columnIndexOfSubdivision)
          }
          val _tmpScannerFrequency: String?
          if (_stmt.isNull(_columnIndexOfScannerFrequency)) {
            _tmpScannerFrequency = null
          } else {
            _tmpScannerFrequency = _stmt.getText(_columnIndexOfScannerFrequency)
          }
          val _tmpPhotoTips: String?
          if (_stmt.isNull(_columnIndexOfPhotoTips)) {
            _tmpPhotoTips = null
          } else {
            _tmpPhotoTips = _stmt.getText(_columnIndexOfPhotoTips)
          }
          val _tmpCreatedMs: Long
          _tmpCreatedMs = _stmt.getLong(_columnIndexOfCreatedMs)
          _result =
              SavedLocation(_tmpId,_tmpName,_tmpLatitude,_tmpLongitude,_tmpNotes,_tmpSubdivision,_tmpScannerFrequency,_tmpPhotoTips,_tmpCreatedMs)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
