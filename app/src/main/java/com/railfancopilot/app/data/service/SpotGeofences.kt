package com.railfancopilot.app.data.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.railfancopilot.app.data.models.SavedLocation
import com.railfancopilot.app.data.models.TrainLocation
import com.railfancopilot.app.data.repository.RailFanRepository
import com.railfancopilot.app.viewmodel.settingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

// ── Saved-spot arrival alerts ─────────────────────────────────────────────────
// Each saved location is registered as an Android geofence. Arriving at one wakes
// the app (even when it's closed) to fetch trains near that spot and notify the user.
// Play services does the location monitoring; the app never tracks a location history.

private const val GEOFENCE_RADIUS_M = 300f
private const val MAX_GEOFENCES = 100                  // Play services per-app limit
private const val NEARBY_TRAIN_RADIUS_MILES = 25.0     // matches the in-app saved-location alerts
private const val GEOFENCE_CHANNEL = "geofence_alerts" // same channel RailFanViewModel creates

object SpotGeofences {

    fun hasPermission(context: Context): Boolean {
        fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        val background = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        return granted(Manifest.permission.ACCESS_FINE_LOCATION) && background
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 0, Intent(context, SpotGeofenceReceiver::class.java),
            // Must be mutable: Play services fills in the geofencing event extras
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

    /** Replaces all registered geofences with one per saved spot (first [MAX_GEOFENCES]). */
    @SuppressLint("MissingPermission")
    fun sync(context: Context, spots: List<SavedLocation>) {
        val app = context.applicationContext
        val client = LocationServices.getGeofencingClient(app)
        val intent = pendingIntent(app)
        client.removeGeofences(intent).addOnCompleteListener {
            if (spots.isEmpty() || !hasPermission(app)) return@addOnCompleteListener
            val fences = spots.take(MAX_GEOFENCES).map { spot ->
                Geofence.Builder()
                    .setRequestId(spot.id)
                    .setCircularRegion(spot.latitude, spot.longitude, GEOFENCE_RADIUS_M)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                    .setNotificationResponsiveness(60_000)
                    .build()
            }
            val request = GeofencingRequest.Builder()
                .setInitialTrigger(0)   // don't fire for a spot the user is already standing at
                .addGeofences(fences)
                .build()
            client.addGeofences(request, intent)
        }
    }
}

/** Receives geofence entries from Play services and hands the train check to WorkManager. */
class SpotGeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError() || event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return
        val ids = event.triggeringGeofences?.map { it.requestId }.orEmpty()
        if (ids.isEmpty()) return

        val work = OneTimeWorkRequestBuilder<SpotArrivalWorker>()
            .setInputData(workDataOf(SpotArrivalWorker.KEY_SPOT_IDS to ids.toTypedArray()))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueue(work)
    }
}

/** Geofences are cleared on reboot and on app update, so re-register them from the database. */
class SpotGeofenceBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SpotGeofences.sync(context, RailFanRepository(context).getSavedLocationsFlow().first())
            } finally {
                pending.finish()
            }
        }
    }
}

class SpotArrivalWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ids = inputData.getStringArray(KEY_SPOT_IDS)?.toSet() ?: return Result.success()
        val repo = RailFanRepository(applicationContext)
        val spots = repo.getSavedLocationsFlow().first().filter { it.id in ids }
        if (spots.isEmpty()) return Result.success()
        val prefs = applicationContext.settingsDataStore.data.first()

        for (spot in spots) {
            val trains = try {
                withTimeout(30_000L) { fetchTrains(repo, prefs, spot.latitude, spot.longitude) }
            } catch (_: Exception) {
                // Feeds are flaky on cellular; one retry, then give up quietly
                return if (runAttemptCount < 1) Result.retry() else Result.success()
            }
            notifyNearbyTrains(spot, trains)
        }
        return Result.success()
    }

    // Same feeds as the live map, honouring the user's per-agency toggles (all default off
    // except Amtrak, which getLiveTrains always includes).
    private suspend fun fetchTrains(
        repo: RailFanRepository, prefs: Preferences, lat: Double, lon: Double
    ): List<TrainLocation> = coroutineScope {
        val r = NEARBY_TRAIN_RADIUS_MILES
        fun on(key: String) = prefs[booleanPreferencesKey(key)] == true
        listOf(
            async { repo.getLiveTrains(lat, lon, null, r) },
            async { if (on("mbta_enabled"))            repo.getMbtaTrains(lat, lon, r)          else emptyList() },
            async { if (on("septa_enabled"))           repo.getSeptaTrains(lat, lon, r)         else emptyList() },
            async { if (on("metra_enabled"))           repo.getMetraTrains(lat, lon, r)         else emptyList() },
            async { if (on("mta_lirr_enabled"))        repo.getMtaLirrTrains(lat, lon, r)       else emptyList() },
            async { if (on("mta_metro_north_enabled")) repo.getMtaMetroNorthTrains(lat, lon, r) else emptyList() },
            async { if (on("caltrain_enabled"))        repo.getCaltrainTrains(lat, lon, r)      else emptyList() },
            async { if (on("sound_transit_enabled"))   repo.getSoundTransitTrains(lat, lon, r)  else emptyList() },
            async { if (on("njt_enabled"))             repo.getNjTransitTrains(lat, lon, r)     else emptyList() },
            async { if (on("vre_enabled"))             repo.getVreTrains(lat, lon, r)           else emptyList() },
            async { if (on("marc_enabled"))            repo.getMarcTrains(lat, lon, r)          else emptyList() },
            async { if (on("metrolink_enabled"))       repo.getMetrolinkTrains(lat, lon, r)     else emptyList() }
        ).awaitAll().flatten()
    }

    private fun notifyNearbyTrains(spot: SavedLocation, trains: List<TrainLocation>) {
        val distBuf = FloatArray(1)
        val nearby = trains.map { train ->
            Location.distanceBetween(spot.latitude, spot.longitude, train.latitude, train.longitude, distBuf)
            train to distBuf[0] / 1609.34
        }.filter { it.second <= NEARBY_TRAIN_RADIUS_MILES }.sortedBy { it.second }
        if (nearby.isEmpty()) return   // nothing to report; stay quiet

        val (train, miles) = nearby.first()
        val distLabel = if (miles < 1.0) "< 1 mile away" else "${"%.0f".format(miles)} miles away"
        val more = if (nearby.size > 1) " (+${nearby.size - 1} more)" else ""

        val mgr = applicationContext.getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(NotificationChannel(
            GEOFENCE_CHANNEL, "Yard & Geofence Alerts", NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Alerts when you enter a classification yard or saved location" })

        val openApp = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
            ?.let { PendingIntent.getActivity(applicationContext, 0, it, PendingIntent.FLAG_IMMUTABLE) }

        val notif = NotificationCompat.Builder(applicationContext, GEOFENCE_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("You're at ${spot.name}")
            .setContentText("${train.symbol} · ${train.railroad.displayName} · $distLabel$more")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        mgr.notify("arrival_${spot.id}".hashCode(), notif)
    }

    companion object {
        const val KEY_SPOT_IDS = "spot_ids"
    }
}
