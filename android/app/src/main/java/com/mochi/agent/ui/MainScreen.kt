package com.mochi.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mochi.agent.ui.screens.ChatScreen
import com.mochi.agent.ui.screens.HomeScreen
import com.mochi.agent.ui.screens.SettingsScreen
import com.mochi.agent.ui.screens.TerminalScreen
import com.mochi.agent.ui.theme.MochiBlue
import com.mochi.agent.ui.theme.MochiDark
import com.mochi.agent.viewmodel.MochiViewModel

private enum class Dest(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME("home", "Home", Icons.Filled.Home),
    CHAT("chat", "Chat", Icons.Rounded.Chat),
    TERMINAL("terminal", "Terminal", Icons.Filled.Terminal),
    SETTINGS("settings", "Settings", Icons.Filled.Settings),
}

@Composable
fun MainScreen(viewModel: MochiViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Dest.HOME.route

    Scaffold(
        containerColor = MochiDark,
        bottomBar = {
            NavigationBar(containerColor = MochiDark) {
                Dest.entries.forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute == dest.route,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MochiBlue,
                            unselectedIconColor = Color(0xFF8B93A7),
                            selectedTextColor = MochiBlue,
                            unselectedTextColor = Color(0xFF8B93A7),
                            indicatorColor = MochiBlue.copy(alpha = 0.15f),
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            NavHost(navController = navController, startDestination = Dest.HOME.route) {
                composable(Dest.HOME.route) {
                    HomeScreen(
                        state = viewModel.agentState.collectAsState().value,
                        actionName = viewModel.actionName.collectAsState().value,
                        actionMessage = viewModel.actionMessage.collectAsState().value,
                    )
                }
                composable(Dest.CHAT.route) {
                    ChatScreen(
                        messages = viewModel.chat.collectAsState().value,
                        onSend = viewModel::sendChat,
                    )
                }
                composable(Dest.TERMINAL.route) {
                    TerminalScreen(
                        lines = viewModel.terminalLines.collectAsState().value,
                        onCommand = viewModel::runTerminalCommand,
                    )
                }
                composable(Dest.SETTINGS.route) {
                    SettingsScreen(
                        githubToken = viewModel.githubToken.collectAsState().value,
                        onSaveToken = viewModel::saveGitHubToken,
                    )
                }
            }
        }
    }
}