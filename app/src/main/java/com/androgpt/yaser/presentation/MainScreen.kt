package com.androgpt.yaser.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.androgpt.yaser.presentation.chat.ChatScreen
import com.androgpt.yaser.presentation.chat.ChatViewModel
import com.androgpt.yaser.presentation.models.ModelsScreen
import com.androgpt.yaser.presentation.settings.SettingsScreen
import com.androgpt.yaser.presentation.settings.SettingsViewModel

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Chat : Screen("chat", "Chat", Icons.Default.Chat)
    object Models : Screen("models", "Models", Icons.Default.Memory)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                val items = listOf(
                    Screen.Chat,
                    Screen.Models,
                    Screen.Settings
                )
                
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Chat.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Chat.route) {
                val chatViewModel: ChatViewModel = hiltViewModel()
                ChatScreen(chatViewModel)
            }
            
            composable(Screen.Models.route) {
                val settingsViewModel: SettingsViewModel = hiltViewModel()
                ModelsScreen(
                    onLoadModel = { model ->
                        settingsViewModel.loadModel(
                            modelPath = model.filePath,
                            modelName = model.name,
                            modelSize = model.size
                        )
                    }
                )
            }
            
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
