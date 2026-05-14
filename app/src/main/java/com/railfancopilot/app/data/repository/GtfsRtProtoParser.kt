package com.railfancopilot.app.data.repository

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Zero-dependency GTFS-Realtime FeedMessage binary parser.
 *
 * Extracts only the VehiclePosition fields the app needs:
 *   entity.id, trip.route_id, position.{latitude,longitude,bearing,speed},
 *   and current_status.
 *
 * All unrecognised fields are skipped according to the protobuf wire-type rules,
 * so the parser is forward-compatible with future GTFS-RT additions.
 *
 * Proto field reference (gtfs-realtime.proto):
 *   FeedMessage  { entity            = 2 }
 *   FeedEntity   { id                = 1; vehicle = 4 }
 *   VehiclePos   { trip=1; vehicle=2(new)/position=2(old); position=3(new); current_status=6(new)/4(old) }
 *   TripDesc     { route_id          = 5 }
 *   Position     { latitude=1; longitude=2; bearing=3; speed=5 }  (all float/wire-5)
 *   VehicleStop  { INCOMING_AT=0, STOPPED_AT=1, IN_TRANSIT_TO=2 }
 */
internal object GtfsRtProtoParser {

    data class ParsedVehicle(
        val entityId:      String = "",
        val routeId:       String = "",
        val latitude:      Float  = 0f,
        val longitude:     Float  = 0f,
        val bearing:       Float  = 0f,
        val speedMs:       Float  = 0f,  // metres per second — caller converts to mph
        /** 0 = INCOMING_AT, 1 = STOPPED_AT, 2 = IN_TRANSIT_TO */
        val currentStatus: Int    = 2
    )

    // ── Public entry point ────────────────────────────────────────────────────

    fun parse(bytes: ByteArray): List<ParsedVehicle> {
        val result = mutableListOf<ParsedVehicle>()
        val r = Reader(bytes)
        while (r.hasMore()) {
            val (field, wire) = r.tag()
            if (field == 2 && wire == 2) {          // FeedEntity (repeated field)
                parseEntity(r.bytes())?.let { result.add(it) }
            } else {
                r.skip(wire)
            }
        }
        return result
    }

    // ── Message parsers ───────────────────────────────────────────────────────

    private fun parseEntity(b: ByteArray): ParsedVehicle? {
        val r = Reader(b)
        var id = ""
        var vehicle: ParsedVehicle? = null
        while (r.hasMore()) {
            val (field, wire) = r.tag()
            when {
                field == 1 && wire == 2 -> id = r.string()
                field == 4 && wire == 2 -> vehicle = parseVehicle(r.bytes(), id)
                else -> r.skip(wire)
            }
        }
        return vehicle
    }

    private fun parseVehicle(b: ByteArray, entityId: String): ParsedVehicle {
        val r = Reader(b)
        var routeId = ""
        var lat = 0f; var lon = 0f; var bearing = 0f; var speedMs = 0f
        var status = 2                                  // default: IN_TRANSIT_TO
        while (r.hasMore()) {
            val (field, wire) = r.tag()
            when {
                field == 1 && wire == 2 -> routeId = parseRouteId(r.bytes())
                // field 2 = VehicleDescriptor (newer spec) OR Position (older spec)
                // field 3 = Position (newer spec, most agencies)
                field == 2 && wire == 2 -> {
                    val p = parsePosition(r.bytes())
                    if (p[0] != 0f || p[1] != 0f) { lat = p[0]; lon = p[1]; bearing = p[2]; speedMs = p[3] }
                }
                field == 3 && wire == 2 -> {
                    val p = parsePosition(r.bytes())
                    lat = p[0]; lon = p[1]; bearing = p[2]; speedMs = p[3]
                }
                // field 4 = current_status (older spec) OR current_stop_sequence (newer spec)
                // field 6 = current_status (newer spec, most agencies)
                field == 4 && wire == 0 -> if (status == 2) status = r.varint().toInt()
                field == 6 && wire == 0 -> status = r.varint().toInt()
                else -> r.skip(wire)
            }
        }
        return ParsedVehicle(entityId, routeId, lat, lon, bearing, speedMs, status)
    }

    private fun parseRouteId(b: ByteArray): String {
        val r = Reader(b)
        var id = ""
        while (r.hasMore()) {
            val (field, wire) = r.tag()
            if (field == 5 && wire == 2) id = r.string() else r.skip(wire)
        }
        return id
    }

    /** Returns [latitude, longitude, bearing, speedMs]. */
    private fun parsePosition(b: ByteArray): FloatArray {
        val r = Reader(b)
        var lat = 0f; var lon = 0f; var bearing = 0f; var speed = 0f
        while (r.hasMore()) {
            val (field, wire) = r.tag()
            when {
                field == 1 && wire == 5 -> lat     = r.float32()
                field == 2 && wire == 5 -> lon     = r.float32()
                field == 3 && wire == 5 -> bearing = r.float32()
                field == 5 && wire == 5 -> speed   = r.float32()
                else -> r.skip(wire)
            }
        }
        return floatArrayOf(lat, lon, bearing, speed)
    }

    // ── Low-level protobuf wire reader ────────────────────────────────────────

    private class Reader(private val data: ByteArray) {
        private var pos = 0

        fun hasMore() = pos < data.size

        /** Reads the next tag and returns (fieldNumber, wireType). */
        fun tag(): Pair<Int, Int> {
            val v = varint().toInt()
            return Pair(v ushr 3, v and 0x7)
        }

        fun varint(): Long {
            var result = 0L
            var shift  = 0
            while (pos < data.size) {
                val b = data[pos++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) break
                shift += 7
            }
            return result
        }

        fun float32(): Float {
            val f = ByteBuffer.wrap(data, pos, 4).order(ByteOrder.LITTLE_ENDIAN).float
            pos += 4
            return f
        }

        /** Read a length-delimited field and return its raw bytes. */
        fun bytes(): ByteArray {
            val len = varint().toInt()
            val out = data.copyOfRange(pos, pos + len)
            pos += len
            return out
        }

        fun string() = String(bytes(), Charsets.UTF_8)

        fun skip(wireType: Int) {
            when (wireType) {
                0 -> { while (pos < data.size && data[pos++].toInt() and 0x80 != 0) {} }
                1 -> pos += 8           // 64-bit fixed
                2 -> pos += varint().toInt()   // length-delimited
                5 -> pos += 4           // 32-bit fixed
                // wire types 3 & 4 (start/end group) are deprecated — ignore
            }
        }
    }
}
