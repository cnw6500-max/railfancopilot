package com.railfancopilot.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.play.core.review.ReviewManagerFactory
import com.railfancopilot.app.data.models.SymbolDecodeEntry
import com.railfancopilot.app.data.models.TrainSymbolDecodeResult
import com.railfancopilot.app.ui.components.*
import com.railfancopilot.app.ui.theme.*
import com.railfancopilot.app.viewmodel.RailFanViewModel

@Composable
fun DecoderScreen(vm: RailFanViewModel, onUpgrade: () -> Unit = {}) {
    val result by vm.decodeResult.collectAsState()
    val isDecoding by vm.isDecoding.collectAsState()
    val error by vm.decodeError.collectAsState()
    val history by vm.decodeHistory.collectAsState()
    val reviewRequested by vm.requestInAppReview.collectAsState()
    var inputText by remember { mutableStateOf("") }

    // Launch Play Store in-app review at milestone decode counts (5th, 25th)
    val context = LocalContext.current
    LaunchedEffect(reviewRequested) {
        if (!reviewRequested) return@LaunchedEffect
        vm.consumeInAppReviewRequest()
        try {
            val manager = ReviewManagerFactory.create(context)
            manager.requestReviewFlow().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val activity = context as? ComponentActivity ?: return@addOnCompleteListener
                    manager.launchReviewFlow(activity, task.result)
                }
            }
        } catch (_: Exception) { /* review is best-effort; silently ignore */ }
    }

    fun submit() { if (inputText.isNotBlank()) vm.decodeSymbol(inputText) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("AI Symbol Decoder", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Text("Enter any train symbol for instant analysis", color = TextMuted, fontSize = 13.sp)
            }
        }

        item {
            Text(
                "Format: PRIORITY-ORIGIN+DESTINATION-TRAIN# (e.g. Q-CHISBD-11)",
                color = TextMuted, fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 0.dp)
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it.uppercase(); vm.clearDecodeError() },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("e.g. Q-CHISBD-11", color = TextMuted, fontFamily = FontFamily.Monospace) },
                    textStyle = LocalTextStyle.current.copy(
                        color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 15.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RailBlueMid,
                        unfocusedBorderColor = BorderLight,
                        cursorColor = RailBlue
                    ),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() })
                )
                Button(
                    onClick = { submit() },
                    enabled = !isDecoding && inputText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = RailBlueDark),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(56.dp)
                ) {
                    if (isDecoding) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp, color = RailBlue)
                    } else {
                        Text("Decode", color = RailBlue)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = RailBlue,
                            modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        error?.let {
            item {
                AlertBanner(it, RailRed, AlertBgDanger, AlertBorderDanger)
            }
        }

        result?.let { res ->
            item { DecodeResultCard(res) }
        }

        if (result == null && error == null && !isDecoding) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgCard)
                        .border(0.5.dp, BorderLight, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.DirectionsRailway,
                        contentDescription = null,
                        tint = TextMuted.copy(alpha = 0.4f),
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        "Enter a train symbol above to decode it",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        if (history.isEmpty()) "Recent decodes" else "Recent decodes (${history.size})",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    if (history.isNotEmpty()) {
                        IconButton(
                            onClick = { vm.clearDecodeHistory() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "Clear all history",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                if (history.isEmpty()) {
                    Text(
                        "No recent decodes — enter a symbol above and tap Decode",
                        color = TextMuted.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                } else {
                    HistoryChips(history = history, onTap = { entry ->
                        inputText = entry.symbol
                        vm.decodeSymbol(entry.symbol)
                    }, onDelete = { entry ->
                        vm.deleteSymbolDecodeEntry(entry)
                    })
                }
            }
        }

        item { Spacer(Modifier.height(100.dp)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryChips(
    history: List<SymbolDecodeEntry>,
    onTap: (SymbolDecodeEntry) -> Unit,
    onDelete: (SymbolDecodeEntry) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        history.forEach { entry ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(BgInput)
                    .border(0.5.dp, BorderLight, RoundedCornerShape(16.dp))
                    .combinedClickable(
                        onClick = { onTap(entry) },
                        onLongClick = { onDelete(entry) }
                    )
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        entry.symbol,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    if (entry.railroad.isNotBlank() && entry.railroad != "OTHER") {
                        Text(
                            entry.railroad,
                            color = TextMuted,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DecodeResultCard(result: TrainSymbolDecodeResult) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(0.5.dp, BorderLight, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RailroadBadge(result.railroad, modifier = Modifier.size(40.dp))
            Column {
                Text(result.symbol, color = RailBlue, fontSize = 18.sp,
                    fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
                Text(result.type, color = TextMuted, fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            PriorityBadge(result.priority)
            // Share result
            IconButton(
                onClick = {
                    val shareText = buildString {
                        appendLine("🚂 Train Symbol: ${result.symbol}")
                        appendLine("Type: ${result.type}")
                        appendLine("Origin: ${result.origin}")
                        appendLine("Destination: ${result.destination}")
                        appendLine("Railroad: ${result.railroad.displayName}")
                        appendLine("Schedule: ${result.schedule}")
                        if (result.notes.isNotBlank()) appendLine("Notes: ${result.notes}")
                        appendLine()
                        append("Decoded with Railfan Copilot")
                    }
                    context.startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }, "Share train symbol"
                    ))
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = "Share decode result",
                    tint = TextMuted, modifier = Modifier.size(18.dp))
            }
        }

        Column {
            ResultRow("Origin", result.origin)
            ResultRow("Destination", result.destination)
            ResultRow("Schedule", result.schedule)
            ResultRow("Railroad", result.railroad.displayName)
        }

        if (result.typicalConsist.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Typical Consist", color = TextMuted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    result.typicalConsist.forEach { loco ->
                        Box(modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(BgInput)
                            .border(0.5.dp, BorderLight, RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)) {
                            Text(loco, color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        if (result.notes.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Notes", color = TextMuted, fontSize = 12.sp)
                Text(result.notes, color = TextSecondary, fontSize = 13.sp, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
fun ResultRow(label: String, value: String) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top) {
        Text(label, color = TextMuted, fontSize = 13.sp, modifier = Modifier.width(100.dp))
        Text(value, color = TextPrimary, fontSize = 13.sp,
            modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
    HorizontalDivider(color = Border.copy(alpha = 0.4f), thickness = 0.5.dp)
}

@Composable
fun PriorityBadge(priority: String) {
    val (bg, fg) = when (priority.lowercase()) {
        "high"   -> StatusBgGreen   to RailGreen
        "medium" -> StatusBgNeutral to RailAmber
        else     -> StatusBgBlue    to TextSecondary
    }
    Box(modifier = Modifier
        .clip(RoundedCornerShape(6.dp))
        .background(bg)
        .padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(priority, color = fg, fontSize = 11.sp)
    }
}
