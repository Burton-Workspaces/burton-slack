package com.burton.slack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.burton.slack.ui.channel.ChannelScreen
import com.burton.slack.ui.home.HomeScreen
import com.burton.slack.ui.navigation.Routes
import com.burton.slack.ui.search.SearchScreen
import com.burton.slack.ui.signin.SignInScreen
import com.burton.slack.ui.theme.BurtonBlack
import com.burton.slack.ui.theme.BurtonIvory
import com.burton.slack.ui.theme.BurtonMute
import com.burton.slack.ui.theme.BurtonSlackTheme
import com.burton.slack.ui.thread.ThreadScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BurtonSlackTheme {
                val appViewModel: AppViewModel = hiltViewModel()
                val snapshot by appViewModel.state.collectAsStateWithLifecycle()
                if (snapshot.tokenPresent) {
                    BurtonApp()
                } else {
                    SignInScreen()
                }
            }
        }
    }
}

@Composable
private fun BurtonApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val tabs = listOf(Routes.HOME, Routes.SEARCH)
    val selectedTab = if (route in tabs) route else Routes.HOME
    val hideTabs = route == Routes.CHANNEL || route == Routes.THREAD
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(BurtonBlack),
        containerColor = BurtonBlack,
        bottomBar = {
            if (!hideTabs) {
                NavigationBar(
                    containerColor = BurtonBlack,
                    contentColor = BurtonIvory,
                    modifier = Modifier.navigationBarsPadding(),
                ) {
                    NavigationBarItem(
                        selected = selectedTab == Routes.HOME,
                        onClick = { navController.goTab(Routes.HOME) },
                        icon = { Icon(Icons.Rounded.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        colors = navColors(selectedTab == Routes.HOME),
                    )
                    NavigationBarItem(
                        selected = selectedTab == Routes.SEARCH,
                        onClick = { navController.goTab(Routes.SEARCH) },
                        icon = { Icon(Icons.Rounded.Search, contentDescription = "Search") },
                        label = { Text("Search") },
                        colors = navColors(selectedTab == Routes.SEARCH),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(onOpenChannel = { navController.navigate(Routes.channel(it)) })
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    onOpenHit = { channelId, threadTs ->
                        navController.navigate(Routes.channel(channelId))
                        if (!threadTs.isNullOrBlank()) {
                            navController.navigate(Routes.thread(channelId, threadTs))
                        }
                    },
                )
            }
            composable(
                Routes.CHANNEL,
                arguments = listOf(navArgument("channelId") { type = NavType.StringType }),
            ) {
                ChannelScreen(
                    onBack = { navController.popBackStack() },
                    onOpenThread = { channelId, threadTs ->
                        navController.navigate(Routes.thread(channelId, threadTs))
                    },
                )
            }
            composable(
                Routes.THREAD,
                arguments = listOf(
                    navArgument("channelId") { type = NavType.StringType },
                    navArgument("threadTs") { type = NavType.StringType },
                ),
            ) {
                ThreadScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun NavHostController.goTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun navColors(selected: Boolean) = NavigationBarItemDefaults.colors(
    selectedIconColor = BurtonIvory,
    selectedTextColor = BurtonIvory,
    unselectedIconColor = BurtonMute,
    unselectedTextColor = BurtonMute,
    indicatorColor = Color(0xFF222222),
)
