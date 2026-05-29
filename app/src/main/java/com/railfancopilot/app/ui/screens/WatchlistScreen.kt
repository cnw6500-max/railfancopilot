package com.railfancopilot.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.railfancopilot.app.data.models.WatchlistEntry
import com.railfancopilot.app.data.models.WatchlistType
import com.railfancopilot.app.ui.theme.*
import com.railfancopilot.app.viewmodel.RailFanViewModel
import java.util.UUID

@Composable
fun WatchlistScreen(vm: RailFanViewModel) {
    val watchlist  by vm.watchlist.collectAsState()
    val userId     by vm.currentUserId.collectAsState()
    val authFailed by vm.authFailed.collectAsState()
    var showAdd    by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Watchlist", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Get notified when spotted by the community",
                        color = TextMuted, fontSize = 12.sp
                    )
                }
                IconButton(
                    onClick = { showAdd = true },
                    enabled = userId != null
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = RailBlue)
                }
            }

            HorizontalDivider(color = Border)

            if (authFailed) {
                WatchlistAuthErrorState(onRetry = { vm.retryAuth() })
            } else if (watchlist.isEmpty()) {
                WatchlistEmptyState(onAdd = { showAdd = true }, authReady = userId != null)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(watchlist, key = { it.id }) { entry ->
                        WatchlistCard(
                            entry = entry,
                            onDelete = { vm.removeFromWatchlist(entry.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddWatchlistDialog(
            onDismiss = { showAdd = false },
            onAdd = { entry ->
                vm.addToWatchlist(entry)
                showAdd = false
            }
        )
    }
}

@Composable
private fun WatchlistAuthErrorState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("⚠️", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Could not connect",
            color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Watchlist requires a network connection. Check your connection and tap Retry.",
            color = TextSecondary, fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = RailBlue)
        ) {
            Text("Retry", color = Color.White)
        }
    }
}

@Composable
private fun WatchlistEmptyState(onAdd: () -> Unit, authReady: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🔍", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Nothing on watch",
            color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Add a train symbol or road number. You'll get a push notification the moment someone spots it.",
            color = TextSecondary, fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onAdd,
            enabled = authReady,
            colors = ButtonDefaults.buttonColors(containerColor = RailBlue)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Add to Watchlist", color = Color.White)
        }
        if (!authReady) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Connecting…", color = TextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun WatchlistCard(entry: WatchlistEntry, onDelete: () -> Unit) {
    val isSymbol = entry.type == WatchlistType.SYMBOL
    val badgeColor = if (isSymbol) RailBlue else RailAmber
    val badgeBg = if (isSymbol) RailBlueDark else Color(0xFF3A2200)
    val badgeLabel = if (isSymbol) "SYMBOL" else "LOCO #"
    val icon = if (isSymbol) Icons.Default.Train else Icons.Default.Numbers
    val displayLabel = entry.label.ifBlank {
        if (isSymbol) entry.value
        else buildString {
            append(entry.value)
            if (entry.railroad.isNotBlank()) append(" · ${entry.railroad}")
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgCard, RoundedCornerShape(12.dp))
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Type badge
        Box(
            modifier = Modifier
                .background(badgeBg, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                displayLabel,
                color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                badgeLabel,
                color = TextMuted, fontSize = 11.sp
            )
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = RailRed, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AddWatchlistDialog(
    onDismiss: () -> Unit,
    onAdd: (WatchlistEntry) -> Unit
) {
    var type      by remember { mutableStateOf(WatchlistType.SYMBOL) }
    var value     by remember { mutableStateOf("") }
    var railroad  by remember { mutableStateOf("") }
    val isValid   = value.trim().isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        title = {
            Text("Add to Watchlist", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

                // Type selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgInput, RoundedCornerShape(10.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    WatchlistTypeChip(
                        label = "Train Symbol",
                        selected = type == WatchlistType.SYMBOL,
                        modifier = Modifier.weight(1f),
                        onClick = { type = WatchlistType.SYMBOL }
                    )
                    WatchlistTypeChip(
                        label = "Road Number",
                        selected = type == WatchlistType.LOCO,
                        modifier = Modifier.weight(1f),
                        onClick = { type = WatchlistType.LOCO }
                    )
                }

                // Value field
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.uppercase() },
                    label = {
                        Text(
                            if (type == WatchlistType.SYMBOL) "Symbol (e.g. QCHLA)" else "Road Number (e.g. 4030)",
                            fontSize = 12.sp
                        )
                    },
                    singleLine = true,
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

                // Railroad field (only for LOCO type)
                if (type == WatchlistType.LOCO) {
                    OutlinedTextField(
                        value = railroad,
                        onValueChange = { railroad = it.uppercase() },
                        label = { Text("Railroad (optional, e.g. BNSF)", fontSize = 12.sp) },
                        singleLine = true,
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

                Text(
                    if (type == WatchlistType.SYMBOL)
                        "You'll be notified when any community sighting includes this train symbol."
                    else
                        "You'll be notified when any community sighting mentions this road number.",
                    color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!isValid) return@Button
                    val v = value.trim().uppercase()
                    val rr = railroad.trim().uppercase()
                    val label = if (type == WatchlistType.SYMBOL) v
                                else buildString { append(v); if (rr.isNotBlank()) append(" · $rr") }
                    onAdd(
                        WatchlistEntry(
                            id       = UUID.randomUUID().toString(),
                            type     = type,
                            value    = v,
                            railroad = rr,
                            label    = label,
                            addedMs  = System.currentTimeMillis()
                        )
                    )
                },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(containerColor = RailBlue)
            ) {
                Text("Add", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}

@Composable
private fun WatchlistTypeChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (selected) RailBlueDark else Color.Transparent
    val textColor = if (selected) RailBlue else TextMuted

    Box(
        modifier = modifier
            .background(bg, RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(label, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}
