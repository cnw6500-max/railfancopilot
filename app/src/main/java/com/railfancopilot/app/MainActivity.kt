package com.railfancopilot.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.*
import com.railfancopilot.app.ui.screens.AlertsScreen
import com.railfancopilot.app.ui.screens.RailAlertBanner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.railfancopilot.app.ui.screens.*
import com.railfancopilot.app.ui.theme.*
import com.railfancopilot.app.viewmodel.RailFanViewModel

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Map          : Screen("map",          "Map",       Icons.Default.Map)
    object Scanner      : Screen("scanner",      "Scanner",   Icons.Default.Radio)
    object Decoder      : Screen("decoder",      "Decoder",   Icons.Default.SmartToy)
    object Photo        : Screen("photo",        "Photo",     Icons.Default.CameraAlt)
    object Community    : Screen("community",    "Community", Icons.Default.Group)
    object Alerts       : Screen("alerts",       "Alerts",    Icons.Default.NotificationsActive)
    object Watchlist       : Screen("watchlist",       "Watchlist", Icons.Default.Bookmarks)
    object Encyclopedia    : Screen("encyclopedia",    "Roster",    Icons.Default.Book)
    object SavedLocations  : Screen("saved_locations", "Saved",     Icons.Default.Bookmark)
    object Spots           : Screen("spots",           "Spots",     Icons.Default.Place)
    object Webcams         : Screen("webcams",         "Webcams",   Icons.Default.Videocam)
    object Settings        : Screen("settings",        "Settings",  Icons.Default.Settings)
    object Upgrade         : Screen("upgrade",         "Upgrade",   Icons.Default.Star)
    object More            : Screen("more",            "More",      Icons.Default.GridView)
}

private val bottomNavItems = listOf(
    Screen.Map, Screen.Community, Screen.Alerts, Screen.Watchlist, Screen.More
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("RAILFAN_CRASH", "FATAL: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
        try {
            enableEdgeToEdge()
            setContent { RailFanTheme { RailFanApp() } }
        } catch (e: Exception) {
            Log.e("RAILFAN_CRASH", "onCreate crash: ${e.message}", e)
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RailFanApp() {
    val navController = rememberNavController()
    val vm: RailFanViewModel = viewModel()
    val permissions = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.CAMERA
        )
    )
    var showPermissionDisclosure by remember { mutableStateOf(!permissions.allPermissionsGranted) }

    if (showPermissionDisclosure) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = BgCard,
            title = {
                Text("Before we begin", color = com.railfancopilot.app.ui.theme.TextPrimary,
                    fontWeight = FontWeight.Medium)
            },
            text = {
                androidx.compose.foundation.layout.Column(
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Railfan Copilot collects and uses the following data:",
                        color = com.railfancopilot.app.ui.theme.TextSecondary, fontSize = 13.sp
                    )
                    listOf(
                        "📍 Location — to show nearby trains on the map, sort scanner feeds by distance, and tag community sighting reports. Sighting locations are stored on shared servers so the community feed works across devices.",
                        "🔔 Notifications — to alert you when trains are approaching your location or a watched locomotive is spotted. An anonymous device ID is used to deliver these alerts.",
                        "📷 Camera — to take photos for the AI locomotive identifier. Photos are sent to Anthropic's Claude API for analysis and are not stored by us.",
                        "☁️ Firebase — community sightings, spots, and watchlist entries are stored in Google Firebase. No personal account is required."
                    ).forEach { line ->
                        Text(line, color = com.railfancopilot.app.ui.theme.TextSecondary, fontSize = 12.sp,
                            lineHeight = 18.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDisclosure = false
                    permissions.launchMultiplePermissionRequest()
                }) {
                    Text("Continue", color = com.railfancopilot.app.ui.theme.RailBlue,
                        fontWeight = FontWeight.Medium)
                }
            }
        )
    }

    val hasLocationPermission = permissions.permissions.any {
        (it.permission == Manifest.permission.ACCESS_FINE_LOCATION ||
         it.permission == Manifest.permission.ACCESS_COARSE_LOCATION) &&
        it.status.isGranted
    }
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) vm.startLocationTracking()
    }

    // Background location: show prominent disclosure first, then request
    var showBgLocationDisclosure by remember { mutableStateOf(false) }
    val bgLocationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        rememberPermissionState(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    } else null

    // Once foreground location is granted, prompt for background (if not already granted)
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission &&
            bgLocationPermission != null &&
            !bgLocationPermission.status.isGranted
        ) {
            showBgLocationDisclosure = true
        }
    }

    if (showBgLocationDisclosure) {
        BackgroundLocationDisclosureDialog(
            onContinue = {
                showBgLocationDisclosure = false
                bgLocationPermission?.launchPermissionRequest()
            },
            onDismiss = {
                showBgLocationDisclosure = false
            }
        )
    }

    val onboardingShown by vm.onboardingShown.collectAsState()

    val newAlert     by vm.newRailAlert.collectAsState()
    val unreadCount  by vm.unreadAlertCount.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = BgPrimary,
            bottomBar = { RailFanBottomBar(navController, unreadCount) }
        ) { innerPadding ->
            val onUpgrade: () -> Unit = {
                navController.navigate(Screen.Upgrade.route) {
                    launchSingleTop = true
                }
            }

            NavHost(
                navController = navController,
                startDestination = Screen.Map.route,
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                composable(Screen.Map.route)          { MapScreen(vm) }
                composable(Screen.Scanner.route)      { ScannerScreen(vm) }
                composable(Screen.Decoder.route)      { DecoderScreen(vm, onUpgrade) }
                composable(Screen.Photo.route)        { PhotoScreen(vm, onUpgrade) }
                composable(Screen.Community.route)    { CommunityScreen(vm, onUpgrade) }
                composable(Screen.Alerts.route)       { AlertsScreen(vm) }
                composable(Screen.Watchlist.route)    { WatchlistScreen(vm) }
                composable(Screen.More.route)         {
                    MoreScreen(onNavigate = { route ->
                        navController.navigate(route) { launchSingleTop = true }
                    })
                }
                composable(Screen.Encyclopedia.route)    { EncyclopediaScreen(vm) }
                composable(Screen.SavedLocations.route) { SavedLocationsScreen(vm, onUpgrade) }
                composable(Screen.Spots.route)          { SpotsScreen(vm, onUpgrade) }
                composable(Screen.Webcams.route)        { WebcamsScreen() }
                composable(Screen.Settings.route)       { SettingsScreen(vm, onUpgrade) }
                composable(Screen.Upgrade.route)        {
                    UpgradeScreen(vm, onBack = { navController.popBackStack() })
                }
            }
        }

        // Show onboarding on first launch only; null = DataStore still loading (don't flash)
        if (onboardingShown == false) {
            OnboardingOverlay(
                onFinish = { vm.markOnboardingShown() }
            )
        }

        // In-app alert banner — slides in from the top, auto-dismisses after 4 s
        AnimatedVisibility(
            visible = newAlert != null,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it },
            exit  = fadeOut() + slideOutVertically { -it }
        ) {
            newAlert?.let { alert ->
                RailAlertBanner(alert = alert, onDismiss = { vm.consumeNewRailAlert() })
            }
        }
    }
}

/**
 * Prominent disclosure dialog required by Google Play User Data policy.
 * Must be shown before requesting ACCESS_BACKGROUND_LOCATION.
 * Explains clearly why background location is needed and how it is used.
 */
@Composable
fun BackgroundLocationDisclosureDialog(
    onContinue: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = RailBlue
            )
        },
        title = {
            Text(
                "Background Location",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Text(
                "RailFan Copilot uses your location in the background to send you proximity alerts " +
                "when trains approach your saved locations — even when the app is closed or not in use.\n\n" +
                "On the next screen, select \"Allow all the time\" to enable this feature. " +
                "You can change this at any time in your device Settings.",
                fontSize = 14.sp,
                lineHeight = 22.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(containerColor = RailBlue)
            ) {
                Text("Continue", color = androidx.compose.ui.graphics.Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not Now", color = TextMuted)
            }
        }
    )
}

@Composable
fun RailFanBottomBar(
    navController: androidx.navigation.NavHostController,
    alertUnreadCount: Int = 0
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    NavigationBar(
        containerColor = BgPrimary,
        tonalElevation = 0.dp,
        windowInsets = NavigationBarDefaults.windowInsets
    ) {
        bottomNavItems.forEach { screen ->
            val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
            NavigationBarItem(
                icon = {
                    if (screen == Screen.Alerts && alertUnreadCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge(containerColor = RailRed) {
                                    Text(
                                        if (alertUnreadCount > 9) "9+" else alertUnreadCount.toString(),
                                        fontSize = 8.sp,
                                        color = androidx.compose.ui.graphics.Color.White
                                    )
                                }
                            }
                        ) {
                            Icon(screen.icon, contentDescription = screen.label, modifier = Modifier.size(22.dp))
                        }
                    } else {
                        Icon(screen.icon, contentDescription = screen.label, modifier = Modifier.size(22.dp))
                    }
                },
                label = { Text(screen.label, fontSize = 10.sp) },
                selected = selected,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = RailBlue,
                    selectedTextColor = RailBlue,
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted,
                    indicatorColor = RailBlueDark
                )
            )
        }
    }
}
