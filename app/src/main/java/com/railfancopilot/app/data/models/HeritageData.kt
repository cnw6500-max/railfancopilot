package com.railfancopilot.app.data.models

data class HeritageUnit(
    val roadNumber: String,   // e.g. "4014"
    val railroad: String,     // owning/operating railroad, e.g. "UP"
    val schemeName: String,   // display name, e.g. "UP Big Boy"
    val notes: String = ""
)

val HERITAGE_UNITS: List<HeritageUnit> = listOf(

    // ── Union Pacific Steam ───────────────────────────────────────────────────
    HeritageUnit("844",  "UP", "UP Northern Steam",    "Only steam loco never retired by a Class I RR"),
    HeritageUnit("3985", "UP", "UP Challenger Steam",  "4-6-6-4 Challenger, operated on excursions"),
    HeritageUnit("4014", "UP", "UP Big Boy",           "Restored 4-8-8-4, world's largest operating steam"),

    // ── Union Pacific Presidential / Special ──────────────────────────────────
    HeritageUnit("4141", "UP", "Bush 41 Presidential"),
    HeritageUnit("1983", "UP", "Reagan Presidential"),
    HeritageUnit("1969", "UP", "Nixon Presidential"),
    HeritageUnit("1976", "UP", "Spirit of the Union Pacific"),
    HeritageUnit("1988", "UP", "Los Angeles Olympics Tribute"),

    // ── BNSF Heritage Fleet ───────────────────────────────────────────────────
    HeritageUnit("100",  "BNSF", "ATSF Warbonnet"),
    HeritageUnit("700",  "BNSF", "GN Empire Builder"),
    HeritageUnit("1521", "BNSF", "NP Monad"),
    HeritageUnit("2194", "BNSF", "CB&Q Chinese Red"),
    HeritageUnit("2650", "BNSF", "FW&D Mineral Red"),
    HeritageUnit("3140", "BNSF", "SF&SF Mustard Yellow"),
    HeritageUnit("4059", "BNSF", "BN Cascade Green"),
    HeritageUnit("4449", "BNSF", "SP Daylight — museum steam"),

    // ── Norfolk Southern Heritage Fleet ──────────────────────────────────────
    HeritageUnit("8025", "NS", "NS Pennsylvania RR"),
    HeritageUnit("8026", "NS", "NS Norfolk & Western"),
    HeritageUnit("8027", "NS", "NS Southern Railway"),
    HeritageUnit("8028", "NS", "NS New York Central"),
    HeritageUnit("8029", "NS", "NS Virginian Railway"),
    HeritageUnit("8030", "NS", "NS Erie Lackawanna"),
    HeritageUnit("8031", "NS", "NS Reading Company"),
    HeritageUnit("8032", "NS", "NS Western Maryland"),
    HeritageUnit("8033", "NS", "NS Lehigh Valley"),
    HeritageUnit("8034", "NS", "NS Central of Georgia"),
    HeritageUnit("8035", "NS", "NS Monon Railroad"),
    HeritageUnit("8036", "NS", "NS Wabash Railroad"),
    HeritageUnit("8037", "NS", "NS Southern Railway — Original"),
    HeritageUnit("8038", "NS", "NS Nickel Plate Road"),
    HeritageUnit("8039", "NS", "NS Norfolk & Western — Original"),
    HeritageUnit("8040", "NS", "NS Conrail"),
    HeritageUnit("8100", "NS", "NS 21C Spirit of Norfolk Southern"),

    // ── CSX Special Schemes ───────────────────────────────────────────────────
    HeritageUnit("911",  "CSX", "CSX Spirit of Louisville"),
    HeritageUnit("1776", "CSX", "CSX Spirit of America"),
    HeritageUnit("3",    "CSX", "CSX Armed Forces Tribute"),

    // ── CPKC / KCS Heritage ───────────────────────────────────────────────────
    HeritageUnit("4062", "CPKC", "CP Beaver Logo Heritage"),
    HeritageUnit("7021", "CPKC", "KCS Southern Belle Heritage"),
    HeritageUnit("7022", "CPKC", "MKT Katy Heritage"),

    // ── Canadian National ─────────────────────────────────────────────────────
    HeritageUnit("100",  "CN",   "CN 100th Anniversary"),
    HeritageUnit("3000", "CN",   "CN Serves Canada Heritage"),

    // ── Amtrak Special ───────────────────────────────────────────────────────
    HeritageUnit("156",  "AMTRAK", "Amtrak 50th Anniversary Phase I"),
    HeritageUnit("48",   "AMTRAK", "Amtrak Vermonter Heritage"),
)

/** Road numbers known to be heritage/special — used for quick client-side hint in submit UI */
val HERITAGE_ROAD_NUMBERS: Set<String> = HERITAGE_UNITS.map { it.roadNumber.uppercase() }.toSet()

/** Look up a heritage unit by road number (case-insensitive) */
fun findHeritageUnit(roadNumber: String): HeritageUnit? =
    HERITAGE_UNITS.find { it.roadNumber.equals(roadNumber.trim(), ignoreCase = true) }
