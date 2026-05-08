package com.railfancopilot.app.ui.screens

import android.content.Intent
import android.location.Location
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.railfancopilot.app.ui.components.*
import com.railfancopilot.app.ui.theme.*
import com.railfancopilot.app.viewmodel.RailFanViewModel

data class RRFeed(
    val name: String,
    val location: String,
    val railroads: String,
    val url: String,
    val dotColor: Color,
    /** Null for "Browse all" which is always pinned at the bottom. */
    val lat: Double? = null,
    val lon: Double? = null
)

val railroadRadioFeeds = listOf(
    RRFeed("CSX Baltimore MD",       "Baltimore, MD",    "CSX",        "http://www.railroadradio.net/content/view/199/241/", Color(0xFF1A6B3C), 39.29, -76.61),
    RRFeed("CSX | NS Elkhorn City KY","Elkhorn City, KY","CSX/NS",    "http://www.railroadradio.net/content/view/278/322/", Color(0xFF1A6B3C), 37.30, -82.35),
    RRFeed("Central New Jersey",     "New Jersey",       "CSX/NS/NJT", "http://www.railroadradio.net/content/view/166/200/", Color(0xFF004B87), 40.29, -74.55),
    RRFeed("Fort Wayne Area",        "Fort Wayne, IN",   "NS/CSX",     "http://www.railroadradio.net/content/view/33/160",   Color(0xFF004B87), 41.08, -85.14),
    RRFeed("Greater Canton Ohio",    "Canton, OH",       "NS/CSX",     "http://www.railroadradio.net/content/view/298/344/", Color(0xFF004B87), 40.80, -81.38),
    RRFeed("Spartanburg SC",         "Spartanburg, SC",  "CSX/NS",     "http://www.railroadradio.net/content/view/192/232/", Color(0xFF1A6B3C), 34.95, -81.93),
    RRFeed("BNSF/UP Ozark Region",   "St. Louis, MO",   "BNSF/UP",    "http://www.railroadradio.net/content/view/282/325/", Color(0xFFFF6600), 38.63, -90.20),
    RRFeed("South Central Virginia", "Virginia",         "NS/CSX",     "http://www.railroadradio.net/content/view/178/215/", Color(0xFF004B87), 37.10, -79.39),
    RRFeed("Vancouver BC",           "Vancouver, BC",    "CN/CPKC",    "http://www.railroadradio.net/content/view/22/130/",  Color(0xFFCC0000), 49.25, -123.10),
    RRFeed("CPKC Calgary Terminal",  "Calgary, AB",      "CPKC",       "http://www.railroadradio.net/content/view/272/315/", Color(0xFF8B0000), 51.05, -114.07),
    RRFeed("Browse all feeds",       "RailroadRadio.net","All railroads","http://www.railroadradio.net",                     RailBlue),   // no lat/lon — always last
)

@Composable
fun ScannerScreen(vm: RailFanViewModel) {
    val channels     by vm.sortedChannels.collectAsState()
    val userLocation by vm.userLocation.collectAsState()
    val context      = LocalContext.current
    var selectedTab  by remember { mutableStateOf(0) }
    var freqRailroadFilter by remember { mutableStateOf<String?>(null) }   // null = All

    // Sort live feeds by distance from the user; "Browse all" always last
    val sortedFeeds = remember(userLocation) {
        val loc = userLocation
        if (loc == null) {
            railroadRadioFeeds  // no GPS yet — keep original order
        } else {
            val distBuf = FloatArray(1)
            railroadRadioFeeds.sortedWith(compareBy { feed ->
                if (feed.lat == null || feed.lon == null) Double.MAX_VALUE
                else {
                    Location.distanceBetween(loc.latitude, loc.longitude, feed.lat, feed.lon, distBuf)
                    distBuf[0].toDouble()
                }
            })
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(color = BgPrimary)) {
        Row(
            modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Radio Scanner", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Text("Live feeds & frequency reference", color = TextMuted, fontSize = 13.sp)
            }
            // Show a pill when the list is actively sorted by location
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

        // Tab selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(color = BgCard)
                .padding(4.dp)
        ) {
            listOf("Live Feeds", "Frequencies").forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(color = if (selectedTab == index) RailBlue else Color.Transparent)
                        .clickable { selectedTab = index }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (selectedTab == index) Color.White else TextMuted,
                        fontSize = 13.sp,
                        fontWeight = if (selectedTab == index) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 100.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (selectedTab == 0) {
                // Info banner
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(color = BgInput)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Radio, contentDescription = null, tint = RailBlue, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Powered by RailroadRadio.net", color = RailBlue, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("Tap a feed to open in browser", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                }

                items(sortedFeeds) { feed ->
                    val isBrowseAll = feed.url == "http://www.railroadradio.net"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 3.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(color = BgCard)
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(feed.url))
                                context.startActivity(intent)
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(color = feed.dotColor)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(feed.name, color = if (isBrowseAll) RailBlue else TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("${feed.location} • ${feed.railroads}", color = TextMuted, fontSize = 12.sp)
                        }
                        Icon(Icons.Default.PlayCircle, contentDescription = "Open", tint = RailBlue, modifier = Modifier.size(18.dp))
                    }
                }

            } else {
                // Source note
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(color = BgInput)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, null, tint = RailBlue, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("AAR Standard Railroad Frequencies", color = RailBlue, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("Source: RadioReference.com · 160–161 MHz band + telemetry", color = TextMuted, fontSize = 11.sp)
                        }
                    }
                }

                // Railroad filter chips — order mirrors the location-sorted channel list
                item {
                    val railroads = remember(channels) {
                        channels.map { it.railroad }.distinct()   // already in distance order
                    }
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

                // Filtered frequency list
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
                    items(filtered) { channel ->
                        FrequencyRow(channel = channel)
                    }
                }
            }
        }
    }
}

@Composable
fun FrequencyRow(channel: RadioChannel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color = BgCard)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color = Color(channel.railroad.color))
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(channel.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(channel.railroad.displayName, color = TextMuted, fontSize = 12.sp)
        }
        Text("${channel.frequencyMhz} MHz", color = RailBlue, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
