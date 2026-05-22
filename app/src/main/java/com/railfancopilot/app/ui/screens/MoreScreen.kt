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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.railfancopilot.app.ui.theme.*

private data class MoreItem(
    val route: String,
    val label: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconTint: androidx.compose.ui.graphics.Color
)

private val MORE_ITEMS = listOf(
    MoreItem("scanner",      "Scanner",      "Live railroad radio feeds",              Icons.Default.Radio,         RailBlue),
    MoreItem("decoder",      "Decoder",      "AI train symbol lookup",                 Icons.Default.SmartToy,      RailPurple),
    MoreItem("photo",        "Photo Tools",  "Loco ID, consist analyzer, enhancer",    Icons.Default.CameraAlt,     RailGreen),
    MoreItem("spots",        "Spots",        "Community & personal railfan locations",  Icons.Default.Place,         RailAmber),
    MoreItem("encyclopedia", "Roster",       "North American locomotive database",      Icons.Default.Book,          RailBlue),
    MoreItem("settings",     "Settings",     "App preferences & railroad toggles",      Icons.Default.Settings,      TextMuted),
)

@Composable
fun MoreScreen(onNavigate: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
    ) {
        Text(
            "More",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        )
        HorizontalDivider(color = Border)
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(MORE_ITEMS) { item ->
                MoreRow(item = item, onClick = { onNavigate(item.route) })
            }
        }
    }
}

@Composable
private fun MoreRow(item: MoreItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgCard, RoundedCornerShape(12.dp))
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(BgInput, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(item.icon, contentDescription = null, tint = item.iconTint, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(item.subtitle, color = TextMuted, fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
    }
}
