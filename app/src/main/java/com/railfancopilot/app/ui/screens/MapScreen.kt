package com.railfancopilot.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.railfancopilot.app.data.models.*
import com.railfancopilot.app.ui.components.*
import com.railfancopilot.app.ui.theme.*
import com.railfancopilot.app.viewmodel.GeoSearchResult
import com.railfancopilot.app.viewmodel.RailFanViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private fun railroadLineColor(operator: String): androidx.compose.ui.graphics.Color {
    val op = operator.uppercase()
    return when {
        "BNSF" in op                              -> androidx.compose.ui.graphics.Color(0xFFFF6200)
        "UNION PACIFIC" in op || op.startsWith("UP ") || op == "UP"
                                                  -> androidx.compose.ui.graphics.Color(0xFFFFB300)
        "CSX" in op                               -> androidx.compose.ui.graphics.Color(0xFF1565C0)
        "NORFOLK SOUTHERN" in op || "NS " in op   -> androidx.compose.ui.graphics.Color(0xFF757575)
        "CANADIAN NATIONAL" in op || "CN " in op  -> androidx.compose.ui.graphics.Color(0xFFD32F2F)
        "CANADIAN PACIFIC" in op || "CP " in op   -> androidx.compose.ui.graphics.Color(0xFF880E4F)
        "AMTRAK" in op                            -> androidx.compose.ui.graphics.Color(0xFF1A237E)
        "KANSAS CITY SOUTHERN" in op || "KCS" in op -> androidx.compose.ui.graphics.Color(0xFF558B2F)
        else                                      -> androidx.compose.ui.graphics.Color(0xFF546E7A)
    }
}

@Composable
fun MapScreen(vm: RailFanViewModel) {
    val trains by vm.filteredTrains.collectAsState()
    val trainTrails by vm.trainTrails.collectAsState()
    val features by vm.mapFeatures.collectAsState()
    val selectedRailroad by vm.selectedRailroad.collectAsState()
    val isLoading by vm.isLoadingTrains.collectAsState()
    val userLocation by vm.userLocation.collectAsState()
    val searchResults by vm.searchResults.collectAsState()
    val isSearching by vm.isSearching.collectAsState()
    val railOverlayDefault by vm.railOverlayDefault.collectAsState()
    val lastRefreshMs by vm.lastRefreshMs.collectAsState()
    val trainFetchError by vm.trainFetchError.collectAsState()
    val railwaySegments by vm.railwaySegments.collectAsState()

    // Tick every 30 s so the stale-data banner age label stays fresh
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(30_000L); nowMs = System.currentTimeMillis() }
    }

    // Store the selected train by ID so the detail sheet always reflects live data after a refresh
    var selectedTrainId by remember { mutableStateOf<String?>(null) }
    val selectedTrain by remember { derivedStateOf { trains.find { it.id == selectedTrainId } } }
    var showSatellite by remember { mutableStateOf(false) }
    var showRailwayMap by remember { mutableStateOf(railOverlayDefault) }
    var showRailLines by remember { mutableStateOf(false) }
    var tappedSegmentName by remember { mutableStateOf<String?>(null) }
    var hasAnimatedToUser by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    var searchPinLatLng by remember { mutableStateOf<LatLng?>(null) }

    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    val railwayTileProvider = remember {
        val subdomains = arrayOf("a", "b", "c")
        var idx = 0
        object : com.google.android.gms.maps.model.UrlTileProvider(256, 256) {
            override fun getTileUrl(x: Int, y: Int, zoom: Int): java.net.URL? {
                if (zoom < 7) return null
                val sub = subdomains[idx++ % subdomains.size]
                return try {
                    java.net.URL("https://$sub.tiles.openrailwaymap.org/standard/$zoom/$x/$y.png")
                } catch (e: Exception) { null }
            }
        }
    }

    val defaultLocation = LatLng(37.33, -96.18)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, 10f)
    }

    // Animate to user's first GPS fix
    LaunchedEffect(userLocation) {
        val loc = userLocation ?: return@LaunchedEffect
        if (!hasAnimatedToUser) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 12f)
            )
            hasAnimatedToUser = true
        }
    }

    // Fetch Overpass rail lines when camera is idle at zoom ≥ 9.
    // snapshotFlow retries whenever isMoving or projection changes, so the
    // first toggle works even if projection was null during map init.
    LaunchedEffect(showRailLines) {
        if (!showRailLines) return@LaunchedEffect
        snapshotFlow {
            cameraPositionState.isMoving to
                cameraPositionState.projection?.visibleRegion?.latLngBounds
        }
            .filter { !it.first && it.second != null && cameraPositionState.position.zoom >= 9f }
            .collect {
                val bounds = it.second ?: return@collect
                vm.fetchRailwaySegments(
                    south = bounds.southwest.latitude,
                    west  = bounds.southwest.longitude,
                    north = bounds.northeast.latitude,
                    east  = bounds.northeast.longitude
                )
            }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val screenHeight = configuration.screenHeightDp.dp
    val mapHeight = if (isLandscape) 180.dp else screenHeight * 0.42f

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Map ───────────────────────────────────────────────────────────────
        Box(modifier = Modifier
            .fillMaxWidth()
            .height(mapHeight)) {

            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    mapType = if (showSatellite) MapType.SATELLITE else MapType.NORMAL,
                    isMyLocationEnabled = userLocation != null
                ),
                uiSettings = MapUiSettings(zoomControlsEnabled = false),
                onMapClick = {
                    tappedSegmentName = null
                    if (searchFocused) {
                        focusManager.clearFocus()
                        vm.clearSearch()
                    }
                }
            ) {
                if (showRailwayMap) {
                    TileOverlay(tileProvider = railwayTileProvider, transparency = 0.0f)
                }

                // Bold Overpass rail lines — drawn under train markers
                if (showRailLines) {
                    railwaySegments.forEach { segment ->
                        Polyline(
                            points = segment.points,
                            color = railroadLineColor(segment.operator),
                            width = 8f,
                            clickable = true,
                            onClick = {
                                tappedSegmentName = segment.name.ifBlank { segment.operator }.ifBlank { "Railway" }
                            }
                        )
                    }
                }

                // Draw trail polylines behind markers
                trains.forEach { train ->
                    val waypoints = trainTrails[train.id]
                    if (waypoints != null && waypoints.size >= 2) {
                        val color = android.graphics.Color.HSVToColor(
                            160, floatArrayOf(train.railroad.markerHue, 0.8f, 0.9f)
                        )
                        Polyline(
                            points = waypoints,
                            color = androidx.compose.ui.graphics.Color(color),
                            width = 6f
                        )
                    }
                }

                trains.forEach { train ->
                    val icon = remember(train.railroad) {
                        com.google.android.gms.maps.model.BitmapDescriptorFactory
                            .defaultMarker(train.railroad.markerHue)
                    }
                    Marker(
                        state = MarkerState(LatLng(train.latitude, train.longitude)),
                        icon = icon,
                        title = train.symbol,
                        snippet = "${train.speedMph} mph · ${train.origin} → ${train.destination}",
                        onClick = { selectedTrainId = train.id; false }
                    )
                }
                features.forEach { feature ->
                    Marker(
                        state = MarkerState(LatLng(feature.latitude, feature.longitude)),
                        title = feature.name,
                        snippet = feature.description
                    )
                }
                // Search-result pin
                searchPinLatLng?.let { pin ->
                    Marker(
                        state = MarkerState(pin),
                        title = "Search result",
                        icon = com.google.android.gms.maps.model.BitmapDescriptorFactory
                            .defaultMarker(com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_AZURE)
                    )
                }
            }

            // ── Tapped segment name chip ───────────────────────────────────────
            tappedSegmentName?.let { name ->
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(BgCard.copy(alpha = 0.95f))
                        .border(0.5.dp, BorderLight, RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.DirectionsRailway, null,
                        tint = RailBlue, modifier = Modifier.size(14.dp))
                    Text(name, color = TextPrimary, fontSize = 13.sp)
                    IconButton(
                        onClick = { tappedSegmentName = null },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(Icons.Default.Close, null,
                            tint = TextMuted, modifier = Modifier.size(12.dp))
                    }
                }
            }

            // ── Controls overlay ───────────────────────────────────────────────
            Column(modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(12.dp)
                .fillMaxWidth()) {

                // Search field
                Box {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(BgCard.copy(alpha = 0.97f))
                            .onFocusChanged { searchFocused = it.isFocused },
                        placeholder = { Text("Search locations, subdivisions…", color = TextMuted, fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = TextMuted, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            when {
                                isSearching -> CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = RailBlue)
                                searchQuery.isNotEmpty() -> IconButton(onClick = {
                                    searchQuery = ""
                                    searchPinLatLng = null
                                    vm.clearSearch()
                                    focusManager.clearFocus()
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = TextMuted, modifier = Modifier.size(18.dp))
                                }
                                isLoading -> CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = RailBlue)
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(onSearch = {
                            vm.searchLocation(searchQuery)
                            focusManager.clearFocus()
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RailBlueMid,
                            unfocusedBorderColor = BorderLight,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = RailBlue,
                            focusedContainerColor = BgCard.copy(alpha = 0.97f),
                            unfocusedContainerColor = BgCard.copy(alpha = 0.95f)
                        ),
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                    )

                    // Dropdown results — capped at 240dp so it never overflows off-screen
                    if (searchResults.isNotEmpty()) {
                        Box(modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 56.dp)) {
                            Column(modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(BgCard)
                                .border(0.5.dp, Border, RoundedCornerShape(12.dp))
                                .verticalScroll(rememberScrollState())) {
                                searchResults.forEachIndexed { i, result ->
                                    SearchResultRow(result, isLast = i == searchResults.lastIndex) {
                                        // Navigate map to result
                                        val pin = LatLng(result.lat, result.lon)
                                        searchPinLatLng = pin
                                        searchQuery = result.displayName.split(",").first().trim()
                                        vm.clearSearch()
                                        focusManager.clearFocus()
                                        scope.launch {
                                            cameraPositionState.animate(
                                                CameraUpdateFactory.newLatLngZoom(pin, 13f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Railroad filter chips
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip("All", selectedRailroad == null) { vm.setRailroadFilter(null) }
                    Railroad.values()
                        .filter { it != Railroad.OTHER }
                        .forEach { rr ->
                            FilterChip(rr.displayName.take(8), selectedRailroad == rr) {
                                vm.setRailroadFilter(if (selectedRailroad == rr) null else rr)
                            }
                        }
                    FilterChip("Commuter", selectedRailroad == Railroad.OTHER) {
                        vm.setRailroadFilter(if (selectedRailroad == Railroad.OTHER) null else Railroad.OTHER)
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Map layer chips
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip("Satellite", showSatellite) { showSatellite = !showSatellite }
                    FilterChip("Rail Map", showRailwayMap) { showRailwayMap = !showRailwayMap }
                    FilterChip("Rail Lines", showRailLines) { showRailLines = !showRailLines }
                }
            }
        }

        // ── Train list ─────────────────────────────────────────────────────────
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Network error banner — shown after a failed fetch
            trainFetchError?.let { msg ->
                item {
                    AlertBanner(
                        message = msg,
                        color = RailRed,
                        bgColor = Color(0xFF1A0A0A),
                        borderColor = Color(0xFF7F1D1D)
                    )
                }
            }

            // Stale-data banner — shown when last successful refresh is > 90 s ago
            lastRefreshMs?.let { ts ->
                val ageMs = nowMs - ts
                if (!isLoading && ageMs > 90_000L) {
                    item { StaleBanner(ageMs) { vm.refreshTrains() } }
                }
            }

            val loc = userLocation   // local val — allows smart cast inside lambdas
            when {
                // No GPS fix yet
                loc == null -> {
                    item { SectionHeader("Live trains nearby") }
                    item { GpsWaitingCard() }
                }
                // GPS acquired, first load in progress
                isLoading && trains.isEmpty() -> {
                    item { SectionHeader("Live trains nearby") }
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = RailBlue, strokeWidth = 2.dp,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                // GPS acquired, no trains in range
                trains.isEmpty() -> {
                    item { SectionHeader("Live trains nearby (0)") }
                    item {
                        EmptyState(
                            icon = Icons.Default.DirectionsRailway,
                            title = "No trains nearby",
                            subtitle = "No Amtrak service detected within 500 mi of your location"
                        )
                    }
                }
                // Normal — show train list
                else -> {
                    // loc is non-null here (all null cases handled above) — no !! needed
                    val locLabel = "%.4f°, %.4f°".format(loc.latitude, loc.longitude)
                    item { SectionHeader("Live trains (${trains.size}) · $locLabel") }
                    items(trains) { train ->
                        TrainCard(train) { selectedTrainId = train.id }
                    }
                }
            }
            item { Spacer(Modifier.height(100.dp)) }
        }
    }

    selectedTrain?.let { train ->
        TrainDetailSheet(train, vm) { selectedTrainId = null }
    }
}

@Composable
fun SearchResultRow(result: GeoSearchResult, isLast: Boolean, onClick: () -> Unit) {
    val shortName = result.displayName.split(",").take(3).joinToString(", ")
    Column(modifier = Modifier
        .fillMaxWidth()
        .clickable { onClick() }
        .padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Place, null, tint = RailBlue, modifier = Modifier.size(16.dp))
            Text(shortName, color = TextPrimary, fontSize = 13.sp,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
    if (!isLast) HorizontalDivider(color = Border.copy(alpha = 0.4f), thickness = 0.5.dp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainDetailSheet(train: TrainLocation, vm: RailFanViewModel, onDismiss: () -> Unit) {
    val userLocation    by vm.userLocation.collectAsState()
    val approachEtaMin  by vm.approachEtaMin.collectAsState()
    var showSaveDialog  by remember { mutableStateOf(false) }
    var savedConfirmed  by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Header: badge · name/rr · compass · speed gauge · close ─────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RailroadBadge(train.railroad, modifier = Modifier.size(48.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(train.symbol, color = TextPrimary, fontSize = 17.sp,
                        fontWeight = FontWeight.Medium, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(train.railroad.displayName, color = TextMuted, fontSize = 12.sp)
                }
                HeadingCompass(train.headingDegrees, modifier = Modifier.size(64.dp))
                SpeedGauge(train.speedMph, modifier = Modifier.size(72.dp))
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // ── ETA approach bar / stopped status ────────────────────────────
            when {
                train.etaMinutes != null -> {
                    EtaApproachBar(train.etaMinutes ?: 0, approachEtaMin)
                }
                train.speedMph < 5 -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PauseCircle, null,
                            tint = TextMuted, modifier = Modifier.size(14.dp))
                        Text(
                            if (train.speedMph == 0) "Stopped" else "Yard speed",
                            color = TextMuted, fontSize = 12.sp
                        )
                    }
                }
            }

            // ── Route detail rows ─────────────────────────────────────────────
            Column {
                DetailRow("Origin", train.origin.ifBlank { "—" })
                DetailRow("Destination", train.destination.ifBlank { "—" })
                DetailRow("Status", train.status.name
                    .replace("_", " ")
                    .lowercase()
                    .replaceFirstChar { it.uppercase() })
                train.subdivision?.let { DetailRow("Subdivision", it) }
                train.milepost?.let    { DetailRow("Milepost", "MP ${"%.1f".format(it)}") }
            }

            // ── Consist ───────────────────────────────────────────────────────
            if (train.consist.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Consist", color = TextMuted, fontSize = 12.sp)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        train.consist.forEach { loco ->
                            Box(modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(BgInput)
                                .border(0.5.dp, BorderLight, RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)) {
                                Text(loco, color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // ── Save location ─────────────────────────────────────────────────
            OutlinedButton(
                onClick = { showSaveDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RailBlue),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, RailBlueMid)
            ) {
                Icon(
                    if (savedConfirmed) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    null, modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (savedConfirmed) "Location saved!" else "Save my location here")
            }
        }
    }

    if (showSaveDialog) {
        val loc = userLocation
        SaveLocationDialog(
            defaultLat = loc?.latitude,
            defaultLon = loc?.longitude,
            onDismiss = { showSaveDialog = false },
            onSave = { name, notes, subdivision, scannerFreq, photoTips, lat, lon ->
                vm.saveLocation(lat, lon, name, notes, subdivision, scannerFreq, photoTips)
                showSaveDialog = false
                savedConfirmed = true
            }
        )
    }
}

// ── Speed arc gauge ───────────────────────────────────────────────────────────

@Composable
private fun SpeedGauge(speedMph: Int, modifier: Modifier = Modifier) {
    val fraction  = (speedMph / 90f).coerceIn(0f, 1f)
    val arcColor  = when {
        speedMph > 70 -> RailGreen
        speedMph > 30 -> RailBlueMid
        speedMph > 0  -> RailBlue
        else          -> Border
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sw  = size.minDimension * 0.13f
            val r   = (size.minDimension - sw) / 2f
            val tl  = Offset(size.width / 2f - r, size.height / 2f - r)
            val sz  = Size(r * 2f, r * 2f)
            // Grey background track
            drawArc(
                color      = Border,
                startAngle = 135f, sweepAngle = 270f,
                useCenter  = false, topLeft = tl, size = sz,
                style      = Stroke(sw, cap = StrokeCap.Round)
            )
            // Coloured speed fill
            if (fraction > 0f) {
                drawArc(
                    color      = arcColor,
                    startAngle = 135f, sweepAngle = 270f * fraction,
                    useCenter  = false, topLeft = tl, size = sz,
                    style      = Stroke(sw, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$speedMph",
                color      = if (speedMph > 0) RailBlue else TextMuted,
                fontSize   = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text("mph", color = TextMuted, fontSize = 9.sp)
        }
    }
}

// ── Heading compass ───────────────────────────────────────────────────────────

@Composable
private fun HeadingCompass(headingDegrees: Int, modifier: Modifier = Modifier) {
    val cardinal = when ((headingDegrees + 22) / 45 % 8) {
        0 -> "N"; 1 -> "NE"; 2 -> "E"; 3 -> "SE"
        4 -> "S"; 5 -> "SW"; 6 -> "W"; else -> "NW"
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx   = size.width  / 2f
            val cy   = size.height / 2f
            val rOut = size.minDimension / 2f
            val rIn  = rOut * 0.62f

            // Filled circle background + border ring
            drawCircle(color = BgInput, radius = rOut)
            drawCircle(color = Border,  radius = rOut, style = Stroke(1.5f))

            // Cardinal tick marks (N/E/S/W)
            for (d in listOf(0f, 90f, 180f, 270f)) {
                val rad  = Math.toRadians((d - 90).toDouble())
                val cosA = cos(rad).toFloat()
                val sinA = sin(rad).toFloat()
                drawLine(
                    color       = BorderLight,
                    start       = Offset(cx + rIn  * cosA, cy + rIn  * sinA),
                    end         = Offset(cx + rOut * 0.88f * cosA, cy + rOut * 0.88f * sinA),
                    strokeWidth = 1.5f
                )
            }

            // Direction arrow
            val rad     = Math.toRadians((headingDegrees - 90).toDouble())
            val cosR    = cos(rad).toFloat()
            val sinR    = sin(rad).toFloat()
            val tipLen  = rIn * 0.82f
            val tailLen = rIn * 0.42f
            val tipX    = cx + tipLen  * cosR
            val tipY    = cy + tipLen  * sinR

            // Shaft
            drawLine(
                color       = RailBlue,
                start       = Offset(cx - tailLen * cosR, cy - tailLen * sinR),
                end         = Offset(tipX, tipY),
                strokeWidth = 3f,
                cap         = StrokeCap.Round
            )
            // Arrowhead wings
            val perpRad = rad + Math.PI / 2
            val cosP    = cos(perpRad).toFloat()
            val sinP    = sin(perpRad).toFloat()
            val hLen    = rIn * 0.24f
            val hWid    = rIn * 0.14f
            for (side in listOf(1f, -1f)) {
                drawLine(
                    color       = RailBlue,
                    start       = Offset(tipX, tipY),
                    end         = Offset(
                        tipX - hLen * cosR + side * hWid * cosP,
                        tipY - hLen * sinR + side * hWid * sinP
                    ),
                    strokeWidth = 2.5f,
                    cap         = StrokeCap.Round
                )
            }
        }
        // Cardinal label at bottom of circle
        Text(
            cardinal, color = TextMuted, fontSize = 9.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 5.dp)
        )
    }
}

// ── ETA approach bar ──────────────────────────────────────────────────────────

@Composable
private fun EtaApproachBar(etaMinutes: Int, thresholdMinutes: Int) {
    val pct      = (etaMinutes.toFloat() / thresholdMinutes.coerceAtLeast(1)).coerceIn(0f, 1f)
    val barColor = when {
        etaMinutes <= 5  -> RailRed
        etaMinutes <= 10 -> RailAmber
        else             -> RailGreen
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0A1A0A))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(Icons.Default.DirectionsRailway, null,
                    tint = barColor, modifier = Modifier.size(13.dp))
                Text("Approaching", color = barColor,
                    fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            Text("$etaMinutes min away", color = barColor,
                fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        LinearProgressIndicator(
            progress  = { 1f - pct },   // fills as ETA → 0
            modifier  = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color      = barColor,
            trackColor = BgInput
        )
    }
}

@Composable
fun StaleBanner(ageMs: Long, onRefresh: () -> Unit) {
    val ageMin = (ageMs / 60_000L).toInt()
    val label = when {
        ageMin < 2  -> "about 1 min ago"
        ageMin < 60 -> "$ageMin min ago"
        else        -> "${ageMin / 60}h ${ageMin % 60}m ago"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF2A1F00))
            .border(0.5.dp, RailAmber.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .clickable { onRefresh() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.AccessTime, null, tint = RailAmber, modifier = Modifier.size(16.dp))
        Text(
            "Last updated $label · Tap to refresh",
            color = RailAmber,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Default.Refresh, null, tint = RailAmber.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextMuted, fontSize = 13.sp)
        Text(value, color = TextPrimary, fontSize = 13.sp)
    }
    HorizontalDivider(color = Border.copy(alpha = 0.5f), thickness = 0.5.dp)
}
