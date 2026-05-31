package com.railfancopilot.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.railfancopilot.app.ui.theme.*

private data class OnboardingPage(
    val icon: ImageVector,
    val accentColor: Color,
    val title: String,
    val body: String
)

private val ONBOARDING_PAGES = listOf(
    OnboardingPage(
        icon = Icons.Default.DirectionsRailway,
        accentColor = Color(0xFF3B82F6),
        title = "Welcome to Railfan Copilot",
        body = "Your all-in-one companion for tracking trains, identifying equipment, and connecting with the railfan community."
    ),
    OnboardingPage(
        icon = Icons.Default.Map,
        accentColor = Color(0xFF10B981),
        title = "Live Train Tracking",
        body = "Follow Amtrak and commuter rail trains in real time on an interactive map. Get proximity alerts when trains approach your saved locations."
    ),
    OnboardingPage(
        icon = Icons.Default.Group,
        accentColor = Color(0xFFF59E0B),
        title = "Community Sightings",
        body = "Log train sightings and see what other railfans spotted nearby — with photos, consist details, and real-time updates across Android and iOS."
    ),
    OnboardingPage(
        icon = Icons.Default.SmartToy,
        accentColor = Color(0xFF8B5CF6),
        title = "Try the AI Decoder — Free",
        body = "Type any train symbol and get an instant AI breakdown of origin, destination, schedule, and typical consist. No subscription needed."
    )
)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OnboardingOverlay(onFinish: () -> Unit, onTryDecoder: () -> Unit = onFinish) {
    var page by remember { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(initialPage = 0) { ONBOARDING_PAGES.size }

    // Keep pager and button-driven page index in sync
    LaunchedEffect(pagerState.currentPage) { page = pagerState.currentPage }
    LaunchedEffect(page) {
        if (pagerState.currentPage != page) pagerState.animateScrollToPage(page)
    }

    val current = ONBOARDING_PAGES[page]
    val isLast = page == ONBOARDING_PAGES.lastIndex

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF08101A))
    ) {
        // Subtle radial glow behind icon
        Box(
            modifier = Modifier
                .size(320.dp)
                .align(Alignment.Center)
                .offset(y = (-80).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(current.accentColor.copy(alpha = 0.12f), Color.Transparent)
                    )
                )
        )

        // Skip button — top right
        TextButton(
            onClick = onFinish,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 8.dp)
                .statusBarsPadding()
        ) {
            Text("Skip", color = TextMuted, fontSize = 14.sp)
        }

        // Swipeable page content — centred vertically
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val pageData = ONBOARDING_PAGES[pageIndex]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Icon badge
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(pageData.accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        pageData.icon,
                        contentDescription = null,
                        tint = pageData.accentColor,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(Modifier.height(36.dp))

                Text(
                    pageData.title,
                    color = TextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 34.sp
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    pageData.body,
                    color = TextSecondary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )
            }
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 32.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Page indicator dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ONBOARDING_PAGES.indices.forEach { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == page) 24.dp else 8.dp, 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == page) current.accentColor
                                else Color(0xFF1E2A3A)
                            )
                    )
                }
            }

            // Next / CTA button
            Button(
                onClick = { if (isLast) onTryDecoder() else page++ },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = current.accentColor),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
            ) {
                Text(
                    if (isLast) "Try the Decoder Now" else "Next",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }

            // Skip to app on non-last pages
            if (isLast) {
                TextButton(onClick = onFinish) {
                    Text("Skip to app", color = Color(0xFF6B7280), fontSize = 13.sp)
                }
            }
        }
    }
}
