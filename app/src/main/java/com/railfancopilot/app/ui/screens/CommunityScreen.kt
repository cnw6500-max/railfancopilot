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
import androidx.compose.foundation.verticalScroll
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
import com.railfancopilot.app.data.models.ConsistEntry
import com.railfancopilot.app.data.models.ConsistEntryType
import com.railfancopilot.app.data.models.LocomotiveEntry
import com.railfancopilot.app.data.models.SightingComment
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
    val locos by vm.locomotives.collectAsState()
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
            ReportCard(
                report      = report,
                userLocation = userLocation,
                onDelete    = { vm.deleteReport(it.id) },
                onGetCommentsFlow = { vm.getCommentsFlow(it) },
                onPostComment     = { id, text -> vm.postComment(id, text) }
            )
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
            userLat = userLocation?.latitude,
            userLon = userLocation?.longitude,
            allLocos = locos,
            onFetchWeather = { lat, lon -> vm.fetchWeather(lat, lon) },
            onFetchLocation = { lat, lon -> vm.reverseGeocode(lat, lon) },
            onDismiss = { showSubmitDialog = false },
            onSubmit = { text, symbol, rr, tags, photoPath, consist, weather, locName ->
                val loc = userLocation
                if (loc != null) {
                    vm.submitReport(loc.latitude, loc.longitude, text, symbol, rr, tags, photoPath, consist, weather, locName)
                    showSubmitDialog = false
                }
            }
        )
    }
}

@Composable
fun ReportCard(
    report: CommunityReport,
    userLocation: Location?,
    onDelete: ((CommunityReport) -> Unit)? = null,
    onGetCommentsFlow: ((String) -> kotlinx.coroutines.flow.Flow<List<SightingComment>>)? = null,
    onPostComment: ((String, String) -> Unit)? = null
) {
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

    // Comments
    var showComments by remember { mutableStateOf(false) }
    var comments by remember { mutableStateOf<List<SightingComment>>(emptyList()) }
    var commentsLoading by remember { mutableStateOf(false) }
    var commentInput by remember { mutableStateOf("") }
    val commentScope = rememberCoroutineScope()

    LaunchedEffect(showComments) {
        if (!showComments || onGetCommentsFlow == null) return@LaunchedEffect
        commentsLoading = true
        onGetCommentsFlow(report.id).collect {
            comments = it
            commentsLoading = false
        }
    }

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

        // Comment toggle + share/delete row
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Comments button
            if (onGetCommentsFlow != null) {
                TextButton(
                    onClick = { showComments = !showComments },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Icon(
                        Icons.Default.ChatBubbleOutline, null,
                        tint = TextMuted, modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (comments.isEmpty() && !showComments) "Comment"
                        else if (comments.isEmpty()) "No comments yet"
                        else "${comments.size} comment${if (comments.size == 1) "" else "s"}",
                        color = TextMuted, fontSize = 11.sp
                    )
                }
            }
            Spacer(Modifier.weight(1f))
        }

        // Comment thread (expanded)
        if (showComments) {
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = Border, thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))

            if (commentsLoading && comments.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = RailBlue)
                }
            }

            comments.forEach { comment ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(RailBlueDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            comment.userName.take(2).ifEmpty { "?" }.uppercase(),
                            color = RailBlue, fontSize = 8.sp, fontWeight = FontWeight.Medium
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(comment.userName, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            val commentAgo = remember(comment.timestampMs) {
                                val diff = System.currentTimeMillis() - comment.timestampMs
                                when {
                                    diff < TimeUnit.MINUTES.toMillis(60) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m"
                                    diff < TimeUnit.HOURS.toMillis(24)   -> "${TimeUnit.MILLISECONDS.toHours(diff)}h"
                                    else -> "${TimeUnit.MILLISECONDS.toDays(diff)}d"
                                }
                            }
                            Text(commentAgo, color = TextMuted, fontSize = 10.sp)
                        }
                        Text(comment.text, color = TextPrimary, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
            }

            // Comment input
            if (onPostComment != null) {
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = commentInput,
                        onValueChange = { commentInput = it },
                        placeholder = { Text("Add a comment…", color = TextMuted, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = RailBlueMid,
                            unfocusedBorderColor = BorderLight,
                            cursorColor          = RailBlue,
                            focusedTextColor     = TextPrimary,
                            unfocusedTextColor   = TextPrimary
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            val trimmed = commentInput.trim()
                            if (trimmed.isNotBlank()) {
                                onPostComment(report.id, trimmed)
                                commentInput = ""
                            }
                        })
                    )
                    IconButton(
                        onClick = {
                            val trimmed = commentInput.trim()
                            if (trimmed.isNotBlank()) {
                                onPostComment(report.id, trimmed)
                                commentInput = ""
                            }
                        },
                        modifier = Modifier.size(36.dp)
                            .clip(CircleShape)
                            .background(if (commentInput.isNotBlank()) RailBlueDark else BgInput)
                    ) {
                        Icon(
                            Icons.Default.Send, null,
                            tint = if (commentInput.isNotBlank()) RailBlue else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmitReportDialog(
    hasLocation: Boolean = true,
    userLat: Double?,
    userLon: Double?,
    allLocos: List<LocomotiveEntry>,
    onFetchWeather: suspend (Double, Double) -> String?,
    onFetchLocation: suspend (Double, Double) -> String?,
    onDismiss: () -> Unit,
    onSubmit: (text: String, symbol: String?, rr: String?, tags: List<String>, photoPath: String?, consist: String?, weather: String?, locationName: String) -> Unit
) {
    val gson = remember { Gson() }

    // Core fields
    var text          by remember { mutableStateOf("") }
    var symbol        by remember { mutableStateOf("") }
    var rr            by remember { mutableStateOf("") }
    var isListening   by remember { mutableStateOf(false) }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var savedPhotoPath by remember { mutableStateOf<String?>(null) }

    // Auto-filled
    var locationName  by remember { mutableStateOf("") }
    var weatherStr    by remember { mutableStateOf<String?>(null) }
    var weatherLoading by remember { mutableStateOf(false) }

    // Locomotive autocomplete
    var locoQuery     by remember { mutableStateOf("") }
    var showLocoSuggestions by remember { mutableStateOf(false) }
    val locoSuggestions = remember(locoQuery, allLocos) {
        if (locoQuery.length < 2) emptyList()
        else allLocos.filter { it.model.contains(locoQuery, ignoreCase = true) }.take(5)
    }

    // Consist builder
    var consist       by remember { mutableStateOf<List<ConsistEntry>>(emptyList()) }
    var showAddLoco   by remember { mutableStateOf(false) }
    var showAddCar    by remember { mutableStateOf(false) }
    // Add-loco form state
    var newLocoModel  by remember { mutableStateOf("") }
    var newLocoRoadNum by remember { mutableStateOf("") }
    var newLocoRailroad by remember { mutableStateOf("") }
    var showNewLocoSuggestions by remember { mutableStateOf(false) }
    val newLocoSuggestions = remember(newLocoModel, allLocos) {
        if (newLocoModel.length < 2) emptyList()
        else allLocos.filter { it.model.contains(newLocoModel, ignoreCase = true) }.take(5)
    }
    // Add-car form state
    var newCarType    by remember { mutableStateOf(ConsistEntryType.BOXCAR) }
    var newCarRoadNum by remember { mutableStateOf("") }
    var newCarRailroad by remember { mutableStateOf("") }
    var showCarTypeMenu by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Auto-fetch location + weather when dialog opens
    LaunchedEffect(userLat, userLon) {
        if (userLat != null && userLon != null) {
            scope.launch { locationName = onFetchLocation(userLat, userLon) ?: "" }
            weatherLoading = true
            scope.launch {
                weatherStr = onFetchWeather(userLat, userLon)
                weatherLoading = false
            }
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: return@rememberLauncherForActivityResult
            text = if (text.isBlank()) spoken else "${text.trimEnd()} $spoken"
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.Default) {
            val scaled = bitmap.scaleToMax(800)
            val path = saveBitmapToFile(context, scaled, "report_${System.currentTimeMillis()}")
            withContext(Dispatchers.Main) { selectedBitmap = bitmap.scaleToMax(400); savedPhotoPath = path }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return@launch
            val scaled = bitmap.scaleToMax(800)
            val path = saveBitmapToFile(context, scaled, "report_${System.currentTimeMillis()}")
            withContext(Dispatchers.Main) { selectedBitmap = bitmap.scaleToMax(400); savedPhotoPath = path }
        }
    }

    fun launchSpeech() {
        isListening = true
        speechLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe what you saw…")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        })
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor   = RailBlueMid,
        unfocusedBorderColor = BorderLight,
        cursorColor          = RailBlue,
        focusedTextColor     = TextPrimary,
        unfocusedTextColor   = TextPrimary
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
                Text("Log a Sighting", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                IconButton(
                    onClick = { launchSpeech() },
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(if (isListening) MicListeningBg else RailBlueDark)
                ) {
                    Icon(Icons.Default.Mic, null, tint = if (isListening) RailRed else RailBlue, modifier = Modifier.size(18.dp))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                // ── Auto-tags row (location + weather) ───────────────────────
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (locationName.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(BgInput)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.LocationOn, null, tint = RailBlue, modifier = Modifier.size(12.dp))
                            Text(locationName, color = TextPrimary, fontSize = 11.sp)
                        }
                    }
                    when {
                        weatherLoading -> {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(BgInput)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Cloud, null, tint = TextMuted, modifier = Modifier.size(12.dp))
                                Text("Getting weather…", color = TextMuted, fontSize = 11.sp)
                            }
                        }
                        weatherStr != null -> {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(BgInput)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.WbSunny, null, tint = RailAmber, modifier = Modifier.size(12.dp))
                                Text(weatherStr!!, color = TextPrimary, fontSize = 11.sp)
                            }
                        }
                    }
                }

                // ── Notes field ───────────────────────────────────────────────
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("What did you see?", color = TextMuted) },
                    placeholder = { Text("Tap 🎤 or type…", color = TextMuted, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                    minLines = 2,
                    trailingIcon = {
                        if (text.isNotBlank()) {
                            IconButton(onClick = { text = "" }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Clear, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                )

                // ── Train symbol + Railroad (side by side) ────────────────────
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = symbol,
                        onValueChange = { symbol = it.uppercase() },
                        label = { Text("Train symbol", color = TextMuted) },
                        modifier = Modifier.weight(1f),
                        colors = fieldColors,
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = rr,
                        onValueChange = { rr = it },
                        label = { Text("Railroad", color = TextMuted) },
                        modifier = Modifier.weight(1f),
                        colors = fieldColors,
                        singleLine = true
                    )
                }

                // ── Locomotive model with autocomplete ───────────────────────
                Column {
                    OutlinedTextField(
                        value = locoQuery,
                        onValueChange = { locoQuery = it; showLocoSuggestions = it.length >= 2 },
                        label = { Text("Lead loco model (optional)", color = TextMuted) },
                        placeholder = { Text("e.g. ES44AC, SD70ACe…", color = TextMuted, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors,
                        singleLine = true,
                        trailingIcon = {
                            if (locoQuery.isNotBlank()) {
                                IconButton(onClick = { locoQuery = ""; showLocoSuggestions = false }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.Clear, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    )
                    if (showLocoSuggestions && locoSuggestions.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(BgInput)
                        ) {
                            locoSuggestions.forEach { loco ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { locoQuery = loco.model; showLocoSuggestions = false }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(loco.model, color = TextPrimary, fontSize = 13.sp)
                                    Text(loco.manufacturer, color = TextMuted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                // ── Consist builder ───────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(BgInput)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Train, null, tint = RailBlue, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Consist", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = { showAddLoco = !showAddLoco; showAddCar = false },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) { Text("+ Loco", color = RailBlue, fontSize = 11.sp) }
                        TextButton(
                            onClick = { showAddCar = !showAddCar; showAddLoco = false },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) { Text("+ Car", color = RailBlue, fontSize = 11.sp) }
                    }

                    // Consist entry list
                    consist.forEachIndexed { idx, entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(BgCard)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (entry.type == ConsistEntryType.LOCOMOTIVE) Icons.Default.Train else Icons.Default.Inventory2,
                                null,
                                tint = if (entry.type == ConsistEntryType.LOCOMOTIVE) RailBlue else TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.model, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                if (entry.roadNumber.isNotBlank() || entry.railroad.isNotBlank()) {
                                    Text("${entry.railroad} ${entry.roadNumber}".trim(), color = TextMuted, fontSize = 11.sp)
                                }
                            }
                            Text("#${idx + 1}", color = TextMuted, fontSize = 10.sp)
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = { consist = consist.toMutableList().also { it.removeAt(idx) } },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.Close, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    if (consist.isEmpty() && !showAddLoco && !showAddCar) {
                        Text("Tap + Loco or + Car to build your consist", color = TextMuted, fontSize = 11.sp)
                    }

                    // Add-loco inline form
                    if (showAddLoco) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(BgCard)
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Add Locomotive", color = RailBlue, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            OutlinedTextField(
                                value = newLocoModel,
                                onValueChange = { newLocoModel = it; showNewLocoSuggestions = it.length >= 2 },
                                label = { Text("Model", color = TextMuted) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = fieldColors,
                                singleLine = true
                            )
                            if (showNewLocoSuggestions && newLocoSuggestions.isNotEmpty()) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(BgInput)
                                ) {
                                    newLocoSuggestions.forEach { loco ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth()
                                                .clickable { newLocoModel = loco.model; showNewLocoSuggestions = false }
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(loco.model, color = TextPrimary, fontSize = 12.sp)
                                            Text(loco.manufacturer, color = TextMuted, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = newLocoRoadNum,
                                    onValueChange = { newLocoRoadNum = it },
                                    label = { Text("Road #", color = TextMuted) },
                                    modifier = Modifier.weight(1f),
                                    colors = fieldColors,
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = newLocoRailroad,
                                    onValueChange = { newLocoRailroad = it },
                                    label = { Text("Railroad", color = TextMuted) },
                                    modifier = Modifier.weight(1f),
                                    colors = fieldColors,
                                    singleLine = true
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { showAddLoco = false; newLocoModel = ""; newLocoRoadNum = ""; newLocoRailroad = "" }) {
                                    Text("Cancel", color = TextMuted, fontSize = 12.sp)
                                }
                                Button(
                                    onClick = {
                                        if (newLocoModel.isNotBlank()) {
                                            consist = consist + ConsistEntry(ConsistEntryType.LOCOMOTIVE, newLocoModel.trim(), newLocoRoadNum.trim(), newLocoRailroad.trim())
                                            showAddLoco = false; newLocoModel = ""; newLocoRoadNum = ""; newLocoRailroad = ""
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = RailBlue),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                                ) { Text("Add", fontSize = 12.sp) }
                            }
                        }
                    }

                    // Add-car inline form
                    if (showAddCar) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(BgCard)
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Add Car", color = RailBlue, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            ExposedDropdownMenuBox(
                                expanded = showCarTypeMenu,
                                onExpandedChange = { showCarTypeMenu = it }
                            ) {
                                OutlinedTextField(
                                    value = newCarType.label,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Car type", color = TextMuted) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(showCarTypeMenu) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                                    colors = fieldColors
                                )
                                ExposedDropdownMenu(
                                    expanded = showCarTypeMenu,
                                    onDismissRequest = { showCarTypeMenu = false },
                                    containerColor = BgCard
                                ) {
                                    ConsistEntryType.entries
                                        .filter { it != ConsistEntryType.LOCOMOTIVE }
                                        .forEach { type ->
                                            DropdownMenuItem(
                                                text = { Text(type.label, color = TextPrimary, fontSize = 13.sp) },
                                                onClick = { newCarType = type; showCarTypeMenu = false }
                                            )
                                        }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = newCarRoadNum,
                                    onValueChange = { newCarRoadNum = it },
                                    label = { Text("Road #", color = TextMuted) },
                                    modifier = Modifier.weight(1f),
                                    colors = fieldColors,
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = newCarRailroad,
                                    onValueChange = { newCarRailroad = it },
                                    label = { Text("Railroad", color = TextMuted) },
                                    modifier = Modifier.weight(1f),
                                    colors = fieldColors,
                                    singleLine = true
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { showAddCar = false; newCarRoadNum = ""; newCarRailroad = "" }) {
                                    Text("Cancel", color = TextMuted, fontSize = 12.sp)
                                }
                                Button(
                                    onClick = {
                                        consist = consist + ConsistEntry(newCarType, newCarType.label, newCarRoadNum.trim(), newCarRailroad.trim())
                                        showAddCar = false; newCarRoadNum = ""; newCarRailroad = ""
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = RailBlue),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                                ) { Text("Add", fontSize = 12.sp) }
                            }
                        }
                    }
                }

                // ── Photo ─────────────────────────────────────────────────────
                if (selectedBitmap != null) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            bitmap = selectedBitmap!!.asImageBitmap(),
                            contentDescription = "Selected photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(8.dp))
                        )
                        IconButton(
                            onClick = { selectedBitmap = null; savedPhotoPath = null },
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { cameraLauncher.launch(null) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RailBlue),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderLight)
                        ) {
                            Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(16.dp))
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
                            Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Gallery", fontSize = 13.sp)
                        }
                    }
                }

                if (text.isBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(RailBlueDark.copy(alpha = 0.5f)).padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Mic, null, tint = RailBlue, modifier = Modifier.size(14.dp))
                        Text("Tap the mic to dictate your sighting", color = TextMuted, fontSize = 11.sp)
                    }
                }

                if (!hasLocation) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(GpsWarnBg).padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.LocationOff, null, tint = RailAmber, modifier = Modifier.size(14.dp))
                        Text("Waiting for GPS — report will be pinned once location is acquired", color = RailAmber, fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {
            val canSubmit = text.isNotBlank() && hasLocation
            TextButton(
                onClick = {
                    if (canSubmit) {
                        val consistJson = if (consist.isNotEmpty()) gson.toJson(consist) else null
                        onSubmit(text, symbol.ifBlank { null }, rr.ifBlank { null }, emptyList(), savedPhotoPath, consistJson, weatherStr, locationName)
                    }
                },
                enabled = canSubmit
            ) { Text("Submit", color = if (canSubmit) RailBlue else TextMuted) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        }
    )
}
