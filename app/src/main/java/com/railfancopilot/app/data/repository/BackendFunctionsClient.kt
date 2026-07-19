package com.railfancopilot.app.data.repository

import android.util.Base64
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Thin wrapper around Firebase Callable Functions.
 * All secret-bearing API calls (Anthropic, Metra, 511.org) go through here so
 * the APK contains zero secret values.
 */
object BackendFunctionsClient {

    private val functions = FirebaseFunctions.getInstance()

    // Suspends until the callable resolves; returns the response map.
    private suspend fun call(name: String, data: Any? = null): Map<String, Any?> =
        suspendCancellableCoroutine { cont ->
            functions.getHttpsCallable(name).call(data)
                .addOnSuccessListener { result ->
                    @Suppress("UNCHECKED_CAST")
                    cont.resume(result.getData() as? Map<String, Any?> ?: emptyMap())
                }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    // ── AI: train symbol decoder ──────────────────────────────────────────────

    suspend fun decodeTrainSymbol(symbol: String, localContext: String): String {
        val map = call("decodeTrainSymbol", hashMapOf("symbol" to symbol, "localContext" to localContext))
        return map["text"] as? String ?: error("Missing 'text' in decodeTrainSymbol response")
    }

    // ── AI: locomotive photo identifier ──────────────────────────────────────

    suspend fun identifyLocomotive(base64Image: String): String {
        val map = call("identifyLocomotive", hashMapOf("base64Image" to base64Image))
        return map["text"] as? String ?: error("Missing 'text' in identifyLocomotive response")
    }

    // ── AI: locomotive number lookup ──────────────────────────────────────────

    suspend fun lookupLocoNumber(roadNumber: String): String {
        val map = call("lookupLocoNumber", hashMapOf("roadNumber" to roadNumber))
        return map["text"] as? String ?: error("Missing 'text' in lookupLocoNumber response")
    }

    // ── AI: consist analyzer ─────────────────────────────────────────────────

    suspend fun analyzeConsist(base64Image: String): String {
        val map = call("analyzeConsist", hashMapOf("base64Image" to base64Image))
        return map["text"] as? String ?: error("Missing 'text' in analyzeConsist response")
    }

    // ── GTFS-RT feeds ─────────────────────────────────────────────────────────

    suspend fun getMetraPositions(): ByteArray = fetchGtfsRt("getMetraPositions")
    suspend fun getMtaLirrPositions(): ByteArray = fetchGtfsRt("getMtaLirrPositions")
    suspend fun getMtaMetroNorthPositions(): ByteArray = fetchGtfsRt("getMtaMetroNorthPositions")
    suspend fun getCaltrainPositions(): ByteArray = fetchGtfsRt("getCaltrainPositions")

    private suspend fun fetchGtfsRt(functionName: String): ByteArray {
        val map = call(functionName)
        val b64 = map["data"] as? String ?: error("Missing 'data' in $functionName response")
        return Base64.decode(b64, Base64.DEFAULT)
    }
}
