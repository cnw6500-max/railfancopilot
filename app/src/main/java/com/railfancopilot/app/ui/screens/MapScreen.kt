package com.railfancopilot.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
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
import com.railfancopilot.shared.tutorial.TutorialStep
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
        "CPKC" in op                              -> androidx.compose.ui.graphics.Color(0xFF880E4F)
        "METRA" in op || "METRO-NORTH" in op || "LIRR" in op || "LONG ISLAND" in op ||
            "NJ TRANSIT" in op || "SEPTA" in op || "CALTRAIN" in op || "METROLINK" in op ||
            "MBTA" in op || "VIRGINIA RAILWAY" in op || "FRONTRUNNER" in op || "TRINITY" in op
                                                  -> androidx.compose.ui.graphics.Color(0xFF00897B)
        else                                      -> androidx.compose.ui.graphics.Color(0xFF546E7A)
    }
}

@Composable
fun MapScreen(vm: RailFanViewModel, onNavigateToCommunity: () -> Unit = {}) {
    val trains by vm.filteredTrains.collectAsState()
    val trainTrails by vm.trainTrails.collectAsState()
    val features by vm.mapFeatures.collectAsState()
    val communityReports by vm.communityReports.collectAsState()
    val selectedRailroad by vm.selectedRailroad.collectAsState()
    val isLoading by vm.isLoadingTrains.collectAsState()
    val userLocation by vm.userLocation.collectAsState()
    val searchResults by vm.searchResults.collectAsState()
    val isSearching by vm.isSearching.collectAsState()
    val railOverlayDefault by vm.railOverlayDefault.collectAsState()
    val lastRefreshMs by vm.lastRefreshMs.collectAsState()
    val trainFetchError by vm.trainFetchError.collectAsState()
    val railwaySegments by vm.railwaySegments.collectAsState()
    val abandonedLines by vm.abandonedLines.collectAsState()

    // Tick every 30 s so the stale-data banner age label stays fresh
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(30_000L); nowMs = System.currentTimeMillis() }
    }

    // Store the selected train by ID so the detail sheet always reflects live data after a refresh
    var selectedTrainId by remember { mutableStateOf<String?>(null) }
    val selectedTrain by remember { derivedStateOf { trains.find { it.id == selectedTrainId } } }
    var selectedSightingId by remember { mutableStateOf<String?>(null) }
    val selectedSighting by remember { derivedStateOf { communityReports.find { it.id == selectedSightingId } } }
    var showSatellite by remember { mutableStateOf(false) }
    var showRailwayMap by remember { mutableStateOf(railOverlayDefault) }
    var showRailLines by remember { mutableStateOf(false) }
    var showAbandoned by remember { mutableStateOf(false) }
    var selectedAbandoned by remember { mutableStateOf<AbandonedRailLine?>(null) }
    var showSightings by remember { mutableStateOf(true) }
    var tappedSegmentName by remember { mutableStateOf<String?>(null) }
    var hasAnimatedToUser by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    var searchPinLatLng by remember { mutableStateOf<LatLng?>(null) }
    var showStationBoard by remember { mutableStateOf(false) }

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

    // Fetch STB abandoned / railbanked lines when camera is idle at zoom ≥ 7.
    LaunchedEffect(showAbandoned) {
        if (!showAbandoned) return@LaunchedEffect
        snapshotFlow {
            cameraPositionState.isMoving to
                cameraPositionState.projection?.visibleRegion?.latLngBounds
        }
            .filter { !it.first && it.second != null && cameraPositionState.position.zoom >= 7f }
            .collect {
                val bounds = it.second ?: return@collect
                vm.fetchAbandonedLines(
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

    CoachMarkBanner(listOf(TutorialStep.LIVE_TRAINS, TutorialStep.NEARBY_SEARCH), vm)

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

                // STB abandoned / railbanked lines — dashed, drawn under active rail lines
                if (showAbandoned) {
                    abandonedLines.forEach { line ->
                        Polyline(
                            points = line.points,
                            color = if (line.railbanked) RailGreen else Color(0xFFB0BEC5),
                            width = 7f,
                            pattern = listOf(
                                com.google.android.gms.maps.model.Dash(18f),
                                com.google.android.gms.maps.model.Gap(10f)
                            ),
                            clickable = true,
                            onClick = { selectedAbandoned = line }
                        )
                    }
                }

                // Bold rail lines (STB/NTAD, Overpass fallback) — drawn under train markers
                if (showRailLines) {
                    railwaySegments.forEach { segment ->
                        Polyline(
                            points = segment.points,
                            color = railroadLineColor(segment.operator),
                            width = if (segment.tracks >= 2) 10f else 8f,
                            clickable = true,
                            onClick = {
                                tappedSegmentName = buildString {
                                    append(segment.operator.ifBlank { segment.ownerMark }.ifBlank { "Railway" })
                                    if (segment.subdivision.isNotBlank()) append(" · ${segment.subdivision} Sub")
                                    else if (segment.name.isNotBlank()) append(" · ${segment.name}")
                                    if (segment.yardName.isNotBlank()) append(" · ${segment.yardName} Yard")
                                    if (segment.tracks >= 2) append(" · ${segment.tracks} tracks")
                                }
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
                        onClick = {
                            selectedTrainId = train.id
                            selectedSightingId = null
                            false
                        }
                    )
                }
                features.forEach { feature ->
                    Marker(
                        state = MarkerState(LatLng(feature.latitude, feature.longitude)),
                        title = feature.name,
                        snippet = feature.description
                    )
                }

                // Community sighting markers — cyan pins
                val cyanIcon = remember {
                    com.google.android.gms.maps.model.BitmapDescriptorFactory
                        .defaultMarker(com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_CYAN)
                }
                if (showSightings) {
                    communityReports.forEach { report ->
                        if (report.latitude != 0.0 && report.longitude != 0.0) {
                            Marker(
                                state = MarkerState(LatLng(report.latitude, report.longitude)),
                                icon = cyanIcon,
                                title = sightingTitle(report.railroad, report.trainSymbol),
                                snippet = "${report.userName} · ${timeAgoLabel(report.timestampMs, nowMs)}",
                                onClick = {
                                    selectedSightingId = report.id
                                    selectedTrainId = null
                                    false
                                }
                            )
                        }
                    }
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
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip("Satellite", showSatellite) { showSatellite = !showSatellite }
                    FilterChip("Rail Map", showRailwayMap) { showRailwayMap = !showRailwayMap }
                    FilterChip("Rail Lines", showRailLines) { showRailLines = !showRailLines }
                    FilterChip("Abandoned", showAbandoned) { showAbandoned = !showAbandoned }
                    FilterChip("Sightings", showSightings) { showSightings = !showSightings }
                    FilterChip("Station Board", false) { showStationBoard = true }
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
                        TrainCard(train) {
                            selectedTrainId = train.id
                            selectedSightingId = null
                        }
                    }
                }
            }

            // ── Community sightings — prompt a first report when the area is empty ──
            // Railfan Copilot runs on user-submitted sightings; an empty map with no
            // call-to-action reads as "broken" rather than "be the first." (loc != null
            // guards against flashing this before GPS resolves, matching the trains logic above.)
            if (showSightings && loc != null && communityReports.isEmpty()) {
                item { SectionHeader("Community sightings nearby (0)") }
                item {
                    EmptyState(
                        icon = Icons.Default.LocationOn,
                        title = "No sightings reported near you yet",
                        subtitle = "Railfan Copilot runs on reports from railfans like you. Be the first to log what you see nearby.",
                        actionLabel = "Report a Sighting",
                        onAction = onNavigateToCommunity
                    )
                }
            }

            item { Spacer(Modifier.height(100.dp)) }
        }
    }

    selectedTrain?.let { train ->
        TrainDetailSheet(train, vm) { selectedTrainId = null }
    }

    selectedSighting?.let { report ->
        SightingDetailSheet(report, nowMs) { selectedSightingId = null }
    }

    if (showStationBoard) {
        StationDeparturesSheet(vm = vm, onDismiss = {
            showStationBoard = false
            vm.clearStationDepartures()
        })
    }

    selectedAbandoned?.let { line ->
        AbandonedLineSheet(line) { selectedAbandoned = null }
    }
}

// ── Abandoned / railbanked line detail sheet (STB docket data) ─────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbandonedLineSheet(line: AbandonedRailLine, onDismiss: () -> Unit) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val accent = if (line.railbanked) RailGreen else Color(0xFFB0BEC5)

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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (line.railbanked) Icons.Default.Hiking else Icons.Default.DirectionsRailway,
                        null, tint = accent, modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(line.railroad.ifBlank { "Unknown railroad" }, color = TextPrimary,
                        fontSize = 16.sp, fontWeight = FontWeight.Medium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(line.statusLabel, color = accent, fontSize = 12.sp)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "Close", tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }

            HorizontalDivider(color = Border)

            @Composable
            fun InfoRow(label: String, value: String) {
                if (value.isBlank()) return
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(label, color = TextMuted, fontSize = 12.sp, modifier = Modifier.width(96.dp))
                    Text(value, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                }
            }

            InfoRow("Docket", line.docket)
            InfoRow("Location", listOf(line.county, line.state).filter { it.isNotBlank() }.joinToString(", "))
            if (line.lengthMiles > 0) InfoRow("Length", String.format("%.2f mi", line.lengthMiles))
            InfoRow("Filed", line.filed)
            InfoRow("Approved", line.approved)
            InfoRow(if (line.railbanked) "Railbanked" else "Completed", line.completed)

            if (line.moreInfo.isNotBlank()) {
                Text(line.moreInfo, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }

            if (line.link.isNotBlank()) {
                Button(
                    onClick = { runCatching { uriHandler.openUri(line.link) } },
                    colors = ButtonDefaults.buttonColors(containerColor = RailBlueDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("STB docket records", fontSize = 13.sp)
                }
            }

            Text(
                "Source: Surface Transportation Board, Office of Environmental Analysis. " +
                "Informational only — does not establish the legal status of any rail line.",
                color = TextMuted, fontSize = 10.sp, lineHeight = 13.sp
            )
        }
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
    val activeTrip      by vm.activeTrip.collectAsState()
    val speedHistory    by vm.speedHistory.collectAsState()
    var showSaveDialog  by remember { mutableStateOf(false) }
    var savedConfirmed  by remember { mutableStateOf(false) }
    var showTimetable        by remember { mutableStateOf(false) }
    var showBoardingDialog   by remember { mutableStateOf(false) }
    var boardingStationInput by remember { mutableStateOf("") }
    val isRidingThis    = activeTrip?.trainId == train.id
    val speeds          = speedHistory[train.id] ?: emptyList()

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

            // ── Speed sparkline ───────────────────────────────────────────────
            if (speeds.size >= 2) {
                SpeedSparkline(speeds = speeds, currentMph = train.speedMph)
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

            // ── Ride this Train ───────────────────────────────────────────────
            when {
                isRidingThis -> {
                    // Already on this train — show live distance + end button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0F2A18))
                            .border(0.5.dp, RailGreen.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(RailGreen)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Riding", color = RailGreen, fontSize = 11.sp)
                            Text(
                                "${"%.1f".format(activeTrip!!.distanceMiles)} mi · ${activeTrip!!.durationMinutes} min",
                                color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium
                            )
                        }
                        OutlinedButton(
                            onClick = { vm.endTrip() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RailGreen),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, RailGreen.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Stop, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("End Trip", fontSize = 12.sp)
                        }
                    }
                }
                activeTrip != null -> {
                    // On a different train — show info, no start button
                    Text(
                        "Trip active on ${activeTrip!!.trainSymbol} — end it first to start a new one",
                        color = TextMuted, fontSize = 12.sp
                    )
                }
                else -> {
                    // No active trip — offer to start one
                    OutlinedButton(
                        onClick = { showBoardingDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RailGreen),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, RailGreen.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.DirectionsRailway, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Ride this Train")
                    }
                }
            }

            // ── Share sighting ────────────────────────────────────────────────
            val context = androidx.compose.ui.platform.LocalContext.current
            OutlinedButton(
                onClick = {
                    val statusText = train.status.name.lowercase().replace("_", " ")
                        .replaceFirstChar { it.uppercase() }
                    val text = buildString {
                        appendLine("🚂 ${train.symbol}")
                        appendLine("${train.railroad.displayName} · ${train.speedMph} mph · $statusText")
                        if (train.origin.isNotBlank() && train.destination.isNotBlank())
                            appendLine("${train.origin} → ${train.destination}")
                        train.subdivision?.let { appendLine("Subdivision: $it") }
                        train.milepost?.let { appendLine("MP ${"%.1f".format(it)}") }
                        append("via Railfan Copilot")
                    }
                    context.startActivity(
                        android.content.Intent.createChooser(
                            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, text)
                            }, "Share sighting"
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderLight)
            ) {
                Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share Sighting")
            }

            // ── Timetable ─────────────────────────────────────────────────────
            OutlinedButton(
                onClick = {
                    showTimetable = true
                    vm.loadTimetable(train)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RailBlue),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, RailBlueMid)
            ) {
                Icon(Icons.Default.Schedule, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Timetable")
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
            lookupRailInfo = { lat, lon -> vm.lookupRailInfo(lat, lon) },
            onDismiss = { showSaveDialog = false },
            onSave = { name, notes, subdivision, scannerFreq, photoTips, lat, lon ->
                vm.saveLocation(lat, lon, name, notes, subdivision, scannerFreq, photoTips)
                showSaveDialog = false
                savedConfirmed = true
            }
        )
    }

    if (showBoardingDialog) {
        AlertDialog(
            onDismissRequest = { showBoardingDialog = false },
            containerColor = BgCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.DirectionsRailway, null, tint = RailGreen,
                        modifier = Modifier.size(20.dp))
                    Text("Start Trip", color = TextPrimary, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Boarding ${train.symbol}", color = TextSecondary, fontSize = 13.sp)
                    OutlinedTextField(
                        value = boardingStationInput,
                        onValueChange = { boardingStationInput = it },
                        label = { Text("Boarding station (optional)", color = TextMuted,
                            fontSize = 12.sp) },
                        placeholder = { Text("e.g. Chicago Union Station", color = TextMuted,
                            fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RailGreen, unfocusedBorderColor = Border,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                            cursorColor = RailGreen,
                            focusedContainerColor = BgCard, unfocusedContainerColor = BgCard
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.startTrip(train, boardingStationInput.ifBlank { null })
                        showBoardingDialog = false
                        boardingStationInput = ""
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RailGreen.copy(alpha = 0.8f))
                ) { Text("Start", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showBoardingDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    if (showTimetable) {
        TimetableSheet(train = train, vm = vm, onDismiss = {
            showTimetable = false
            vm.clearTimetable()
        })
    }
}

// ── StationDeparturesSheet ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationDeparturesSheet(vm: RailFanViewModel, onDismiss: () -> Unit) {
    val departures by vm.stationDepartures.collectAsState()
    val loading    by vm.stationDeparturesLoading.collectAsState()
    val error      by vm.stationDeparturesError.collectAsState()
    var codeInput  by remember { mutableStateOf("") }

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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Train, null, tint = RailBlue, modifier = Modifier.size(20.dp))
                Text("Station Board", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }

            // Station code input
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it.uppercase().take(5) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Station code", color = TextMuted, fontSize = 12.sp) },
                    placeholder = { Text("e.g. CHI, NYP, LAX", color = TextMuted, fontSize = 13.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RailBlue, unfocusedBorderColor = Border,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                        cursorColor = RailBlue,
                        focusedContainerColor = BgCard, unfocusedContainerColor = BgCard
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Search
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = { if (codeInput.isNotBlank()) vm.loadStationDepartures(codeInput) }
                    )
                )
                Button(
                    onClick = { if (codeInput.isNotBlank()) vm.loadStationDepartures(codeInput) },
                    colors = ButtonDefaults.buttonColors(containerColor = RailBlue),
                    enabled = codeInput.isNotBlank() && !loading
                ) {
                    if (loading) CircularProgressIndicator(color = Color.White,
                        modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp))
                }
            }

            // Common station quick-picks
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val common = listOf("NYP", "CHI", "LAX", "WAS", "BOS", "SEA", "NOL", "SAS", "EMY")
                items(common) { code ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(BgInput)
                            .border(0.5.dp, BorderLight, RoundedCornerShape(8.dp))
                            .clickable { codeInput = code; vm.loadStationDepartures(code) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(code, color = RailBlue, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            when {
                error != null -> {
                    Row(
                        modifier = Modifier.clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1A0A0A)).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info, null, tint = RailAmber, modifier = Modifier.size(16.dp))
                        Text(error!!, color = TextSecondary, fontSize = 13.sp)
                    }
                }
                departures.isNotEmpty() -> {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(departures) { dep ->
                            val stop = dep.stops.firstOrNull { it.code.equals(codeInput, ignoreCase = true) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BgPrimary)
                                    .border(0.5.dp, BorderLight, RoundedCornerShape(10.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(dep.trainSymbol, color = TextPrimary, fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium)
                                    Text(dep.routeName, color = TextMuted, fontSize = 12.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    val depTime = stop?.actualDeparture ?: stop?.scheduledDeparture
                                    val arrTime = stop?.actualArrival ?: stop?.scheduledArrival
                                    depTime?.let { Text("DEP $it", color = RailBlue, fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium) }
                                    arrTime?.let { Text("ARR $it", color = TextMuted, fontSize = 11.sp) }
                                    stop?.departureStatus?.let {
                                        val isLate = it.contains("Late", ignoreCase = true)
                                        Text(it, color = if (isLate) RailAmber else RailGreen,
                                            fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Speed sparkline ───────────────────────────────────────────────────────────

@Composable
fun SpeedSparkline(speeds: List<Int>, currentMph: Int) {
    val lineColor = when {
        currentMph > 70 -> RailGreen
        currentMph > 30 -> RailBlueMid
        currentMph > 0  -> RailBlue
        else            -> Border
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
        ) {
            val max = speeds.maxOrNull()?.toFloat()?.coerceAtLeast(10f) ?: 10f
            val stepX = if (speeds.size > 1) size.width / (speeds.size - 1) else size.width
            val pts = speeds.mapIndexed { i, spd ->
                Offset(i * stepX, size.height - (spd / max) * size.height * 0.9f)
            }
            // Fill area under line
            if (pts.size >= 2) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(pts.first().x, size.height)
                    pts.forEach { lineTo(it.x, it.y) }
                    lineTo(pts.last().x, size.height)
                    close()
                }
                drawPath(path, color = lineColor.copy(alpha = 0.12f))
                // Draw line
                for (i in 0 until pts.lastIndex) {
                    drawLine(lineColor.copy(alpha = 0.7f), pts[i], pts[i + 1], strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                }
                // Current point dot
                drawCircle(lineColor, radius = 3.dp.toPx(), center = pts.last())
            }
        }
        Text("$currentMph mph", color = lineColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

// ── TimetableSheet ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableSheet(train: TrainLocation, vm: RailFanViewModel, onDismiss: () -> Unit) {
    val stops   by vm.timetable.collectAsState()
    val loading by vm.timetableLoading.collectAsState()
    val error   by vm.timetableError.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(Icons.Default.Schedule, null, tint = RailBlue, modifier = Modifier.size(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Timetable", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Text(train.symbol, color = TextMuted, fontSize = 12.sp)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }

            when {
                loading -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(color = RailBlue, strokeWidth = 2.dp,
                                modifier = Modifier.size(24.dp))
                            Text("Loading timetable…", color = TextMuted, fontSize = 13.sp)
                        }
                    }
                }
                error != null -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1A0A0A))
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, null, tint = RailAmber, modifier = Modifier.size(16.dp))
                        Text(error!!, color = TextSecondary, fontSize = 13.sp)
                    }
                }
                stops.isEmpty() -> {
                    Text("No stops found.", color = TextMuted, fontSize = 13.sp)
                }
                else -> {
                    // Find the index of the "current" stop (last departed or first not-yet-arrived)
                    val currentIdx = stops.indexOfLast { it.hasDeparted }
                        .coerceAtLeast(0)

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 480.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        itemsIndexed(stops) { index, stop ->
                            TimetableStopRow(
                                stop      = stop,
                                isPast    = stop.hasDeparted,
                                isCurrent = index == currentIdx + 1 && !stops[currentIdx].hasDeparted.not(),
                                isLast    = index == stops.lastIndex
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Scheduled times shown. Actual times update when known.",
                        color = TextMuted, fontSize = 10.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TimetableStopRow(
    stop: TimetableStop,
    isPast: Boolean,
    isCurrent: Boolean,
    isLast: Boolean
) {
    val textColor = when {
        isCurrent -> TextPrimary
        isPast    -> TextMuted
        else      -> TextSecondary
    }
    val timeColor = when {
        isCurrent -> RailBlue
        isPast    -> TextMuted.copy(alpha = 0.6f)
        else      -> TextPrimary
    }
    val dotColor = when {
        isCurrent -> RailBlue
        isPast    -> Border
        else      -> BorderLight
    }

    // Determine the best departure time to show: actual if known, else scheduled
    val arrDisplay = stop.actualArrival ?: stop.scheduledArrival
    val depDisplay = stop.actualDeparture ?: stop.scheduledDeparture

    // Status chip text
    val statusText = stop.departureStatus ?: stop.arrivalStatus

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Timeline column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(16.dp)
        ) {
            if (!isLast) {
                Box(modifier = Modifier.width(2.dp).height(8.dp)
                    .background(if (isPast) Border else BorderLight))
            } else {
                Spacer(Modifier.height(8.dp))
            }
            Box(modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(dotColor)
                .then(if (isCurrent) Modifier.border(2.dp, RailBlue, RoundedCornerShape(5.dp)) else Modifier)
            )
            if (!isLast) {
                Box(modifier = Modifier.width(2.dp).weight(1f).heightIn(min = 20.dp)
                    .background(if (isPast) Border else BorderLight))
            }
        }

        // Stop content
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stop.code,
                    color = if (isCurrent) TextPrimary else textColor,
                    fontSize = if (isCurrent) 15.sp else 14.sp,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal
                )
                if (stop.isBusThruway) {
                    Box(modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(RailAmber.copy(alpha = 0.15f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)) {
                        Text("Bus", color = RailAmber, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                    }
                }
                if (isCurrent) {
                    Box(modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(RailBlue.copy(alpha = 0.15f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)) {
                        Text("Next stop", color = RailBlue, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                    }
                }
                statusText?.let {
                    val isLate = it.contains("Late", ignoreCase = true) ||
                                 it.contains("min", ignoreCase = true)
                    Text(it, color = if (isLate) RailAmber else RailGreen.copy(alpha = 0.8f),
                        fontSize = 10.sp)
                }
            }

            if (arrDisplay != null || depDisplay != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    arrDisplay?.let {
                        Column {
                            Text("ARR", color = TextMuted, fontSize = 9.sp)
                            Text(it, color = timeColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    depDisplay?.let {
                        Column {
                            Text("DEP", color = TextMuted, fontSize = 9.sp)
                            Text(it, color = timeColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
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

// ── Shared time-ago helper (used by map markers and sighting sheet) ───────────

internal fun timeAgoLabel(timestampMs: Long, nowMs: Long): String {
    val diffMin = ((nowMs - timestampMs) / 60_000L).toInt().coerceAtLeast(0)
    return when {
        diffMin < 1    -> "just now"
        diffMin < 60   -> "$diffMin min ago"
        diffMin < 1440 -> "${diffMin / 60}h ago"
        else           -> "${diffMin / 1440}d ago"
    }
}

// ── Sighting title builder (used by map markers and sighting sheet) ───────────

internal fun sightingTitle(
    railroad: String?,
    trainSymbol: String?,
    fallback: String = "Sighting"
): String = buildString {
    if (!railroad.isNullOrBlank()) append(railroad)
    if (!trainSymbol.isNullOrBlank()) {
        if (isNotEmpty()) append(" · ")
        append(trainSymbol)
    }
    if (isEmpty()) append(fallback)
}

// ── Community sighting detail sheet ──────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SightingDetailSheet(
    report: com.railfancopilot.app.data.models.CommunityReport,
    nowMs: Long,
    onDismiss: () -> Unit
) {
    val isRemotePhoto = report.localPhotoPath?.startsWith("https://") == true

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
            // ── Header ────────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(StatusBgGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Group, null,
                        tint = RailGreen,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        sightingTitle(report.railroad, report.trainSymbol, "Community Sighting"),
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${report.userName} · ${timeAgoLabel(report.timestampMs, nowMs)}",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close, null,
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // ── Location name ─────────────────────────────────────────────────
            if (report.locationName.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Place, null,
                        tint = TextMuted, modifier = Modifier.size(14.dp))
                    Text(report.locationName, color = TextMuted, fontSize = 12.sp)
                }
            }

            // ── Sighting note ─────────────────────────────────────────────────
            if (report.text.isNotBlank()) {
                Text(report.text, color = TextSecondary, fontSize = 13.sp, lineHeight = 20.sp)
            }

            // ── Photo ─────────────────────────────────────────────────────────
            if (isRemotePhoto) {
                coil.compose.AsyncImage(
                    model = report.localPhotoPath,
                    contentDescription = "Sighting photo",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(0.5.dp, BorderLight, RoundedCornerShape(10.dp))
                )
            }

            // ── Coordinates ───────────────────────────────────────────────────
            Text(
                "%.5f°, %.5f°".format(report.latitude, report.longitude),
                color = TextMuted.copy(alpha = 0.6f),
                fontSize = 10.sp
            )
        }
    }
}
