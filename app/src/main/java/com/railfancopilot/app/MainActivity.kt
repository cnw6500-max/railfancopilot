package com.railfancopilot.app

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.railfancopilot.app.ui.screens.*
import com.railfancopilot.app.ui.theme.*
import com.railfancopilot.app.viewmodel.RailFanViewModel

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Map          : Screen("map",          "Map",       Icons.Default.Map)
    object Scanner      : Screen("scanner",      "Scanner",   Icons.Default.Radio)
    object Decoder      : Screen("decoder",      "Decoder",   Icons.Default.SmartToy)
    object Photo        : Screen("photo",        "Photo",     Icons.Default.CameraAlt)
    object Community    : Screen("community",    "Community", Icons.Default.Group)
    object Encyclopedia    : Screen("encyclopedia",     "Roster",  Icons.Default.Book)
    object SavedLocations  : Screen("saved_locations",  "Saved",   Icons.Default.Bookmark)
    object Settings        : Screen("settings",         "Settings",Icons.Default.Settings)
    object Upgrade         : Screen("upgrade",          "Upgrade", Icons.Default.Star)
}

private val bottomNavItems = listOf(
    Screen.Map, Screen.Scanner, Screen.Decoder,
    Screen.Photo, Screen.Community, Screen.Encyclopedia,
    Screen.SavedLocations, Screen.Settings
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
    LaunchedEffect(Unit) { permissions.launchMultiplePermissionRequest() }

    val hasLocationPermission = permissions.permissions.any {
        (it.permission == Manifest.permission.ACCESS_FINE_LOCATION ||
         it.permission == Manifest.permission.ACCESS_COARSE_LOCATION) &&
        it.status.isGranted
    }
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) vm.startLocationTracking()
    }

    val onboardingShown by vm.onboardingShown.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = BgPrimary,
            bottomBar = { RailFanBottomBar(navController) }
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
                composable(Screen.Encyclopedia.route)    { EncyclopediaScreen(vm) }
                composable(Screen.SavedLocations.route) { SavedLocationsScreen(vm, onUpgrade) }
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
    }
}

@Composable
fun RailFanBottomBar(navController: androidx.navigation.NavHostController) {
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
                icon = { Icon(screen.icon, contentDescription = screen.label, modifier = Modifier.size(22.dp)) },
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
