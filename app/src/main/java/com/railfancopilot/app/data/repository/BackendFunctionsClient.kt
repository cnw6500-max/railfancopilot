package com.railfancopilot.app.data.repository

import android.util.Base64
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/**
 * Thin wrapper around Firebase Callable Functions.
 * All secret-bearing API calls (Anthropic, Metra, 511.org) go through here so
 * the APK contains zero secret values.
 */
object BackendFunctionsClient {

    private val functions = FirebaseFunctions.getInstance()

    // ── AI: train symbol decoder ──────────────────────────────────────────────

    /**
     * @param symbol       Raw symbol string (e.g. "MCHI-CHLA-05").
     * @param localContext Pre-built local-DB lookup text from [SymbolDatabase].
     * @return Raw JSON string matching the TrainSymbolDecodeResult schema.
     */
    suspend fun decodeTrainSymbol(symbol: String, localContext: String): String {
        val data = hashMapOf("symbol" to symbol, "localContext" to localContext)
        val result = functions.getHttpsCallable("decodeTrainSymbol").call(data).await()
        @Suppress("UNCHECKED_CAST")
        val map = result.data as? Map<String, Any?> ?: error("Unexpected response type")
        return map["text"] as? String ?: error("Missing 'text' in decodeTrainSymbol response")
    }

    // ── AI: locomotive photo identifier ──────────────────────────────────────

    /**
     * @param base64Image JPEG image encoded as Base64.
     * @return Plain-text identification result from Claude.
     */
    suspend fun identifyLocomotive(base64Image: String): String {
        val data = hashMapOf("base64Image" to base64Image)
        val result = functions.getHttpsCallable("identifyLocomotive").call(data).await()
        @Suppress("UNCHECKED_CAST")
        val map = result.data as? Map<String, Any?> ?: error("Unexpected response type")
        return map["text"] as? String ?: error("Missing 'text' in identifyLocomotive response")
    }

    // ── GTFS-RT feeds ─────────────────────────────────────────────────────────

    suspend fun getMetraPositions(): ByteArray = fetchGtfsRt("getMetraPositions")
    suspend fun getMtaLirrPositions(): ByteArray = fetchGtfsRt("getMtaLirrPositions")
    suspend fun getMtaMetroNorthPositions(): ByteArray = fetchGtfsRt("getMtaMetroNorthPositions")
    suspend fun getCaltrainPositions(): ByteArray = fetchGtfsRt("getCaltrainPositions")

    private suspend fun fetchGtfsRt(functionName: String): ByteArray {
        val result = functions.getHttpsCallable(functionName).call().await()
        @Suppress("UNCHECKED_CAST")
        val map = result.data as? Map<String, Any?> ?: error("Unexpected response type")
        val b64 = map["data"] as? String ?: error("Missing 'data' in $functionName response")
        return Base64.decode(b64, Base64.DEFAULT)
    }
}
