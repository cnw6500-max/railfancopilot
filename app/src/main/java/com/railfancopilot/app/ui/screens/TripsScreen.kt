package com.railfancopilot.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.railfancopilot.app.data.models.Railroad
import com.railfancopilot.app.data.models.TripLog
import com.railfancopilot.app.ui.components.EmptyState
import com.railfancopilot.app.ui.components.SectionHeader
import com.railfancopilot.app.ui.theme.*
import com.railfancopilot.app.viewmodel.RailFanViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TripsScreen(vm: RailFanViewModel) {
    val trips      by vm.tripLogs.collectAsState()
    val activeTrip by vm.activeTrip.collectAsState()
    val stats      by vm.tripStats.collectAsState()

    // Compute total hours from completed trips
    val totalHours = remember(trips) {
        trips.filter { !it.isActive }
            .sumOf { it.durationMinutes } / 60.0
    }

    var showEndDialog       by remember { mutableStateOf(false) }
    var notesInput          by remember { mutableStateOf("") }
    var alightingInput      by remember { mutableStateOf("") }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 100.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
    ) {

        // ── Header ─────────────────────────────────────────────────────────────
        item {
            Column(modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp)) {
                Text("Trip Log", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Text("Your rail journeys", color = TextMuted, fontSize = 13.sp)
            }
        }

        // ── Stats row ──────────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(BgCard)
                    .border(0.5.dp, BorderLight, RoundedCornerShape(14.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TripStat(
                    label = "Miles",
                    value = "%.1f".format(stats.totalMiles),
                    icon  = Icons.Default.Speed
                )
                VerticalDivider(
                    modifier = Modifier.height(40.dp),
                    color = Border
                )
                TripStat(
                    label = "Trips",
                    value = stats.completedTrips.toString(),
                    icon  = Icons.Default.DirectionsRailway
                )
                VerticalDivider(
                    modifier = Modifier.height(40.dp),
                    color = Border
                )
                TripStat(
                    label = "Hours",
                    value = "%.1f".format(totalHours),
                    icon  = Icons.Default.Schedule
                )
            }
        }

        // ── Active trip banner ─────────────────────────────────────────────────
        activeTrip?.let { trip ->
            item {
                val rr = remember(trip.railroad) {
                    Railroad.values().find { it.name == trip.railroad }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0F2A18))
                        .border(1.dp, RailGreen.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(RailGreen)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Riding ${trip.trainSymbol}",
                                color = RailGreen,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                buildString {
                                    append(rr?.displayName ?: trip.railroad)
                                    append(" · ${"%.1f".format(trip.distanceMiles)} mi")
                                    val mins = ((System.currentTimeMillis() - trip.startMs) / 60_000).toInt()
                                    if (mins > 0) append(" · ${mins} min")
                                },
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Button(
                        onClick = { showEndDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = RailGreen.copy(alpha = 0.2f)),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.Stop, null,
                            modifier = Modifier.size(16.dp), tint = RailGreen)
                        Spacer(Modifier.width(6.dp))
                        Text("End Trip", color = RailGreen, fontSize = 14.sp)
                    }
                }
            }
        }

        // ── Trip list ──────────────────────────────────────────────────────────
        if (trips.filter { !it.isActive }.isEmpty() && activeTrip == null) {
            item {
                EmptyState(
                    icon     = Icons.Default.DirectionsRailway,
                    title    = "No trips yet",
                    subtitle = "Tap \"Ride this Train\" on any live train to start logging a trip"
                )
            }
        } else {
            val completed = trips.filter { !it.isActive }
            if (completed.isNotEmpty()) {
                item { SectionHeader("Completed Trips (${completed.size})") }
                items(completed, key = { it.id }) { trip ->
                    TripCard(trip = trip, onDelete = { vm.deleteTrip(trip) })
                }
            }
        }
    }

    // ── End trip dialog ────────────────────────────────────────────────────────
    if (showEndDialog) {
        AlertDialog(
            onDismissRequest = { showEndDialog = false },
            containerColor   = BgCard,
            title = {
                Row(
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Flag, null, tint = RailGreen, modifier = Modifier.size(20.dp))
                    Text("End Trip", color = TextPrimary, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    activeTrip?.let { trip ->
                        Text(
                            "${"%.1f".format(trip.distanceMiles)} miles · ${trip.durationMinutes} min",
                            color = TextSecondary, fontSize = 14.sp
                        )
                    }
                    OutlinedTextField(
                        value         = alightingInput,
                        onValueChange = { alightingInput = it },
                        label         = { Text("Alighting station (optional)", color = TextMuted) },
                        placeholder   = { Text("e.g. New York Penn Station", color = TextMuted,
                            fontSize = 12.sp) },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true,
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = RailGreen,
                            unfocusedBorderColor = Border,
                            focusedTextColor     = TextPrimary,
                            unfocusedTextColor   = TextPrimary,
                            cursorColor          = RailGreen,
                            focusedContainerColor   = BgCard,
                            unfocusedContainerColor = BgCard
                        )
                    )
                    OutlinedTextField(
                        value         = notesInput,
                        onValueChange = { notesInput = it },
                        label         = { Text("Notes (optional)", color = TextMuted) },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = false,
                        maxLines      = 3,
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = RailGreen,
                            unfocusedBorderColor = Border,
                            focusedTextColor     = TextPrimary,
                            unfocusedTextColor   = TextPrimary,
                            cursorColor          = RailGreen,
                            focusedContainerColor   = BgCard,
                            unfocusedContainerColor = BgCard
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.endTrip(
                            notes             = notesInput.ifBlank { null },
                            alightingStation  = alightingInput.ifBlank { null }
                        )
                        showEndDialog    = false
                        notesInput       = ""
                        alightingInput   = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RailGreen.copy(alpha = 0.8f))
                ) { Text("Save Trip", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showEndDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun TripStat(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = RailBlue, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = TextMuted, fontSize = 11.sp)
    }
}

@Composable
fun TripCard(trip: TripLog, onDelete: () -> Unit) {
    val rr = remember(trip.railroad) {
        Railroad.values().find { it.name == trip.railroad }
    }
    var showDelete by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(0.5.dp, BorderLight, RoundedCornerShape(12.dp))
            .clickable { showDelete = !showDelete }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Railroad color accent strip
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (rr != null) Color(rr.color) else BorderLight
                )
        )

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    trip.trainSymbol,
                    color      = TextPrimary,
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text("·", color = TextMuted, fontSize = 12.sp)
                Text(
                    rr?.displayName ?: trip.railroad,
                    color    = TextMuted,
                    fontSize = 12.sp
                )
            }
            Text(
                buildString {
                    append("%.1f mi".format(trip.distanceMiles))
                    if (trip.durationMinutes > 0) {
                        val h = trip.durationMinutes / 60
                        val m = trip.durationMinutes % 60
                        append(" · ")
                        if (h > 0) append("${h}h ")
                        append("${m}m")
                    }
                },
                color    = TextSecondary,
                fontSize = 12.sp
            )
            if (trip.boardingStation != null || trip.alightingStation != null) {
                Text(
                    buildString {
                        trip.boardingStation?.let { append(it) }
                        if (trip.boardingStation != null && trip.alightingStation != null) append(" → ")
                        trip.alightingStation?.let { append(it) }
                    },
                    color = TextSecondary, fontSize = 11.sp, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            trip.notes?.let {
                Text(it, color = TextMuted, fontSize = 11.sp, maxLines = 1)
            }
            Text(
                remember(trip.startMs) {
                    SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.US).format(Date(trip.startMs))
                },
                color    = TextMuted,
                fontSize = 11.sp
            )
        }

        if (showDelete) {
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.DeleteOutline, null, tint = RailRed, modifier = Modifier.size(18.dp))
            }
        } else {
            Icon(
                Icons.Default.DirectionsRailway,
                null,
                tint     = RailBlue.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
