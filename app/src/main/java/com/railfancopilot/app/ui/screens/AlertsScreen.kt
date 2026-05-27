package com.railfancopilot.app.ui.screens

import android.location.Location
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.railfancopilot.app.data.models.RailAlert
import com.railfancopilot.app.data.models.RailAlertType
import com.railfancopilot.app.ui.components.EmptyState
import com.railfancopilot.app.ui.components.SectionHeader
import com.railfancopilot.app.ui.theme.*
import com.railfancopilot.app.viewmodel.RailFanViewModel
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@Composable
fun AlertsScreen(vm: RailFanViewModel) {
    val alerts       by vm.railAlerts.collectAsState()
    val rareLoco     by vm.alertRareLoco.collectAsState()
    val hotTrain     by vm.alertHotTrain.collectAsState()
    val highSpeed    by vm.alertHighSpeed.collectAsState()
    val scanner      by vm.alertScanner.collectAsState()
    val approaching  by vm.alertApproaching.collectAsState()
    val heritage     by vm.alertHeritage.collectAsState()
    val userLocation by vm.userLocation.collectAsState()

    LaunchedEffect(Unit) { vm.markAllAlertsRead() }

    LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {

        // ── Header ────────────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Railfan Alerts", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (alerts.isEmpty()) "No alerts yet" else "${alerts.size} alert${if (alerts.size == 1) "" else "s"}",
                        color = TextMuted, fontSize = 12.sp
                    )
                }
                if (alerts.isNotEmpty()) {
                    TextButton(onClick = { vm.clearAlerts() }) {
                        Text("Clear all", color = TextMuted, fontSize = 12.sp)
                    }
                }
            }
        }

        // ── Alert type toggles ────────────────────────────────────────────────
        item { SectionHeader("Alert Types") }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgCard)
                    .border(0.5.dp, Border, RoundedCornerShape(12.dp))
            ) {
                AlertToggleRow(
                    emoji = "⭐", label = "Rare Locomotives",
                    subtitle = "Heritage units, foreign power, rebuilds",
                    checked = rareLoco, onCheckedChange = vm::setAlertRareLoco
                )
                HorizontalDivider(color = Border, thickness = 0.5.dp)
                AlertToggleRow(
                    emoji = "🔥", label = "Hot Trains",
                    subtitle = "Z trains, priority intermodal, hotshots",
                    checked = hotTrain, onCheckedChange = vm::setAlertHotTrain
                )
                HorizontalDivider(color = Border, thickness = 0.5.dp)
                AlertToggleRow(
                    emoji = "⚡", label = "High Speed",
                    subtitle = "Trains exceeding 79 mph on live data",
                    checked = highSpeed, onCheckedChange = vm::setAlertHighSpeed
                )
                HorizontalDivider(color = Border, thickness = 0.5.dp)
                AlertToggleRow(
                    emoji = "📻", label = "Scanner Activity",
                    subtitle = "Transmissions you log on the scanner",
                    checked = scanner, onCheckedChange = vm::setAlertScanner
                )
                HorizontalDivider(color = Border, thickness = 0.5.dp)
                AlertToggleRow(
                    emoji = "🚂", label = "Train Approaching",
                    subtitle = "Trains within your ETA threshold",
                    checked = approaching, onCheckedChange = vm::setAlertApproaching
                )
                HorizontalDivider(color = Border, thickness = 0.5.dp)
                AlertToggleRow(
                    emoji = "🏆", label = "Heritage & Special Units",
                    subtitle = "Verified heritage liveries, presidential units, rare steam",
                    checked = heritage, onCheckedChange = vm::setAlertHeritage,
                    isLast = true
                )
            }
        }

        // ── Alert feed ────────────────────────────────────────────────────────
        if (alerts.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.NotificationsNone,
                    title = "No alerts yet",
                    subtitle = "Enable alert types above and keep the app open — alerts appear here in real time"
                )
            }
        } else {
            item { SectionHeader("Recent (${alerts.size})") }
            items(alerts, key = { it.id }) { alert ->
                AlertCard(alert = alert, userLocation = userLocation)
            }
        }
    }
}

@Composable
private fun AlertToggleRow(
    emoji: String,
    label: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isLast: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = if (isLast) 10.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(emoji, fontSize = 18.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextMuted, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor  = RailBlue,
                checkedTrackColor  = RailBlueDark,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = BgInput
            )
        )
    }
}

@Composable
fun AlertCard(alert: RailAlert, userLocation: android.location.Location? = null) {
    val ago = remember(alert.timestampMs) {
        val diff = System.currentTimeMillis() - alert.timestampMs
        when {
            diff < TimeUnit.MINUTES.toMillis(60) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
            diff < TimeUnit.HOURS.toMillis(24)   -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
            else -> "${TimeUnit.MILLISECONDS.toDays(diff)}d ago"
        }
    }
    val distLabel = remember(userLocation, alert.latitude, alert.longitude) {
        val lat = alert.latitude ?: return@remember null
        val lon = alert.longitude ?: return@remember null
        if (userLocation == null) return@remember null
        val r = FloatArray(1)
        Location.distanceBetween(userLocation.latitude, userLocation.longitude, lat, lon, r)
        val mi = r[0] / 1609.34
        when {
            mi < 0.1 -> "< 0.1 mi"
            mi < 10  -> String.format("%.1f mi", mi)
            else     -> "${mi.roundToInt()} mi"
        }
    }

    val (accent, bg) = when (alert.type) {
        RailAlertType.HOT_TRAIN        -> RailRed                        to AlertBgDanger
        RailAlertType.RARE_LOCO        -> RailAmber                      to AlertBgWarning
        RailAlertType.HIGH_SPEED       -> RailBlue                       to RailBlueDark
        RailAlertType.SCANNER_ACTIVITY -> RailGreen                      to StatusBgGreen
        RailAlertType.TRAIN_APPROACHING-> RailAmber                      to AlertBgWarning
        RailAlertType.HERITAGE_UNIT    -> androidx.compose.ui.graphics.Color(0xFFFFD700) to androidx.compose.ui.graphics.Color(0xFF2A2000)
        RailAlertType.SPECIAL_MOVE     -> RailPurple                     to androidx.compose.ui.graphics.Color(0xFF1A0A2A)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(0.5.dp, Border, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Type badge
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            Text(alert.type.emoji, fontSize = 17.sp)
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(alert.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false))
                if (alert.isVerified) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(androidx.compose.ui.graphics.Color(0xFF2A2000))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text("✓ Verified", color = androidx.compose.ui.graphics.Color(0xFFFFD700),
                            fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (!alert.isRead) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(RailBlue)
                    )
                }
            }
            alert.heritageName?.let { scheme ->
                Text(scheme, color = androidx.compose.ui.graphics.Color(0xFFFFD700),
                    fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(2.dp))
            Text(alert.message, color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                alert.trainSymbol?.let { symbol ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(bg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(symbol, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    }
                }
                if (distLabel != null) {
                    Text(distLabel, color = TextMuted, fontSize = 10.sp)
                    Text("·", color = TextMuted, fontSize = 10.sp)
                }
                Text(ago, color = TextMuted, fontSize = 10.sp)
            }
        }
    }
}

// ── In-app alert banner (shown in RailFanApp overlay) ────────────────────────

@Composable
fun RailAlertBanner(alert: RailAlert, onDismiss: () -> Unit) {
    LaunchedEffect(alert.id) {
        kotlinx.coroutines.delay(4_000)
        onDismiss()
    }

    val (accent, bg) = when (alert.type) {
        RailAlertType.HOT_TRAIN        -> RailRed                        to AlertBgDanger
        RailAlertType.RARE_LOCO        -> RailAmber                      to AlertBgWarning
        RailAlertType.HIGH_SPEED       -> RailBlue                       to RailBlueDark
        RailAlertType.SCANNER_ACTIVITY -> RailGreen                      to StatusBgGreen
        RailAlertType.TRAIN_APPROACHING-> RailAmber                      to AlertBgWarning
        RailAlertType.HERITAGE_UNIT    -> androidx.compose.ui.graphics.Color(0xFFFFD700) to androidx.compose.ui.graphics.Color(0xFF2A2000)
        RailAlertType.SPECIAL_MOVE     -> RailPurple                     to androidx.compose.ui.graphics.Color(0xFF1A0A2A)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            Text(alert.type.emoji, fontSize = 15.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(alert.title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(alert.message.take(60), color = TextSecondary, fontSize = 11.sp, maxLines = 1)
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Close, null, tint = TextMuted, modifier = Modifier.size(14.dp))
        }
    }
}
