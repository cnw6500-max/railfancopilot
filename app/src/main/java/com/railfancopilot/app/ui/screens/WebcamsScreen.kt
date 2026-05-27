package com.railfancopilot.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.railfancopilot.app.data.models.ALL_RAILCAMS
import com.railfancopilot.app.data.models.RailcamEntry
import com.railfancopilot.app.ui.theme.*

@Composable
fun WebcamsScreen() {
    val context = LocalContext.current
    var filter by remember { mutableStateOf("") }

    val filtered = remember(filter) {
        if (filter.isBlank()) ALL_RAILCAMS
        else ALL_RAILCAMS.filter {
            filter.lowercase() in it.railroad.lowercase() ||
            filter.lowercase() in it.location.lowercase() ||
            filter.lowercase() in it.name.lowercase()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgCard)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text("Railroad Webcams", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("Live cams at famous spots — tap to open in browser", color = TextMuted, fontSize = 13.sp)

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Filter by railroad or location…", color = TextMuted, fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (filter.isNotEmpty()) {
                        IconButton(onClick = { filter = "" }) {
                            Icon(Icons.Default.Clear, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RailBlueMid,
                    unfocusedBorderColor = BorderLight,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = RailBlue,
                    focusedContainerColor = BgInput,
                    unfocusedContainerColor = BgInput
                ),
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
            )
        }

        HorizontalDivider(color = Border, thickness = 0.5.dp)

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (filtered.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.VideocamOff, null,
                                tint = TextMuted, modifier = Modifier.size(36.dp))
                            Text("No cams match \"$filter\"", color = TextMuted, fontSize = 14.sp)
                        }
                    }
                }
            } else {
                items(filtered, key = { it.id }) { cam ->
                    WebcamCard(cam) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cam.url))
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, "No browser found to open link", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(100.dp)) }
        }
    }
}

@Composable
private fun WebcamCard(cam: RailcamEntry, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(0.5.dp, Border, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Videocam, null,
                tint = RailBlue, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(cam.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(cam.location, color = TextMuted, fontSize = 12.sp)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RailroadTag(cam.railroad)
            SubdivisionTag(cam.subdivision)
        }

        Text(cam.description, color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)

        Button(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = RailBlueMid.copy(alpha = 0.18f),
                contentColor = RailBlue
            ),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            Icon(Icons.Default.OpenInBrowser, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Open Webcam", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun RailroadTag(railroad: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(RailBlue.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(railroad, color = RailBlueMid, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SubdivisionTag(subdivision: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(BorderLight.copy(alpha = 0.3f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            subdivision.take(30) + if (subdivision.length > 30) "…" else "",
            color = TextMuted, fontSize = 11.sp
        )
    }
}
