package com.railfancopilot.app.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.railfancopilot.app.data.models.*
import com.railfancopilot.app.ui.components.FilterChip
import com.railfancopilot.app.ui.components.ProGateScreen
import com.railfancopilot.app.ui.components.SectionHeader
import com.railfancopilot.app.ui.theme.*
import com.railfancopilot.app.viewmodel.RailFanViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

@Composable
fun PhotoScreen(vm: RailFanViewModel, onUpgrade: () -> Unit = {}) {
    val isPro by vm.isProUser.collectAsState()

    if (!isPro) {
        ProGateScreen(
            featureName = "Photo & Loco ID",
            description = "Tag photos with GPS and train data, and identify any locomotive from a photo using Claude AI.",
            onUpgrade = onUpgrade
        )
        return
    }

    val sunInfo by vm.sunInfo.collectAsState()
    val newAchievement by vm.newAchievement.collectAsState()
    val isIdentifying by vm.isIdentifying.collectAsState()
    val locoIdResult by vm.locoIdResult.collectAsState()
    val locoIdError by vm.locoIdError.collectAsState()
    val isAnalyzingConsist by vm.isAnalyzingConsist.collectAsState()
    val consistResult by vm.consistResult.collectAsState()
    val consistError by vm.consistError.collectAsState()
    val achievements by vm.achievements.collectAsState()
    val taggedPhotos by vm.taggedPhotos.collectAsState()
    val userLocation by vm.userLocation.collectAsState()
    val trains by vm.trains.collectAsState()
    val locoIdHistory by vm.locoIdHistory.collectAsState()

    // Reverse once here so items() doesn't allocate a new list on every recomposition
    val taggedPhotosReversed by remember { derivedStateOf { taggedPhotos.reversed() } }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── Consist Analyzer state ────────────────────────────────────────────────
    var showConsistPicker by remember { mutableStateOf(false) }

    // ── Photo Enhancer state ──────────────────────────────────────────────────
    var originalBitmap  by remember { mutableStateOf<Bitmap?>(null) }
    var enhancedBitmap  by remember { mutableStateOf<Bitmap?>(null) }
    var enhancedPath    by remember { mutableStateOf<String?>(null) }
    var isEnhancing     by remember { mutableStateOf(false) }
    var showEnhancer    by remember { mutableStateOf(false) }
    var showOriginal    by remember { mutableStateOf(false) }   // before/after toggle

    // ── Loco Identifier state ─────────────────────────────────────────────────
    var showSourcePicker by remember { mutableStateOf(false) }

    // ── Camera + Tagging state ────────────────────────────────────────────────
    var capturedTagBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pendingTag by remember { mutableStateOf<PhotoMetadata?>(null) }
    var showTagDialog by remember { mutableStateOf(false) }
    var showNoGpsDialog by remember { mutableStateOf(false) }
    var notesText by remember { mutableStateOf("") }

    // ── Loco Identifier — camera launcher ────────────────────────────────────
    val locoCameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.Default) {
            val thumbPath = saveBitmapToFile(context, bitmap.scaleToMax(400), "loco_${System.currentTimeMillis()}")
            val base64 = bitmap.scaleToMax(800).toBase64Jpeg()
            withContext(Dispatchers.Main) { vm.identifyLocomotive(base64, thumbPath) }
        }
    }

    // ── Loco Identifier — gallery launcher ───────────────────────────────────
    val locoGalleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return@launch
            val thumbPath = saveBitmapToFile(context, bitmap.scaleToMax(400), "loco_${System.currentTimeMillis()}")
            val base64 = bitmap.scaleToMax(800).toBase64Jpeg()
            withContext(Dispatchers.Main) { vm.identifyLocomotive(base64, thumbPath) }
        }
    }

    // ── Consist Analyzer — camera launcher ───────────────────────────────────
    val consistCameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.Default) {
            val base64 = bitmap.scaleToMax(1200).toBase64Jpeg()
            withContext(Dispatchers.Main) { vm.analyzeConsist(base64) }
        }
    }

    // ── Consist Analyzer — gallery launcher ──────────────────────────────────
    val consistGalleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return@launch
            val base64 = bitmap.scaleToMax(1200).toBase64Jpeg()
            withContext(Dispatchers.Main) { vm.analyzeConsist(base64) }
        }
    }

    // ── Photo Enhancer — gallery launcher ─────────────────────────────────────
    val enhancerGalleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            isEnhancing = true
            val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                ?: run { isEnhancing = false; return@launch }
            val original = bmp.scaleToMax(1600)
            val enhanced = original.applyRailfanEnhancement()
            val path = saveBitmapToFile(context, enhanced, "enhanced_${System.currentTimeMillis()}")
            withContext(Dispatchers.Main) {
                originalBitmap = original
                enhancedBitmap = enhanced
                enhancedPath   = path
                showOriginal   = false
                isEnhancing    = false
                showEnhancer   = true
            }
        }
    }

    // ── Camera + Tagging — camera launcher ───────────────────────────────────
    val tagCameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap ?: return@rememberLauncherForActivityResult
        val lat = userLocation?.latitude ?: 0.0
        val lon = userLocation?.longitude ?: 0.0
        val nearest = trains.minByOrNull { train ->
            val dist = FloatArray(1)
            Location.distanceBetween(lat, lon, train.latitude, train.longitude, dist)
            dist[0]
        }
        val photoId = java.util.UUID.randomUUID().toString()
        // Persist JPEG to private storage so the thumbnail survives app restarts
        val savedPath = saveBitmapToFile(context, bitmap, photoId)
        capturedTagBitmap = bitmap
        pendingTag = PhotoMetadata(
            id = photoId,
            railroad = nearest?.railroad?.name,
            trainSymbol = nearest?.symbol,
            latitude = lat,
            longitude = lon,
            locationName = nearest?.subdivision,  // updated below once geocode resolves
            timestampMs = System.currentTimeMillis(),
            locoModel = null,
            notes = null,
            localPath = savedPath
        )
        showTagDialog = true
        // Resolve a human-readable place name in the background; update dialog live
        scope.launch {
            val placeName = vm.reverseGeocode(lat, lon)
            if (placeName != null) {
                pendingTag = pendingTag?.copy(
                    locationName = if (nearest?.subdivision != null)
                        "${nearest.subdivision} · $placeName"
                    else placeName
                )
            }
        }
    }

    // ── Loco Identifier source picker ─────────────────────────────────────────
    if (showSourcePicker) {
        AlertDialog(
            onDismissRequest = { showSourcePicker = false },
            containerColor = BgCard,
            title = { Text("Identify Locomotive", color = TextPrimary, fontSize = 16.sp) },
            text = { Text("Choose an image source", color = TextSecondary, fontSize = 14.sp) },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showSourcePicker = false; locoCameraLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = RailBlue)
                    ) {
                        Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Take Photo")
                    }
                    OutlinedButton(
                        onClick = { showSourcePicker = false; locoGalleryLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Choose from Gallery")
                    }
                    TextButton(onClick = { showSourcePicker = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel", color = TextMuted)
                    }
                }
            },
            dismissButton = {}
        )
    }

    // ── Consist Analyzer source picker ───────────────────────────────────────
    if (showConsistPicker) {
        AlertDialog(
            onDismissRequest = { showConsistPicker = false },
            containerColor = BgCard,
            title = { Text("Analyze Consist", color = TextPrimary, fontSize = 16.sp) },
            text = { Text("Choose an image source", color = TextSecondary, fontSize = 14.sp) },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showConsistPicker = false; consistCameraLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = RailBlue)
                    ) {
                        Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Take Photo")
                    }
                    OutlinedButton(
                        onClick = { showConsistPicker = false; consistGalleryLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Choose from Gallery")
                    }
                    TextButton(onClick = { showConsistPicker = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel", color = TextMuted)
                    }
                }
            },
            dismissButton = {}
        )
    }

    // ── Consist Analyzer loading ──────────────────────────────────────────────
    if (isAnalyzingConsist) {
        AlertDialog(
            onDismissRequest = { vm.cancelConsistAnalysis() },
            containerColor = BgCard,
            title = { Text("Analyzing Consist…", color = TextPrimary, fontSize = 16.sp) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(color = RailBlue, modifier = Modifier.size(28.dp))
                    Text("Claude is reading every unit in the photo", color = TextSecondary, fontSize = 14.sp)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { vm.cancelConsistAnalysis() }) { Text("Cancel", color = TextMuted) } }
        )
    }

    // ── Consist Analyzer result ───────────────────────────────────────────────
    val consistResultText = consistResult ?: consistError
    if (consistResultText != null && !isAnalyzingConsist) {
        AlertDialog(
            onDismissRequest = { vm.clearConsistResult() },
            containerColor = BgCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        if (consistError != null) Icons.Outlined.ErrorOutline else Icons.Default.Train,
                        null,
                        tint = if (consistError != null) RailRed else RailBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(if (consistError != null) "Error" else "Consist Analysis", color = TextPrimary, fontSize = 16.sp)
                }
            },
            text = {
                val scroll = rememberScrollState()
                Column(modifier = Modifier.verticalScroll(scroll)) {
                    Text(consistResultText, color = TextSecondary, fontSize = 13.sp, lineHeight = 20.sp)
                }
            },
            confirmButton = {
                Button(onClick = { vm.clearConsistResult() }, colors = ButtonDefaults.buttonColors(containerColor = RailBlue)) {
                    Text("Close")
                }
            }
        )
    }

    // ── Photo Enhancer — enhancing spinner ────────────────────────────────────
    if (isEnhancing) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = BgCard,
            title = { Text("Enhancing Photo…", color = TextPrimary, fontSize = 16.sp) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(color = RailBlue, modifier = Modifier.size(28.dp))
                    Text("Applying railfan preset", color = TextSecondary, fontSize = 14.sp)
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }

    // ── Photo Enhancer — result dialog ────────────────────────────────────────
    if (showEnhancer && enhancedBitmap != null && originalBitmap != null) {
        AlertDialog(
            onDismissRequest = { showEnhancer = false; originalBitmap = null; enhancedBitmap = null },
            containerColor = BgCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AutoFixHigh, null, tint = RailBlue, modifier = Modifier.size(20.dp))
                    Text("Photo Enhanced", color = TextPrimary, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Before/After toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        FilterChip("Before", showOriginal) { showOriginal = true }
                        Spacer(Modifier.width(8.dp))
                        FilterChip("After", !showOriginal) { showOriginal = false }
                    }
                    val displayBitmap = if (showOriginal) originalBitmap else enhancedBitmap
                    displayBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = if (showOriginal) "Original photo" else "Enhanced photo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                    Text(
                        "Saturation +35% · Contrast +15% · Warm tones",
                        color = TextMuted, fontSize = 10.sp
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (enhancedPath != null) {
                        OutlinedButton(
                            onClick = {
                                val file = File(enhancedPath!!)
                                if (file.exists()) {
                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                        context, "${context.packageName}.fileprovider", file
                                    )
                                    context.startActivity(Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "image/jpeg"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }, "Share enhanced photo"
                                    ))
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RailBlue)
                        ) {
                            Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Share")
                        }
                    }
                    Button(
                        onClick = { showEnhancer = false; originalBitmap = null; enhancedBitmap = null },
                        colors = ButtonDefaults.buttonColors(containerColor = RailBlue)
                    ) { Text("Done") }
                }
            },
            dismissButton = {}
        )
    }

    // ── Loco Identifier loading ───────────────────────────────────────────────
    if (isIdentifying) {
        AlertDialog(
            onDismissRequest = { vm.cancelIdentification() },
            containerColor = BgCard,
            title = { Text("Identifying...", color = TextPrimary, fontSize = 16.sp) },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = RailBlue, modifier = Modifier.size(28.dp))
                    Text("Analyzing locomotive image", color = TextSecondary, fontSize = 14.sp)
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { vm.cancelIdentification() }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    // ── Loco Identifier result ────────────────────────────────────────────────
    val locoResultText = locoIdResult ?: locoIdError
    if (locoResultText != null && !isIdentifying) {
        val resultLower = locoIdResult?.lowercase() ?: ""
        val isHeritage = locoIdResult != null && listOf(
            "heritage", "retro", "patched", "spirit of", "fallen flag",
            "commemorative", "historic", "paint scheme", "special livery"
        ).any { resultLower.contains(it) }
        val isForeign = locoIdResult != null && listOf(
            "ferromex", "via rail", "foreign power", "foreign unit",
            "mexican power", "canadian national power", "canadian pacific power", "kcs de mexico"
        ).any { resultLower.contains(it) }

        AlertDialog(
            onDismissRequest = { vm.clearLocoIdResult() },
            containerColor = BgCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        if (locoIdError != null) Icons.Outlined.ErrorOutline else Icons.Default.DirectionsRailway,
                        null,
                        tint = if (locoIdError != null) Color(0xFFEF4444) else RailBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(if (locoIdError != null) "Error" else "Locomotive Identified", color = TextPrimary, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (isHeritage || isForeign) {
                        val badgeColor = if (isHeritage) Color(0xFF2E7D32) else Color(0xFF1565C0)
                        val badgeBg   = if (isHeritage) Color(0xFF0F2D10) else Color(0xFF0D1F38)
                        val badgeLabel = if (isHeritage) "Heritage Unit Detected" else "Rare Find — Foreign Power"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(badgeBg)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Star, null, tint = badgeColor, modifier = Modifier.size(14.dp))
                            Text(badgeLabel, color = badgeColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    Text(locoResultText, color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp)
                }
            },
            confirmButton = {
                Button(onClick = { vm.clearLocoIdResult() }, colors = ButtonDefaults.buttonColors(containerColor = RailBlue)) {
                    Text("Close")
                }
            }
        )
    }

    // ── Tag confirmation dialog ───────────────────────────────────────────────
    if (showTagDialog && pendingTag != null) {
        AlertDialog(
            onDismissRequest = { showTagDialog = false; capturedTagBitmap = null },
            containerColor = BgCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.LocalOffer, null, tint = RailBlue, modifier = Modifier.size(20.dp))
                    Text("Photo Tagged", color = TextPrimary, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    capturedTagBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    pendingTag?.let { tag ->
                        TagRow(
                            "Train",
                            tag.trainSymbol ?: "No train nearby"
                        )
                        tag.railroad?.let { rrName ->
                            val displayName = Railroad.values().find { it.name == rrName }?.displayName ?: rrName
                            TagRow("Railroad", displayName)
                        }
                        tag.locationName?.let { TagRow("Location", it) }
                        TagRow("GPS", String.format("%.4f, %.4f", tag.latitude, tag.longitude))
                        TagRow("Time", remember(tag.timestampMs) {
                            SimpleDateFormat("MMM d, h:mm:ss a", Locale.US).format(Date(tag.timestampMs))
                        })
                    }
                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text("Notes (optional)", color = TextMuted, fontSize = 13.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = RailBlue,
                            unfocusedBorderColor = BorderLight,
                            focusedLabelColor = RailBlue,
                            unfocusedLabelColor = TextMuted
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingTag?.let { tag ->
                            vm.saveTaggedPhoto(tag.copy(notes = notesText.ifBlank { null }))
                        }
                        showTagDialog = false
                        capturedTagBitmap = null
                        notesText = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RailBlue)
                ) { Text("Save Tag") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showTagDialog = false
                    capturedTagBitmap = null
                    notesText = ""
                }) { Text("Discard", color = TextMuted) }
            }
        )
    }

    // ── No-GPS dialog ─────────────────────────────────────────────────────────
    if (showNoGpsDialog) {
        AlertDialog(
            onDismissRequest = { showNoGpsDialog = false },
            containerColor = BgCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.LocationOff, null, tint = RailAmber, modifier = Modifier.size(20.dp))
                    Text("No GPS Fix", color = TextPrimary, fontSize = 16.sp)
                }
            },
            text = { Text("Waiting for a location fix. Photos can't be tagged without GPS coordinates — please try again once location is acquired.", color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp) },
            confirmButton = {
                Button(onClick = { showNoGpsDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = RailBlue)) {
                    Text("OK")
                }
            }
        )
    }

    // ── Main content ──────────────────────────────────────────────────────────
    LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
        item {
            Column(modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp)) {
                Text("Photography Tools", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                val locLabel = userLocation?.let {
                    String.format("%.4f°, %.4f°", it.latitude, it.longitude)
                } ?: "Waiting for location fix…"
                Text(locLabel, color = TextMuted, fontSize = 13.sp)
            }
        }

        // Achievement unlock banner — auto-dismisses after 4 s or when tapped
        newAchievement?.let { achievement ->
            item {
                // Auto-dismiss after 4 seconds; key on achievement so the timer resets for each new one
                LaunchedEffect(achievement) {
                    kotlinx.coroutines.delay(4_000L)
                    vm.consumeNewAchievement()
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AchievementBannerBg)
                        .border(1.dp, RailGreen.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .clickable { vm.consumeNewAchievement() }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(achievement.iconEmoji, fontSize = 22.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Achievement unlocked!", color = RailGreen,
                            fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Text(achievement.title, color = TextPrimary,
                            fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(achievement.description, color = TextSecondary, fontSize = 11.sp)
                    }
                    Icon(Icons.Default.Close, contentDescription = "Dismiss",
                        tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }
        }

        item { SunPredictorCard(sunInfo) }

        item { SectionHeader("Tools") }

        item {
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                PhotoToolCard(
                    icon = Icons.Default.CameraAlt,
                    title = "Camera + Tagging",
                    desc = "Auto-tag railroad, symbol, GPS",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (userLocation == null) showNoGpsDialog = true
                        else tagCameraLauncher.launch(null)
                    },
                    highlight = true
                )
                Spacer(Modifier.height(10.dp))
                PhotoToolCard(
                    icon = Icons.Default.Search,
                    title = "Loco Identifier",
                    desc = "Snap photo to ID model",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showSourcePicker = true },
                    highlight = true
                )
                Spacer(Modifier.height(10.dp))
                PhotoToolCard(
                    icon = Icons.Default.Train,
                    title = "Consist Analyzer",
                    desc = "ID every unit front-to-back with Claude AI",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showConsistPicker = true },
                    highlight = true
                )
                Spacer(Modifier.height(10.dp))
                PhotoToolCard(
                    icon = Icons.Default.AutoFixHigh,
                    title = "Photo Enhancer",
                    desc = "Railfan preset: saturation, contrast, warmth",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { enhancerGalleryLauncher.launch("image/*") },
                    highlight = false
                )
            }
        }

        if (locoIdHistory.isNotEmpty()) {
            item { SectionHeader("ID History (${locoIdHistory.size})") }
            items(locoIdHistory, key = { it.id }) { entry ->
                LocoIdHistoryCard(entry, onDelete = { vm.deleteLocoIdEntry(entry) })
            }
        }

        if (taggedPhotos.isNotEmpty()) {
            item { SectionHeader("Recent Tags (${taggedPhotos.size})") }
            items(taggedPhotosReversed, key = { it.id }) { tag ->
                TaggedPhotoCard(
                    tag = tag,
                    onDelete = { vm.deleteTaggedPhoto(tag) },
                    onPostToCommunity = { vm.postPhotoToCommunity(tag) }
                )
            }
        }

        item { SectionHeader("Achievements") }

        item { AchievementsCard(achievements) }

        item { Spacer(Modifier.height(100.dp)) }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

internal fun Bitmap.scaleToMax(maxPx: Int): Bitmap {
    if (width <= maxPx && height <= maxPx) return this
    val scale = maxPx.toFloat() / maxOf(width, height)
    return Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
}

private fun Bitmap.toBase64Jpeg(quality: Int = 80): String {
    val out = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, quality, out)
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

/** Applies the Railfan Enhancement preset: saturation +35%, contrast +15%, slight brightness lift and warmth. */
internal fun Bitmap.applyRailfanEnhancement(): Bitmap {
    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(result)
    val paint  = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    val cm = android.graphics.ColorMatrix()

    // 1. Saturation boost — makes railroad paint schemes pop
    val satMatrix = android.graphics.ColorMatrix()
    satMatrix.setSaturation(1.35f)
    cm.postConcat(satMatrix)

    // 2. Contrast + brightness lift
    val contrast  = 1.15f
    val brightness = 10f
    val translate  = (-0.5f * contrast + 0.5f) * 255f + brightness
    cm.postConcat(android.graphics.ColorMatrix(floatArrayOf(
        contrast, 0f, 0f, 0f, translate,
        0f, contrast, 0f, 0f, translate,
        0f, 0f, contrast, 0f, translate,
        0f, 0f, 0f, 1f, 0f
    )))

    // 3. Slight warmth — flatters locomotive paint and golden-hour sky
    cm.postConcat(android.graphics.ColorMatrix(floatArrayOf(
        1f, 0f, 0f, 0f,  8f,
        0f, 1f, 0f, 0f,  0f,
        0f, 0f, 1f, 0f, -6f,
        0f, 0f, 0f, 1f,  0f
    )))

    paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
    canvas.drawBitmap(this, 0f, 0f, paint)
    return result
}

/** Write [bitmap] as a JPEG into the app's private photos directory. Returns the absolute path, or null on error. */
internal fun saveBitmapToFile(context: android.content.Context, bitmap: Bitmap, id: String): String? =
    try {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val file = File(dir, "$id.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        file.absolutePath
    } catch (_: Exception) { null }

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
fun LocoIdHistoryCard(
    entry: LocoIdEntry,
    onDelete: () -> Unit
) {
    val thumbnail by produceState<Bitmap?>(initialValue = null, key1 = entry.thumbnailPath) {
        value = entry.thumbnailPath?.let { path ->
            withContext(Dispatchers.IO) {
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                BitmapFactory.decodeFile(path, opts)
            }
        }
    }

    // First line of result as a title, remainder as body
    val lines = entry.resultText.trim().lines()
    val title = lines.firstOrNull()?.take(80) ?: "Identified locomotive"
    val body  = lines.drop(1).joinToString(" ").trim().take(160)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(0.5.dp, BorderLight, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(LocoPlaceholderBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.DirectionsRailway, null,
                    tint = RailBlue.copy(alpha = 0.6f),
                    modifier = Modifier.size(26.dp))
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (body.isNotBlank()) {
                Text(body, color = TextSecondary, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
            }
            Text(
                remember(entry.timestampMs) {
                    SimpleDateFormat("MMM d, h:mm:ss a", Locale.US).format(Date(entry.timestampMs))
                },
                color = TextMuted, fontSize = 10.sp
            )
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.DeleteOutline, null, tint = TextMuted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun TagRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextMuted, fontSize = 12.sp)
        Text(value, color = TextPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun TaggedPhotoCard(tag: PhotoMetadata, onDelete: (() -> Unit)? = null, onPostToCommunity: (() -> Unit)? = null) {
    val context = LocalContext.current
    // railroad is stored as enum name string — resolve back to Railroad for display
    val railroad = remember(tag.railroad) {
        tag.railroad?.let { name -> Railroad.values().find { it.name == name } }
    }

    // Load thumbnail off the main thread; null while loading or if no path saved
    val thumbnail by produceState<Bitmap?>(initialValue = null, key1 = tag.localPath) {
        value = tag.localPath?.let { path ->
            withContext(Dispatchers.IO) {
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                BitmapFactory.decodeFile(path, opts)
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(0.5.dp, BorderLight, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Thumbnail image or coloured railroad placeholder
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (railroad != null) Color(railroad.color) else RrPlaceholderBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CameraAlt, null,
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                tag.trainSymbol ?: "No train nearby",
                color = if (tag.trainSymbol != null) TextPrimary else TextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    append(railroad?.displayName ?: tag.railroad ?: "Unknown")
                    append(" · ")
                    append(tag.locationName ?: String.format("%.4f°, %.4f°", tag.latitude, tag.longitude))
                },
                color = TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            tag.notes?.let {
                Text(it, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                remember(tag.timestampMs) {
                    SimpleDateFormat("MMM d, h:mm:ss a", Locale.US).format(Date(tag.timestampMs))
                },
                color = TextMuted,
                fontSize = 11.sp
            )
        }

        Column {
            // Share photo — only available when there is a saved file on disk
            if (tag.localPath != null) {
                IconButton(
                    onClick = {
                        val file = File(tag.localPath)
                        if (file.exists()) {
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/jpeg"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                putExtra(Intent.EXTRA_TEXT, buildString {
                                    tag.trainSymbol?.let { appendLine("Train: $it") }
                                    tag.locationName?.let { appendLine("Location: $it") }
                                    append("Captured with Railfan Copilot")
                                })
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share photo"))
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share photo",
                        tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }

            if (onPostToCommunity != null) {
                IconButton(onClick = onPostToCommunity, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Upload, contentDescription = "Post to Community",
                        tint = RailBlue, modifier = Modifier.size(18.dp))
                }
            }

            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.DeleteOutline, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun SunPredictorCard(sunInfo: SunInfo?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(0.5.dp, BorderLight, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Text("Sun Angle Predictor", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))

        if (sunInfo == null) {
            // No GPS fix yet — don't show fake placeholder values
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = RailBlue
                )
                Text("Waiting for location fix…", color = TextMuted, fontSize = 13.sp)
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Compass dial with N/S/E/W labels overlaid
                Box(modifier = Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cx = size.width / 2
                        val cy = size.height / 2
                        val r  = size.width * 0.42f

                        // Outer ring
                        drawCircle(color = Border, style = Stroke(1.5.dp.toPx()), radius = r)
                        // Horizontal cross-hair
                        drawLine(BorderLight, Offset(cx - r, cy), Offset(cx + r, cy), strokeWidth = 0.5.dp.toPx())
                        // Vertical cross-hair
                        drawLine(BorderLight, Offset(cx, cy - r), Offset(cx, cy + r), strokeWidth = 0.5.dp.toPx())

                        // Sun dot — distance from centre encodes elevation (dot at edge = horizon, centre = zenith)
                        val elev     = sunInfo.elevationDegrees.toFloat()
                        val azimuth  = sunInfo.azimuthDegrees.toFloat()
                        // Convert compass azimuth to canvas angle: 0°N = up, increases clockwise
                        val angleRad = Math.toRadians((azimuth - 90.0))
                        val sunR     = r * (1f - (elev / 90f).coerceIn(0f, 1f) * 0.85f)
                        val sx       = cx + (cos(angleRad) * sunR).toFloat()
                        val sy       = cy + (sin(angleRad) * sunR).toFloat()

                        // Ray from centre to sun
                        drawLine(
                            color = SunRayAmber.copy(alpha = 0.35f),
                            start = Offset(cx, cy), end = Offset(sx, sy),
                            strokeWidth = 1.dp.toPx()
                        )
                        // Sun dot — amber when above horizon, dim when below
                        drawCircle(
                            color = if (elev >= -0.833f) RailAmber else SunBelowHorizon,
                            radius = 8.dp.toPx(),
                            center = Offset(sx, sy)
                        )
                    }

                    // Cardinal labels — pure Compose Text, no nativeCanvas needed
                    val labelColor = TextMuted
                    val labelSp = 9.sp
                    Text("N", color = labelColor, fontSize = labelSp,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 3.dp))
                    Text("S", color = labelColor, fontSize = labelSp,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp))
                    Text("E", color = labelColor, fontSize = labelSp,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 3.dp))
                    Text("W", color = labelColor, fontSize = labelSp,
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 3.dp))
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SunMetric("Elevation", "${String.format("%.0f", sunInfo.elevationDegrees)}°")
                    SunMetric("Azimuth",   "${String.format("%.0f", sunInfo.azimuthDegrees)}°")
                    SunMetric("GH start",  sunInfo.goldenHourStart)
                    SunMetric("GH end",    sunInfo.goldenHourEnd)
                }
            }

            if (sunInfo.isGoldenHour) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(GoldenHourBg)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("✦ Golden hour active — perfect lighting conditions!", color = RailAmber, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun SunMetric(label: String, value: String) {
    Column {
        Text(value, color = RailAmber, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(label, color = TextMuted, fontSize = 11.sp)
    }
}

@Composable
fun PhotoToolCard(
    icon: ImageVector,
    title: String,
    desc: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    highlight: Boolean = false
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(
                width = if (highlight) 1.dp else 0.5.dp,
                color = if (highlight) RailBlue else BorderLight,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Icon(icon, contentDescription = title, tint = RailBlue, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(8.dp))
        Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(desc, color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp)
    }
}

@Composable
fun AchievementsCard(achievements: List<Achievement>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(0.5.dp, BorderLight, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val earned = achievements.count { it.earned }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$earned / ${achievements.size} earned", color = TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            LinearProgressIndicator(
                progress = { if (achievements.isEmpty()) 0f else earned.toFloat() / achievements.size },
                modifier = Modifier.width(80.dp).clip(RoundedCornerShape(4.dp)),
                color = RailBlue,
                trackColor = BgInput
            )
        }

        achievements.forEach { achievement ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (achievement.earned) LocoPlaceholderBg else BgInput),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        achievement.iconEmoji,
                        fontSize = 18.sp,
                        color = if (achievement.earned) Color.Unspecified else TextMuted.copy(alpha = 0.3f)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        achievement.title,
                        color = if (achievement.earned) TextPrimary else TextMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(achievement.description, color = TextMuted, fontSize = 11.sp)
                }
                if (achievement.earned) {
                    Icon(Icons.Default.CheckCircle, null, tint = RailGreen, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
