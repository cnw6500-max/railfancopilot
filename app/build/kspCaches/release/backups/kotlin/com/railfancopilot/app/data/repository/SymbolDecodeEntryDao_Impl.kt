package com.railfancopilot.app.`data`.repository

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.railfancopilot.app.`data`.models.SymbolDecodeEntry
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
public class SymbolDecodeEntryDao_Impl(
  __db: RoomDatabase,
) : SymbolDecodeEntryDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfSymbolDecodeEntry: EntityInsertAdapter<SymbolDecodeEntry>

  private val __deleteAdapterOfSymbolDecodeEntry: EntityDeleteOrUpdateAdapter<SymbolDecodeEntry>
  init {
    this.__db = __db
    this.__insertAdapterOfSymbolDecodeEntry = object : EntityInsertAdapter<SymbolDecodeEntry>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `symbol_decode_history` (`id`,`symbol`,`railroad`,`type`,`origin`,`destination`,`timestampMs`) VALUES (?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: SymbolDecodeEntry) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.symbol)
        statement.bindText(3, entity.railroad)
        statement.bindText(4, entity.type)
        statement.bindText(5, entity.origin)
        statement.bindText(6, entity.destination)
        statement.bindLong(7, entity.timestampMs)
      }
    }
    this.__deleteAdapterOfSymbolDecodeEntry = object :
        EntityDeleteOrUpdateAdapter<SymbolDecodeEntry>() {
      protected override fun createQuery(): String =
          "DELETE FROM `symbol_decode_history` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: SymbolDecodeEntry) {
        statement.bindText(1, entity.id)
      }
    }
  }

  public override suspend fun insert(entry: SymbolDecodeEntry): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfSymbolDecodeEntry.insert(_connection, entry)
  }

  public override suspend fun delete(entry: SymbolDecodeEntry): Unit = performSuspending(__db,
      false, true) { _connection ->
    __deleteAdapterOfSymbolDecodeEntry.handle(_connection, entry)
  }

  public override fun getAllFlow(): Flow<List<SymbolDecodeEntry>> {
    val _sql: String = "SELECT * FROM symbol_decode_history ORDER BY timestampMs DESC LIMIT 50"
    return createFlow(__db, false, arrayOf("symbol_decode_history")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfSymbol: Int = getColumnIndexOrThrow(_stmt, "symbol")
        val _columnIndexOfRailroad: Int = getColumnIndexOrThrow(_stmt, "railroad")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfOrigin: Int = getColumnIndexOrThrow(_stmt, "origin")
        val _columnIndexOfDestination: Int = getColumnIndexOrThrow(_stmt, "destination")
        val _columnIndexOfTimestampMs: Int = getColumnIndexOrThrow(_stmt, "timestampMs")
        val _result: MutableList<SymbolDecodeEntry> = mutableListOf()
        while (_stmt.step()) {
          val _item: SymbolDecodeEntry
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpSymbol: String
          _tmpSymbol = _stmt.getText(_columnIndexOfSymbol)
          val _tmpRailroad: String
          _tmpRailroad = _stmt.getText(_columnIndexOfRailroad)
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
          val _tmpOrigin: String
          _tmpOrigin = _stmt.getText(_columnIndexOfOrigin)
          val _tmpDestination: String
          _tmpDestination = _stmt.getText(_columnIndexOfDestination)
          val _tmpTimestampMs: Long
          _tmpTimestampMs = _stmt.getLong(_columnIndexOfTimestampMs)
          _item =
              SymbolDecodeEntry(_tmpId,_tmpSymbol,_tmpRailroad,_tmpType,_tmpOrigin,_tmpDestination,_tmpTimestampMs)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun prune() {
    val _sql: String = """
        |
        |        DELETE FROM symbol_decode_history
        |        WHERE id NOT IN (
        |            SELECT id FROM symbol_decode_history ORDER BY timestampMs DESC LIMIT 50
        |        )
        |    
        """.trimMargin()
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteAll() {
    val _sql: String = "DELETE FROM symbol_decode_history"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
