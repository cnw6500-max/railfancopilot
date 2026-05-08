package com.railfancopilot.app.`data`.repository

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.railfancopilot.app.`data`.models.CommunityReport
import javax.`annotation`.processing.Generated
import kotlin.Boolean
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
public class CommunityReportDao_Impl(
  __db: RoomDatabase,
) : CommunityReportDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfCommunityReport: EntityInsertAdapter<CommunityReport>
  init {
    this.__db = __db
    this.__insertAdapterOfCommunityReport = object : EntityInsertAdapter<CommunityReport>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `community_reports` (`id`,`userId`,`userName`,`latitude`,`longitude`,`text`,`trainSymbol`,`railroad`,`tags`,`timestampMs`,`upvotes`,`isVerified`,`localPhotoPath`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: CommunityReport) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.userId)
        statement.bindText(3, entity.userName)
        statement.bindDouble(4, entity.latitude)
        statement.bindDouble(5, entity.longitude)
        statement.bindText(6, entity.text)
        val _tmpTrainSymbol: String? = entity.trainSymbol
        if (_tmpTrainSymbol == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpTrainSymbol)
        }
        val _tmpRailroad: String? = entity.railroad
        if (_tmpRailroad == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpRailroad)
        }
        statement.bindText(9, entity.tags)
        statement.bindLong(10, entity.timestampMs)
        statement.bindLong(11, entity.upvotes.toLong())
        val _tmp: Int = if (entity.isVerified) 1 else 0
        statement.bindLong(12, _tmp.toLong())
        val _tmpLocalPhotoPath: String? = entity.localPhotoPath
        if (_tmpLocalPhotoPath == null) {
          statement.bindNull(13)
        } else {
          statement.bindText(13, _tmpLocalPhotoPath)
        }
      }
    }
  }

  public override suspend fun insert(report: CommunityReport): Unit = performSuspending(__db, false,
      true) { _connection ->
    __insertAdapterOfCommunityReport.insert(_connection, report)
  }

  public override fun getRecentFlow(): Flow<List<CommunityReport>> {
    val _sql: String = "SELECT * FROM community_reports ORDER BY timestampMs DESC LIMIT 100"
    return createFlow(__db, false, arrayOf("community_reports")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "userId")
        val _columnIndexOfUserName: Int = getColumnIndexOrThrow(_stmt, "userName")
        val _columnIndexOfLatitude: Int = getColumnIndexOrThrow(_stmt, "latitude")
        val _columnIndexOfLongitude: Int = getColumnIndexOrThrow(_stmt, "longitude")
        val _columnIndexOfText: Int = getColumnIndexOrThrow(_stmt, "text")
        val _columnIndexOfTrainSymbol: Int = getColumnIndexOrThrow(_stmt, "trainSymbol")
        val _columnIndexOfRailroad: Int = getColumnIndexOrThrow(_stmt, "railroad")
        val _columnIndexOfTags: Int = getColumnIndexOrThrow(_stmt, "tags")
        val _columnIndexOfTimestampMs: Int = getColumnIndexOrThrow(_stmt, "timestampMs")
        val _columnIndexOfUpvotes: Int = getColumnIndexOrThrow(_stmt, "upvotes")
        val _columnIndexOfIsVerified: Int = getColumnIndexOrThrow(_stmt, "isVerified")
        val _columnIndexOfLocalPhotoPath: Int = getColumnIndexOrThrow(_stmt, "localPhotoPath")
        val _result: MutableList<CommunityReport> = mutableListOf()
        while (_stmt.step()) {
          val _item: CommunityReport
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpUserId: String
          _tmpUserId = _stmt.getText(_columnIndexOfUserId)
          val _tmpUserName: String
          _tmpUserName = _stmt.getText(_columnIndexOfUserName)
          val _tmpLatitude: Double
          _tmpLatitude = _stmt.getDouble(_columnIndexOfLatitude)
          val _tmpLongitude: Double
          _tmpLongitude = _stmt.getDouble(_columnIndexOfLongitude)
          val _tmpText: String
          _tmpText = _stmt.getText(_columnIndexOfText)
          val _tmpTrainSymbol: String?
          if (_stmt.isNull(_columnIndexOfTrainSymbol)) {
            _tmpTrainSymbol = null
          } else {
            _tmpTrainSymbol = _stmt.getText(_columnIndexOfTrainSymbol)
          }
          val _tmpRailroad: String?
          if (_stmt.isNull(_columnIndexOfRailroad)) {
            _tmpRailroad = null
          } else {
            _tmpRailroad = _stmt.getText(_columnIndexOfRailroad)
          }
          val _tmpTags: String
          _tmpTags = _stmt.getText(_columnIndexOfTags)
          val _tmpTimestampMs: Long
          _tmpTimestampMs = _stmt.getLong(_columnIndexOfTimestampMs)
          val _tmpUpvotes: Int
          _tmpUpvotes = _stmt.getLong(_columnIndexOfUpvotes).toInt()
          val _tmpIsVerified: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsVerified).toInt()
          _tmpIsVerified = _tmp != 0
          val _tmpLocalPhotoPath: String?
          if (_stmt.isNull(_columnIndexOfLocalPhotoPath)) {
            _tmpLocalPhotoPath = null
          } else {
            _tmpLocalPhotoPath = _stmt.getText(_columnIndexOfLocalPhotoPath)
          }
          _item =
              CommunityReport(_tmpId,_tmpUserId,_tmpUserName,_tmpLatitude,_tmpLongitude,_tmpText,_tmpTrainSymbol,_tmpRailroad,_tmpTags,_tmpTimestampMs,_tmpUpvotes,_tmpIsVerified,_tmpLocalPhotoPath)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getNearby(
    minLat: Double,
    maxLat: Double,
    minLon: Double,
    maxLon: Double,
  ): List<CommunityReport> {
    val _sql: String = """
        |
        |        SELECT * FROM community_reports
        |        WHERE (latitude BETWEEN ? AND ?)
        |          AND (longitude BETWEEN ? AND ?)
        |        ORDER BY timestampMs DESC
        |    
        """.trimMargin()
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindDouble(_argIndex, minLat)
        _argIndex = 2
        _stmt.bindDouble(_argIndex, maxLat)
        _argIndex = 3
        _stmt.bindDouble(_argIndex, minLon)
        _argIndex = 4
        _stmt.bindDouble(_argIndex, maxLon)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "userId")
        val _columnIndexOfUserName: Int = getColumnIndexOrThrow(_stmt, "userName")
        val _columnIndexOfLatitude: Int = getColumnIndexOrThrow(_stmt, "latitude")
        val _columnIndexOfLongitude: Int = getColumnIndexOrThrow(_stmt, "longitude")
        val _columnIndexOfText: Int = getColumnIndexOrThrow(_stmt, "text")
        val _columnIndexOfTrainSymbol: Int = getColumnIndexOrThrow(_stmt, "trainSymbol")
        val _columnIndexOfRailroad: Int = getColumnIndexOrThrow(_stmt, "railroad")
        val _columnIndexOfTags: Int = getColumnIndexOrThrow(_stmt, "tags")
        val _columnIndexOfTimestampMs: Int = getColumnIndexOrThrow(_stmt, "timestampMs")
        val _columnIndexOfUpvotes: Int = getColumnIndexOrThrow(_stmt, "upvotes")
        val _columnIndexOfIsVerified: Int = getColumnIndexOrThrow(_stmt, "isVerified")
        val _columnIndexOfLocalPhotoPath: Int = getColumnIndexOrThrow(_stmt, "localPhotoPath")
        val _result: MutableList<CommunityReport> = mutableListOf()
        while (_stmt.step()) {
          val _item: CommunityReport
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpUserId: String
          _tmpUserId = _stmt.getText(_columnIndexOfUserId)
          val _tmpUserName: String
          _tmpUserName = _stmt.getText(_columnIndexOfUserName)
          val _tmpLatitude: Double
          _tmpLatitude = _stmt.getDouble(_columnIndexOfLatitude)
          val _tmpLongitude: Double
          _tmpLongitude = _stmt.getDouble(_columnIndexOfLongitude)
          val _tmpText: String
          _tmpText = _stmt.getText(_columnIndexOfText)
          val _tmpTrainSymbol: String?
          if (_stmt.isNull(_columnIndexOfTrainSymbol)) {
            _tmpTrainSymbol = null
          } else {
            _tmpTrainSymbol = _stmt.getText(_columnIndexOfTrainSymbol)
          }
          val _tmpRailroad: String?
          if (_stmt.isNull(_columnIndexOfRailroad)) {
            _tmpRailroad = null
          } else {
            _tmpRailroad = _stmt.getText(_columnIndexOfRailroad)
          }
          val _tmpTags: String
          _tmpTags = _stmt.getText(_columnIndexOfTags)
          val _tmpTimestampMs: Long
          _tmpTimestampMs = _stmt.getLong(_columnIndexOfTimestampMs)
          val _tmpUpvotes: Int
          _tmpUpvotes = _stmt.getLong(_columnIndexOfUpvotes).toInt()
          val _tmpIsVerified: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsVerified).toInt()
          _tmpIsVerified = _tmp != 0
          val _tmpLocalPhotoPath: String?
          if (_stmt.isNull(_columnIndexOfLocalPhotoPath)) {
            _tmpLocalPhotoPath = null
          } else {
            _tmpLocalPhotoPath = _stmt.getText(_columnIndexOfLocalPhotoPath)
          }
          _item =
              CommunityReport(_tmpId,_tmpUserId,_tmpUserName,_tmpLatitude,_tmpLongitude,_tmpText,_tmpTrainSymbol,_tmpRailroad,_tmpTags,_tmpTimestampMs,_tmpUpvotes,_tmpIsVerified,_tmpLocalPhotoPath)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getNearbyFlow(
    minLat: Double,
    maxLat: Double,
    minLon: Double,
    maxLon: Double,
  ): Flow<List<CommunityReport>> {
    val _sql: String = """
        |
        |        SELECT * FROM community_reports
        |        WHERE (latitude BETWEEN ? AND ?)
        |          AND (longitude BETWEEN ? AND ?)
        |        ORDER BY timestampMs DESC
        |    
        """.trimMargin()
    return createFlow(__db, false, arrayOf("community_reports")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindDouble(_argIndex, minLat)
        _argIndex = 2
        _stmt.bindDouble(_argIndex, maxLat)
        _argIndex = 3
        _stmt.bindDouble(_argIndex, minLon)
        _argIndex = 4
        _stmt.bindDouble(_argIndex, maxLon)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "userId")
        val _columnIndexOfUserName: Int = getColumnIndexOrThrow(_stmt, "userName")
        val _columnIndexOfLatitude: Int = getColumnIndexOrThrow(_stmt, "latitude")
        val _columnIndexOfLongitude: Int = getColumnIndexOrThrow(_stmt, "longitude")
        val _columnIndexOfText: Int = getColumnIndexOrThrow(_stmt, "text")
        val _columnIndexOfTrainSymbol: Int = getColumnIndexOrThrow(_stmt, "trainSymbol")
        val _columnIndexOfRailroad: Int = getColumnIndexOrThrow(_stmt, "railroad")
        val _columnIndexOfTags: Int = getColumnIndexOrThrow(_stmt, "tags")
        val _columnIndexOfTimestampMs: Int = getColumnIndexOrThrow(_stmt, "timestampMs")
        val _columnIndexOfUpvotes: Int = getColumnIndexOrThrow(_stmt, "upvotes")
        val _columnIndexOfIsVerified: Int = getColumnIndexOrThrow(_stmt, "isVerified")
        val _columnIndexOfLocalPhotoPath: Int = getColumnIndexOrThrow(_stmt, "localPhotoPath")
        val _result: MutableList<CommunityReport> = mutableListOf()
        while (_stmt.step()) {
          val _item: CommunityReport
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpUserId: String
          _tmpUserId = _stmt.getText(_columnIndexOfUserId)
          val _tmpUserName: String
          _tmpUserName = _stmt.getText(_columnIndexOfUserName)
          val _tmpLatitude: Double
          _tmpLatitude = _stmt.getDouble(_columnIndexOfLatitude)
          val _tmpLongitude: Double
          _tmpLongitude = _stmt.getDouble(_columnIndexOfLongitude)
          val _tmpText: String
          _tmpText = _stmt.getText(_columnIndexOfText)
          val _tmpTrainSymbol: String?
          if (_stmt.isNull(_columnIndexOfTrainSymbol)) {
            _tmpTrainSymbol = null
          } else {
            _tmpTrainSymbol = _stmt.getText(_columnIndexOfTrainSymbol)
          }
          val _tmpRailroad: String?
          if (_stmt.isNull(_columnIndexOfRailroad)) {
            _tmpRailroad = null
          } else {
            _tmpRailroad = _stmt.getText(_columnIndexOfRailroad)
          }
          val _tmpTags: String
          _tmpTags = _stmt.getText(_columnIndexOfTags)
          val _tmpTimestampMs: Long
          _tmpTimestampMs = _stmt.getLong(_columnIndexOfTimestampMs)
          val _tmpUpvotes: Int
          _tmpUpvotes = _stmt.getLong(_columnIndexOfUpvotes).toInt()
          val _tmpIsVerified: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsVerified).toInt()
          _tmpIsVerified = _tmp != 0
          val _tmpLocalPhotoPath: String?
          if (_stmt.isNull(_columnIndexOfLocalPhotoPath)) {
            _tmpLocalPhotoPath = null
          } else {
            _tmpLocalPhotoPath = _stmt.getText(_columnIndexOfLocalPhotoPath)
          }
          _item =
              CommunityReport(_tmpId,_tmpUserId,_tmpUserName,_tmpLatitude,_tmpLongitude,_tmpText,_tmpTrainSymbol,_tmpRailroad,_tmpTags,_tmpTimestampMs,_tmpUpvotes,_tmpIsVerified,_tmpLocalPhotoPath)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getAllFlow(): Flow<List<CommunityReport>> {
    val _sql: String = "SELECT * FROM community_reports ORDER BY timestampMs DESC LIMIT 200"
    return createFlow(__db, false, arrayOf("community_reports")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "userId")
        val _columnIndexOfUserName: Int = getColumnIndexOrThrow(_stmt, "userName")
        val _columnIndexOfLatitude: Int = getColumnIndexOrThrow(_stmt, "latitude")
        val _columnIndexOfLongitude: Int = getColumnIndexOrThrow(_stmt, "longitude")
        val _columnIndexOfText: Int = getColumnIndexOrThrow(_stmt, "text")
        val _columnIndexOfTrainSymbol: Int = getColumnIndexOrThrow(_stmt, "trainSymbol")
        val _columnIndexOfRailroad: Int = getColumnIndexOrThrow(_stmt, "railroad")
        val _columnIndexOfTags: Int = getColumnIndexOrThrow(_stmt, "tags")
        val _columnIndexOfTimestampMs: Int = getColumnIndexOrThrow(_stmt, "timestampMs")
        val _columnIndexOfUpvotes: Int = getColumnIndexOrThrow(_stmt, "upvotes")
        val _columnIndexOfIsVerified: Int = getColumnIndexOrThrow(_stmt, "isVerified")
        val _columnIndexOfLocalPhotoPath: Int = getColumnIndexOrThrow(_stmt, "localPhotoPath")
        val _result: MutableList<CommunityReport> = mutableListOf()
        while (_stmt.step()) {
          val _item: CommunityReport
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpUserId: String
          _tmpUserId = _stmt.getText(_columnIndexOfUserId)
          val _tmpUserName: String
          _tmpUserName = _stmt.getText(_columnIndexOfUserName)
          val _tmpLatitude: Double
          _tmpLatitude = _stmt.getDouble(_columnIndexOfLatitude)
          val _tmpLongitude: Double
          _tmpLongitude = _stmt.getDouble(_columnIndexOfLongitude)
          val _tmpText: String
          _tmpText = _stmt.getText(_columnIndexOfText)
          val _tmpTrainSymbol: String?
          if (_stmt.isNull(_columnIndexOfTrainSymbol)) {
            _tmpTrainSymbol = null
          } else {
            _tmpTrainSymbol = _stmt.getText(_columnIndexOfTrainSymbol)
          }
          val _tmpRailroad: String?
          if (_stmt.isNull(_columnIndexOfRailroad)) {
            _tmpRailroad = null
          } else {
            _tmpRailroad = _stmt.getText(_columnIndexOfRailroad)
          }
          val _tmpTags: String
          _tmpTags = _stmt.getText(_columnIndexOfTags)
          val _tmpTimestampMs: Long
          _tmpTimestampMs = _stmt.getLong(_columnIndexOfTimestampMs)
          val _tmpUpvotes: Int
          _tmpUpvotes = _stmt.getLong(_columnIndexOfUpvotes).toInt()
          val _tmpIsVerified: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsVerified).toInt()
          _tmpIsVerified = _tmp != 0
          val _tmpLocalPhotoPath: String?
          if (_stmt.isNull(_columnIndexOfLocalPhotoPath)) {
            _tmpLocalPhotoPath = null
          } else {
            _tmpLocalPhotoPath = _stmt.getText(_columnIndexOfLocalPhotoPath)
          }
          _item =
              CommunityReport(_tmpId,_tmpUserId,_tmpUserName,_tmpLatitude,_tmpLongitude,_tmpText,_tmpTrainSymbol,_tmpRailroad,_tmpTags,_tmpTimestampMs,_tmpUpvotes,_tmpIsVerified,_tmpLocalPhotoPath)
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
