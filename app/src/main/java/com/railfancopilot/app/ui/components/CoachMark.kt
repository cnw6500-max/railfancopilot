package com.railfancopilot.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.railfancopilot.app.ui.theme.*
import com.railfancopilot.app.viewmodel.RailFanViewModel
import com.railfancopilot.shared.tutorial.TutorialStep

/**
 * Floating bottom banner that shows the first unseen step from [steps].
 * Uses a non-focusable Popup so the user can still interact with the screen below.
 */
@Composable
fun CoachMarkBanner(
    steps: List<TutorialStep>,
    vm: RailFanViewModel
) {
    val unseenSteps by vm.unseenTutorialSteps.collectAsState()
    val step = unseenSteps.firstOrNull { it in steps } ?: return

    Popup(
        alignment = Alignment.BottomCenter,
        properties = PopupProperties(focusable = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 80.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(BgCard)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        step.title,
                        color = RailBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        step.description,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { vm.markTutorialStepSeen(step) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss tip", tint = TextMuted)
                }
            }
        }
    }
}
