package com.railfancopilot.app.`data`.repository

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.railfancopilot.app.`data`.models.PhotoMetadata
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
public class PhotoMetadataDao_Impl(
  __db: RoomDatabase,
) : PhotoMetadataDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfPhotoMetadata: EntityInsertAdapter<PhotoMetadata>

  private val __deleteAdapterOfPhotoMetadata: EntityDeleteOrUpdateAdapter<PhotoMetadata>
  init {
    this.__db = __db
    this.__insertAdapterOfPhotoMetadata = object : EntityInsertAdapter<PhotoMetadata>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `tagged_photos` (`id`,`railroad`,`trainSymbol`,`latitude`,`longitude`,`locationName`,`timestampMs`,`locoModel`,`notes`,`localPath`) VALUES (?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: PhotoMetadata) {
        statement.bindText(1, entity.id)
        val _tmpRailroad: String? = entity.railroad
        if (_tmpRailroad == null) {
          statement.bindNull(2)
        } else {
          statement.bindText(2, _tmpRailroad)
        }
        val _tmpTrainSymbol: String? = entity.trainSymbol
        if (_tmpTrainSymbol == null) {
          statement.bindNull(3)
        } else {
          statement.bindText(3, _tmpTrainSymbol)
        }
        statement.bindDouble(4, entity.latitude)
        statement.bindDouble(5, entity.longitude)
        val _tmpLocationName: String? = entity.locationName
        if (_tmpLocationName == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpLocationName)
        }
        statement.bindLong(7, entity.timestampMs)
        val _tmpLocoModel: String? = entity.locoModel
        if (_tmpLocoModel == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpLocoModel)
        }
        val _tmpNotes: String? = entity.notes
        if (_tmpNotes == null) {
          statement.bindNull(9)
        } else {
          statement.bindText(9, _tmpNotes)
        }
        val _tmpLocalPath: String? = entity.localPath
        if (_tmpLocalPath == null) {
          statement.bindNull(10)
        } else {
          statement.bindText(10, _tmpLocalPath)
        }
      }
    }
    this.__deleteAdapterOfPhotoMetadata = object : EntityDeleteOrUpdateAdapter<PhotoMetadata>() {
      protected override fun createQuery(): String = "DELETE FROM `tagged_photos` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: PhotoMetadata) {
        statement.bindText(1, entity.id)
      }
    }
  }

  public override suspend fun insert(photo: PhotoMetadata): Unit = performSuspending(__db, false,
      true) { _connection ->
    __insertAdapterOfPhotoMetadata.insert(_connection, photo)
  }

  public override suspend fun delete(photo: PhotoMetadata): Unit = performSuspending(__db, false,
      true) { _connection ->
    __deleteAdapterOfPhotoMetadata.handle(_connection, photo)
  }

  public override fun getAllFlow(): Flow<List<PhotoMetadata>> {
    val _sql: String = "SELECT * FROM tagged_photos ORDER BY timestampMs DESC"
    return createFlow(__db, false, arrayOf("tagged_photos")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfRailroad: Int = getColumnIndexOrThrow(_stmt, "railroad")
        val _columnIndexOfTrainSymbol: Int = getColumnIndexOrThrow(_stmt, "trainSymbol")
        val _columnIndexOfLatitude: Int = getColumnIndexOrThrow(_stmt, "latitude")
        val _columnIndexOfLongitude: Int = getColumnIndexOrThrow(_stmt, "longitude")
        val _columnIndexOfLocationName: Int = getColumnIndexOrThrow(_stmt, "locationName")
        val _columnIndexOfTimestampMs: Int = getColumnIndexOrThrow(_stmt, "timestampMs")
        val _columnIndexOfLocoModel: Int = getColumnIndexOrThrow(_stmt, "locoModel")
        val _columnIndexOfNotes: Int = getColumnIndexOrThrow(_stmt, "notes")
        val _columnIndexOfLocalPath: Int = getColumnIndexOrThrow(_stmt, "localPath")
        val _result: MutableList<PhotoMetadata> = mutableListOf()
        while (_stmt.step()) {
          val _item: PhotoMetadata
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpRailroad: String?
          if (_stmt.isNull(_columnIndexOfRailroad)) {
            _tmpRailroad = null
          } else {
            _tmpRailroad = _stmt.getText(_columnIndexOfRailroad)
          }
          val _tmpTrainSymbol: String?
          if (_stmt.isNull(_columnIndexOfTrainSymbol)) {
            _tmpTrainSymbol = null
          } else {
            _tmpTrainSymbol = _stmt.getText(_columnIndexOfTrainSymbol)
          }
          val _tmpLatitude: Double
          _tmpLatitude = _stmt.getDouble(_columnIndexOfLatitude)
          val _tmpLongitude: Double
          _tmpLongitude = _stmt.getDouble(_columnIndexOfLongitude)
          val _tmpLocationName: String?
          if (_stmt.isNull(_columnIndexOfLocationName)) {
            _tmpLocationName = null
          } else {
            _tmpLocationName = _stmt.getText(_columnIndexOfLocationName)
          }
          val _tmpTimestampMs: Long
          _tmpTimestampMs = _stmt.getLong(_columnIndexOfTimestampMs)
          val _tmpLocoModel: String?
          if (_stmt.isNull(_columnIndexOfLocoModel)) {
            _tmpLocoModel = null
          } else {
            _tmpLocoModel = _stmt.getText(_columnIndexOfLocoModel)
          }
          val _tmpNotes: String?
          if (_stmt.isNull(_columnIndexOfNotes)) {
            _tmpNotes = null
          } else {
            _tmpNotes = _stmt.getText(_columnIndexOfNotes)
          }
          val _tmpLocalPath: String?
          if (_stmt.isNull(_columnIndexOfLocalPath)) {
            _tmpLocalPath = null
          } else {
            _tmpLocalPath = _stmt.getText(_columnIndexOfLocalPath)
          }
          _item =
              PhotoMetadata(_tmpId,_tmpRailroad,_tmpTrainSymbol,_tmpLatitude,_tmpLongitude,_tmpLocationName,_tmpTimestampMs,_tmpLocoModel,_tmpNotes,_tmpLocalPath)
          _result.add(_item)
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
