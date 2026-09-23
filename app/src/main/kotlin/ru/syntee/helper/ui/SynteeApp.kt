package ru.syntee.helper.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import ru.syntee.helper.core.data.auth.AuthState
import ru.syntee.helper.core.designsystem.components.SynteeLogo
import ru.syntee.helper.core.designsystem.theme.SynteeTheme
import ru.syntee.helper.feature.auth.LoginScreen
import ru.syntee.helper.feature.chat.ChatListScreen
import ru.syntee.helper.feature.chat.ChatScreen
import ru.syntee.helper.feature.settings.SettingsScreen
import ru.syntee.helper.navigation.ChatListRoute
import ru.syntee.helper.navigation.ChatRoute
import ru.syntee.helper.navigation.LoginRoute
import ru.syntee.helper.navigation.SettingsRoute

/** Идентификатор «нового чата» до появления Room-идентификаторов (стадия 03). */
private const val NEW_CHAT_ID = "new"

@Composable
fun SynteeApp(
    appViewModel: AppViewModel = hiltViewModel(),
) {
    SynteeTheme {
        val authState by appViewModel.authState.collectAsStateWithLifecycle()

        when (authState) {
            AuthState.Loading -> Splash()
            else -> MainScaffold(isLoggedIn = authState !is AuthState.LoggedOut)
        }
    }
}

@Composable
private fun Splash() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        SynteeLogo(size = 64.dp)
    }
}

@Composable
private fun MainScaffold(isLoggedIn: Boolean) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val showBottomBar = currentDestination?.hasRoute(ChatListRoute::class) == true ||
        currentDestination?.hasRoute(SettingsRoute::class) == true

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                SynteeBottomBar(
                    chatsSelected = currentDestination?.hasRoute(ChatListRoute::class) == true,
                    settingsSelected = currentDestination?.hasRoute(SettingsRoute::class) == true,
                    onChatsClick = { navController.navigateToTab(ChatListRoute) },
                    onSettingsClick = { navController.navigateToTab(SettingsRoute) },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (isLoggedIn) ChatListRoute else LoginRoute,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable<LoginRoute> {
                LoginScreen(
                    onLoggedIn = {
                        navController.navigate(ChatListRoute) {
                            popUpTo(LoginRoute) { inclusive = true }
                        }
                    },
                )
            }
            composable<ChatListRoute> {
                ChatListScreen(
                    onChatClick = { chatId -> navController.navigate(ChatRoute(chatId)) },
                    onNewChat = { navController.navigate(ChatRoute(NEW_CHAT_ID)) },
                )
            }
            composable<ChatRoute> { entry ->
                val route = entry.toRoute<ChatRoute>()
                ChatScreen(
                    chatId = route.chatId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<SettingsRoute> {
                SettingsScreen()
            }
        }
    }
}

/** Паттерн переключения табов: сохраняем состояние стека, single top. */
private fun NavHostController.navigateToTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
