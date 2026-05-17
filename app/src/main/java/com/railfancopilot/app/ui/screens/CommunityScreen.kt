package com.railfancopilot.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.railfancopilot.app.data.models.AlertSeverity
import com.railfancopilot.app.data.models.CommunityReport
import com.railfancopilot.app.ui.components.*
import com.railfancopilot.app.ui.theme.*
import com.railfancopilot.app.viewmodel.RailFanViewModel
import com.railfancopilot.app.ui.components.ProGateScreen
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@Composable
fun CommunityScreen(vm: RailFanViewModel, onUpgrade: () -> Unit = {}) {
    val isPro by vm.isProUser.collectAsState()
    val reports by vm.communityReports.collectAsState()
    val safetyAlerts by vm.safetyAlerts.collectAsState()
    val userLocation by vm.userLocation.collectAsState()
    val radiusMiles by vm.reportRadiusMiles.collectAsState()
    var showSubmitDialog by remember { mutableStateOf(false) }
    var showProGate by remember { mutableStateOf(false) }

    if (showProGate) {
        ProGateScreen(
            featureName = "Community Reports",
            description = "Submit train sightings and contribute to the community feed. Reading reports is always free.",
            onUpgrade = { showProGate = false; onUpgrade() }
        )
        return
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
        item {
            Row(modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Community & Safety", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    val radiusLabel = if (radiusMiles >= 999) "all distances"
                                      else "${radiusMiles.roundToInt()} mi radius"
                    val locLabel = if (userLocation != null) radiusLabel else "no location fix"
                    Text("Showing sightings · $locLabel", color = TextMuted, fontSize = 12.sp)
                }
                FloatingActionButton(
                    onClick = { if (isPro) showSubmitDialog = true else showProGate = true },
                    containerColor = RailBlueDark,
                    contentColor = RailBlue,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add sighting report", modifier = Modifier.size(20.dp))
                }
            }
        }

        // Radius filter chips
        item {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(25.0 to "25 mi", 50.0 to "50 mi", 100.0 to "100 mi", 999.0 to "All").forEach { (miles, label) ->
                    FilterChip(label, radiusMiles == miles) { vm.setReportRadius(miles) }
                }
            }
        }

        // Safety alerts — only shown when real alerts are present
        if (safetyAlerts.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    safetyAlerts.forEach { alert ->
                        val (fg, bg, border) = when (alert.severity) {
                            AlertSeverity.DANGER  -> Triple(RailRed,   AlertBgDanger,  AlertBorderDanger)
                            AlertSeverity.WARNING -> Triple(RailAmber, AlertBgWarning, AlertBorderWarning)
                            else                  -> Triple(RailBlue,  AlertBgInfo,    RailBlueDark)
                        }
                        AlertBanner(alert.message, fg, bg, border)
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }

        item {
            SectionHeader("Recent sightings (${reports.size})")
        }

        items(reports) { report ->
            ReportCard(report, userLocation, onDelete = { vm.deleteReport(it.id) })
        }

        if (reports.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.ChatBubbleOutline,
                    title = "No sightings yet",
                    subtitle = if (userLocation == null)
                        "Waiting for GPS to filter nearby reports"
                    else
                        "No reports within ${radiusMiles.roundToInt()} mi — tap + to add one"
                )
            }
        }

        // RRPD contacts
        item { SectionHeader("Railroad police (non-emergency)") }
        item {
            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(BgCard)
                .border(0.5.dp, Border, RoundedCornerShape(12.dp))
                .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    "BNSF Railway Police" to "1-800-832-5452",
                    "Union Pacific Special Agents" to "1-888-877-7267",
                    "Amtrak Police" to "1-800-331-0008"
                ).forEach { (name, number) ->
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Phone, contentDescription = "Phone", tint = RailBlue, modifier = Modifier.size(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name, color = TextPrimary, fontSize = 13.sp)
                            Text(number, color = RailBlue, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(100.dp)) }
    }

    if (showSubmitDialog) {
        SubmitReportDialog(
            hasLocation = userLocation != null,
            onDismiss = { showSubmitDialog = false },
            onSubmit = { text, symbol, rr, tags, photoPath ->
                val loc = userLocation
                if (loc != null) {
                    vm.submitReport(loc.latitude, loc.longitude, text, symbol, rr, tags, photoPath)
                    showSubmitDialog = false
                }
                // If loc is null the dialog stays open — the no-GPS warning is visible
            }
        )
    }
}

@Composable
fun ReportCard(report: CommunityReport, userLocation: Location?, onDelete: ((CommunityReport) -> Unit)? = null) {
    val context = LocalContext.current
    val gson = remember { Gson() }
    val tags = remember(report.tags) {
        try { gson.fromJson<List<String>>(report.tags, object : TypeToken<List<String>>() {}.type) }
        catch (e: Exception) {
            if (com.railfancopilot.app.BuildConfig.DEBUG) android.util.Log.e("CommunityScreen", "Failed to parse tags: ${e.message}", e)
            emptyList()
        }
    }
    val ago = remember(report.timestampMs) {
        val diff = System.currentTimeMillis() - report.timestampMs
        when {
            diff < TimeUnit.MINUTES.toMillis(60) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
            diff < TimeUnit.HOURS.toMillis(24) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
            else -> "${TimeUnit.MILLISECONDS.toDays(diff)}d ago"
        }
    }
    val distanceLabel = remember(userLocation, report.latitude, report.longitude) {
        if (userLocation == null) return@remember null
        val results = FloatArray(1)
        Location.distanceBetween(
            userLocation.latitude, userLocation.longitude,
            report.latitude, report.longitude, results
        )
        val miles = results[0] / 1609.34
        when {
            miles < 0.1 -> "< 0.1 mi"
            miles < 10 -> String.format("%.1f mi", miles)
            else -> "${miles.roundToInt()} mi"
        }
    }

    var textExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val isLongText = report.text.length > 160

    // Load photo thumbnail asynchronously
    val thumbnail by produceState<Bitmap?>(initialValue = null, key1 = report.localPhotoPath) {
        value = report.localPhotoPath?.let { path ->
            withContext(Dispatchers.IO) {
                try {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    BitmapFactory.decodeFile(path, opts)
                } catch (_: Exception) { null }
            }
        }
    }

    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 4.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(BgCard)
        .border(0.5.dp, Border, RoundedCornerShape(12.dp))
        .padding(12.dp)) {

        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(RailBlueDark),
                contentAlignment = Alignment.Center) {
                Text(report.userName.take(2).ifEmpty { "?" }.uppercase(), color = RailBlue, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
            Text(report.userName, color = TextSecondary, fontSize = 12.sp)
            if (report.isVerified) {
                Icon(Icons.Default.Verified, contentDescription = "Verified user", tint = RailBlue, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.weight(1f))
            if (distanceLabel != null) {
                Text(distanceLabel, color = TextMuted, fontSize = 11.sp)
                Text("·", color = TextMuted, fontSize = 11.sp)
            }
            Text(ago, color = TextMuted, fontSize = 11.sp)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            report.text, color = TextPrimary, fontSize = 13.sp, lineHeight = 20.sp,
            maxLines = if (textExpanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis
        )
        if (isLongText) {
            Text(
                if (textExpanded) "Show less" else "Show more",
                color = RailBlue, fontSize = 11.sp,
                modifier = Modifier
                    .clickable { textExpanded = !textExpanded }
                    .padding(top = 2.dp)
            )
        }

        if (tags.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                tags.forEach { tag ->
                    Box(modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(BgInput)
                        .padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(tag, color = TextMuted, fontSize = 10.sp)
                    }
                }
            }
        }

        // Photo thumbnail
        thumbnail?.let { bmp ->
            Spacer(Modifier.height(8.dp))
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Report photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        }

        // Share / delete row
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            if (onDelete != null) {
                IconButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Delete sighting",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            IconButton(
                onClick = {
                    val shareText = buildString {
                        appendLine("🚂 Rail sighting reported by ${report.userName}")
                        report.trainSymbol?.let { appendLine("Train: $it") }
                        report.railroad?.let { appendLine("Railroad: $it") }
                        appendLine(report.text)
                        appendLine()
                        append("Spotted with Railfan Copilot")
                    }
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }, "Share sighting"
                        )
                    )
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = "Share sighting",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = BgCard,
            title = { Text("Delete sighting?", color = TextPrimary, fontSize = 16.sp) },
            text = { Text("This will permanently remove the sighting from the community feed.", color = TextSecondary, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = { onDelete?.invoke(report); showDeleteDialog = false }) {
                    Text("Delete", color = RailRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}

@Composable
fun SubmitReportDialog(
    hasLocation: Boolean = true,
    onDismiss: () -> Unit,
    onSubmit: (text: String, symbol: String?, rr: String?, tags: List<String>, photoPath: String?) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var symbol by remember { mutableStateOf("") }
    var rr by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var savedPhotoPath by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val textFocus = remember { FocusRequester() }
    val symbolFocus = remember { FocusRequester() }
    val rrFocus = remember { FocusRequester() }

    // Speech recognition launcher
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull() ?: return@rememberLauncherForActivityResult
            text = if (text.isBlank()) spoken else "${text.trimEnd()} $spoken"
        }
    }

    // Camera launcher — uses TakePicturePreview (no FileProvider needed)
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.Default) {
            val scaled = bitmap.scaleToMax(800)
            val path = saveBitmapToFile(context, scaled, "report_${System.currentTimeMillis()}")
            withContext(Dispatchers.Main) {
                selectedBitmap = bitmap.scaleToMax(400)
                savedPhotoPath = path
            }
        }
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return@launch
            val scaled = bitmap.scaleToMax(800)
            val path = saveBitmapToFile(context, scaled, "report_${System.currentTimeMillis()}")
            withContext(Dispatchers.Main) {
                selectedBitmap = bitmap.scaleToMax(400)
                savedPhotoPath = path
            }
        }
    }

    fun launchSpeech() {
        isListening = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe what you saw…")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechLauncher.launch(intent)
    }

    LaunchedEffect(Unit) { textFocus.requestFocus() }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = RailBlueMid,
        unfocusedBorderColor = BorderLight,
        cursorColor = RailBlue,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Report a Sighting", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                // Mic button in title row — pulses red while listening
                IconButton(
                    onClick = { launchSpeech() },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isListening) MicListeningBg else RailBlueDark)
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Voice input",
                        tint = if (isListening) RailRed else RailBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("What did you see?", color = TextMuted) },
                    placeholder = { Text("Tap 🎤 or type…", color = TextMuted, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth().focusRequester(textFocus),
                    colors = fieldColors,
                    minLines = 2,
                    trailingIcon = {
                        if (text.isNotBlank()) {
                            IconButton(onClick = { text = "" }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear text", tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { symbolFocus.requestFocus() })
                )
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { symbol = it.uppercase() },
                    label = { Text("Train symbol (optional)", color = TextMuted) },
                    modifier = Modifier.fillMaxWidth().focusRequester(symbolFocus),
                    colors = fieldColors,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { rrFocus.requestFocus() })
                )
                OutlinedTextField(
                    value = rr,
                    onValueChange = { rr = it },
                    label = { Text("Railroad (optional)", color = TextMuted) },
                    modifier = Modifier.fillMaxWidth().focusRequester(rrFocus),
                    colors = fieldColors,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (text.isNotBlank()) onSubmit(text, symbol.ifBlank { null }, rr.ifBlank { null }, emptyList(), savedPhotoPath)
                    })
                )

                // ── Photo attachment row ──────────────────────────────────────
                if (selectedBitmap != null) {
                    // Thumbnail preview with remove button
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            bitmap = selectedBitmap!!.asImageBitmap(),
                            contentDescription = "Selected photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        // Remove photo button
                        IconButton(
                            onClick = { selectedBitmap = null; savedPhotoPath = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove photo",
                                tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                } else {
                    // Camera / gallery buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { cameraLauncher.launch(null) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RailBlue),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderLight)
                        ) {
                            Icon(Icons.Default.PhotoCamera, null,
                                modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Camera", fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RailBlue),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderLight)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, null,
                                modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Gallery", fontSize = 13.sp)
                        }
                    }
                }

                // Hint shown when text field is empty
                if (text.isBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(RailBlueDark.copy(alpha = 0.5f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Mic, null, tint = RailBlue, modifier = Modifier.size(14.dp))
                        Text("Tap the mic to dictate your sighting", color = TextMuted, fontSize = 11.sp)
                    }
                }

                // No-GPS warning — shown when location is unavailable
                if (!hasLocation) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(GpsWarnBg)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.LocationOff, null,
                            tint = RailAmber, modifier = Modifier.size(14.dp))
                        Text("Waiting for GPS — report will be pinned once location is acquired",
                            color = RailAmber, fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {
            val canSubmit = text.isNotBlank() && hasLocation
            TextButton(
                onClick = {
                    if (canSubmit) onSubmit(text, symbol.ifBlank { null }, rr.ifBlank { null }, emptyList(), savedPhotoPath)
                },
                enabled = canSubmit
            ) { Text("Submit", color = if (canSubmit) RailBlue else TextMuted) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        }
    )
}
