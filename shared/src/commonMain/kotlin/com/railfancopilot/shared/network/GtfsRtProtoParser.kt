package com.railfancopilot.shared.network

/**
 * Zero-dependency, pure-Kotlin GTFS-Realtime VehiclePositions binary parser.
 *
 * Ported from the Android module's GtfsRtProtoParser — java.nio.ByteBuffer
 * replaced with direct bit manipulation so this compiles on all KMP targets.
 *
 * Extracts: entity.id, trip.route_id,
 *           position.{latitude, longitude, bearing, speed}, current_status.
 */
internal object GtfsRtProtoParser {

    data class ParsedVehicle(
        val entityId:      String = "",
        val routeId:       String = "",
        val latitude:      Float  = 0f,
        val longitude:     Float  = 0f,
        val bearing:       Float  = 0f,
        val speedMs:       Float  = 0f,
        /** 0 = INCOMING_AT, 1 = STOPPED_AT, 2 = IN_TRANSIT_TO */
        val currentStatus: Int    = 2
    )

    fun parse(bytes: ByteArray): List<ParsedVehicle> {
        val result = mutableListOf<ParsedVehicle>()
        val r = Reader(bytes)
        while (r.hasMore()) {
            val (field, wire) = r.tag()
            if (field == 2 && wire == 2) {
                parseEntity(r.bytes())?.let { result.add(it) }
            } else {
                r.skip(wire)
            }
        }
        return result
    }

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
        var routeId = ""; var lat = 0f; var lon = 0f; var bearing = 0f; var speedMs = 0f
        var status = 2
        while (r.hasMore()) {
            val (field, wire) = r.tag()
            when {
                field == 1 && wire == 2 -> routeId = parseRouteId(r.bytes())
                // field 2 = position (older GTFS-RT spec / some feeds)
                field == 2 && wire == 2 -> {
                    val p = parsePosition(r.bytes())
                    if (p[0] != 0f || p[1] != 0f) { lat = p[0]; lon = p[1]; bearing = p[2]; speedMs = p[3] }
                }
                // field 3 = position (current GTFS-RT spec)
                field == 3 && wire == 2 -> {
                    val p = parsePosition(r.bytes())
                    lat = p[0]; lon = p[1]; bearing = p[2]; speedMs = p[3]
                }
                // field 4 = current_status (older spec) — only apply if field 6 hasn't set it
                field == 4 && wire == 0 -> if (status == 2) status = r.varint().toInt()
                // field 6 = current_status (current GTFS-RT spec)
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

    private class Reader(private val data: ByteArray) {
        private var pos = 0

        fun hasMore() = pos < data.size

        fun tag(): Pair<Int, Int> {
            val v = varint().toInt()
            return Pair(v ushr 3, v and 0x7)
        }

        fun varint(): Long {
            var result = 0L; var shift = 0
            while (pos < data.size) {
                val b = data[pos++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) break
                shift += 7
            }
            return result
        }

        // Pure Kotlin little-endian IEEE 754 float — replaces java.nio.ByteBuffer
        fun float32(): Float {
            val bits = (data[pos].toInt() and 0xFF) or
                       ((data[pos + 1].toInt() and 0xFF) shl 8) or
                       ((data[pos + 2].toInt() and 0xFF) shl 16) or
                       ((data[pos + 3].toInt() and 0xFF) shl 24)
            pos += 4
            return Float.fromBits(bits)
        }

        fun bytes(): ByteArray {
            val len = varint().toInt()
            val out = data.copyOfRange(pos, pos + len)
            pos += len
            return out
        }

        fun string() = bytes().decodeToString()

        fun skip(wireType: Int) {
            when (wireType) {
                0 -> { while (pos < data.size && data[pos++].toInt() and 0x80 != 0) {} }
                1 -> pos += 8
                2 -> pos += varint().toInt()
                5 -> pos += 4
            }
        }
    }
}
