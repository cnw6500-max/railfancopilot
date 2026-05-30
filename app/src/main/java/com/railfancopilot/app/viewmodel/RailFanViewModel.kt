package com.railfancopilot.app.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import android.location.Location
import android.os.Looper
import androidx.datastore.core.DataStore
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.railfancopilot.app.billing.ProRepository
import com.railfancopilot.app.data.models.*
import com.railfancopilot.app.data.repository.RailFanRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Collections
import java.util.UUID

private val Context.achievementDataStore: DataStore<Preferences> by preferencesDataStore(name = "achievements")
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

// ── Settings preference keys ──────────────────────────────────────────────────
private val PREF_REFRESH_INTERVAL_SEC = intPreferencesKey("refresh_interval_sec")       // default 30
private val PREF_TRAIN_RADIUS_MILES   = doublePreferencesKey("train_radius_miles")       // default 500
private val PREF_APPROACH_ETA_MIN     = intPreferencesKey("approach_eta_min")            // default 14
private val PREF_REPORT_RADIUS_MILES  = doublePreferencesKey("report_radius_miles")      // default 50
private val PREF_RAIL_OVERLAY_DEFAULT = booleanPreferencesKey("rail_overlay_default")    // default true
private val PREF_ONBOARDING_SHOWN     = booleanPreferencesKey("onboarding_shown")         // null = first launch
private val PREF_MBTA_ENABLED              = booleanPreferencesKey("mbta_enabled")              // default false
private val PREF_SEPTA_ENABLED             = booleanPreferencesKey("septa_enabled")             // default false
private val PREF_METRA_ENABLED             = booleanPreferencesKey("metra_enabled")             // default false
private val PREF_MTA_LIRR_ENABLED          = booleanPreferencesKey("mta_lirr_enabled")          // default false
private val PREF_MTA_METRO_NORTH_ENABLED   = booleanPreferencesKey("mta_metro_north_enabled")   // default false
private val PREF_CALTRAIN_ENABLED          = booleanPreferencesKey("caltrain_enabled")          // default false
private val PREF_SOUND_TRANSIT_ENABLED     = booleanPreferencesKey("sound_transit_enabled")     // default false
private val PREF_USER_NAME                 = stringPreferencesKey("user_name")                    // default "Railfan"
private val PREF_DECODE_COUNT              = intPreferencesKey("decode_count")
private val PREF_LOCO_COUNT               = intPreferencesKey("loco_id_count")
private val PREF_PHOTO_COUNT              = intPreferencesKey("photo_tag_count")
private val PREF_DECODED_RAILROADS        = stringSetPreferencesKey("decoded_railroads")
private val PREF_ALERT_RARE_LOCO   = booleanPreferencesKey("alert_rare_loco")
private val PREF_ALERT_HOT_TRAIN   = booleanPreferencesKey("alert_hot_train")
private val PREF_ALERT_HIGH_SPEED  = booleanPreferencesKey("alert_high_speed")
private val PREF_ALERT_SCANNER     = booleanPreferencesKey("alert_scanner")
private val PREF_ALERT_APPROACHING = booleanPreferencesKey("alert_approaching")
private val PREF_ALERT_HERITAGE    = booleanPreferencesKey("alert_heritage")

private val EARNED_IDS_KEY = stringSetPreferencesKey("earned_ids")
private val VISITED_YARDS_KEY = stringSetPreferencesKey("visited_yards")
private fun tsKey(id: String) = longPreferencesKey("ts_$id")

data class GeoSearchResult(val displayName: String, val lat: Double, val lon: Double)

private val BASE_ACHIEVEMENTS = listOf(
    Achievement("a1",  "Heritage Spotter",   "Photograph a heritage unit",                    "⭐", false, null),
    Achievement("a2",  "Night Owl",          "Capture a night shot after dark",               "🌙", false, null),
    Achievement("a3",  "Grain Rush",         "Spot a train during grain rush season",         "🌾", false, null),
    Achievement("a4",  "Double Stack",       "Photograph a double-stack intermodal",          "📦", false, null),
    Achievement("a5",  "Speed Demon",        "See a train exceed 79 mph",                     "⚡", false, null),
    Achievement("a6",  "Yard Master",        "Visit 5 classification yards",                  "🚂", false, null),
    Achievement("a7",  "Amtrak Spotter",     "See an Amtrak train on the live map",           "🚄", false, null),
    Achievement("a8",  "Commuter Pass",      "See a commuter rail train on the live map",     "🚃", false, null),
    Achievement("a9",  "Decoder Novice",     "Decode 5 train symbols",                        "🔤", false, null),
    Achievement("a10", "Decoder Expert",     "Decode 25 train symbols",                       "📖", false, null),
    Achievement("a11", "Century Club",       "See a train exceed 100 mph",                    "💨", false, null),
    Achievement("a12", "Foreign Power",      "AI spots a foreign or rare visitor locomotive", "🌎", false, null),
    Achievement("a13", "First ID",           "Identify your first locomotive via AI",         "🔍", false, null),
    Achievement("a14", "Loco Expert",        "AI-identify 10 locomotives",                    "🏆", false, null),
    Achievement("a15", "Snapshot",           "Tag your first railfan photo",                  "📷", false, null),
    Achievement("a16", "Photo Journalist",   "Tag 10 railfan photos",                         "🎞", false, null),
    Achievement("a17", "Network Spotter",    "Decode symbols from 3 different railroads",     "🗺", false, null)
)

class RailFanViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = RailFanRepository(application)
    private val dataStore = application.achievementDataStore
    private val settingsStore = application.settingsDataStore

    // ── Pro / billing ─────────────────────────────────────────────────────────

    private val proRepository = ProRepository(application, viewModelScope)
    val isProUser: StateFlow<Boolean> = proRepository.isProUser

    fun purchasePro(activity: android.app.Activity) = proRepository.purchasePro(activity)
    fun restorePurchases() = proRepository.restorePurchases()

    // ── Settings ──────────────────────────────────────────────────────────────

    private val _refreshIntervalSec = MutableStateFlow(30)
    val refreshIntervalSec: StateFlow<Int> = _refreshIntervalSec.asStateFlow()

    private val _trainRadiusMiles = MutableStateFlow(500.0)
    val trainRadiusMiles: StateFlow<Double> = _trainRadiusMiles.asStateFlow()

    private val _approachEtaMin = MutableStateFlow(14)
    val approachEtaMin: StateFlow<Int> = _approachEtaMin.asStateFlow()

    private val _railOverlayDefault = MutableStateFlow(true)
    val railOverlayDefault: StateFlow<Boolean> = _railOverlayDefault.asStateFlow()

    private val _mbtaEnabled            = MutableStateFlow(false)
    val mbtaEnabled: StateFlow<Boolean> = _mbtaEnabled.asStateFlow()

    private val _septaEnabled            = MutableStateFlow(false)
    val septaEnabled: StateFlow<Boolean> = _septaEnabled.asStateFlow()

    private val _metraEnabled            = MutableStateFlow(false)
    val metraEnabled: StateFlow<Boolean> = _metraEnabled.asStateFlow()

    private val _mtaLirrEnabled            = MutableStateFlow(false)
    val mtaLirrEnabled: StateFlow<Boolean> = _mtaLirrEnabled.asStateFlow()

    private val _mtaMetroNorthEnabled            = MutableStateFlow(false)
    val mtaMetroNorthEnabled: StateFlow<Boolean> = _mtaMetroNorthEnabled.asStateFlow()

    private val _caltrainEnabled            = MutableStateFlow(false)
    val caltrainEnabled: StateFlow<Boolean> = _caltrainEnabled.asStateFlow()

    private val _soundTransitEnabled            = MutableStateFlow(false)
    val soundTransitEnabled: StateFlow<Boolean> = _soundTransitEnabled.asStateFlow()

    private val _userName            = MutableStateFlow("Railfan")
    val userName: StateFlow<String>  = _userName.asStateFlow()

    fun saveUserName(name: String) {
        val trimmed = name.trim().ifEmpty { "Railfan" }
        _userName.value = trimmed
        viewModelScope.launch { settingsStore.edit { it[PREF_USER_NAME] = trimmed } }
    }

    // ── Onboarding ────────────────────────────────────────────────────────────
    // null = DataStore not yet read; false = first launch; true = already shown

    val onboardingShown: StateFlow<Boolean?> = settingsStore.data
        .map { it[PREF_ONBOARDING_SHOWN] }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun markOnboardingShown() {
        viewModelScope.launch { settingsStore.edit { it[PREF_ONBOARDING_SHOWN] = true } }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val prefs = settingsStore.data.first()
            _refreshIntervalSec.value    = prefs[PREF_REFRESH_INTERVAL_SEC]        ?: 30
            _trainRadiusMiles.value      = prefs[PREF_TRAIN_RADIUS_MILES]          ?: 500.0
            _approachEtaMin.value        = prefs[PREF_APPROACH_ETA_MIN]            ?: 14
            _reportRadiusMiles.value     = prefs[PREF_REPORT_RADIUS_MILES]         ?: 50.0
            _railOverlayDefault.value    = prefs[PREF_RAIL_OVERLAY_DEFAULT]        ?: true
            _mbtaEnabled.value           = prefs[PREF_MBTA_ENABLED]               ?: false
            _septaEnabled.value          = prefs[PREF_SEPTA_ENABLED]              ?: false
            _metraEnabled.value          = prefs[PREF_METRA_ENABLED]              ?: false
            _mtaLirrEnabled.value        = prefs[PREF_MTA_LIRR_ENABLED]           ?: false
            _mtaMetroNorthEnabled.value  = prefs[PREF_MTA_METRO_NORTH_ENABLED]    ?: false
            _caltrainEnabled.value       = prefs[PREF_CALTRAIN_ENABLED]           ?: false
            _soundTransitEnabled.value   = prefs[PREF_SOUND_TRANSIT_ENABLED]      ?: false
            _userName.value              = prefs[PREF_USER_NAME]                  ?: "Railfan"
            _alertRareLoco.value         = prefs[PREF_ALERT_RARE_LOCO]            ?: true
            _alertHotTrain.value         = prefs[PREF_ALERT_HOT_TRAIN]            ?: true
            _alertHighSpeed.value        = prefs[PREF_ALERT_HIGH_SPEED]           ?: true
            _alertScanner.value          = prefs[PREF_ALERT_SCANNER]              ?: true
            _alertApproaching.value      = prefs[PREF_ALERT_APPROACHING]          ?: true
            _alertHeritage.value         = prefs[PREF_ALERT_HERITAGE]             ?: true
        }
    }

    fun saveRefreshInterval(seconds: Int) {
        _refreshIntervalSec.value = seconds
        viewModelScope.launch { settingsStore.edit { it[PREF_REFRESH_INTERVAL_SEC] = seconds } }
    }

    fun saveTrainRadius(miles: Double) {
        _trainRadiusMiles.value = miles
        viewModelScope.launch { settingsStore.edit { it[PREF_TRAIN_RADIUS_MILES] = miles } }
    }

    fun saveApproachEta(minutes: Int) {
        _approachEtaMin.value = minutes
        viewModelScope.launch { settingsStore.edit { it[PREF_APPROACH_ETA_MIN] = minutes } }
    }

    fun saveRailOverlayDefault(enabled: Boolean) {
        _railOverlayDefault.value = enabled
        viewModelScope.launch { settingsStore.edit { it[PREF_RAIL_OVERLAY_DEFAULT] = enabled } }
    }

    fun saveMbtaEnabled(enabled: Boolean) {
        _mbtaEnabled.value = enabled
        viewModelScope.launch { settingsStore.edit { it[PREF_MBTA_ENABLED] = enabled } }
    }

    fun saveSeptaEnabled(enabled: Boolean) {
        _septaEnabled.value = enabled
        viewModelScope.launch { settingsStore.edit { it[PREF_SEPTA_ENABLED] = enabled } }
    }

    fun saveMetraEnabled(enabled: Boolean) {
        _metraEnabled.value = enabled
        viewModelScope.launch { settingsStore.edit { it[PREF_METRA_ENABLED] = enabled } }
    }

    fun saveMtaLirrEnabled(enabled: Boolean) {
        _mtaLirrEnabled.value = enabled
        viewModelScope.launch { settingsStore.edit { it[PREF_MTA_LIRR_ENABLED] = enabled } }
    }

    fun saveMtaMetroNorthEnabled(enabled: Boolean) {
        _mtaMetroNorthEnabled.value = enabled
        viewModelScope.launch { settingsStore.edit { it[PREF_MTA_METRO_NORTH_ENABLED] = enabled } }
    }

    fun saveCaltrainEnabled(enabled: Boolean) {
        _caltrainEnabled.value = enabled
        viewModelScope.launch { settingsStore.edit { it[PREF_CALTRAIN_ENABLED] = enabled } }
    }

    fun saveSoundTransitEnabled(enabled: Boolean) {
        _soundTransitEnabled.value = enabled
        viewModelScope.launch { settingsStore.edit { it[PREF_SOUND_TRANSIT_ENABLED] = enabled } }
    }

    fun saveReportRadius(miles: Double) {
        _reportRadiusMiles.value = miles
        viewModelScope.launch { settingsStore.edit { it[PREF_REPORT_RADIUS_MILES] = miles } }
    }

    // ── Location ──────────────────────────────────────────────────────────────

    private val _userLocation = MutableStateFlow<Location?>(null)
    val userLocation: StateFlow<Location?> = _userLocation.asStateFlow()

    fun updateLocation(location: Location) {
        val firstFix = _userLocation.value == null
        _userLocation.value = location
        if (firstFix) {
            // First real GPS fix — seed map features at actual location and kick off trains immediately
            loadMapFeatures(location.latitude, location.longitude)
            refreshSunInfo(location.latitude, location.longitude)
            refreshTrains()
            startSpotsListener()
        }
        triggerGeofenceCheck(location)
        checkYardProximity(location)
    }

    private var fusedLocationClient: FusedLocationProviderClient? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { updateLocation(it) }
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationTracking() {
        if (fusedLocationClient != null) return   // already tracking — avoid duplicate requests
        val ctx = getApplication<Application>()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(ctx)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L)
            .setMinUpdateDistanceMeters(50f)
            .setWaitForAccurateLocation(false)
            .build()
        fusedLocationClient?.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        // deliver last known position immediately while GPS warms up
        fusedLocationClient?.lastLocation?.addOnSuccessListener { loc ->
            loc?.let { updateLocation(it) }
        }
    }

    override fun onCleared() {
        fusedLocationClient?.removeLocationUpdates(locationCallback)
        super.onCleared()
    }

    // ── Live trains ───────────────────────────────────────────────────────────

    private val _trains = MutableStateFlow<List<TrainLocation>>(emptyList())
    val trains: StateFlow<List<TrainLocation>> = _trains.asStateFlow()

    // trainId → ordered list of (lat, lon) waypoints, kept for the session only
    private val _trainTrails = MutableStateFlow<Map<String, List<LatLng>>>(emptyMap())
    val trainTrails: StateFlow<Map<String, List<LatLng>>> = _trainTrails.asStateFlow()

    private val _selectedRailroad = MutableStateFlow<Railroad?>(null)
    val selectedRailroad: StateFlow<Railroad?> = _selectedRailroad.asStateFlow()

    val filteredTrains: StateFlow<List<TrainLocation>> = combine(_trains, _selectedRailroad) { trains, rr ->
        if (rr == null) trains else trains.filter { it.railroad == rr }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setRailroadFilter(rr: Railroad?) { _selectedRailroad.value = rr }

    private val _isLoadingTrains = MutableStateFlow(false)
    val isLoadingTrains: StateFlow<Boolean> = _isLoadingTrains.asStateFlow()

    private val _lastRefreshMs = MutableStateFlow<Long?>(null)
    val lastRefreshMs: StateFlow<Long?> = _lastRefreshMs.asStateFlow()

    private val _trainFetchError = MutableStateFlow<String?>(null)
    val trainFetchError: StateFlow<String?> = _trainFetchError.asStateFlow()

    fun dismissTrainFetchError() { _trainFetchError.value = null }

    fun refreshTrains() {
        val loc = _userLocation.value ?: return   // no-op until we have real GPS
        viewModelScope.launch {
            _isLoadingTrains.value = true
            _trainFetchError.value = null

            try {
                // Launch all enabled feeds concurrently; cancel everything after 30 s
                val lat = loc.latitude
                val lon = loc.longitude
                val radius = _trainRadiusMiles.value

                val all = kotlinx.coroutines.withTimeout(30_000L) {
                    val amtrakDeferred       = async { repo.getLiveTrains(lat, lon, _selectedRailroad.value?.name, radius) }
                    val mbtaDeferred         = async { if (_mbtaEnabled.value)          repo.getMbtaTrains(lat, lon, radius)          else emptyList() }
                    val septaDeferred        = async { if (_septaEnabled.value)         repo.getSeptaTrains(lat, lon, radius)         else emptyList() }
                    val metraDeferred        = async { if (_metraEnabled.value)         repo.getMetraTrains(lat, lon, radius)         else emptyList() }
                    val lirrDeferred         = async { if (_mtaLirrEnabled.value)       repo.getMtaLirrTrains(lat, lon, radius)       else emptyList() }
                    val metroNorthDeferred   = async { if (_mtaMetroNorthEnabled.value) repo.getMtaMetroNorthTrains(lat, lon, radius) else emptyList() }
                    val caltrainDeferred     = async { if (_caltrainEnabled.value)      repo.getCaltrainTrains(lat, lon, radius)      else emptyList() }
                    val soundTransitDeferred = async { if (_soundTransitEnabled.value)  repo.getSoundTransitTrains(lat, lon, radius)  else emptyList() }

                    amtrakDeferred.await() +
                        mbtaDeferred.await() +
                        septaDeferred.await() +
                        metraDeferred.await() +
                        lirrDeferred.await() +
                        metroNorthDeferred.await() +
                        caltrainDeferred.await() +
                        soundTransitDeferred.await()
                }

                _trains.value = all
                _lastRefreshMs.value = System.currentTimeMillis()
                val updatedTrails = _trainTrails.value.toMutableMap()
                all.forEach { train ->
                    val waypoint = LatLng(train.latitude, train.longitude)
                    val existing = updatedTrails[train.id] ?: emptyList()
                    // Only append if position actually changed
                    if (existing.lastOrNull() != waypoint) {
                        updatedTrails[train.id] = (existing + waypoint).takeLast(50)
                    }
                }
                // Drop trails for trains no longer in the feed
                updatedTrails.keys.retainAll(all.map { it.id }.toSet())
                _trainTrails.value = updatedTrails
                checkTrainAchievements(all)
                checkApproachNotifications(all)
            } catch (e: Exception) {
                android.util.Log.e("RailFanVM", "Train fetch failed: ${e.message}", e)
                _trainFetchError.value = "Couldn't load trains — check your connection"
                // Keep whatever trains were shown previously rather than blanking the map
            } finally {
                _isLoadingTrains.value = false
            }
        }
    }

    private var autoRefreshJob: kotlinx.coroutines.Job? = null

    fun startAutoRefresh() {
        if (autoRefreshJob?.isActive == true) return   // already running — don't launch a second loop
        autoRefreshJob = viewModelScope.launch {
            // Block until the first real GPS fix — never refresh with hardcoded coordinates
            _userLocation.first { it != null }
            while (true) {
                refreshTrains()
                delay(_refreshIntervalSec.value * 1000L)
            }
        }
    }

    // ── Map features ──────────────────────────────────────────────────────────

    private val _mapFeatures = MutableStateFlow<List<MapFeature>>(emptyList())
    val mapFeatures: StateFlow<List<MapFeature>> = _mapFeatures.asStateFlow()

    fun loadMapFeatures(lat: Double, lon: Double) {
        // Static dataset of major North American classification yards.
        // These never change so there's no network call; the yard list is the
        // authoritative source for the Yard Master achievement and safety geofences.
        _mapFeatures.value = CLASSIFICATION_YARDS
    }

    // ── Scanner ───────────────────────────────────────────────────────────────

    private val _channels = MutableStateFlow<List<RadioChannel>>(emptyList())
    val channels: StateFlow<List<RadioChannel>> = _channels.asStateFlow()

    /**
     * Same channel list as [channels] but sorted so railroads whose primary territory
     * is closest to the user appear first.  [Railroad.OTHER] (telemetry / safety channels
     * that apply everywhere) is always pinned to the bottom.  Falls back to the original
     * insertion order when no GPS fix is available yet.
     */
    val sortedChannels: StateFlow<List<RadioChannel>> =
        combine(_channels, _userLocation) { channels, loc ->
            if (loc == null) return@combine channels   // no fix yet — keep original order

            val distBuf = FloatArray(1)

            // Build a per-railroad ranking based on distance from the user to each
            // railroad's approximate territory centroid.
            val railroadRank: Map<Railroad, Int> = Railroad.values()
                .filter { it != Railroad.OTHER }
                .sortedBy { rr ->
                    val (clat, clon) = RAILROAD_TERRITORY_CENTERS[rr] ?: return@sortedBy Double.MAX_VALUE
                    Location.distanceBetween(loc.latitude, loc.longitude, clat, clon, distBuf)
                    distBuf[0].toDouble()
                }
                .mapIndexed { index, rr -> rr to index }
                .toMap()

            channels.sortedWith(
                compareBy { ch ->
                    if (ch.railroad == Railroad.OTHER) Int.MAX_VALUE
                    else railroadRank[ch.railroad] ?: Int.MAX_VALUE - 1
                }
            )
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _activeChannelId = MutableStateFlow<String?>(null)
    val activeChannelId: StateFlow<String?> = _activeChannelId.asStateFlow()

    private val _transcripts = MutableStateFlow<List<Transcript>>(emptyList())

    /** Last 10 transmissions, newest first. */
    val recentTransmissions: StateFlow<List<Transcript>> = _transcripts
        .map { it.reversed() }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isScannerPlaying = MutableStateFlow(false)
    val isScannerPlaying: StateFlow<Boolean> = _isScannerPlaying.asStateFlow()

    fun loadChannels() { _channels.value = repo.getAARFrequencies() }

    fun selectChannel(channelId: String) {
        _activeChannelId.value = channelId
        _isScannerPlaying.value = false
    }

    fun stopScanner() {
        _isScannerPlaying.value = false
        _activeChannelId.value = null
    }

    fun logTransmission(channelId: String, note: String, trainSymbol: String?) {
        val entry = Transcript(UUID.randomUUID().toString(), channelId, note, System.currentTimeMillis(), 1.0f, trainSymbol)
        _transcripts.value = (_transcripts.value + entry).takeLast(10)
        if (_alertScanner.value && note.isNotBlank()) {
            val channelName = _channels.value.find { it.id == channelId }?.name ?: channelId
            fireRailAlert(RailAlert(
                id          = "scanner_${entry.id}",
                type        = RailAlertType.SCANNER_ACTIVITY,
                title       = "Scanner Activity",
                message     = "$channelName: ${note.take(80)}",
                timestampMs = entry.timestampMs,
                trainSymbol = trainSymbol
            ))
        }
    }

    // ── AI Decoder ────────────────────────────────────────────────────────────

    private val _decodeResult = MutableStateFlow<TrainSymbolDecodeResult?>(null)
    val decodeResult: StateFlow<TrainSymbolDecodeResult?> = _decodeResult.asStateFlow()

    private val _isDecoding = MutableStateFlow(false)
    val isDecoding: StateFlow<Boolean> = _isDecoding.asStateFlow()

    private val _decodeError = MutableStateFlow<String?>(null)
    val decodeError: StateFlow<String?> = _decodeError.asStateFlow()

    val decodeHistory: StateFlow<List<SymbolDecodeEntry>> =
        repo.getSymbolDecodeHistoryFlow()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private var decodeJob: kotlinx.coroutines.Job? = null

    // One-shot event: UI observes this to launch Play In-App Review dialog
    private val _requestInAppReview = MutableStateFlow(false)
    val requestInAppReview: StateFlow<Boolean> = _requestInAppReview.asStateFlow()
    fun consumeInAppReviewRequest() { _requestInAppReview.value = false }

    fun decodeSymbol(symbol: String) {
        if (symbol.isBlank()) return
        decodeJob?.cancel()   // cancel any in-flight request before starting a new one
        decodeJob = viewModelScope.launch {
            _isDecoding.value = true
            _decodeError.value = null
            try {
                val result = repo.decodeTrainSymbol(symbol.trim().uppercase())
                result.onSuccess { decoded ->
                    _decodeResult.value = decoded
                    // Persist to Room — duplicates replaced via REPLACE strategy
                    repo.saveSymbolDecodeEntry(
                        SymbolDecodeEntry(
                            id          = symbol.trim().uppercase(),   // symbol as PK deduplicates
                            symbol      = symbol.trim().uppercase(),
                            railroad    = decoded.railroad.name,
                            type        = decoded.type,
                            origin      = decoded.origin,
                            destination = decoded.destination,
                            timestampMs = System.currentTimeMillis()
                        )
                    )
                    // Increment cumulative decode count; trigger in-app review at milestones
                    settingsStore.edit { prefs ->
                        val newCount = (prefs[PREF_DECODE_COUNT] ?: 0) + 1
                        prefs[PREF_DECODE_COUNT] = newCount
                        if (newCount == 5 || newCount == 25) _requestInAppReview.value = true
                        if (newCount == 5)  unlockAchievement("a9")
                        if (newCount == 25) unlockAchievement("a10")
                        val seenRailroads = (prefs[PREF_DECODED_RAILROADS] ?: emptySet()) + decoded.railroad.name
                        prefs[PREF_DECODED_RAILROADS] = seenRailroads
                        if (seenRailroads.size >= 3) unlockAchievement("a17")
                    }
                }.onFailure {
                    _decodeError.value = "Decode service is temporarily unavailable. Please try again."
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _decodeError.value = "Decode service is temporarily unavailable. Please try again."
            } finally {
                _isDecoding.value = false
            }
        }
    }

    fun clearDecodeError() { _decodeError.value = null }

    fun deleteSymbolDecodeEntry(entry: SymbolDecodeEntry) {
        viewModelScope.launch { repo.deleteSymbolDecodeEntry(entry) }
    }

    fun clearDecodeHistory() {
        viewModelScope.launch { repo.clearSymbolDecodeHistory() }
    }

    // ── Photography ───────────────────────────────────────────────────────────

    private val _sunInfo = MutableStateFlow<SunInfo?>(null)
    val sunInfo: StateFlow<SunInfo?> = _sunInfo.asStateFlow()

    fun refreshSunInfo(lat: Double, lon: Double) {
        _sunInfo.value = repo.calculateSunInfo(lat, lon)
    }

    /** Background loop: re-compute sun position every 5 minutes while a location is known. */
    private fun startSunRefreshLoop() {
        viewModelScope.launch {
            var wasGoldenHour = false
            while (true) {
                delay(5 * 60 * 1000L)
                _userLocation.value?.let { loc ->
                    val sun = repo.calculateSunInfo(loc.latitude, loc.longitude)
                    _sunInfo.value = sun
                    if (sun.isGoldenHour && !wasGoldenHour) {
                        val notifMgr = getApplication<Application>()
                            .getSystemService(NotificationManager::class.java)
                        val label = if (sun.elevationDegrees > 0) "Sunrise" else "Sunset"
                        val notif = NotificationCompat.Builder(getApplication(), GOLDENHOUR_CHANNEL)
                            .setSmallIcon(android.R.drawable.ic_menu_camera)
                            .setContentTitle("Golden Hour — $label")
                            .setContentText("Perfect light for rail photography right now")
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .setAutoCancel(true)
                            .build()
                        notifMgr.notify("golden_hour".hashCode(), notif)
                    }
                    wasGoldenHour = sun.isGoldenHour
                }
            }
        }
    }

    val taggedPhotos: StateFlow<List<PhotoMetadata>> =
        repo.getTaggedPhotosFlow()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun saveTaggedPhoto(metadata: PhotoMetadata) {
        viewModelScope.launch {
            repo.saveTaggedPhoto(metadata)
            settingsStore.edit { prefs ->
                val newCount = (prefs[PREF_PHOTO_COUNT] ?: 0) + 1
                prefs[PREF_PHOTO_COUNT] = newCount
                if (newCount == 1)  unlockAchievement("a15")
                if (newCount == 10) unlockAchievement("a16")
            }
        }
    }

    fun deleteTaggedPhoto(metadata: PhotoMetadata) {
        viewModelScope.launch {
            metadata.localPath?.let { path ->
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    java.io.File(path).delete()
                }
            }
            repo.deleteTaggedPhoto(metadata)
        }
    }

    // ── Loco Identifier ───────────────────────────────────────────────────────

    private val _isIdentifying = MutableStateFlow(false)
    val isIdentifying: StateFlow<Boolean> = _isIdentifying.asStateFlow()

    private val _locoIdResult = MutableStateFlow<String?>(null)
    val locoIdResult: StateFlow<String?> = _locoIdResult.asStateFlow()

    private val _locoIdError = MutableStateFlow<String?>(null)
    val locoIdError: StateFlow<String?> = _locoIdError.asStateFlow()

    val locoIdHistory: StateFlow<List<LocoIdEntry>> =
        repo.getLocoIdHistoryFlow()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private var locoIdJob: kotlinx.coroutines.Job? = null

    fun identifyLocomotive(base64Image: String, thumbnailPath: String? = null) {
        locoIdJob = viewModelScope.launch {
            _isIdentifying.value = true
            _locoIdError.value = null
            try {
                val result = repo.identifyLocomotive(base64Image)
                result.onSuccess { text ->
                    _locoIdResult.value = text
                    checkLocoIdAchievements(text)
                    repo.saveLocoIdEntry(
                        LocoIdEntry(
                            id = UUID.randomUUID().toString(),
                            resultText = text,
                            thumbnailPath = thumbnailPath,
                            timestampMs = System.currentTimeMillis()
                        )
                    )
                    settingsStore.edit { prefs ->
                        val newCount = (prefs[PREF_LOCO_COUNT] ?: 0) + 1
                        prefs[PREF_LOCO_COUNT] = newCount
                        if (newCount == 1)  unlockAchievement("a13")
                        if (newCount == 10) unlockAchievement("a14")
                    }
                }.onFailure {
                    _locoIdError.value = "Identification failed. Check your connection and try again."
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _locoIdError.value = "Identification failed. Check your connection and try again."
            } finally {
                _isIdentifying.value = false
            }
        }
    }

    fun deleteLocoIdEntry(entry: LocoIdEntry) {
        viewModelScope.launch {
            entry.thumbnailPath?.let { path ->
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    java.io.File(path).delete()
                }
            }
            repo.deleteLocoIdEntry(entry)
        }
    }

    fun clearLocoIdResult() {
        _locoIdResult.value = null
        _locoIdError.value = null
    }

    fun cancelIdentification() {
        locoIdJob?.cancel()
        _isIdentifying.value = false
        _locoIdError.value = null
    }

    // ── Consist Analyzer ──────────────────────────────────────────────────────

    private val _isAnalyzingConsist = MutableStateFlow(false)
    val isAnalyzingConsist: StateFlow<Boolean> = _isAnalyzingConsist.asStateFlow()

    private val _consistResult = MutableStateFlow<String?>(null)
    val consistResult: StateFlow<String?> = _consistResult.asStateFlow()

    private val _consistError = MutableStateFlow<String?>(null)
    val consistError: StateFlow<String?> = _consistError.asStateFlow()

    private var consistJob: kotlinx.coroutines.Job? = null

    fun analyzeConsist(base64Image: String) {
        consistJob?.cancel()
        consistJob = viewModelScope.launch {
            _isAnalyzingConsist.value = true
            _consistError.value = null
            try {
                val result = repo.analyzeConsist(base64Image)
                result.onSuccess { _consistResult.value = it }
                    .onFailure { _consistError.value = "Consist analysis failed. Check your connection and try again." }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _consistError.value = "Consist analysis failed. Check your connection and try again."
            } finally {
                _isAnalyzingConsist.value = false
            }
        }
    }

    fun clearConsistResult() { _consistResult.value = null; _consistError.value = null }
    fun cancelConsistAnalysis() { consistJob?.cancel(); _isAnalyzingConsist.value = false }

    // ── Community ─────────────────────────────────────────────────────────────

    private val _reportRadiusMiles = MutableStateFlow(50.0)
    val reportRadiusMiles: StateFlow<Double> = _reportRadiusMiles.asStateFlow()

    fun setReportRadius(miles: Double) { _reportRadiusMiles.value = miles }

    /** Live community reports filtered by user location + radius. Falls back to all reports if no location fix. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val communityReports: StateFlow<List<CommunityReport>> =
        combine(_userLocation, _reportRadiusMiles) { loc, radius -> Pair(loc, radius) }
            .flatMapLatest { (loc, radius) ->
                if (loc == null) {
                    repo.getAllReportsFlow()
                } else {
                    repo.getNearbyReportsFlow(loc.latitude, loc.longitude, radius)
                }
            }
            .map { dbReports -> dbReports.sortedByDescending { it.timestampMs } }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun submitReport(
        lat: Double, lon: Double, text: String,
        trainSymbol: String?, railroad: String?, tags: List<String>,
        localPhotoPath: String? = null,
        consist: String? = null,
        weather: String? = null,
        locationName: String = ""
    ) {
        viewModelScope.launch {
            repo.addReport(lat, lon, text, trainSymbol, railroad, tags, localPhotoPath, _userName.value, consist, weather, locationName)
        }
    }

    suspend fun fetchWeather(lat: Double, lon: Double): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            repo.fetchWeather(lat, lon)
        }

    fun deleteReport(reportId: String) {
        viewModelScope.launch { repo.deleteCommunityReport(reportId) }
    }

    fun getCommentsFlow(sightingId: String) =
        com.railfancopilot.app.data.repository.FirestoreCommunityRepo.getCommentsFlow(sightingId)

    fun postComment(sightingId: String, text: String) {
        com.railfancopilot.app.data.repository.FirestoreCommunityRepo.addComment(sightingId, text, _userName.value)
    }

    fun postPhotoToCommunity(photo: PhotoMetadata) {
        val lat = photo.latitude ?: _userLocation.value?.latitude ?: return
        val lon = photo.longitude ?: _userLocation.value?.longitude ?: return
        viewModelScope.launch {
            repo.addReport(
                lat          = lat,
                lon          = lon,
                text         = photo.notes ?: "",
                trainSymbol  = photo.trainSymbol,
                railroad     = photo.railroad,
                tags         = emptyList(),
                localPhotoPath = photo.localPath,
                reporterName = _userName.value
            )
        }
    }

    /** Returns a short human-readable place name for the given coordinates, or null on failure. */
    suspend fun reverseGeocode(lat: Double, lon: Double): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            repo.reverseGeocode(lat, lon)
        }

    // ── Railfan Alerts ────────────────────────────────────────────────────────

    private val _railAlerts = MutableStateFlow<List<RailAlert>>(emptyList())
    val railAlerts: StateFlow<List<RailAlert>> = _railAlerts.asStateFlow()

    val unreadAlertCount: StateFlow<Int> = _railAlerts
        .map { list -> list.count { !it.isRead } }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    private val _newRailAlert = MutableStateFlow<RailAlert?>(null)
    val newRailAlert: StateFlow<RailAlert?> = _newRailAlert.asStateFlow()
    fun consumeNewRailAlert() { _newRailAlert.value = null }

    private val _alertRareLoco   = MutableStateFlow(true)
    val alertRareLoco: StateFlow<Boolean>   = _alertRareLoco.asStateFlow()

    private val _alertHotTrain   = MutableStateFlow(true)
    val alertHotTrain: StateFlow<Boolean>   = _alertHotTrain.asStateFlow()

    private val _alertHighSpeed  = MutableStateFlow(true)
    val alertHighSpeed: StateFlow<Boolean>  = _alertHighSpeed.asStateFlow()

    private val _alertScanner    = MutableStateFlow(true)
    val alertScanner: StateFlow<Boolean>    = _alertScanner.asStateFlow()

    private val _alertApproaching = MutableStateFlow(true)
    val alertApproaching: StateFlow<Boolean> = _alertApproaching.asStateFlow()

    private val _alertHeritage   = MutableStateFlow(true)
    val alertHeritage: StateFlow<Boolean>   = _alertHeritage.asStateFlow()

    fun setAlertRareLoco(on: Boolean)   { _alertRareLoco.value = on;   viewModelScope.launch { settingsStore.edit { it[PREF_ALERT_RARE_LOCO]   = on } } }
    fun setAlertHotTrain(on: Boolean)   { _alertHotTrain.value = on;   viewModelScope.launch { settingsStore.edit { it[PREF_ALERT_HOT_TRAIN]   = on } } }
    fun setAlertHighSpeed(on: Boolean)  { _alertHighSpeed.value = on;  viewModelScope.launch { settingsStore.edit { it[PREF_ALERT_HIGH_SPEED]  = on } } }
    fun setAlertScanner(on: Boolean)    { _alertScanner.value = on;    viewModelScope.launch { settingsStore.edit { it[PREF_ALERT_SCANNER]     = on } } }
    fun setAlertApproaching(on: Boolean){ _alertApproaching.value = on; viewModelScope.launch { settingsStore.edit { it[PREF_ALERT_APPROACHING] = on } } }
    fun setAlertHeritage(on: Boolean)   { _alertHeritage.value = on; viewModelScope.launch { settingsStore.edit { it[PREF_ALERT_HERITAGE] = on } } }

    // Tracks which Firestore alert IDs have already been merged so we don't
    // re-fire the in-app banner on every snapshot refresh.
    private val seenFirestoreAlertIds = Collections.synchronizedSet(mutableSetOf<String>())

    fun markAlertRead(id: String) {
        _railAlerts.value = _railAlerts.value.map { if (it.id == id) it.copy(isRead = true) else it }
    }
    fun markAllAlertsRead() {
        _railAlerts.value = _railAlerts.value.map { it.copy(isRead = true) }
    }
    fun clearAlerts() { _railAlerts.value = emptyList() }

    private val RAILFAN_ALERTS_CHANNEL = "railfan_alerts"
    private val seenReportIds = Collections.synchronizedSet(mutableSetOf<String>())
    private var alertsSeeded  = false

    /** Start listening to Firestore rail_alerts — call once after auth is ready. */
    fun startFirestoreAlertsListener() {
        viewModelScope.launch {
            com.railfancopilot.app.data.repository.FirestoreCommunityRepo
                .getAlertsFlow()
                .collect { firestoreAlerts ->
                    val filtered = firestoreAlerts.filter { a ->
                        when (a.type) {
                            RailAlertType.HERITAGE_UNIT, RailAlertType.SPECIAL_MOVE -> _alertHeritage.value
                            RailAlertType.RARE_LOCO    -> _alertRareLoco.value
                            RailAlertType.HOT_TRAIN    -> _alertHotTrain.value
                            else -> true
                        }
                    }
                    // Merge with locally-generated alerts, deduplicate by id
                    val localAlerts = _railAlerts.value.filter { it.id !in filtered.map { a -> a.id }.toSet() }
                    val merged = (filtered + localAlerts)
                        .sortedByDescending { it.timestampMs }
                        .take(50)
                    _railAlerts.value = merged

                    // Fire in-app banner only for genuinely new Firestore alerts
                    filtered.filter { it.id !in seenFirestoreAlertIds }.forEach { alert ->
                        seenFirestoreAlertIds.add(alert.id)
                        _newRailAlert.value = alert
                    }
                }
        }
    }

    private fun fireRailAlert(alert: RailAlert) {
        _railAlerts.value = (listOf(alert) + _railAlerts.value).take(50)
        _newRailAlert.value = alert
        val notifMgr = getApplication<Application>().getSystemService(NotificationManager::class.java)
        val notif = NotificationCompat.Builder(getApplication(), RAILFAN_ALERTS_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("${alert.type.emoji} ${alert.title}")
            .setContentText(alert.message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        notifMgr.notify(alert.id.hashCode(), notif)
    }

    private fun checkCommunityReportsForAlerts(reports: List<CommunityReport>) {
        if (!alertsSeeded) {
            seenReportIds.addAll(reports.map { it.id })
            alertsSeeded = true
            return
        }
        if (!_alertRareLoco.value && !_alertHotTrain.value) return
        val newReports = reports.filter { it.id !in seenReportIds }
        newReports.forEach { report ->
            seenReportIds.add(report.id)
            val symbol = report.trainSymbol?.uppercase() ?: ""
            val text   = report.text.lowercase()

            if (_alertHotTrain.value) {
                val isHot = symbol.startsWith("Z") ||
                            symbol.startsWith("Q") ||
                            symbol.contains("IMX") ||
                            text.contains("z train") ||
                            text.contains("hotshot") ||
                            text.contains("priority intermodal")
                if (isHot) {
                    val loc = if (report.locationName.isNotBlank()) " at ${report.locationName}" else ""
                    fireRailAlert(RailAlert(
                        id          = "hot_${report.id}",
                        type        = RailAlertType.HOT_TRAIN,
                        title       = "Hot Train Spotted",
                        message     = "${symbol.ifBlank { "Expedited train" }} spotted$loc",
                        timestampMs = report.timestampMs,
                        latitude    = report.latitude,
                        longitude   = report.longitude,
                        trainSymbol = report.trainSymbol
                    ))
                    return@forEach
                }
            }

            if (_alertRareLoco.value) {
                val heritageKw = listOf("heritage", "fallen flag", "spirit of", "rebuild",
                    "foreign power", "patched", "warbonnet", "commemorative", "daylight",
                    "retro", "historic paint", "special paint", "cn power", "cp power",
                    "ferromex", "via rail", "mexican power", "foreign unit")
                val foreignRR  = setOf("CN", "CP", "CPKC", "FERROMEX", "VIA", "KCS DE MEXICO")
                val isRare = heritageKw.any { text.contains(it) } ||
                             report.railroad?.uppercase() in foreignRR
                if (isRare) {
                    fireRailAlert(RailAlert(
                        id          = "rare_${report.id}",
                        type        = RailAlertType.RARE_LOCO,
                        title       = "Rare Locomotive Spotted",
                        message     = report.text.take(120),
                        timestampMs = report.timestampMs,
                        latitude    = report.latitude,
                        longitude   = report.longitude,
                        trainSymbol = report.trainSymbol
                    ))
                }
            }
        }
    }

    // ── Location search (Nominatim) ───────────────────────────────────────────

    private val _searchResults = MutableStateFlow<List<GeoSearchResult>>(emptyList())
    val searchResults: StateFlow<List<GeoSearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    fun searchLocation(query: String) {
        if (query.isBlank()) { _searchResults.value = emptyList(); return }
        viewModelScope.launch {
            _isSearching.value = true
            try {
                val raw = com.railfancopilot.app.data.repository.NetworkModule.nominatimApi.search(query)
                _searchResults.value = raw.map {
                    GeoSearchResult(
                        displayName = it.display_name,
                        lat = it.lat.toDoubleOrNull() ?: 0.0,
                        lon = it.lon.toDoubleOrNull() ?: 0.0
                    )
                }.filter { it.lat != 0.0 && it.lon != 0.0 }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("GeoSearch", "Nominatim failed: ${e.message}")
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clearSearch() { _searchResults.value = emptyList() }

    // ── Safety / Geofencing ───────────────────────────────────────────────────

    private val _safetyAlerts = MutableStateFlow<List<SafetyAlert>>(emptyList())
    val safetyAlerts: StateFlow<List<SafetyAlert>> = _safetyAlerts.asStateFlow()

    private fun triggerGeofenceCheck(location: Location) {
        val distBuf = FloatArray(1)
        val alerts  = mutableListOf<SafetyAlert>()

        CLASSIFICATION_YARDS.forEach { yard ->
            Location.distanceBetween(
                location.latitude, location.longitude,
                yard.latitude, yard.longitude, distBuf
            )
            val meters = distBuf[0]
            when {
                meters < 150 ->
                    alerts += SafetyAlert(
                        type     = SafetyAlertType.PRIVATE_PROPERTY,
                        message  = "⚠ You appear to be inside ${yard.name} — this is private railroad property. Stay on public rights-of-way.",
                        severity = AlertSeverity.DANGER
                    )
                meters < 400 ->
                    alerts += SafetyAlert(
                        type     = SafetyAlertType.PRIVATE_PROPERTY,
                        message  = "${yard.name} is nearby. Railroad property is restricted — do not trespass.",
                        severity = AlertSeverity.WARNING
                    )
            }
        }

        _safetyAlerts.value = alerts

        // Fire a one-time notification when first entering a DANGER zone
        val notifMgr = getApplication<Application>().getSystemService(NotificationManager::class.java)
        CLASSIFICATION_YARDS.forEach { yard ->
            Location.distanceBetween(
                location.latitude, location.longitude,
                yard.latitude, yard.longitude, distBuf
            )
            if (distBuf[0] < 400 && yard.id !in notifiedGeofenceIds) {
                notifiedGeofenceIds.add(yard.id)
                val notif = NotificationCompat.Builder(getApplication(), GEOFENCE_CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("Classification Yard Nearby")
                    .setContentText("${yard.name} — railroad property, stay on public rights-of-way")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .build()
                notifMgr.notify(yard.id.hashCode(), notif)
            } else if (distBuf[0] >= 400) {
                notifiedGeofenceIds.remove(yard.id)
            }
        }
    }

    // ── Encyclopedia ──────────────────────────────────────────────────────────

    private val _locomotives = MutableStateFlow<List<LocomotiveEntry>>(emptyList())
    val locomotives: StateFlow<List<LocomotiveEntry>> = _locomotives.asStateFlow()

    private val _isLoadingLocos = MutableStateFlow(false)
    val isLoadingLocos: StateFlow<Boolean> = _isLoadingLocos.asStateFlow()

    fun loadEncyclopedia() {
        viewModelScope.launch {
            _isLoadingLocos.value = true
            _locomotives.value = repo.getLocomotivedDatabase()
            _isLoadingLocos.value = false
        }
    }

    // ── Saved locations ───────────────────────────────────────────────────────

    val savedLocations: StateFlow<List<SavedLocation>> =
        repo.getSavedLocationsFlow()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun saveLocation(lat: Double, lon: Double, name: String, notes: String?,
                     subdivision: String? = null, scannerFrequency: String? = null, photoTips: String? = null) {
        viewModelScope.launch {
            repo.saveLocation(SavedLocation(
                id = UUID.randomUUID().toString(),
                name = name, latitude = lat, longitude = lon,
                notes = notes, subdivision = subdivision,
                scannerFrequency = scannerFrequency, photoTips = photoTips,
                createdMs = System.currentTimeMillis()
            ))
        }
    }

    fun deleteLocation(location: SavedLocation) {
        viewModelScope.launch { repo.deleteLocation(location) }
    }

    // ── Achievements ─────────────────────────────────────────────────────────

    private val _achievements = MutableStateFlow(BASE_ACHIEVEMENTS)
    val achievements: StateFlow<List<Achievement>> = _achievements.asStateFlow()

    /** Emits when an achievement is first unlocked. Consumers call consumeNewAchievement() to clear. */
    private val _newAchievement = MutableStateFlow<Achievement?>(null)
    val newAchievement: StateFlow<Achievement?> = _newAchievement.asStateFlow()

    fun consumeNewAchievement() { _newAchievement.value = null }

    private fun loadAchievements() {
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            val earnedIds = prefs[EARNED_IDS_KEY] ?: emptySet()
            _achievements.value = BASE_ACHIEVEMENTS.map { a ->
                if (a.id in earnedIds) {
                    val ts = prefs[tsKey(a.id)] ?: System.currentTimeMillis()
                    a.copy(earned = true, earnedMs = ts)
                } else a
            }
        }
    }

    private fun unlockAchievement(id: String) {
        if (_achievements.value.find { it.id == id }?.earned == true) return
        val now = System.currentTimeMillis()
        _achievements.value = _achievements.value.map { a ->
            if (a.id == id) a.copy(earned = true, earnedMs = now) else a
        }
        // Notify observers so the UI can show a banner
        _newAchievement.value = _achievements.value.find { it.id == id }
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[EARNED_IDS_KEY] = (prefs[EARNED_IDS_KEY] ?: emptySet()) + id
                prefs[tsKey(id)] = now
            }
        }
    }

    // Called after loco identifier returns a result
    private fun checkLocoIdAchievements(result: String) {
        val lower = result.lowercase()

        // Night Owl: photo taken between 9 PM and 5 AM
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour >= 21 || hour < 5) unlockAchievement("a2")

        // Heritage Spotter: Claude mentions heritage paint keywords
        val heritageKeywords = listOf("heritage", "retro", "patched", "spirit of", "fallen flag",
            "commemorative", "historic", "paint scheme", "special livery")
        if (heritageKeywords.any { lower.contains(it) }) unlockAchievement("a1")

        // Double Stack: Claude mentions intermodal / container keywords
        val dsKeywords = listOf("double stack", "intermodal", "container", "cofc", "stack train", "well car")
        if (dsKeywords.any { lower.contains(it) }) unlockAchievement("a4")

        // Foreign Power: Claude identifies a foreign-railroad or rare visitor unit
        val foreignKeywords = listOf("ferromex", "via rail", "foreign power", "foreign unit",
            "mexican power", "canadian national power", "canadian pacific power", "kcs de mexico")
        if (foreignKeywords.any { lower.contains(it) }) unlockAchievement("a12")
    }

    // ── Approach notifications ────────────────────────────────────────────────

    private val notifiedTrainIds = Collections.synchronizedSet(mutableSetOf<String>())
    private val APPROACH_CHANNEL  = "approach_alerts"
    private val GEOFENCE_CHANNEL  = "geofence_alerts"
    private val GOLDENHOUR_CHANNEL = "golden_hour_alerts"

    private val notifiedGeofenceIds = Collections.synchronizedSet(mutableSetOf<String>())

    private fun createApproachNotificationChannel() {
        val mgr = getApplication<Application>().getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(NotificationChannel(
            APPROACH_CHANNEL, "Train Approach Alerts", NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Alerts when a train is approaching your location" })
        mgr.createNotificationChannel(NotificationChannel(
            GEOFENCE_CHANNEL, "Yard & Geofence Alerts", NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Alerts when you enter a classification yard or saved location" })
        mgr.createNotificationChannel(NotificationChannel(
            GOLDENHOUR_CHANNEL, "Golden Hour Alerts", NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Alerts at sunrise and sunset golden hour for photography" })
        mgr.createNotificationChannel(NotificationChannel(
            RAILFAN_ALERTS_CHANNEL, "Railfan Alerts", NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Alerts for rare locomotives, hot trains, high speed, and scanner activity" })
    }

    private fun checkApproachNotifications(trains: List<TrainLocation>) {
        if (_userLocation.value == null) return
        val notifMgr = getApplication<Application>()
            .getSystemService(NotificationManager::class.java)
        val approaching = trains.filter { it.etaMinutes?.let { eta -> eta in 0.._approachEtaMin.value } == true }

        approaching.forEach { train ->
            if (train.id !in notifiedTrainIds) {
                notifiedTrainIds.add(train.id)
                // Reverse-geocode in background so notification carries a city name
                viewModelScope.launch {
                    val city = try {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            repo.reverseGeocode(train.latitude, train.longitude)
                        }
                    } catch (_: Exception) { null }
                    val nearLabel = city ?: train.subdivision ?: "your area"
                    val eta = train.etaMinutes
                    val body = "${train.symbol} · ETA ${eta} min"
                    val notif = NotificationCompat.Builder(getApplication(), APPROACH_CHANNEL)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle("Train approaching $nearLabel")
                        .setContentText(body)
                        .setSubText(train.railroad.displayName)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .build()
                    notifMgr.notify(train.id.hashCode(), notif)
                    if (_alertApproaching.value) {
                        fireRailAlert(RailAlert(
                            id          = "approach_${train.id}",
                            type        = RailAlertType.TRAIN_APPROACHING,
                            title       = "Train approaching $nearLabel",
                            message     = "$body · ${train.railroad.displayName}",
                            timestampMs = System.currentTimeMillis(),
                            latitude    = train.latitude,
                            longitude   = train.longitude,
                            trainSymbol = train.symbol
                        ))
                    }
                }
            }
        }

        // Reset tracking once a train has passed (no longer in approaching list)
        notifiedTrainIds.retainAll(approaching.map { it.id }.toSet())
    }

    // Called after each train list refresh
    private fun checkTrainAchievements(trains: List<TrainLocation>) {
        if (trains.isEmpty()) return

        // Amtrak Spotter: any live Amtrak train visible
        if (trains.any { it.railroad == Railroad.AMTRAK }) unlockAchievement("a7")

        // Commuter Pass: any commuter/regional rail train visible (non-Amtrak agencies map to OTHER)
        if (trains.any { it.railroad == Railroad.OTHER }) unlockAchievement("a8")

        // Speed Demon: any live train over 79 mph
        if (trains.any { it.speedMph > 79 }) {
            unlockAchievement("a5")
            if (_alertHighSpeed.value) {
                val fast = trains.filter { it.speedMph > 79 }.maxByOrNull { it.speedMph }!!
                val alertId = "speed_${fast.id}_${fast.speedMph}"
                if (_railAlerts.value.none { it.id == alertId }) {
                    fireRailAlert(RailAlert(
                        id          = alertId,
                        type        = RailAlertType.HIGH_SPEED,
                        title       = "High-Speed Train",
                        message     = "${fast.symbol} running ${fast.speedMph} mph · ${fast.railroad.displayName}",
                        timestampMs = System.currentTimeMillis(),
                        latitude    = fast.latitude,
                        longitude   = fast.longitude,
                        trainSymbol = fast.symbol
                    ))
                }
            }
        }

        // Century Club: any live train over 100 mph
        if (trains.any { it.speedMph > 99 }) unlockAchievement("a11")

        // Grain Rush: Aug–Oct, any train spotted
        val month = Calendar.getInstance().get(Calendar.MONTH) + 1
        if (month in 8..10) unlockAchievement("a3")
    }

    // Called on every location update
    private fun checkYardProximity(location: Location) {
        if (_achievements.value.find { it.id == "a6" }?.earned == true) return
        val yards = _mapFeatures.value.filter { it.type == MapFeatureType.YARD }
        if (yards.isEmpty()) return
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            val visited = (prefs[VISITED_YARDS_KEY] ?: emptySet()).toMutableSet()
            var changed = false
            yards.forEach { yard ->
                val dist = FloatArray(1)
                Location.distanceBetween(
                    location.latitude, location.longitude,
                    yard.latitude, yard.longitude, dist
                )
                if (dist[0] < 500 && yard.id !in visited) {
                    visited.add(yard.id)
                    changed = true
                }
            }
            if (changed) {
                dataStore.edit { it[VISITED_YARDS_KEY] = visited }
                if (visited.size >= 5) unlockAchievement("a6")
            }
        }
    }

    // ── Community Spots ───────────────────────────────────────────────────────

    private val _communitySpots = MutableStateFlow<List<com.railfancopilot.app.data.models.RailfanSpot>>(emptyList())
    val communitySpots: StateFlow<List<com.railfancopilot.app.data.models.RailfanSpot>> = _communitySpots.asStateFlow()

    private val _isSubmittingSpot = MutableStateFlow(false)
    val isSubmittingSpot: StateFlow<Boolean> = _isSubmittingSpot.asStateFlow()

    private val _spotSubmitError = MutableStateFlow<String?>(null)
    val spotSubmitError: StateFlow<String?> = _spotSubmitError.asStateFlow()

    private var spotsListenerJob: kotlinx.coroutines.Job? = null

    fun startSpotsListener() {
        if (spotsListenerJob?.isActive == true) return
        val loc = _userLocation.value ?: return
        spotsListenerJob = viewModelScope.launch {
            com.railfancopilot.app.data.repository.FirestoreSpotsRepo
                .getSpotsFlow(loc.latitude, loc.longitude)
                .collect { _communitySpots.value = it }
        }
    }

    fun submitSpot(
        spot: com.railfancopilot.app.data.models.RailfanSpot,
        photoBytes: ByteArray? = null
    ) {
        viewModelScope.launch {
            _isSubmittingSpot.value = true
            _spotSubmitError.value = null
            try {
                com.railfancopilot.app.data.repository.FirestoreSpotsRepo.submitSpot(spot, photoBytes)
            } catch (e: Exception) {
                _spotSubmitError.value = "Failed to submit spot. Check your connection and try again."
            } finally {
                _isSubmittingSpot.value = false
            }
        }
    }

    fun upvoteSpot(spotId: String) {
        com.railfancopilot.app.data.repository.FirestoreSpotsRepo.upvoteSpot(spotId)
    }

    fun addSpotPhoto(spotId: String, photoBytes: ByteArray) {
        viewModelScope.launch {
            try {
                com.railfancopilot.app.data.repository.FirestoreSpotsRepo.addPhoto(spotId, photoBytes)
            } catch (_: Exception) { }
        }
    }

    fun clearSpotSubmitError() { _spotSubmitError.value = null }

    // ── Railway map lines (Overpass) ───────────────────────────────────────────
    private val _railwaySegments = MutableStateFlow<List<com.railfancopilot.app.data.models.RailwaySegment>>(emptyList())
    val railwaySegments: StateFlow<List<com.railfancopilot.app.data.models.RailwaySegment>> = _railwaySegments.asStateFlow()
    @Volatile private var isFetchingRailLines = false

    fun fetchRailwaySegments(south: Double, west: Double, north: Double, east: Double) {
        if (isFetchingRailLines) return
        viewModelScope.launch {
            isFetchingRailLines = true
            val segments = com.railfancopilot.app.data.repository.OverpassFetcher
                .fetchRailwaySegments(south, west, north, east)
            if (segments.isNotEmpty()) _railwaySegments.value = segments
            isFetchingRailLines = false
        }
    }

    // ── Watchlist ──────────────────────────────────────────────────────────────

    private val WATCHLIST_CHANNEL = "watchlist_alerts"

    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    private val _authFailed = MutableStateFlow(false)
    val authFailed: StateFlow<Boolean> = _authFailed.asStateFlow()

    private val _watchlist = MutableStateFlow<List<com.railfancopilot.app.data.models.WatchlistEntry>>(emptyList())
    val watchlist: StateFlow<List<com.railfancopilot.app.data.models.WatchlistEntry>> = _watchlist.asStateFlow()

    private var watchlistListenerJob: kotlinx.coroutines.Job? = null

    private fun initAuth() {
        _authFailed.value = false
        viewModelScope.launch {
            try {
                val uid = com.railfancopilot.app.data.repository.FirestoreCommunityRepo.ensureAnonymousAuth()
                _currentUserId.value = uid
                _authFailed.value = false
                // Start listening to this user's watchlist
                watchlistListenerJob?.cancel()
                watchlistListenerJob = viewModelScope.launch {
                    com.railfancopilot.app.data.repository.FirestoreCommunityRepo
                        .getWatchlistFlow(uid)
                        .collect { _watchlist.value = it }
                }
                // Start Firestore heritage/special alerts listener
                startFirestoreAlertsListener()

                // Create notification channels
                val mgr = getApplication<Application>().getSystemService(android.app.NotificationManager::class.java)
                mgr.createNotificationChannel(android.app.NotificationChannel(
                    WATCHLIST_CHANNEL, "Watchlist Alerts", android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Alerts when a watched locomotive or train symbol is spotted" })
                mgr.createNotificationChannel(android.app.NotificationChannel(
                    "heritage_alerts", "Heritage Unit Alerts", android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Alerts when a verified heritage or special locomotive is spotted" })
            } catch (e: Exception) {
                android.util.Log.e("RailFanVM", "Auth failed: ${e.message}", e)
                _authFailed.value = true
            }
        }
    }

    fun retryAuth() { initAuth() }

    fun addToWatchlist(entry: com.railfancopilot.app.data.models.WatchlistEntry) {
        val uid = _currentUserId.value ?: return
        com.railfancopilot.app.data.repository.FirestoreCommunityRepo.addWatchlistEntry(uid, entry)
    }

    fun removeFromWatchlist(entryId: String) {
        val uid = _currentUserId.value ?: return
        com.railfancopilot.app.data.repository.FirestoreCommunityRepo.removeWatchlistEntry(uid, entryId)
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        createApproachNotificationChannel()
        loadSettings()
        loadAchievements()
        loadChannels()
        loadEncyclopedia()
        startAutoRefresh()   // waits for GPS fix before first train fetch
        startSunRefreshLoop()
        initAuth()
        viewModelScope.launch { communityReports.collect { checkCommunityReportsForAlerts(it) } }
        // Load symbol database off the main thread
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.railfancopilot.app.utils.SymbolDatabase.load(getApplication())
        }
    }

    companion object {
        /**
         * Approximate geographic centroid of each railroad's primary US operating territory.
         * Used to rank scanner channels by proximity to the user.
         *
         *  BNSF   — Transcon / Great Plains          (38°N 100°W)
         *  UP     — Overland Route / Pacific          (41°N 105°W)
         *  CSX    — Eastern seaboard / Ohio Valley    (38.5°N 82°W)
         *  NS     — Virginia / Southeast              (37.5°N 80.5°W)
         *  CN     — Great Lakes / Illinois corridor   (44.5°N 88°W)
         *  CP     — Northern tier / Upper Midwest     (47°N 97°W)
         *  AMTRAK — Northeast Corridor / nationwide   (40.7°N 74°W)
         *  KCS    — Kansas City / Gulf Coast          (35.5°N 94°W)
         */
        /**
         * Static dataset of 30 major North American classification yards.
         * Used for: Yard Master achievement tracking, safety geofence alerts,
         * and as MapFeature pins on the map.
         */
        val CLASSIFICATION_YARDS: List<MapFeature> = listOf(
            // ── Union Pacific ─────────────────────────────────────────────────
            MapFeature("yard-up-bailey",    MapFeatureType.YARD, 41.1403, -100.7601,
                "Bailey Yard (UP)", "World's largest classification yard — 315 car lengths",
                "Active mainline; private property — no trespassing", "161.100", true),
            MapFeature("yard-up-proviso",   MapFeatureType.YARD, 41.9003, -87.8614,
                "Proviso Yard (UP)", "UP's primary Chicago gateway yard",
                "Private property — railfan from designated public areas only", "160.515", true),
            MapFeature("yard-up-neff",      MapFeatureType.YARD, 39.1136, -94.5572,
                "Neff Yard (UP)", "UP Kansas City classification yard",
                "Private property", "161.010", true),
            MapFeature("yard-up-roseville", MapFeatureType.YARD, 38.7521, -121.2880,
                "Roseville Yard (UP)", "Largest UP yard on the West Coast",
                "Private property", "160.515", true),
            MapFeature("yard-up-eugene",    MapFeatureType.YARD, 44.0521, -123.0868,
                "Eugene Yard (UP)", "UP Pacific Northwest hub",
                "Private property", "161.070", true),
            MapFeature("yard-up-englewood", MapFeatureType.YARD, 29.7354, -95.2971,
                "Englewood Yard (UP)", "UP Houston gateway — heavy petrochemical traffic",
                "Private property", "160.590", true),
            // ── BNSF ──────────────────────────────────────────────────────────
            MapFeature("yard-bnsf-argentine", MapFeatureType.YARD, 39.0864, -94.6603,
                "Argentine Yard (BNSF)", "BNSF's largest Kansas City facility",
                "Private property", "160.410", true),
            MapFeature("yard-bnsf-galesburg", MapFeatureType.YARD, 40.9478, -90.3712,
                "Barr Yard / Galesburg (BNSF)", "Key BNSF hub on the Transcon",
                "Private property", "160.410", true),
            MapFeature("yard-bnsf-cicero",    MapFeatureType.YARD, 41.8681, -87.7461,
                "Cicero Yard (BNSF)", "BNSF Chicago area hump yard",
                "Private property", "161.385", true),
            MapFeature("yard-bnsf-spokane",   MapFeatureType.YARD, 47.6588, -117.4260,
                "Spokane Yard (BNSF)", "BNSF Pacific Northwest hub",
                "Private property", "160.410", true),
            MapFeature("yard-bnsf-alliance",  MapFeatureType.YARD, 32.9543, -97.4119,
                "Alliance Yard (BNSF)", "BNSF Fort Worth intermodal facility",
                "Private property", "160.515", true),
            MapFeature("yard-bnsf-dilworth",  MapFeatureType.YARD, 46.8619, -96.7344,
                "Dilworth Yard (BNSF)", "BNSF northern plains hub near Moorhead MN",
                "Private property", "160.410", true),
            // ── CSX ───────────────────────────────────────────────────────────
            MapFeature("yard-csx-selkirk",   MapFeatureType.YARD, 42.5537, -73.8426,
                "Selkirk Yard (CSX)", "CSX's largest Northeast yard near Albany NY",
                "Private property", "160.230", true),
            MapFeature("yard-csx-willard",   MapFeatureType.YARD, 41.0548, -82.7235,
                "Willard Yard (CSX)", "Major CSX hump yard in northern Ohio",
                "Private property", "160.560", true),
            MapFeature("yard-csx-russell",   MapFeatureType.YARD, 38.5162, -82.6843,
                "Russell Yard (CSX)", "CSX Kentucky hub — heavy coal and manifest traffic",
                "Private property", "160.230", true),
            MapFeature("yard-csx-waycross",  MapFeatureType.YARD, 31.2138, -82.3579,
                "Rice Yard / Waycross (CSX)", "CSX Southeast hub — busiest yard in the SE",
                "Private property", "161.070", true),
            MapFeature("yard-csx-calumet",   MapFeatureType.YARD, 41.6789, -87.5876,
                "Calumet Yard (CSX)", "CSX Chicago south side intermodal yard",
                "Private property", "160.410", true),
            MapFeature("yard-csx-cumberland",MapFeatureType.YARD, 39.6529, -78.7631,
                "Cumberland Yard (CSX)", "CSX mountain subdivision hub",
                "Private property", "160.230", true),
            MapFeature("yard-csx-hamlet",    MapFeatureType.YARD, 34.8887, -79.6978,
                "Hamlet Yard (CSX)", "CSX Carolinas hub",
                "Private property", "160.560", true),
            // ── Norfolk Southern ──────────────────────────────────────────────
            MapFeature("yard-ns-conway",     MapFeatureType.YARD, 40.6553, -80.2418,
                "Conway Yard (NS)", "NS Pittsburgh-area hump yard",
                "Private property", "160.410", true),
            MapFeature("yard-ns-elkhart",    MapFeatureType.YARD, 41.6856, -85.9669,
                "Elkhart Yard (NS)", "NS Chicago-line gateway — high intermodal volume",
                "Private property", "161.070", true),
            MapFeature("yard-ns-linwood",    MapFeatureType.YARD, 36.0729, -79.7772,
                "Linwood Yard (NS)", "NS Piedmont Division hub near Greensboro NC",
                "Private property", "160.410", true),
            MapFeature("yard-ns-enola",      MapFeatureType.YARD, 40.2851, -76.9555,
                "Enola Yard (NS)", "NS former PRR hump yard near Harrisburg PA",
                "Private property", "161.190", true),
            MapFeature("yard-ns-chattanooga",MapFeatureType.YARD, 35.0456, -85.3097,
                "Chattanooga Yard (NS)", "NS Southeast hub at the Tennessee Gateway",
                "Private property", "160.410", true),
            MapFeature("yard-ns-decatur",    MapFeatureType.YARD, 39.8519, -88.8695,
                "Decatur Yard (NS)", "NS Illinois hub — heavy grain and manifest",
                "Private property", "160.515", true),
            // ── Canadian National ─────────────────────────────────────────────
            MapFeature("yard-cn-braidwood",  MapFeatureType.YARD, 41.2553, -88.2109,
                "Braidwood Yard (CN)", "CN Chicago-area classification yard",
                "Private property", "160.410", true),
            MapFeature("yard-cn-memphis",    MapFeatureType.YARD, 35.1067, -90.0534,
                "Johnston Yard (CN)", "CN Memphis gateway — connects to IC lines",
                "Private property", "161.070", true),
            // ── Canadian Pacific / CPKC ───────────────────────────────────────
            MapFeature("yard-cp-bensenville",MapFeatureType.YARD, 41.9575, -87.9425,
                "Bensenville Yard (CPKC)", "CPKC Chicago gateway hump yard",
                "Private property", "160.515", true),
            MapFeature("yard-cp-kansas-city",MapFeatureType.YARD, 39.1042, -94.6261,
                "Knoche Yard (CPKC)", "CPKC Kansas City hub — post-merger KCS traffic",
                "Private property", "160.410", true),
            // ── KCS / CPKC South ─────────────────────────────────────────────
            MapFeature("yard-kcs-shreveport",MapFeatureType.YARD, 32.5252, -93.7502,
                "Deramus Yard (CPKC)", "Former KCS Shreveport hub on the Meridian Speedway",
                "Private property", "161.130", true),
        )

        private val RAILROAD_TERRITORY_CENTERS: Map<Railroad, Pair<Double, Double>> = mapOf(
            Railroad.BNSF   to Pair(38.0, -100.0),
            Railroad.UP     to Pair(41.0, -105.0),
            Railroad.CSX    to Pair(38.5,  -82.0),
            Railroad.NS     to Pair(37.5,  -80.5),
            Railroad.CN     to Pair(44.5,  -88.0),
            Railroad.CP     to Pair(47.0,  -97.0),
            Railroad.AMTRAK to Pair(40.7,  -74.0),
            Railroad.KCS    to Pair(35.5,  -94.0),
            // Railroad.OTHER intentionally omitted — always pinned to bottom
        )
    }
}
