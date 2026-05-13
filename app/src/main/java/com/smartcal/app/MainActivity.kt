package com.smartcal.app

import android.os.Bundle
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.smartcal.app.ui.screens.*
import com.smartcal.app.ui.theme.SmartCalendarAITheme
import com.smartcal.app.viewmodel.CalendarViewModel
import com.smartcal.app.widget.WakeWordService
import dagger.hilt.android.AndroidEntryPoint

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dom",       Icons.Default.Home)
    object Calendar  : Screen("calendar",  "Kalendarz", Icons.Default.CalendarMonth)
    object Finance   : Screen("finance",   "Finanse",   Icons.Default.AttachMoney)
    object Voice     : Screen("voice",     "Lemon",     Icons.Default.Mic)
}

// Order: Dom / Kalendarz / Finanse / Marek
val screens = listOf(Screen.Dashboard, Screen.Calendar, Screen.Finance, Screen.Voice)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences("lemon_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("wake_word_enabled", false)) {
            WakeWordService.start(this)
        }
        // Widget/Tile passes "start_destination" = "voice" to open Marek directly
        val startDest    = intent?.getStringExtra("start_destination") ?: "dashboard"
        val voiceCommand = intent?.getStringExtra("voice_command")
        setContent {
            SmartCalendarAITheme {
                MainAppContent(startDestination = startDest, pendingVoiceCommand = voiceCommand)
            }
        }
    }
}

@Composable
fun MainAppContent(startDestination: String = "dashboard", pendingVoiceCommand: String? = null) {
    val navController = rememberNavController()
    val calVm: CalendarViewModel = hiltViewModel()

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            NavigationBar {
                screens.forEach { screen ->
                    NavigationBarItem(
                        icon     = { Icon(screen.icon, contentDescription = screen.label) },
                        label    = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick  = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState    = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = startDestination,
            modifier         = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(calVm = calVm,
                    onVoiceClick = { navController.navigate(Screen.Voice.route) })
            }
            composable(Screen.Calendar.route) {
                CalendarScreen(vm = calVm)
            }
            composable(Screen.Finance.route) {
                FinanceScreen()
            }
            composable(Screen.Voice.route) {
                VoiceScreen()
            }
        }
    }
}
