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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.railfancopilot.app.data.models.LocomotiveEntry
import com.railfancopilot.app.ui.components.*
import androidx.compose.foundation.lazy.itemsIndexed
import com.railfancopilot.app.ui.theme.*
import com.railfancopilot.app.viewmodel.RailFanViewModel

@Composable
fun EncyclopediaScreen(vm: RailFanViewModel) {
    val locos by vm.locomotives.collectAsState()
    val isLoadingLocos by vm.isLoadingLocos.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedLoco by remember { mutableStateOf<LocomotiveEntry?>(null) }

    val filtered = locos.filter {
        searchQuery.isBlank() ||
        it.model.contains(searchQuery, ignoreCase = true) ||
        it.manufacturer.contains(searchQuery, ignoreCase = true) ||
        it.railroads.any { rr -> rr.displayName.contains(searchQuery, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp)) {
            Text("Railroad Encyclopedia", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }

        // Search
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            placeholder = { Text("Search models, railroads...", color = TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted, modifier = Modifier.size(18.dp)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RailBlueMid,
                unfocusedBorderColor = BorderLight,
                cursorColor = RailBlue,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(10.dp),
            singleLine = true
        )

        LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
            item { SectionHeader("Locomotive Roster (${filtered.size})") }

            if (isLoadingLocos) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = RailBlue, modifier = Modifier.size(32.dp))
                    }
                }
            } else if (filtered.isEmpty() && searchQuery.isNotBlank()) {
                item {
                    EmptyState(
                        icon = Icons.Default.SearchOff,
                        title = "No results for \"$searchQuery\"",
                        subtitle = "Try a model name, manufacturer, or railroad"
                    )
                }
            } else {
                items(filtered, key = { it.id }) { loco ->
                    LocoCard(loco) { selectedLoco = loco }
                }
            }

            // Signal Systems section — hidden while user is actively searching
            if (searchQuery.isBlank()) {
                item {
                    SectionHeader("Signal Systems")
                    Column(modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgCard)
                        .border(0.5.dp, Border, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(
                            "CTC" to "Centralized Traffic Control — dispatcher controls all signals/switches remotely",
                            "ABS" to "Automatic Block Signals — train presence triggers signals automatically",
                            "PTC" to "Positive Train Control — GPS-based system that can stop trains automatically",
                            "DTC" to "Direct Traffic Control — used on single-track lines without signals",
                            "TWC" to "Track Warrant Control — dispatcher issues paper/verbal authority"
                        ).forEach { (abbr, desc) ->
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(RailBlueDark)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)) {
                                    Text(abbr, color = RailBlue, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                                Text(desc, color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(100.dp)) }
        }
    }

    selectedLoco?.let { loco ->
        LocoDetailSheet(loco) { selectedLoco = null }
    }
}

@Composable
fun LocoCard(loco: LocomotiveEntry, onClick: () -> Unit) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 4.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(BgCard)
        .border(0.5.dp, Border, RoundedCornerShape(12.dp))
        .clickable(onClick = onClick)
        .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {

        // Loco thumbnail — emoji shown as placeholder/fallback behind the photo
        Box(modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(BgInput),
            contentAlignment = Alignment.Center) {
            Text("🚂", fontSize = 22.sp)
            if (loco.imageUrl != null) {
                AsyncImage(
                    model = loco.imageUrl,
                    contentDescription = loco.model,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(loco.model, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text("${loco.manufacturer} · ${loco.introduced}", color = TextMuted, fontSize = 12.sp)
            Text("${loco.horsepower.toLocaleString()} hp · ${loco.wheelArrangement}", color = TextSecondary, fontSize = 12.sp)
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(loco.tractionMotors.take(2).uppercase(), color = RailBlue, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Icon(Icons.Default.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}

private fun Int.toLocaleString(): String = String.format("%,d", this)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocoDetailSheet(loco: LocomotiveEntry, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)) {

            // Hero photo
            if (loco.imageUrl != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgInput)
                ) {
                    AsyncImage(
                        model = loco.imageUrl,
                        contentDescription = loco.model,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.height(14.dp))
            }

            Text(loco.model, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            Text("${loco.manufacturer} · Introduced ${loco.introduced}", color = TextMuted, fontSize = 14.sp)

            Spacer(Modifier.height(16.dp))

            // Spec cards
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SpecCard("${loco.horsepower.toLocaleString()} hp", "Horsepower", Modifier.weight(1f))
                SpecCard(loco.wheelArrangement, "Wheel arrange.", Modifier.weight(1f))
                SpecCard(loco.tractionMotors, "Traction", Modifier.weight(1f))
            }

            Spacer(Modifier.height(16.dp))

            DetailRow("Model", loco.model)
            DetailRow("Manufacturer", loco.manufacturer)
            DetailRow("Introduced", loco.introduced.toString())
            DetailRow("Horsepower", "${loco.horsepower.toLocaleString()} hp")
            DetailRow("Wheel arrangement", loco.wheelArrangement)
            DetailRow("Traction", loco.tractionMotors)

            Spacer(Modifier.height(12.dp))
            Text("Operated by", color = TextMuted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                loco.railroads.forEach { rr ->
                    RailroadBadge(rr, modifier = Modifier.size(36.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Notes", color = TextMuted, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Text(loco.notes, color = TextSecondary, fontSize = 13.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
fun SpecCard(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier
        .clip(RoundedCornerShape(10.dp))
        .background(BgInput)
        .padding(10.dp)) {
        Text(value, color = RailBlue, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(label, color = TextMuted, fontSize = 11.sp)
    }
}

