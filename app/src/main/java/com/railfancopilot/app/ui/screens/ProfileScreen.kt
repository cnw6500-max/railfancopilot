package com.railfancopilot.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.railfancopilot.app.data.models.FollowEntry
import com.railfancopilot.app.ui.components.SectionHeader
import com.railfancopilot.app.ui.theme.*
import com.railfancopilot.app.viewmodel.RailFanViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class FollowListMode { FOLLOWERS, FOLLOWING }

@Composable
fun ProfileScreen(vm: RailFanViewModel, uid: String, onBack: () -> Unit, onNavigateToProfile: (String) -> Unit = {}) {
    val viewedProfile          by vm.viewedProfile.collectAsState()
    val currentUserId          by vm.currentUserId.collectAsState()
    val following              by vm.following.collectAsState()
    val viewedFollowers        by vm.viewedFollowers.collectAsState()
    val viewedFollowing        by vm.viewedFollowing.collectAsState()
    val isFollowActionPending  by vm.isFollowActionPending.collectAsState()
    val achievements           by vm.achievements.collectAsState()
    val usernameClaimResult    by vm.usernameClaimResult.collectAsState()
    val isClaimingUsername     by vm.isClaimingUsername.collectAsState()
    var usernameDraft by remember { mutableStateOf("") }
    var followListMode by remember { mutableStateOf<FollowListMode?>(null) }

    val isOwnProfile = uid.isNotBlank() && uid == currentUserId
    val isFollowing = following.any { it.uid == uid }

    LaunchedEffect(uid) { vm.loadProfile(uid) }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 100.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Text(
                    if (isOwnProfile) "My profile" else "Profile",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        val profile = viewedProfile
        if (profile == null) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = RailBlue, modifier = Modifier.size(28.dp))
                }
            }
            return@LazyColumn
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(BgCard)
                    .border(0.5.dp, Border, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(64.dp).clip(CircleShape).background(RailBlueDark),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        profile.displayName.ifBlank { profile.username }.take(2).ifEmpty { "?" }.uppercase(),
                        color = RailBlue,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(profile.displayName.ifBlank { "Railfan" }, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                if (profile.username.isNotBlank()) {
                    Text("@${profile.username}", color = TextMuted, fontSize = 13.sp)
                }
                if (profile.joinedMs > 0) {
                    Text(
                        "Joined ${SimpleDateFormat("MMM yyyy", Locale.US).format(Date(profile.joinedMs))}",
                        color = TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    ProfileStat(profile.sightingCount.toString(), "Sightings")
                    ProfileStat(
                        profile.followerCount.toString(), "Followers",
                        onClick = { followListMode = FollowListMode.FOLLOWERS }
                    )
                    ProfileStat(
                        profile.followingCount.toString(), "Following",
                        onClick = { followListMode = FollowListMode.FOLLOWING }
                    )
                }

                if (!isOwnProfile && profile.username.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { if (isFollowing) vm.unfollowUser(uid) else vm.followUser(profile) },
                        enabled = !isFollowActionPending,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFollowing) BgInput else RailBlue,
                            contentColor = if (isFollowing) TextPrimary else Color.White
                        )
                    ) {
                        Text(if (isFollowing) "Following" else "Follow")
                    }
                }

                if (isOwnProfile && profile.username.isBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Claim a username to complete your profile",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = usernameDraft,
                            onValueChange = {
                                usernameDraft = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' }.take(20)
                                vm.clearUsernameClaimResult()
                            },
                            label = { Text("Username", color = TextMuted, fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.width(180.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = RailBlue,
                                unfocusedBorderColor = TextMuted,
                            )
                        )
                        Button(
                            onClick = { vm.claimUsername(usernameDraft) },
                            enabled = !isClaimingUsername && usernameDraft.length >= 3,
                            colors = ButtonDefaults.buttonColors(containerColor = RailBlue, contentColor = Color.White)
                        ) {
                            Text(if (isClaimingUsername) "Claiming…" else "Claim")
                        }
                    }
                    val (feedbackText, feedbackColor) = when (usernameClaimResult) {
                        is com.railfancopilot.shared.models.UsernameClaimResult.Success ->
                            "Username claimed!" to RailGreen
                        is com.railfancopilot.shared.models.UsernameClaimResult.Taken ->
                            "That username is already taken" to RailRed
                        is com.railfancopilot.shared.models.UsernameClaimResult.InvalidFormat ->
                            "3–20 letters, numbers, or underscores" to RailRed
                        is com.railfancopilot.shared.models.UsernameClaimResult.Error ->
                            "Couldn't claim username — try again" to RailRed
                        null -> null to TextMuted
                    }
                    if (feedbackText != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(feedbackText, color = feedbackColor, fontSize = 12.sp)
                    }
                }
            }
        }

        if (isOwnProfile) {
            item { Spacer(Modifier.height(20.dp)) }
            item { SectionHeader("Achievements") }
            item { AchievementsCard(achievements) }
        }
    }

    followListMode?.let { mode ->
        FollowListSheet(
            mode = mode,
            entries = if (mode == FollowListMode.FOLLOWERS) viewedFollowers else viewedFollowing,
            onEntryClick = { entryUid ->
                followListMode = null
                onNavigateToProfile(entryUid)
            },
            onDismiss = { followListMode = null }
        )
    }
}

@Composable
private fun ProfileStat(value: String, label: String, onClick: (() -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (onClick != null) {
            Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(4.dp)
        } else Modifier
    ) {
        Text(value, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Text(label, color = TextMuted, fontSize = 11.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FollowListSheet(
    mode: FollowListMode,
    entries: List<FollowEntry>,
    onEntryClick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = BgCard, tonalElevation = 0.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                if (mode == FollowListMode.FOLLOWERS) "Followers (${entries.size})" else "Following (${entries.size})",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            if (entries.isEmpty()) {
                Text(
                    if (mode == FollowListMode.FOLLOWERS) "No followers yet" else "Not following anyone yet",
                    color = TextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(entries, key = { it.uid }) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEntryClick(entry.uid) }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(36.dp).clip(CircleShape).background(RailBlueDark),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    entry.displayName.ifBlank { entry.username }.take(2).ifEmpty { "?" }.uppercase(),
                                    color = RailBlue, fontSize = 12.sp, fontWeight = FontWeight.Medium
                                )
                            }
                            Column {
                                Text(entry.displayName.ifBlank { "Railfan" }, color = TextPrimary, fontSize = 14.sp)
                                if (entry.username.isNotBlank()) {
                                    Text("@${entry.username}", color = TextMuted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
