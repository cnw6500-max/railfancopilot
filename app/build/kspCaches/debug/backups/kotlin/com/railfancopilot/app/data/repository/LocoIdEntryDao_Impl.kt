package com.railfancopilot.app.`data`.repository

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.railfancopilot.app.`data`.models.LocoIdEntry
import javax.`annotation`.processing.Generated
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
public class LocoIdEntryDao_Impl(
  __db: RoomDatabase,
) : LocoIdEntryDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfLocoIdEntry: EntityInsertAdapter<LocoIdEntry>

  private val __deleteAdapterOfLocoIdEntry: EntityDeleteOrUpdateAdapter<LocoIdEntry>
  init {
    this.__db = __db
    this.__insertAdapterOfLocoIdEntry = object : EntityInsertAdapter<LocoIdEntry>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `loco_id_history` (`id`,`resultText`,`thumbnailPath`,`timestampMs`) VALUES (?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: LocoIdEntry) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.resultText)
        val _tmpThumbnailPath: String? = entity.thumbnailPath
        if (_tmpThumbnailPath == null) {
          statement.bindNull(3)
        } else {
          statement.bindText(3, _tmpThumbnailPath)
        }
        statement.bindLong(4, entity.timestampMs)
      }
    }
    this.__deleteAdapterOfLocoIdEntry = object : EntityDeleteOrUpdateAdapter<LocoIdEntry>() {
      protected override fun createQuery(): String = "DELETE FROM `loco_id_history` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: LocoIdEntry) {
        statement.bindText(1, entity.id)
      }
    }
  }

  public override suspend fun insert(entry: LocoIdEntry): Unit = performSuspending(__db, false,
      true) { _connection ->
    __insertAdapterOfLocoIdEntry.insert(_connection, entry)
  }

  public override suspend fun delete(entry: LocoIdEntry): Unit = performSuspending(__db, false,
      true) { _connection ->
    __deleteAdapterOfLocoIdEntry.handle(_connection, entry)
  }

  public override fun getAllFlow(): Flow<List<LocoIdEntry>> {
    val _sql: String = "SELECT * FROM loco_id_history ORDER BY timestampMs DESC LIMIT 50"
    return createFlow(__db, false, arrayOf("loco_id_history")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfResultText: Int = getColumnIndexOrThrow(_stmt, "resultText")
        val _columnIndexOfThumbnailPath: Int = getColumnIndexOrThrow(_stmt, "thumbnailPath")
        val _columnIndexOfTimestampMs: Int = getColumnIndexOrThrow(_stmt, "timestampMs")
        val _result: MutableList<LocoIdEntry> = mutableListOf()
        while (_stmt.step()) {
          val _item: LocoIdEntry
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpResultText: String
          _tmpResultText = _stmt.getText(_columnIndexOfResultText)
          val _tmpThumbnailPath: String?
          if (_stmt.isNull(_columnIndexOfThumbnailPath)) {
            _tmpThumbnailPath = null
          } else {
            _tmpThumbnailPath = _stmt.getText(_columnIndexOfThumbnailPath)
          }
          val _tmpTimestampMs: Long
          _tmpTimestampMs = _stmt.getLong(_columnIndexOfTimestampMs)
          _item = LocoIdEntry(_tmpId,_tmpResultText,_tmpThumbnailPath,_tmpTimestampMs)
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
