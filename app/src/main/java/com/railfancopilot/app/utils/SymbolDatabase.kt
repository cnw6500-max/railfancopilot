package com.railfancopilot.app.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class SymbolEntry(
    val symbol: String = "",
    val origin: String = "",
    val destination: String = "",
    val notes: String = "",
    val type: String = ""
)

object SymbolDatabase {

    private var symbols: List<SymbolEntry> = emptyList()
    private val gson = Gson()

    fun load(context: Context) {
        if (symbols.isNotEmpty()) return
        try {
            val json = context.assets.open("train_symbols.json")
                .bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<SymbolEntry>>() {}.type
            symbols = gson.fromJson(json, type)
        } catch (e: Exception) {
            android.util.Log.e("SymbolDatabase", "Failed to load symbols: ${e.message}")
        }
    }

    // Exact match lookup
    fun find(symbol: String): SymbolEntry? {
        val upper = symbol.trim().uppercase()
        return symbols.find { it.symbol.uppercase() == upper }
    }

    // Fuzzy search — returns top matches for partial input
    fun search(query: String, limit: Int = 5): List<SymbolEntry> {
        val upper = query.trim().uppercase()
        return symbols.filter {
            it.symbol.uppercase().contains(upper) ||
            it.origin.uppercase().contains(upper) ||
            it.destination.uppercase().contains(upper)
        }.take(limit)
    }

    // Build a compact context string for Claude about a specific symbol
    // Only sends what's needed — no 800+ symbols in every API call
    fun buildContext(symbol: String): String {
        val upper = symbol.trim().uppercase()
        val exact = find(upper)
        val similar = symbols.filter {
            it.symbol.uppercase().startsWith(upper.take(3)) && it.symbol != upper
        }.take(8)

        val sb = StringBuilder()
        if (exact != null) {
            sb.appendLine("EXACT MATCH FOUND:")
            sb.appendLine("Symbol: ${exact.symbol}")
            sb.appendLine("Type: ${exact.type}")
            sb.appendLine("Origin: ${exact.origin}")
            sb.appendLine("Destination: ${exact.destination}")
            sb.appendLine("Notes: ${exact.notes}")
        } else {
            sb.appendLine("No exact match found for $symbol.")
        }

        if (similar.isNotEmpty()) {
            sb.appendLine("\nSIMILAR SYMBOLS (same prefix/route family):")
            similar.forEach { s ->
                sb.appendLine("${s.symbol} = ${s.origin} to ${s.destination} — ${s.notes}")
            }
        }

        return sb.toString()
    }

    val size: Int get() = symbols.size
}
