package com.railfancopilot.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.railfancopilot.app.ui.components.SectionHeader
import com.railfancopilot.app.ui.theme.*
import com.railfancopilot.app.viewmodel.RailFanViewModel
import android.app.Activity
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import kotlin.math.roundToInt

private val FAQ = listOf(
    "How often does train data update?" to
        "Trains refresh on the interval you set (15–120 s). Commuter feeds like MBTA and SEPTA update in near-real-time; Amtrak typically updates every 1–2 minutes on their end.",
    "Why do I see no trains on the map?" to
        "Make sure location permission is granted and your GPS has a fix. The app only fetches trains within your configured radius (default 500 mi). Try tapping the refresh button on the map.",
    "How does the AI Decoder work?" to
        "It sends your symbol to Claude AI, which cross-references a built-in railroad symbol database to return origin, destination, train type, and notes. An Anthropic API key is required.",
    "Can I use the app without internet?" to
        "The map, community reports, and live train data all require internet. The frequency reference and saved locations work fully offline.",
    "Why can't I submit a community report?" to
        "The Submit button requires a GPS fix. Wait for the location indicator to show coordinates, then try again.",
    "How do I earn achievements?" to
        "Achievements unlock automatically as you use the app — identifying locomotives, spotting fast trains, visiting rail yards, and more. Check the Photo screen to see your progress.",
    "Why does the scanner use HTTP instead of HTTPS?" to
        "Live railroad radio streams from sources like railroadradio.net are broadcast over standard HTTP audio streams — the industry standard for public scanner feeds. The audio is not encrypted at the source. No personal data is transmitted over these connections.",
    "How do I get a refund?" to
        "Purchases can be refunded through Google Play within 48 hours. Open the Google Play Store, tap your profile, go to Payments & subscriptions → Order history, and select Request a refund."
)

@Composable
fun SettingsScreen(vm: RailFanViewModel, onUpgrade: () -> Unit = {}) {
    val context = LocalContext.current
    val isPro         by vm.isProUser.collectAsState()
    val isPurchased   by vm.isPurchased.collectAsState()
    val isInTrial     by vm.isInTrial.collectAsState()
    val trialDaysLeft by vm.trialDaysLeft.collectAsState()
    val refreshIntervalSec by vm.refreshIntervalSec.collectAsState()
    val trainRadiusMiles   by vm.trainRadiusMiles.collectAsState()
    val approachEtaMin     by vm.approachEtaMin.collectAsState()
    val reportRadiusMiles  by vm.reportRadiusMiles.collectAsState()
    val railOverlayDefault by vm.railOverlayDefault.collectAsState()
    val mbtaEnabled          by vm.mbtaEnabled.collectAsState()
    val septaEnabled         by vm.septaEnabled.collectAsState()
    val metraEnabled         by vm.metraEnabled.collectAsState()
    val mtaLirrEnabled       by vm.mtaLirrEnabled.collectAsState()
    val mtaMetroNorthEnabled by vm.mtaMetroNorthEnabled.collectAsState()
    val caltrainEnabled      by vm.caltrainEnabled.collectAsState()
    val soundTransitEnabled  by vm.soundTransitEnabled.collectAsState()
    val userName             by vm.userName.collectAsState()
    var userNameDraft by remember(userName) { mutableStateOf(userName) }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp)) {
                Text("Settings", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Text("Customize app behavior", color = TextMuted, fontSize = 13.sp)
            }
        }

        // ── Pro status ────────────────────────────────────────────────────────
        item { SectionHeader("Subscription") }

        item {
            SettingsCard {
                when {
                    isPurchased -> {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Star, null, tint = RailAmber, modifier = Modifier.size(20.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Railfan Copilot Pro", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("All Pro features unlocked", color = TextMuted, fontSize = 12.sp)
                            }
                            Icon(Icons.Default.CheckCircle, null, tint = RailGreen, modifier = Modifier.size(18.dp))
                        }
                    }
                    isInTrial -> {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Timer, null, tint = RailGreen, modifier = Modifier.size(20.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Free trial active", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    if (trialDaysLeft > 1) "$trialDaysLeft days remaining"
                                    else if (trialDaysLeft == 1) "Last day — expires tomorrow"
                                    else "Expires today",
                                    color = TextMuted, fontSize = 12.sp
                                )
                            }
                        }
                        SettingsDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onUpgrade() }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Star, null, tint = RailAmber, modifier = Modifier.size(20.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Unlock Pro permanently — \$2.99", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("Keep all features after your trial ends", color = TextMuted, fontSize = 12.sp)
                            }
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                    else -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onUpgrade() }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Star, null, tint = RailAmber, modifier = Modifier.size(20.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Upgrade to Pro — \$2.99", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("Loco ID, Photo Tagging, Community, unlimited spots", color = TextMuted, fontSize = 12.sp)
                            }
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                        SettingsDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.restorePurchases() }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Restore, null, tint = TextMuted, modifier = Modifier.size(20.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Restore purchases", color = TextSecondary, fontSize = 14.sp)
                                Text("Already purchased on another device?", color = TextMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // ── Community ──────────────────────────────────────────────────────────
        item { SectionHeader("Community") }

        item {
            SettingsCard {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Person, null, tint = RailBlue, modifier = Modifier.size(20.dp))
                    OutlinedTextField(
                        value = userNameDraft,
                        onValueChange = { userNameDraft = it },
                        label = { Text("Display name", color = TextMuted, fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = RailBlue,
                            unfocusedBorderColor = TextMuted,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { vm.saveUserName(userNameDraft) })
                    )
                }
            }
        }

        // ── Live trains ────────────────────────────────────────────────────────
        item { SectionHeader("Live trains") }

        item {
            SettingsCard {
                SliderSetting(
                    icon = Icons.Default.Refresh,
                    title = "Refresh interval",
                    subtitle = "How often to check for train updates",
                    value = refreshIntervalSec.toFloat(),
                    valueRange = 15f..120f,
                    steps = 6,    // 15, 30, 45, 60, 75, 90, 105, 120
                    displayValue = "${refreshIntervalSec}s",
                    onValueChangeFinished = { vm.saveRefreshInterval(it.roundToInt()) }
                )
                SettingsDivider()
                SliderSetting(
                    icon = Icons.Default.MyLocation,
                    title = "Nearby train radius",
                    subtitle = "Max distance to show Amtrak trains",
                    value = trainRadiusMiles.toFloat(),
                    valueRange = 100f..1000f,
                    steps = 8,
                    displayValue = "${trainRadiusMiles.roundToInt()} mi",
                    onValueChangeFinished = { vm.saveTrainRadius(it.toDouble()) }
                )
            }
        }

        // ── Notifications ──────────────────────────────────────────────────────
        item { SectionHeader("Notifications") }

        item {
            SettingsCard {
                if (isPro) {
                    SliderSetting(
                        icon = Icons.Default.NotificationsActive,
                        title = "Approach alert threshold",
                        subtitle = "Alert when a train is within this many minutes",
                        value = approachEtaMin.toFloat(),
                        valueRange = 5f..30f,
                        steps = 4,
                        displayValue = "${approachEtaMin} min",
                        onValueChangeFinished = { vm.saveApproachEta(it.roundToInt()) }
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onUpgrade() }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.NotificationsActive, null,
                            tint = TextMuted.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Approach alert threshold",
                                color = TextMuted.copy(alpha = 0.5f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Pro feature — tap to upgrade", color = TextMuted, fontSize = 12.sp)
                        }
                        Icon(Icons.Default.Lock, null, tint = RailAmber, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // ── Community ──────────────────────────────────────────────────────────
        item { SectionHeader("Community reports") }

        item {
            SettingsCard {
                SliderSetting(
                    icon = Icons.Default.Group,
                    title = "Default report radius",
                    subtitle = "Distance filter shown when opening Community",
                    value = if (reportRadiusMiles >= 999) 999f else reportRadiusMiles.toFloat(),
                    valueRange = 25f..999f,
                    steps = 0,
                    displayValue = if (reportRadiusMiles >= 999) "All" else "${reportRadiusMiles.roundToInt()} mi",
                    onValueChangeFinished = { vm.saveReportRadius(if (it >= 990f) 999.0 else it.toDouble()) }
                )
            }
        }

        // ── Map ────────────────────────────────────────────────────────────────
        item { SectionHeader("Map") }

        item {
            SettingsCard {
                SwitchSetting(
                    icon = Icons.Default.Map,
                    title = "Rail overlay on by default",
                    subtitle = "Show OpenRailwayMap lines when map opens",
                    checked = railOverlayDefault,
                    onCheckedChange = { vm.saveRailOverlayDefault(it) }
                )
            }
        }

        // ── Commuter rail feeds ────────────────────────────────────────────────
        item { SectionHeader("Commuter rail feeds") }

        // Northeast
        item {
            CommuterRegionLabel("Northeast")
        }
        item {
            SettingsCard {
                SwitchSetting(
                    icon = Icons.Default.Train,
                    title = "MBTA Commuter Rail",
                    subtitle = "Massachusetts · No account needed",
                    checked = mbtaEnabled,
                    onCheckedChange = { vm.saveMbtaEnabled(it) }
                )
                SettingsDivider()
                SwitchSetting(
                    icon = Icons.Default.Train,
                    title = "SEPTA Regional Rail",
                    subtitle = "Philadelphia area · No account needed",
                    checked = septaEnabled,
                    onCheckedChange = { vm.saveSeptaEnabled(it) }
                )
                SettingsDivider()
                SwitchSetting(
                    icon = Icons.Default.Train,
                    title = "MTA Long Island Rail Road",
                    subtitle = "New York · No account needed",
                    checked = mtaLirrEnabled,
                    onCheckedChange = { vm.saveMtaLirrEnabled(it) }
                )
                SettingsDivider()
                SwitchSetting(
                    icon = Icons.Default.Train,
                    title = "MTA Metro-North",
                    subtitle = "New York · No account needed",
                    checked = mtaMetroNorthEnabled,
                    onCheckedChange = { vm.saveMtaMetroNorthEnabled(it) }
                )
            }
        }

        // Midwest
        item {
            CommuterRegionLabel("Midwest")
        }
        item {
            SettingsCard {
                SwitchSetting(
                    icon = Icons.Default.Train,
                    title = "Metra",
                    subtitle = "Chicago area · Metra GTFS-RT",
                    checked = metraEnabled,
                    onCheckedChange = { vm.saveMetraEnabled(it) }
                )
            }
        }

        // West Coast
        item {
            CommuterRegionLabel("West Coast")
        }
        item {
            SettingsCard {
                SwitchSetting(
                    icon = Icons.Default.Train,
                    title = "Caltrain",
                    subtitle = "Bay Area · 511.org GTFS-RT",
                    checked = caltrainEnabled,
                    onCheckedChange = { vm.saveCaltrainEnabled(it) }
                )
                SettingsDivider()
                SwitchSetting(
                    icon = Icons.Default.Train,
                    title = "Sound Transit Sounder",
                    subtitle = "Seattle area · No account needed",
                    checked = soundTransitEnabled,
                    onCheckedChange = { vm.saveSoundTransitEnabled(it) }
                )
            }
        }

        // ── Support ───────────────────────────────────────────────────────────
        item { SectionHeader("Support") }

        item {
            SettingsCard {
                // Rate the app
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW,
                                Uri.parse("market://details?id=com.railfancopilot.app"))
                            try { context.startActivity(intent) }
                            catch (_: android.content.ActivityNotFoundException) {
                                context.startActivity(Intent(Intent.ACTION_VIEW,
                                    Uri.parse("https://play.google.com/store/apps/details?id=com.railfancopilot.app")))
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Star, null, tint = RailAmber, modifier = Modifier.size(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Rate Railfan Copilot", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Enjoying the app? Leave a review on Google Play", color = TextMuted, fontSize = 12.sp)
                    }
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }
        }

        // ── Help & FAQ ────────────────────────────────────────────────────────
        item { SectionHeader("Help & FAQ") }

        item {
            SettingsCard {
                FAQ.forEachIndexed { index, (question, answer) ->
                    FaqRow(question = question, answer = answer)
                    if (index < FAQ.lastIndex) SettingsDivider()
                }
            }
        }

        // ── Legal ──────────────────────────────────────────────────────────────
        item { SectionHeader("Legal & Privacy") }

        item {
            SettingsCard {
                LinkRow(
                    icon = Icons.Default.PrivacyTip,
                    title = "Privacy Policy",
                    subtitle = "How we collect and use your data",
                    url = "https://docs.google.com/document/d/12D9nfLIoCKgYhjIm2c2ljLvksKsm6n0alWkTI3IVoH8/edit?usp=sharing"
                )
                SettingsDivider()
                LinkRow(
                    icon = Icons.Default.Gavel,
                    title = "Terms of Service",
                    subtitle = "App usage terms and conditions",
                    url = "https://docs.google.com/document/d/12D9nfLIoCKgYhjIm2c2ljLvksKsm6n0alWkTI3IVoH8/edit?usp=sharing"
                )
                SettingsDivider()
                LinkRow(
                    icon = Icons.Default.CreditCard,
                    title = "Refund Policy",
                    subtitle = "Purchases can be refunded via Google Play within 48 hours",
                    url = "https://support.google.com/googleplay/answer/2479637"
                )
                SettingsDivider()
                LinkRow(
                    icon = Icons.Default.Security,
                    title = "Data & Privacy",
                    subtitle = "Location, photos, and scanner audio stay on your device or are sent only to Anthropic's Claude API for AI analysis. Community sightings and spots are stored in Firebase. An anonymous device ID is used for watchlist notifications.",
                    url = "https://docs.google.com/document/d/12D9nfLIoCKgYhjIm2c2ljLvksKsm6n0alWkTI3IVoH8/edit?usp=sharing"
                )
            }
        }

        // ── About ──────────────────────────────────────────────────────────────
        item { SectionHeader("About") }

        item {
            SettingsCard {
                InfoRow(Icons.Default.DirectionsRailway, "Railfan Copilot", "v${com.railfancopilot.app.BuildConfig.VERSION_NAME}")
                SettingsDivider()
                InfoRow(Icons.Default.Cloud, "Live train data", "Amtrak · MBTA · SEPTA · GTFS-RT")
                SettingsDivider()
                InfoRow(Icons.Default.Map, "Map tiles", "OpenRailwayMap / OpenStreetMap")
                SettingsDivider()
                InfoRow(Icons.Default.Radio, "Frequency reference", "RadioReference.com · AAR standard band")
                SettingsDivider()
                InfoRow(Icons.Default.SmartToy, "AI features", "Claude by Anthropic")
                SettingsDivider()
                InfoRow(Icons.Default.Router, "Scanner streams", "Audio via HTTP — required for live railroad radio feeds")
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

// ── Reusable setting composables ──────────────────────────────────────────────

@Composable
private fun CommuterRegionLabel(region: String) {
    Text(
        text = region,
        color = TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 24.dp, top = 10.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(0.5.dp, Border, RoundedCornerShape(12.dp))
            .padding(4.dp),
        content = content
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 12.dp),
        color = Border.copy(alpha = 0.6f),
        thickness = 0.5.dp
    )
}

@Composable
private fun SliderSetting(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    displayValue: String,
    onValueChangeFinished: (Float) -> Unit
) {
    var sliderValue by remember(value) { mutableStateOf(value) }

    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, null, tint = RailBlue, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, color = TextMuted, fontSize = 12.sp)
            }
            Text(
                displayValue,
                color = RailBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onValueChangeFinished(sliderValue) },
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = RailBlue,
                activeTrackColor = RailBlueMid,
                inactiveTrackColor = Border
            ),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun SwitchSetting(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null,
            tint = if (enabled) RailBlue else TextMuted.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title,
                color = if (enabled) TextPrimary else TextMuted.copy(alpha = 0.5f),
                fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextMuted, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = RailBlueMid,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = BgInput,
                disabledCheckedThumbColor = TextMuted.copy(alpha = 0.4f),
                disabledUncheckedThumbColor = TextMuted.copy(alpha = 0.3f),
                disabledCheckedTrackColor = BgInput,
                disabledUncheckedTrackColor = BgInput
            )
        )
    }
}

@Composable
private fun FaqRow(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.HelpOutline, null, tint = RailBlue, modifier = Modifier.size(18.dp))
            Text(question, color = TextPrimary, fontSize = 13.sp,
                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null, tint = TextMuted, modifier = Modifier.size(18.dp)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                answer,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(start = 28.dp, top = 6.dp)
            )
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, tint = TextMuted, modifier = Modifier.size(18.dp))
        Text(label, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, subtitle: String, url: String) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, tint = RailBlue, modifier = Modifier.size(18.dp).padding(top = 2.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp)
        }
        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = TextMuted, modifier = Modifier.size(14.dp).padding(top = 2.dp))
    }
}
