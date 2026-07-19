package com.railfancopilot.app.ui.screens

import android.app.Activity
import android.content.ContextWrapper
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.railfancopilot.app.ui.theme.*
import com.railfancopilot.app.viewmodel.RailFanViewModel

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private data class ProFeature(val icon: ImageVector, val title: String, val subtitle: String)

private val FREE_FEATURES = listOf(
    ProFeature(Icons.Default.SmartToy,            "AI Symbol Decoder",          "Decode any train symbol — origin, destination, consist. Always free."),
    ProFeature(Icons.Default.Map,                 "Live Train Map",             "Real-time Amtrak and commuter rail positions. Always free."),
    ProFeature(Icons.Default.Group,               "Community Feed",             "Browse sightings from railfans near you. Always free."),
)

private val PRO_FEATURES = listOf(
    ProFeature(Icons.Default.CameraAlt,           "AI Loco Identifier",         "Identify any locomotive from a photo using Claude AI"),
    ProFeature(Icons.Default.PhotoCamera,         "Photo Tagging & Enhancer",   "Tag photos with GPS + train data, enhance with railfan preset"),
    ProFeature(Icons.Default.Group,               "Submit Sightings",           "Post to the community feed with photo attachments"),
    ProFeature(Icons.Default.Bookmark,            "Unlimited Saved Locations",  "Save as many railfan spots as you want — free tier: 3"),
    ProFeature(Icons.Default.NotificationsActive, "Approach Notifications",     "Get alerted when a train is approaching your location"),
    ProFeature(Icons.Default.Train,               "Consist Analyzer",           "AI identifies every unit in a consist front-to-back"),
)

@Composable
fun UpgradeScreen(vm: RailFanViewModel, onBack: () -> Unit) {
    val context      = LocalContext.current
    val isPurchased  by vm.isPurchased.collectAsState()
    val isInTrial    by vm.isInTrial.collectAsState()
    val trialDaysLeft by vm.trialDaysLeft.collectAsState()

    // Auto-dismiss only when an actual purchase completes, not on trial state
    val wasNotPurchasedAtOpen = remember { !isPurchased }
    LaunchedEffect(isPurchased) {
        if (isPurchased && wasNotPurchasedAtOpen) onBack()
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(16.dp)) }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Railfan Copilot Pro",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "One-time purchase — unlock everything forever",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            }
        }

        // Trial banner
        if (isInTrial && !isPurchased) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(StatusBgGreen)
                        .border(0.5.dp, RailGreen.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Timer, contentDescription = null,
                        tint = RailGreen, modifier = Modifier.size(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Free trial active",
                            color = RailGreen,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (trialDaysLeft > 1) "$trialDaysLeft days remaining"
                            else if (trialDaysLeft == 1) "Last day — expires tomorrow"
                            else "Expires today",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Free tier
        item {
            Text("Always Free", color = RailBlue, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(BgCard)
                    .border(0.5.dp, BorderLight, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                FREE_FEATURES.forEachIndexed { index, feature ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(feature.icon, contentDescription = feature.title,
                            tint = RailBlue, modifier = Modifier.size(22.dp))
                        Column {
                            Text(feature.title, color = TextPrimary, fontSize = 14.sp,
                                fontWeight = FontWeight.Medium)
                            Text(feature.subtitle, color = TextMuted, fontSize = 12.sp)
                        }
                    }
                    if (index < FREE_FEATURES.lastIndex)
                        HorizontalDivider(color = Border.copy(alpha = 0.4f), thickness = 0.5.dp)
                }
            }
        }

        // Pro tier
        item {
            Text("Pro Features", color = RailAmber, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp, top = 4.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(BgCard)
                    .border(0.5.dp, BorderLight, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PRO_FEATURES.forEachIndexed { index, feature ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(feature.icon, contentDescription = feature.title,
                            tint = RailAmber, modifier = Modifier.size(22.dp))
                        Column {
                            Text(feature.title, color = TextPrimary, fontSize = 14.sp,
                                fontWeight = FontWeight.Medium)
                            Text(feature.subtitle, color = TextMuted, fontSize = 12.sp)
                        }
                    }
                    if (index < PRO_FEATURES.lastIndex)
                        HorizontalDivider(color = Border.copy(alpha = 0.4f), thickness = 0.5.dp)
                }
            }
        }

        item {
            Button(
                onClick = { vm.purchasePro(context.findActivity() ?: return@Button) },
                colors = ButtonDefaults.buttonColors(containerColor = RailBlueDark),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = RailAmber, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Upgrade to Pro — \$2.99",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        item {
            TextButton(
                onClick = { vm.restorePurchases() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Restore purchases", color = TextMuted, fontSize = 13.sp)
            }
        }

        item {
            Text(
                "One-time purchase — no subscription. Processed securely by Google Play.",
                color = TextMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }
    }
}
