package com.railfancopilot.shared

import com.railfancopilot.shared.models.*
import com.railfancopilot.shared.repository.RailFanRepository
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * iOS-facing callback wrapper around RailFanRepository.
 *
 * Kotlin suspend functions are not directly callable from Swift without a bridge.
 * This class exposes every repository function via onSuccess/onError callbacks
 * that map cleanly to Swift closures. Use from Swift:
 *
 *   helper.getAllTrains(lat: lat, lon: lon,
 *       onSuccess: { trains in … },
 *       onError:   { msg in … })
 */
class IOSRailFanHelper(
    anthropicApiKey: String = "",
    metraUser: String = "",
    metraPassword: String = "",
    mtaApiKey: String = "",
    fiveElevenKey: String = ""
) {
    private val repo = RailFanRepository(
        anthropicApiKey = anthropicApiKey,
        metraUser       = metraUser,
        metraPassword   = metraPassword,
        mtaApiKey       = mtaApiKey,
        fiveElevenKey   = fiveElevenKey
    )
    private val scope = MainScope()

    // ── Train feeds ───────────────────────────────────────────────────────────

    fun getLiveTrains(
        lat: Double, lon: Double,
        onSuccess: (List<TrainLocation>) -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch {
            try { onSuccess(repo.getLiveTrains(lat, lon)) }
            catch (e: Exception) { onError(e.message ?: "getLiveTrains failed") }
        }
    }

    /** Fetch all active trains from every configured source in parallel. */
    fun getAllTrains(
        lat: Double, lon: Double,
        onSuccess: (List<TrainLocation>) -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch {
            try {
                val all = mutableListOf<TrainLocation>()
                all += repo.getLiveTrains(lat, lon)
                all += repo.getSeptaTrains(lat, lon)
                all += repo.getMbtaTrains(lat, lon)
                all += repo.getMetraTrains(lat, lon)
                all += repo.getMtaLirrTrains(lat, lon)
                all += repo.getMtaMetroNorthTrains(lat, lon)
                all += repo.getCaltrainTrains(lat, lon)
                onSuccess(all)
            } catch (e: Exception) {
                onError(e.message ?: "getAllTrains failed")
            }
        }
    }

    // ── AI features ───────────────────────────────────────────────────────────

    fun decodeSymbol(
        symbol: String,
        onSuccess: (TrainSymbolDecodeResult) -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch {
            repo.decodeTrainSymbol(symbol)
                .onSuccess { onSuccess(it) }
                .onFailure { onError(it.message ?: "decode failed") }
        }
    }

    fun identifyLoco(
        base64Jpeg: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch {
            repo.identifyLocomotive(base64Jpeg)
                .onSuccess { onSuccess(it) }
                .onFailure { onError(it.message ?: "identify failed") }
        }
    }

    // ── Geocoding ─────────────────────────────────────────────────────────────

    fun reverseGeocode(
        lat: Double, lon: Double,
        onComplete: (String?) -> Unit
    ) {
        scope.launch {
            try { onComplete(repo.reverseGeocode(lat, lon)) }
            catch (_: Exception) { onComplete(null) }
        }
    }

    // ── Synchronous helpers (safe to call on any thread) ─────────────────────

    fun getAARFrequencies(): List<RadioChannel> = repo.getAARFrequencies()

    fun calculateSunInfo(lat: Double, lon: Double): SunInfo =
        repo.calculateSunInfo(lat, lon)

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun cancel() { scope.cancel() }
}
