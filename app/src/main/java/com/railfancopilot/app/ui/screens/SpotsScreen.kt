package com.railfancopilot.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.railfancopilot.app.data.models.RailfanSpot
import com.railfancopilot.app.data.models.SavedLocation
import com.railfancopilot.app.data.models.TrainFrequency
import com.railfancopilot.app.ui.theme.*
import com.railfancopilot.app.viewmodel.RailFanViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SpotsScreen(vm: RailFanViewModel, onUpgrade: () -> Unit = {}) {
    val communitySpots  by vm.communitySpots.collectAsState()
    val savedLocations  by vm.savedLocations.collectAsState()
    val userLocation    by vm.userLocation.collectAsState()
    val isSubmitting    by vm.isSubmittingSpot.collectAsState()
    val submitError     by vm.spotSubmitError.collectAsState()
    val userName        by vm.userName.collectAsState()
    val isPro           by vm.isProUser.collectAsState()

    var selectedTab     by remember { mutableIntStateOf(0) }
    var showSubmit      by remember { mutableStateOf(false) }
    var selectedSpot    by remember { mutableStateOf<RailfanSpot?>(null) }

    val context = LocalContext.current

    LaunchedEffect(submitError) {
        if (submitError != null) {
            Toast.makeText(context, submitError, Toast.LENGTH_LONG).show()
            vm.clearSpotSubmitError()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Railfan Spots", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Community & personal trackside locations", color = TextMuted, fontSize = 12.sp)
            }
            IconButton(onClick = { showSubmit = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add spot", tint = RailBlue)
            }
        }

        // Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = BgPrimary,
            contentColor = RailBlue,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = RailBlue
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Text(
                        "Nearby (${communitySpots.size})",
                        color = if (selectedTab == 0) RailBlue else TextMuted,
                        fontSize = 13.sp
                    )
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Text(
                        "Mine (${savedLocations.size})",
                        color = if (selectedTab == 1) RailBlue else TextMuted,
                        fontSize = 13.sp
                    )
                }
            )
        }

        when (selectedTab) {
            0 -> CommunitySpotsList(
                spots = communitySpots,
                onSpotClick = { selectedSpot = it },
                onUpvote = { vm.upvoteSpot(it) }
            )
            1 -> PrivateSpotsList(
                locations = savedLocations,
                userLocation = userLocation,
                onDelete = { vm.deleteLocation(it) },
                onAdd = {
                    if (!isPro && savedLocations.size >= 3) onUpgrade()
                    else showSubmit = true
                }
            )
        }
    }

    // Spot detail sheet
    selectedSpot?.let { spot ->
        SpotDetailSheet(
            spot = spot,
            onDismiss = { selectedSpot = null },
            onUpvote = { vm.upvoteSpot(spot.id) },
            onNavigate = {
                val uri = Uri.parse("geo:${spot.latitude},${spot.longitude}?q=${spot.latitude},${spot.longitude}(${Uri.encode(spot.name)})")
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
        )
    }

    // Submit dialog
    if (showSubmit) {
        SubmitSpotDialog(
            userLat = userLocation?.latitude,
            userLon = userLocation?.longitude,
            userName = userName,
            isSubmitting = isSubmitting,
            onDismiss = { showSubmit = false },
            onSubmit = { spot, photoBytes ->
                vm.submitSpot(spot, photoBytes)
                showSubmit = false
            }
        )
    }
}

@Composable
private fun CommunitySpotsList(
    spots: List<RailfanSpot>,
    onSpotClick: (RailfanSpot) -> Unit,
    onUpvote: (String) -> Unit
) {
    if (spots.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("📍", fontSize = 48.sp)
            Spacer(Modifier.height(16.dp))
            Text("No spots nearby yet", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Be the first to submit a railfan spot in your area.",
                color = TextSecondary, fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(spots, key = { it.id }) { spot ->
            CommunitySpotCard(spot = spot, onClick = { onSpotClick(spot) }, onUpvote = { onUpvote(spot.id) })
        }
    }
}

@Composable
private fun CommunitySpotCard(spot: RailfanSpot, onClick: () -> Unit, onUpvote: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgCard, RoundedCornerShape(12.dp))
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        // Photo preview if available
        if (spot.photoUrls.isNotEmpty()) {
            AsyncImage(
                model = spot.photoUrls.first(),
                contentDescription = spot.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            )
        }

        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    spot.name,
                    color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (spot.isPublicProperty) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF1A3A1A), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Public", color = RailGreen, fontSize = 10.sp)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            if (spot.railroad.isNotBlank()) {
                Text(
                    buildString {
                        append(spot.railroad)
                        if (spot.subdivision.isNotBlank()) append(" · ${spot.subdivision}")
                    },
                    color = RailBlue, fontSize = 12.sp
                )
            }

            if (spot.notes.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(spot.notes, color = TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            Spacer(Modifier.height(8.dp))

            // Amenity chips
            val amenities = buildList {
                if (spot.hasParking) add("🚗 Parking")
                if (spot.hasRestrooms) add("🚻 Restrooms")
                if (spot.hasFood) add("🍔 Food")
                if (spot.hasShade) add("🌳 Shade")
            }
            if (amenities.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    amenities.forEach { tag ->
                        Text(
                            tag,
                            color = TextMuted, fontSize = 10.sp,
                            modifier = Modifier
                                .background(BgInput, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    spot.trainFrequency.label,
                    color = TextMuted, fontSize = 11.sp, modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onUpvote,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.ThumbUp, contentDescription = null, tint = RailBlue, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${spot.upvotes}", color = RailBlue, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun PrivateSpotsList(
    locations: List<SavedLocation>,
    userLocation: android.location.Location?,
    onDelete: (SavedLocation) -> Unit,
    onAdd: () -> Unit
) {
    var toDelete by remember { mutableStateOf<SavedLocation?>(null) }
    val context = LocalContext.current

    if (locations.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🔖", fontSize = 48.sp)
            Spacer(Modifier.height(16.dp))
            Text("No saved spots", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Tap + to save your current GPS position as a private spot.",
                color = TextSecondary, fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(locations, key = { it.id }) { loc ->
                SavedLocationCard(location = loc, onDelete = { toDelete = loc })
            }
        }
    }

    toDelete?.let { loc ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = BgCard,
            title = { Text("Delete spot?", color = TextPrimary, fontSize = 16.sp) },
            text = { Text("\"${loc.name}\" will be permanently removed.", color = TextSecondary, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = { onDelete(loc); toDelete = null }) {
                    Text("Delete", color = RailRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("Cancel", color = TextMuted) }
            }
        )
    }
}

@Composable
private fun SpotDetailSheet(
    spot: RailfanSpot,
    onDismiss: () -> Unit,
    onUpvote: () -> Unit,
    onNavigate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        modifier = Modifier.fillMaxWidth(),
        title = {
            Text(spot.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (spot.photoUrls.isNotEmpty()) {
                    AsyncImage(
                        model = spot.photoUrls.first(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }

                if (spot.railroad.isNotBlank()) SpotDetailRow("Railroad", buildString {
                    append(spot.railroad)
                    if (spot.subdivision.isNotBlank()) append(" · ${spot.subdivision}")
                })
                if (spot.notes.isNotBlank()) SpotDetailRow("Notes", spot.notes)
                if (spot.photoAngles.isNotBlank()) SpotDetailRow("Photo angles", spot.photoAngles)
                if (spot.safetyNotes.isNotBlank()) SpotDetailRow("Safety", spot.safetyNotes)
                if (spot.parkingNotes.isNotBlank()) SpotDetailRow("Parking", spot.parkingNotes)
                if (spot.scannerFrequency.isNotBlank()) SpotDetailRow("Scanner", spot.scannerFrequency)
                if (spot.seasonalNotes.isNotBlank()) SpotDetailRow("Seasonal", spot.seasonalNotes)
                SpotDetailRow("Train traffic", spot.trainFrequency.label)
                SpotDetailRow("Access", if (spot.isPublicProperty) "Public property" else "Private — check before visiting")

                val amenities = buildList {
                    if (spot.hasParking) add("🚗 Parking")
                    if (spot.hasRestrooms) add("🚻 Restrooms")
                    if (spot.hasFood) add("🍔 Food nearby")
                    if (spot.hasShade) add("🌳 Shade")
                }
                if (amenities.isNotEmpty()) SpotDetailRow("Amenities", amenities.joinToString(" · "))

                Text(
                    "Submitted by ${spot.submittedBy} · ${
                        SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(spot.submittedMs))
                    }",
                    color = TextMuted, fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onUpvote) {
                    Icon(Icons.Default.ThumbUp, contentDescription = null, tint = RailBlue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${spot.upvotes}", color = RailBlue)
                }
                Button(
                    onClick = onNavigate,
                    colors = ButtonDefaults.buttonColors(containerColor = RailBlue)
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Navigate", color = Color.White)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = TextMuted) }
        }
    )
}

@Composable
private fun SpotDetailRow(label: String, value: String) {
    Column {
        Text(label, color = TextMuted, fontSize = 11.sp)
        Text(value, color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun SubmitSpotDialog(
    userLat: Double?,
    userLon: Double?,
    userName: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (RailfanSpot, ByteArray?) -> Unit
) {
    val context = LocalContext.current
    var name            by remember { mutableStateOf("") }
    var railroad        by remember { mutableStateOf("") }
    var subdivision     by remember { mutableStateOf("") }
    var notes           by remember { mutableStateOf("") }
    var photoAngles     by remember { mutableStateOf("") }
    var safetyNotes     by remember { mutableStateOf("") }
    var parkingNotes    by remember { mutableStateOf("") }
    var scannerFreq     by remember { mutableStateOf("") }
    var seasonalNotes   by remember { mutableStateOf("") }
    var trainFreq       by remember { mutableStateOf(TrainFrequency.UNKNOWN) }
    var isPublic        by remember { mutableStateOf(true) }
    var hasParking      by remember { mutableStateOf(false) }
    var hasRestrooms    by remember { mutableStateOf(false) }
    var hasFood         by remember { mutableStateOf(false) }
    var hasShade        by remember { mutableStateOf(false) }
    var photoBytes      by remember { mutableStateOf<ByteArray?>(null) }
    var photoLabel      by remember { mutableStateOf("") }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            photoBytes = bytes
            photoLabel = uri.lastPathSegment ?: "photo selected"
        } catch (_: Exception) { }
    }

    val hasGps = userLat != null && userLon != null
    val canSubmit = name.isNotBlank() && hasGps && !isSubmitting

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        title = { Text("Submit a Spot", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // GPS readout
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgInput, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        if (hasGps) Icons.Default.GpsFixed else Icons.Default.GpsOff,
                        contentDescription = null,
                        tint = if (hasGps) RailBlue else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        if (hasGps) String.format("%.4f°, %.4f°", userLat, userLon) else "No GPS fix",
                        color = if (hasGps) TextPrimary else TextMuted, fontSize = 13.sp
                    )
                }

                SpotField("Spot name *", name, { name = it }, "e.g. MP 330 Overpass")
                SpotField("Railroad", railroad, { railroad = it.uppercase() }, "e.g. BNSF")
                SpotField("Subdivision", subdivision, { subdivision = it }, "e.g. Transcon")
                SpotField("Notes / description", notes, { notes = it }, "What makes this spot great?", singleLine = false)
                SpotField("Photo angles", photoAngles, { photoAngles = it }, "e.g. Eastbound facing west", singleLine = false)
                SpotField("Safety notes", safetyNotes, { safetyNotes = it }, "Hazards, sight lines, fencing")
                SpotField("Parking notes", parkingNotes, { parkingNotes = it }, "Lot, street, pullout")
                SpotField("Scanner frequency", scannerFreq, { scannerFreq = it }, "e.g. 161.100")
                SpotField("Seasonal notes", seasonalNotes, { seasonalNotes = it }, "Foliage, lighting changes")

                // Train frequency picker
                Text("Train frequency", color = TextMuted, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TrainFrequency.entries.forEach { freq ->
                        val selected = trainFreq == freq
                        Box(
                            modifier = Modifier
                                .background(
                                    if (selected) RailBlueDark else BgInput,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { trainFreq = freq }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                freq.name.lowercase().replaceFirstChar { it.uppercase() },
                                color = if (selected) RailBlue else TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Public property toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Public property", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = isPublic,
                        onCheckedChange = { isPublic = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = RailBlue, checkedTrackColor = RailBlueDark)
                    )
                }

                // Amenities
                Text("Amenities", color = TextMuted, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AmenityToggle("🚗", "Parking", hasParking) { hasParking = it }
                    AmenityToggle("🚻", "Restrooms", hasRestrooms) { hasRestrooms = it }
                    AmenityToggle("🍔", "Food", hasFood) { hasFood = it }
                    AmenityToggle("🌳", "Shade", hasShade) { hasShade = it }
                }

                // Photo picker
                OutlinedButton(
                    onClick = { photoLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = RailBlue, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (photoLabel.isNotBlank()) photoLabel else "Add a photo (optional)",
                        color = if (photoLabel.isNotBlank()) TextPrimary else TextMuted,
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!canSubmit) return@Button
                    onSubmit(
                        RailfanSpot(
                            id               = UUID.randomUUID().toString(),
                            name             = name.trim(),
                            latitude         = userLat!!,
                            longitude        = userLon!!,
                            submittedBy      = userName,
                            submittedMs      = System.currentTimeMillis(),
                            railroad         = railroad.trim(),
                            subdivision      = subdivision.trim(),
                            notes            = notes.trim(),
                            photoAngles      = photoAngles.trim(),
                            safetyNotes      = safetyNotes.trim(),
                            parkingNotes     = parkingNotes.trim(),
                            scannerFrequency = scannerFreq.trim(),
                            seasonalNotes    = seasonalNotes.trim(),
                            trainFrequency   = trainFreq,
                            isPublicProperty = isPublic,
                            hasParking       = hasParking,
                            hasRestrooms     = hasRestrooms,
                            hasFood          = hasFood,
                            hasShade         = hasShade
                        ),
                        photoBytes
                    )
                },
                enabled = canSubmit,
                colors = ButtonDefaults.buttonColors(containerColor = RailBlue)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Submit", color = Color.White)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        }
    )
}

@Composable
private fun SpotField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        placeholder = { Text(placeholder, fontSize = 12.sp) },
        singleLine = singleLine,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = RailBlue,
            unfocusedBorderColor = Border,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedLabelColor = RailBlue,
            unfocusedLabelColor = TextMuted,
            cursorColor = RailBlue
        )
    )
}

@Composable
private fun AmenityToggle(emoji: String, label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(if (checked) RailBlueDark else BgInput, RoundedCornerShape(8.dp))
            .clickable { onToggle(!checked) }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(emoji, fontSize = 18.sp)
        Text(label, color = if (checked) RailBlue else TextMuted, fontSize = 10.sp)
    }
}
