package com.railfancopilot.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.railfancopilot.app.data.models.SavedLocation
import com.railfancopilot.app.ui.components.EmptyState
import com.railfancopilot.app.ui.components.ProGateScreen
import com.railfancopilot.app.ui.components.SectionHeader
import com.railfancopilot.app.ui.theme.*
import com.railfancopilot.app.viewmodel.RailFanViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val FREE_LOCATION_LIMIT = 3

@Composable
fun SavedLocationsScreen(vm: RailFanViewModel, onUpgrade: () -> Unit = {}) {
    val isPro by vm.isProUser.collectAsState()
    val savedLocations by vm.savedLocations.collectAsState()
    val userLocation by vm.userLocation.collectAsState()
    var showSaveDialog by remember { mutableStateOf(false) }
    var locationToDelete by remember { mutableStateOf<SavedLocation?>(null) }
    var showProGate by remember { mutableStateOf(false) }

    if (showProGate) {
        ProGateScreen(
            featureName = "Unlimited Saved Locations",
            description = "Free tier allows up to $FREE_LOCATION_LIMIT saved spots. Upgrade to Pro to save unlimited railfan locations.",
            onUpgrade = { showProGate = false; onUpgrade() }
        )
        return
    }

    val atFreeLimit = !isPro && savedLocations.size >= FREE_LOCATION_LIMIT

    LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
        item {
            Row(
                modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Saved Locations", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    val countLabel = if (!isPro)
                        "${savedLocations.size} / $FREE_LOCATION_LIMIT spots (free)"
                    else
                        "${savedLocations.size} saved spot${if (savedLocations.size == 1) "" else "s"}"
                    Text(countLabel, color = TextMuted, fontSize = 13.sp)
                }
                FloatingActionButton(
                    onClick = { if (atFreeLimit) showProGate = true else showSaveDialog = true },
                    containerColor = RailBlueDark,
                    contentColor = RailBlue,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Save new location", modifier = Modifier.size(20.dp))
                }
            }
        }

        if (savedLocations.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.BookmarkBorder,
                    title = "No saved locations",
                    subtitle = "Tap + to save your current GPS position, or save a spot from a train's detail card on the map"
                )
            }
        } else {
            item { SectionHeader("Saved spots") }
            items(savedLocations, key = { it.id }) { location ->
                SavedLocationCard(
                    location = location,
                    onDelete = { locationToDelete = location }
                )
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }

    // Save current GPS position dialog
    if (showSaveDialog) {
        SaveLocationDialog(
            defaultLat = userLocation?.latitude,
            defaultLon = userLocation?.longitude,
            onDismiss = { showSaveDialog = false },
            onSave = { name, notes, subdivision, scannerFreq, photoTips, lat, lon ->
                vm.saveLocation(lat, lon, name, notes, subdivision, scannerFreq, photoTips)
                showSaveDialog = false
            }
        )
    }

    // Delete confirmation
    locationToDelete?.let { loc ->
        AlertDialog(
            onDismissRequest = { locationToDelete = null },
            containerColor = BgCard,
            title = { Text("Delete location?", color = TextPrimary, fontSize = 16.sp) },
            text = { Text("\"${loc.name}\" will be permanently removed.", color = TextSecondary, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteLocation(loc)
                    locationToDelete = null
                }) { Text("Delete", color = RailRed) }
            },
            dismissButton = {
                TextButton(onClick = { locationToDelete = null }) { Text("Cancel", color = TextMuted) }
            }
        )
    }
}

@Composable
fun SavedLocationCard(location: SavedLocation, onDelete: () -> Unit) {
    val context = LocalContext.current
    val dateStr = remember(location.createdMs) {
        SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(location.createdMs))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(0.5.dp, Border, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.Bookmark, contentDescription = "Saved location", tint = RailBlue, modifier = Modifier.size(20.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(location.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                String.format("%.4f°, %.4f°", location.latitude, location.longitude),
                color = TextMuted, fontSize = 12.sp
            )
            if (!location.notes.isNullOrBlank()) {
                Text(location.notes, color = TextSecondary, fontSize = 12.sp, maxLines = 2)
            }
            if (!location.subdivision.isNullOrBlank()) {
                Text(location.subdivision, color = RailBlue, fontSize = 11.sp)
            }
            if (!location.scannerFrequency.isNullOrBlank()) {
                Text("Scanner: ${location.scannerFrequency}", color = TextMuted, fontSize = 11.sp)
            }
            if (!location.photoTips.isNullOrBlank()) {
                Text(location.photoTips, color = TextSecondary, fontSize = 11.sp, maxLines = 2)
            }
            Text(dateStr, color = TextMuted, fontSize = 11.sp)
        }

        // Navigate — opens coordinates in Google Maps (or any installed maps app)
        IconButton(
            onClick = {
                val encodedName = Uri.encode(location.name)
                val uri = Uri.parse("geo:${location.latitude},${location.longitude}?q=${location.latitude},${location.longitude}($encodedName)")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                try {
                    context.startActivity(intent)
                } catch (_: android.content.ActivityNotFoundException) {
                    Toast.makeText(context, "No maps app found — install Google Maps or similar", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Default.Navigation, contentDescription = "Navigate to ${location.name}",
                tint = RailBlue, modifier = Modifier.size(18.dp))
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete ${location.name}",
                tint = TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun SaveLocationDialog(
    defaultLat: Double?,
    defaultLon: Double?,
    onDismiss: () -> Unit,
    onSave: (name: String, notes: String?, subdivision: String?, scannerFreq: String?, photoTips: String?, lat: Double, lon: Double) -> Unit
) {
    var name        by remember { mutableStateOf("") }
    var notes       by remember { mutableStateOf("") }
    var subdivision by remember { mutableStateOf("") }
    var scannerFreq by remember { mutableStateOf("") }
    var photoTips   by remember { mutableStateOf("") }

    val nameFocus       = remember { FocusRequester() }
    val notesFocus      = remember { FocusRequester() }
    val subdivFocus     = remember { FocusRequester() }
    val frequencyFocus  = remember { FocusRequester() }
    val tipsFocus       = remember { FocusRequester() }

    LaunchedEffect(Unit) { nameFocus.requestFocus() }

    val hasGps = defaultLat != null && defaultLon != null
    val coordLabel = if (hasGps)
        String.format("%.4f°, %.4f°", defaultLat, defaultLon)
    else "No GPS fix yet"

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = RailBlueMid,
        unfocusedBorderColor = BorderLight,
        cursorColor = RailBlue,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary
    )

    fun doSave() {
        val lat = defaultLat; val lon = defaultLon
        if (name.isNotBlank() && lat != null && lon != null)
            onSave(name, notes.ifBlank { null }, subdivision.ifBlank { null },
                scannerFreq.ifBlank { null }, photoTips.ifBlank { null }, lat, lon)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        title = {
            Text("Save Location", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // GPS readout
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(BgInput)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        if (hasGps) Icons.Default.GpsFixed else Icons.Default.GpsOff,
                        contentDescription = null,
                        tint = if (hasGps) RailBlue else TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        coordLabel,
                        color = if (hasGps) TextPrimary else TextSecondary,
                        fontSize = 13.sp
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Location name", color = TextMuted) },
                    placeholder = { Text("e.g. MP 330 Overpass", color = TextMuted) },
                    modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
                    colors = fieldColors,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { notesFocus.requestFocus() })
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)", color = TextMuted) },
                    placeholder = { Text("Lighting tips, access info…", color = TextMuted) },
                    modifier = Modifier.fillMaxWidth().focusRequester(notesFocus),
                    colors = fieldColors,
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { subdivFocus.requestFocus() })
                )

                OutlinedTextField(
                    value = subdivision,
                    onValueChange = { subdivision = it },
                    label = { Text("Subdivision (optional)", color = TextMuted) },
                    placeholder = { Text("e.g. Transcon, Mains, Springfield", color = TextMuted) },
                    modifier = Modifier.fillMaxWidth().focusRequester(subdivFocus),
                    colors = fieldColors,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { frequencyFocus.requestFocus() })
                )

                OutlinedTextField(
                    value = scannerFreq,
                    onValueChange = { scannerFreq = it },
                    label = { Text("Scanner frequency (optional)", color = TextMuted) },
                    placeholder = { Text("e.g. 161.100", color = TextMuted) },
                    modifier = Modifier.fillMaxWidth().focusRequester(frequencyFocus),
                    colors = fieldColors,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { tipsFocus.requestFocus() })
                )

                OutlinedTextField(
                    value = photoTips,
                    onValueChange = { photoTips = it },
                    label = { Text("Photo tips (optional)", color = TextMuted) },
                    placeholder = { Text("Best angle, time of day, access notes…", color = TextMuted) },
                    modifier = Modifier.fillMaxWidth().focusRequester(tipsFocus),
                    colors = fieldColors,
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { doSave() })
                )
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(
                    onClick = { doSave() },
                    enabled = name.isNotBlank() && hasGps
                ) { Text("Save", color = if (name.isNotBlank() && hasGps) RailBlue else TextMuted) }
                if (!hasGps) {
                    Text(
                        "Waiting for GPS fix…",
                        color = TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(end = 8.dp, bottom = 4.dp)
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        }
    )
}
