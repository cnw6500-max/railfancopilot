package com.railfancopilot.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRailway
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.railfancopilot.app.data.models.Railroad
import com.railfancopilot.app.data.models.TrainLocation
import com.railfancopilot.app.data.models.TrainStatus
import com.railfancopilot.app.ui.theme.*

// ── Railroad color badge ──────────────────────────────────────────────────────

@Composable
fun RailroadBadge(railroad: Railroad, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(railroad.color)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = when (railroad) {
                Railroad.BNSF -> "BNSF"
                Railroad.UP -> "UP"
                Railroad.CSX -> "CSX"
                Railroad.NS -> "NS"
                Railroad.CN -> "CN"
                Railroad.CP -> "CP"
                Railroad.AMTRAK -> "AMT"
                Railroad.KCS -> "KCS"
                Railroad.OTHER -> "RR"
            },
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ── Train card ────────────────────────────────────────────────────────────────

@Composable
fun TrainCard(train: TrainLocation, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(0.5.dp, Border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RailroadBadge(train.railroad)
        Column(modifier = Modifier.weight(1f)) {
            Text(train.symbol, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("${train.origin} → ${train.destination}",
                color = TextMuted, fontSize = 12.sp, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${train.speedMph}", color = RailBlue, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Text("mph", color = TextMuted, fontSize = 10.sp)
            if (train.etaMinutes != null) {
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(StatusBgGreen)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("ETA ${train.etaMinutes}m", color = RailGreen, fontSize = 10.sp)
                }
            }
            if (train.status == TrainStatus.DELAYED) {
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(StatusBgAmber)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("Delayed", color = RailAmber, fontSize = 10.sp)
                }
            }
        }
    }
}

// ── Filter chip ───────────────────────────────────────────────────────────────

@Composable
fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) RailBlueDark else BgInput)
            .border(0.5.dp, if (selected) RailBlueMid else BorderLight, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(label, color = if (selected) RailBlue else TextSecondary, fontSize = 12.sp)
    }
}

// ── Section header ────────────────────────────────────────────────────────────

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        color = TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

// ── Alert banner ──────────────────────────────────────────────────────────────

@Composable
fun AlertBanner(message: String, color: Color, bgColor: Color, borderColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(0.5.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.Warning, contentDescription = "Warning", tint = color, modifier = Modifier.size(16.dp))
        Text(message, color = color, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
    }
}

// ── Live indicator dot ────────────────────────────────────────────────────────

@Composable
fun LiveDot(isLive: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(if (isLive) RailGreen else LiveDotInactive)
    )
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
fun EmptyState(
    icon: ImageVector = Icons.Default.DirectionsRailway,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, tint = TextMuted.copy(alpha = 0.4f), modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(4.dp))
        Text(title, color = TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(subtitle, color = TextMuted, fontSize = 13.sp,
            textAlign = TextAlign.Center, lineHeight = 20.sp)
    }
}

// ── GPS waiting card ──────────────────────────────────────────────────────────

@Composable
fun GpsWaitingCard(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(0.5.dp, Border, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = RailBlue)
        Column {
            Text("Acquiring GPS signal…", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("Train data loads once your location is confirmed", color = TextMuted, fontSize = 12.sp)
        }
    }
}

// ── Screen scaffold ───────────────────────────────────────────────────────────

@Composable
fun ScreenScaffold(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp)) {
            Text(title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null)
                Text(subtitle, color = TextMuted, fontSize = 13.sp)
        }
        content()
    }
}
