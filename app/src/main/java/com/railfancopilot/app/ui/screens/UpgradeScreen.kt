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

private val PRO_FEATURES = listOf(
    ProFeature(Icons.Default.SmartToy,            "AI Loco Identifier",         "Identify any locomotive from a photo using Claude AI"),
    ProFeature(Icons.Default.CameraAlt,           "Photo Tagging",              "Tag photos with GPS, train symbol, and locomotive data"),
    ProFeature(Icons.Default.Group,               "Community Reports",          "Submit train sightings and contribute to the community feed"),
    ProFeature(Icons.Default.Bookmark,            "Unlimited Saved Locations",  "Save as many railfan spots as you want — free tier: 3"),
    ProFeature(Icons.Default.NotificationsActive, "Approach Notifications",     "Get alerted when a train is approaching your location"),
)

@Composable
fun UpgradeScreen(vm: RailFanViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val isPro by vm.isProUser.collectAsState()

    // Auto-dismiss once purchase completes
    LaunchedEffect(isPro) {
        if (isPro) onBack()
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
                    "Unlock the full experience — one-time purchase",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            }
        }

        item {
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
                        Icon(feature.icon, contentDescription = feature.title, tint = RailBlue, modifier = Modifier.size(22.dp))
                        Column {
                            Text(feature.title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(feature.subtitle, color = TextMuted, fontSize = 12.sp)
                        }
                    }
                    if (index < PRO_FEATURES.lastIndex) {
                        HorizontalDivider(color = Border.copy(alpha = 0.4f), thickness = 0.5.dp)
                    }
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
