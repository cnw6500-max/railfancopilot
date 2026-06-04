package com.railfancopilot.app.ui.screens

import android.content.Intent
import android.location.Location
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.railfancopilot.app.data.models.RadioChannel
import com.railfancopilot.app.data.models.TrainLocation
import com.railfancopilot.app.data.models.Transcript
import com.railfancopilot.app.ui.components.*
import com.railfancopilot.app.ui.theme.*
import com.railfancopilot.app.viewmodel.RailFanViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RRFeed(
    val name: String,
    val location: String,
    val railroads: String,
    val url: String,
    val dotColor: Color,
    val lat: Double? = null,
    val lon: Double? = null
)

// ── Live Railcam data ─────────────────────────────────────────────────────────

data class RailCamFeed(
    val name: String,       // e.g. "Rochelle, IL — Diamond"
    val location: String,   // city, state
    val railroads: String,  // e.g. "BNSF / UP"
    /** YouTube watch URL or channel search URL */
    val youtubeUrl: String,
    val isMembersOnly: Boolean = false,
    val lat: Double,
    val lon: Double
)

/**
 * Curated list of Virtual Railfan live-stream cameras on YouTube.
 * Video IDs marked [confirmed] were verified live at time of coding; permanent
 * VRF streams keep the same ID indefinitely.  Others link to a VRF channel
 * search so the feature degrades gracefully if a stream is ever re-keyed.
 */
val railCamFeeds: List<RailCamFeed> = listOf(
    // ── Confirmed permanent stream IDs ────────────────────────────────────────
    RailCamFeed(
        name        = "Rochelle, IL — Diamond",
        location    = "Rochelle, IL",
        railroads   = "BNSF / UP",
        youtubeUrl  = "https://www.youtube.com/watch?v=LhNpn9L5ndM",  // [confirmed]
        lat         = 41.9231, lon = -89.0676
    ),
    RailCamFeed(
        name        = "Kearney, NE — Platte Valley",
        location    = "Kearney, NE",
        railroads   = "BNSF",
        youtubeUrl  = "https://www.youtube.com/watch?v=23tmCNeFh7A",  // [confirmed]
        lat         = 40.6993, lon = -99.0817
    ),
    RailCamFeed(
        name        = "Flagstaff, AZ — Transcon",
        location    = "Flagstaff, AZ",
        railroads   = "BNSF",
        youtubeUrl  = "https://www.youtube.com/watch?v=7xdHH9KMSVk",  // [confirmed]
        lat         = 35.1983, lon = -111.6513
    ),
    RailCamFeed(
        name        = "Tucson, AZ — Sunset Route",
        location    = "Tucson, AZ",
        railroads   = "UP",
        youtubeUrl  = "https://www.youtube.com/watch?v=-Q9VQJdqIlk",  // [confirmed]
        lat         = 32.2226, lon = -110.9747
    ),
    RailCamFeed(
        name        = "Folkston, GA — Funnel",
        location    = "Folkston, GA",
        railroads   = "CSX",
        youtubeUrl  = "https://www.youtube.com/watch?v=xKUkjFJkKgc",  // [confirmed]
        lat         = 30.8357, lon = -82.0077
    ),
    // ── Channel-search links (stream ID not pinned) ───────────────────────────
    RailCamFeed(
        name        = "Barstow, CA — Transcon",
        location    = "Barstow, CA",
        railroads   = "BNSF / UP",
        youtubeUrl  = "https://www.youtube.com/@VirtualRailfan/search?query=Barstow",
        lat         = 34.8958, lon = -117.0228
    ),
    RailCamFeed(
        name        = "Cajon Pass, CA",
        location    = "San Bernardino, CA",
        railroads   = "BNSF / UP",
        youtubeUrl  = "https://www.youtube.com/@VirtualRailfan/search?query=Cajon",
        lat         = 34.3617, lon = -117.4592
    ),
    RailCamFeed(
        name        = "Altoona, PA — Horseshoe Curve",
        location    = "Altoona, PA",
        railroads   = "NS",
        youtubeUrl  = "https://www.youtube.com/@VirtualRailfan/search?query=Altoona",
        lat         = 40.5120, lon = -78.4050
    ),
    RailCamFeed(
        name        = "Chattanooga, TN",
        location    = "Chattanooga, TN",
        railroads   = "NS / CSX",
        youtubeUrl  = "https://www.youtube.com/@VirtualRailfan/search?query=Chattanooga",
        lat         = 35.0456, lon = -85.3097
    ),
    RailCamFeed(
        name        = "Laramie, WY — Sherman Hill",
        location    = "Laramie, WY",
        railroads   = "UP",
        youtubeUrl  = "https://www.youtube.com/@VirtualRailfan/search?query=Laramie",
        lat         = 41.3114, lon = -105.5911
    ),
    RailCamFeed(
        name        = "Helper, UT — Soldier Summit",
        location    = "Helper, UT",
        railroads   = "UP",
        youtubeUrl  = "https://www.youtube.com/@VirtualRailfan/search?query=Helper",
        lat         = 39.6849, lon = -110.8560
    ),
    RailCamFeed(
        name        = "Marias Pass, MT",
        location    = "Essex, MT",
        railroads   = "BNSF",
        youtubeUrl  = "https://www.youtube.com/@VirtualRailfan/search?query=Marias",
        lat         = 48.3258, lon = -113.5847
    ),
    RailCamFeed(
        name        = "Waycross, GA — Rice Yard",
        location    = "Waycross, GA",
        railroads   = "CSX",
        youtubeUrl  = "https://www.youtube.com/@VirtualRailfan/search?query=Waycross",
        lat         = 31.2138, lon = -82.3579
    ),
    RailCamFeed(
        name        = "Dunsmuir, CA — Shasta Route",
        location    = "Dunsmuir, CA",
        railroads   = "UP",
        youtubeUrl  = "https://www.youtube.com/@VirtualRailfan/search?query=Dunsmuir",
        lat         = 41.2007, lon = -122.2727
    ),
    RailCamFeed(
        name        = "Browse all cameras →",
        location    = "Virtual Railfan",
        railroads   = "All railroads",
        youtubeUrl  = "https://www.youtube.com/@VirtualRailfan/streams",
        lat         = 0.0, lon = 0.0   // sentinel — sorted to bottom
    ),
)

val railroadRadioFeeds = listOf(
    RRFeed("CSX Baltimore MD",        "Baltimore, MD",    "CSX",         "http://www.railroadradio.net/content/view/199/241/", Color(0xFF1A6B3C), 39.29,  -76.61),
    RRFeed("CSX | NS Elkhorn City KY","Elkhorn City, KY", "CSX/NS",      "http://www.railroadradio.net/content/view/278/322/", Color(0xFF1A6B3C), 37.30,  -82.35),
    RRFeed("Central New Jersey",      "New Jersey",        "CSX/NS/NJT",  "http://www.railroadradio.net/content/view/166/200/", Color(0xFF004B87), 40.29,  -74.55),
    RRFeed("Fort Wayne Area",         "Fort Wayne, IN",    "NS/CSX",      "http://www.railroadradio.net/content/view/33/160",   Color(0xFF004B87), 41.08,  -85.14),
    RRFeed("Greater Canton Ohio",     "Canton, OH",        "NS/CSX",      "http://www.railroadradio.net/content/view/298/344/", Color(0xFF004B87), 40.80,  -81.38),
    RRFeed("Spartanburg SC",          "Spartanburg, SC",   "CSX/NS",      "http://www.railroadradio.net/content/view/192/232/", Color(0xFF1A6B3C), 34.95,  -81.93),
    RRFeed("BNSF/UP Ozark Region",    "St. Louis, MO",     "BNSF/UP",     "http://www.railroadradio.net/content/view/282/325/", Color(0xFFFF6600), 38.63,  -90.20),
    RRFeed("South Central Virginia",  "Virginia",          "NS/CSX",      "http://www.railroadradio.net/content/view/178/215/", Color(0xFF004B87), 37.10,  -79.39),
    RRFeed("Vancouver BC",            "Vancouver, BC",     "CN/CPKC",     "http://www.railroadradio.net/content/view/22/130/",  Color(0xFFCC0000), 49.25, -123.10),
    RRFeed("CPKC Calgary Terminal",   "Calgary, AB",       "CPKC",        "http://www.railroadradio.net/content/view/272/315/", Color(0xFF8B0000), 51.05, -114.07),
    RRFeed("Browse all feeds",        "RailroadRadio.net", "All railroads","http://www.railroadradio.net",                      RailBlue),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(vm: RailFanViewModel) {
    val channels            by vm.sortedChannels.collectAsState()
    val userLocation        by vm.userLocation.collectAsState()
    val activeChannelId     by vm.activeChannelId.collectAsState()
    val recentTransmissions by vm.recentTransmissions.collectAsState()
    val trains              by vm.trains.collectAsState()
    val context             = LocalContext.current

    val favoriteFeedUrls   by vm.favoriteFeedUrls.collectAsState()
    var selectedTab        by remember { mutableStateOf(0) }
    var freqRailroadFilter by remember { mutableStateOf<String?>(null) }
    var showLogSheet       by remember { mutableStateOf(false) }

    // Auto-select the nearest railroad filter the first time location + channels arrive
    LaunchedEffect(userLocation, channels) {
        if (freqRailroadFilter == null && userLocation != null && channels.isNotEmpty()) {
            freqRailroadFilter = channels.firstOrNull { it.railroad.name != "OTHER" }?.railroad?.name
        }
    }

    val sortedFeeds = remember(userLocation) {
        val loc = userLocation
        if (loc == null) return@remember railroadRadioFeeds
        val buf = FloatArray(1)
        railroadRadioFeeds.sortedWith(compareBy { feed ->
            if (feed.lat == null) Double.MAX_VALUE
            else { Location.distanceBetween(loc.latitude, loc.longitude, feed.lat, feed.lon!!, buf); buf[0].toDouble() }
        })
    }

    // Favorites first, then distance-sorted rest, "Browse all" always last
    val displayFeeds = remember(sortedFeeds, favoriteFeedUrls) {
        val pinned    = sortedFeeds.filter { it.lat != null && it.url in favoriteFeedUrls }
        val unpinned  = sortedFeeds.filter { it.lat != null && it.url !in favoriteFeedUrls }
        val browseAll = sortedFeeds.filter { it.lat == null }
        pinned + unpinned + browseAll
    }
    val nearestFeedUrl = remember(sortedFeeds) { sortedFeeds.firstOrNull { it.lat != null }?.url }

    val sortedCams = remember(userLocation) {
        val loc = userLocation
        val buf = FloatArray(1)
        railCamFeeds.sortedWith(compareBy { cam ->
            if (cam.lat == 0.0 && cam.lon == 0.0) Double.MAX_VALUE  // "Browse all" sentinel → bottom
            else if (loc == null) 0.0
            else { Location.distanceBetween(loc.latitude, loc.longitude, cam.lat, cam.lon, buf); buf[0].toDouble() }
        })
    }

    val activeChannel = remember(activeChannelId, channels) {
        channels.firstOrNull { it.id == activeChannelId }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        Column(modifier = Modifier.fillMaxSize().background(BgPrimary)) {

            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Radio Scanner", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    Text("Live feeds, railcams & frequency reference", color = TextMuted, fontSize = 13.sp)
                }
                if (userLocation != null) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(BgInput)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.LocationOn, null, tint = RailBlue, modifier = Modifier.size(12.dp))
                        Text("Near you", color = RailBlue, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Tab bar ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgCard)
                    .padding(4.dp)
            ) {
                listOf("Live Feeds", "Live Cams", "Frequencies", "Log").forEachIndexed { index, label ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selectedTab == index) RailBlue else Color.Transparent)
                            .clickable { selectedTab = index }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                label,
                                color = if (selectedTab == index) Color.White else TextMuted,
                                fontSize = 13.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Medium else FontWeight.Normal
                            )
                            if (index == 3 && recentTransmissions.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selectedTab == 3) Color.White.copy(alpha = 0.25f) else RailBlue)
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        "${recentTransmissions.size}",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Content ───────────────────────────────────────────────────────
            LazyColumn(
                contentPadding = PaddingValues(bottom = if (activeChannel != null) 148.dp else 100.dp),
                modifier = Modifier.weight(1f)
            ) {
                when (selectedTab) {

                    // ── Live Feeds ───────────────────────────────────────────
                    0 -> {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(BgInput)
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Radio, null, tint = RailBlue, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("Powered by RailroadRadio.net", color = RailBlue, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        if (userLocation != null) "Sorted by distance · Tap to open in browser"
                                        else "Tap a feed to open in browser",
                                        color = TextMuted, fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        items(displayFeeds) { feed ->
                            val isBrowseAll = feed.lat == null
                            val isNearest   = !isBrowseAll && feed.url == nearestFeedUrl && userLocation != null
                            val isFavorite  = feed.url in favoriteFeedUrls

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 3.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .then(
                                        when {
                                            isFavorite  -> Modifier.border(1.dp, RailAmber.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                            isNearest   -> Modifier.border(1.dp, RailBlue.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                            else        -> Modifier
                                        }
                                    )
                                    .background(BgCard)
                                    .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(feed.url))) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(feed.dotColor)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            feed.name,
                                            color = if (isBrowseAll) RailBlue else TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (isFavorite) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(RailAmber.copy(alpha = 0.12f))
                                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                                            ) {
                                                Text("Pinned", color = RailAmber, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                            }
                                        } else if (isNearest) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(RailBlue.copy(alpha = 0.12f))
                                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                                            ) {
                                                Text("Nearest", color = RailBlue, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                            }
                                        }
                                    }
                                    Text("${feed.location} · ${feed.railroads}", color = TextMuted, fontSize = 12.sp)
                                }
                                if (!isBrowseAll) {
                                    IconButton(
                                        onClick = { vm.toggleFavoriteFeed(feed.url) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = if (isFavorite) "Unpin feed" else "Pin feed",
                                            tint = if (isFavorite) RailAmber else TextMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                } else {
                                    Icon(Icons.Default.PlayCircle, null, tint = RailBlue, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    // ── Live Cams ────────────────────────────────────────────
                    1 -> {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(BgInput)
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Videocam, null, tint = RailBlue, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("Powered by Virtual Railfan", color = RailBlue, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        if (userLocation != null) "Sorted by distance · Opens YouTube app"
                                        else "Tap a camera to watch in YouTube",
                                        color = TextMuted, fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        val nearestCamUrl = sortedCams.firstOrNull { it.lat != 0.0 }?.youtubeUrl

                        items(sortedCams) { cam ->
                            val isBrowseAll = cam.lat == 0.0 && cam.lon == 0.0
                            val isNearest   = !isBrowseAll && cam.youtubeUrl == nearestCamUrl && userLocation != null
                            val distLabel: String? = remember(userLocation, cam) {
                                val loc = userLocation ?: return@remember null
                                if (isBrowseAll) return@remember null
                                val buf = FloatArray(1)
                                Location.distanceBetween(loc.latitude, loc.longitude, cam.lat, cam.lon, buf)
                                val miles = buf[0] / 1609.34
                                if (miles < 10) "< 10 mi" else "${"%.0f".format(miles)} mi"
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 3.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .then(
                                        if (isNearest) Modifier.border(1.dp, RailBlue.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                        else Modifier
                                    )
                                    .background(BgCard)
                                    .clickable {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(cam.youtubeUrl)))
                                    }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    if (isBrowseAll) Icons.Default.OpenInNew else Icons.Default.Videocam,
                                    contentDescription = null,
                                    tint = if (isBrowseAll) RailBlue else Color(0xFFE53935),
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            cam.name,
                                            color = if (isBrowseAll) RailBlue else TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (isNearest) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(RailBlue.copy(alpha = 0.12f))
                                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                                            ) {
                                                Text("Nearest", color = RailBlue, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                            }
                                        }
                                        if (cam.isMembersOnly) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0xFF7B1FA2).copy(alpha = 0.15f))
                                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                                            ) {
                                                Text("Members", color = Color(0xFFBA68C8), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                            }
                                        }
                                    }
                                    if (!isBrowseAll) {
                                        Text(
                                            buildString {
                                                append(cam.railroads)
                                                if (distLabel != null) append(" · $distLabel")
                                            },
                                            color = TextMuted,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                Icon(Icons.Default.PlayCircle, null, tint = if (isBrowseAll) RailBlue else Color(0xFFE53935), modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    // ── Frequencies ──────────────────────────────────────────
                    2 -> {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(BgInput)
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, null, tint = RailBlue, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("AAR Standard Railroad Frequencies", color = RailBlue, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        if (freqRailroadFilter != null && userLocation != null)
                                            "Nearest railroad pre-selected · Tap a row to monitor · Source: RadioReference.com"
                                        else
                                            "Tap a row to monitor · Source: RadioReference.com · 160–161 MHz",
                                        color = TextMuted, fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        item {
                            val railroads = remember(channels) { channels.map { it.railroad }.distinct() }
                            LazyRow(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                item {
                                    FilterChip("All", freqRailroadFilter == null) { freqRailroadFilter = null }
                                }
                                items(railroads) { rr ->
                                    FilterChip(rr.displayName, freqRailroadFilter == rr.name) {
                                        freqRailroadFilter = if (freqRailroadFilter == rr.name) null else rr.name
                                    }
                                }
                            }
                        }

                        val filtered = if (freqRailroadFilter == null) channels
                                       else channels.filter { it.railroad.name == freqRailroadFilter }

                        if (filtered.isEmpty()) {
                            item {
                                EmptyState(
                                    icon = Icons.Default.Radio,
                                    title = "No frequencies",
                                    subtitle = "No entries for the selected railroad"
                                )
                            }
                        } else {
                            items(filtered, key = { it.id }) { channel ->
                                FrequencyRow(
                                    channel = channel,
                                    isActive = channel.id == activeChannelId,
                                    onSelect = { vm.selectChannel(channel.id) }
                                )
                            }
                        }
                    }

                    // ── Log ──────────────────────────────────────────────────
                    3 -> {
                        if (recentTransmissions.isEmpty()) {
                            item {
                                EmptyState(
                                    icon = Icons.Default.MicNone,
                                    title = "No transmissions logged",
                                    subtitle = "Select a frequency, then tap Log to record what you hear"
                                )
                            }
                        } else {
                            items(recentTransmissions, key = { it.id }) { tx ->
                                val channel = channels.firstOrNull { it.id == tx.channelId }
                                TransmissionRow(
                                    transcript = tx,
                                    channelLabel = channel?.let { "${it.frequencyMhz} MHz — ${it.name}" } ?: "Unknown channel",
                                    onTune = {
                                        if (channel != null) {
                                            vm.selectChannel(channel.id)
                                            freqRailroadFilter = channel.railroad.name
                                            selectedTab = 2
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Monitoring bar (shown when a channel is active) ────────────────────
        if (activeChannel != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, RailBlue.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .background(BgCard)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF4CAF50))
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Monitoring", color = TextMuted, fontSize = 11.sp)
                    Text(
                        "${activeChannel.frequencyMhz} MHz — ${activeChannel.name}",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                TextButton(
                    onClick = { vm.stopScanner() },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("Clear", color = TextMuted, fontSize = 12.sp)
                }
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = { showLogSheet = true },
                    colors = ButtonDefaults.buttonColors(containerColor = RailBlue),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Log", fontSize = 13.sp)
                }
            }
        }
    }

    if (showLogSheet && activeChannel != null) {
        LogTransmissionSheet(
            channel = activeChannel,
            nearbyTrains = trains,
            onDismiss = { showLogSheet = false },
            onLog = { note, trainSymbol ->
                vm.logTransmission(activeChannel.id, note, trainSymbol)
                showLogSheet = false
            }
        )
    }
}

// ── FrequencyRow ──────────────────────────────────────────────────────────────

@Composable
fun FrequencyRow(
    channel: RadioChannel,
    isActive: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(if (isActive) Modifier.border(1.5.dp, RailBlue, RoundedCornerShape(12.dp)) else Modifier)
            .background(if (isActive) RailBlue.copy(alpha = 0.08f) else BgCard)
            .clickable { onSelect() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (isActive) RailBlue else Color(channel.railroad.color))
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                channel.name,
                color = if (isActive) RailBlue else TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(channel.railroad.displayName, color = TextMuted, fontSize = 12.sp)
        }
        Text("${channel.frequencyMhz} MHz", color = RailBlue, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        if (isActive) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.CheckCircle, null, tint = RailBlue, modifier = Modifier.size(16.dp))
        }
    }
}

// ── TransmissionRow ───────────────────────────────────────────────────────────

@Composable
fun TransmissionRow(
    transcript: Transcript,
    channelLabel: String,
    onTune: () -> Unit
) {
    val timeStr = remember(transcript.timestampMs) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(transcript.timestampMs))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(timeStr, color = TextMuted, fontSize = 11.sp)
                if (transcript.taggedTrainSymbol != null) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF1A6B3C).copy(alpha = 0.15f))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(Icons.Default.Train, null, tint = Color(0xFF1A6B3C), modifier = Modifier.size(10.dp))
                        Text(transcript.taggedTrainSymbol, color = Color(0xFF1A6B3C), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(channelLabel, color = RailBlue, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            if (transcript.text.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(transcript.text, color = TextPrimary, fontSize = 13.sp)
            }
        }
        TextButton(
            onClick = onTune,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(Icons.Default.Tune, null, modifier = Modifier.size(14.dp), tint = RailBlue)
            Spacer(Modifier.width(2.dp))
            Text("Tune", color = RailBlue, fontSize = 11.sp)
        }
    }
}

// ── LogTransmissionSheet ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogTransmissionSheet(
    channel: RadioChannel,
    nearbyTrains: List<TrainLocation>,
    onDismiss: () -> Unit,
    onLog: (note: String, trainSymbol: String?) -> Unit
) {
    var note          by remember { mutableStateOf("") }
    var selectedTrain by remember { mutableStateOf<TrainLocation?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BgCard
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text("Log Transmission", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text("${channel.frequencyMhz} MHz — ${channel.name}", color = RailBlue, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Notes (optional)", color = TextMuted) },
                placeholder = { Text("What did you hear?", color = TextMuted) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RailBlue,
                    unfocusedBorderColor = BgInput,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                minLines = 2
            )

            if (nearbyTrains.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Tag to train", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(nearbyTrains.take(10)) { train ->
                        val isSelected = selectedTrain?.id == train.id
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) RailBlue else BgInput)
                                .clickable { selectedTrain = if (isSelected) null else train }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Train, null,
                                tint = if (isSelected) Color.White else TextMuted,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                train.symbol,
                                color = if (isSelected) Color.White else TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { onLog(note.trim(), selectedTrain?.symbol) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = RailBlue)
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Log Transmission")
            }
        }
    }
}
