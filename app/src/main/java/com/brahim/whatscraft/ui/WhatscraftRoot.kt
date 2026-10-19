package com.brahim.whatscraft.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.brahim.whatscraft.di.Container
import com.brahim.whatscraft.notify.Notifications
import com.brahim.whatscraft.ui.auth.AuthScreen
import com.brahim.whatscraft.ui.chats.ChatScreen
import com.brahim.whatscraft.ui.chats.ChatsScreen
import com.brahim.whatscraft.ui.contacts.ContactsScreen
import com.brahim.whatscraft.ui.settings.SettingsScreen
import com.brahim.whatscraft.ui.theme.Motion

private object Routes {
    const val Chats = "chats"
    const val People = "people"
    const val Settings = "settings"
    const val Chat = "chat/{conversationId}"

    fun chat(conversationId: String) = "chat/$conversationId"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val Tabs = listOf(
    Tab(Routes.Chats, "Chats", Icons.Outlined.ChatBubbleOutline, Icons.Rounded.ChatBubble),
    Tab(Routes.People, "People", Icons.Outlined.PersonOutline, Icons.Rounded.Person),
    Tab(Routes.Settings, "Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
)

/**
 * The whole app behind one call: it decides between the sign-in flow and the
 * tabs, keeps the error snackbar, and animates between screens.
 */
@Composable
fun WhatscraftRoot(
    container: Container,
    modifier: Modifier = Modifier,
    pendingChatId: String? = null,
    onPendingChatHandled: () -> Unit = {},
) {
    val loaded by container.sessions.loaded.collectAsStateWithLifecycle()
    val session by container.sessions.session.collectAsStateWithLifecycle()
    val error by container.repository.lastError.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.allowed(context)) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(error) {
        val message = error
        if (message != null) {
            snackbar.showSnackbar(message)
            container.repository.clearError()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when {
            !loaded -> LoadingGate()

            session == null -> AuthScreen(container = container, onAuthenticated = {})

            else -> SignedIn(
                container = container,
                snackbar = snackbar,
                pendingChatId = pendingChatId,
                onPendingChatHandled = onPendingChatHandled,
            )
        }
    }
}

@Composable
private fun LoadingGate() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun SignedIn(
    container: Container,
    snackbar: SnackbarHostState,
    pendingChatId: String?,
    onPendingChatHandled: () -> Unit,
) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = Tabs.any { it.route == route }

    // Open the websocket, sync, and start the background relay if asked to.
    LaunchedEffect(Unit) { container.start() }

    LaunchedEffect(pendingChatId) {
        val id = pendingChatId ?: return@LaunchedEffect
        nav.navigate(Routes.chat(id)) { launchSingleTop = true }
        onPendingChatHandled()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            AnimatedVisibility(
                visible = showBar,
                enter = fadeIn(tween(Motion.QuickMillis)),
                exit = fadeOut(tween(Motion.QuickMillis)),
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    Tabs.forEach { tab ->
                        val selected = route == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.switchTab(tab.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.icon,
                                    contentDescription = tab.label,
                                )
                            },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        AppNavigation(
            container = container,
            nav = nav,
            padding = padding,
        )
    }
}

@Composable
private fun AppNavigation(
    container: Container,
    nav: NavHostController,
    padding: PaddingValues,
) {
    NavHost(
        navController = nav,
        startDestination = Routes.Chats,
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        enterTransition = {
            slideInHorizontally(animationSpec = Motion.screenEnter()) { width -> width / 5 } +
                fadeIn(animationSpec = Motion.screenEnter())
        },
        exitTransition = {
            slideOutHorizontally(animationSpec = Motion.screenExit()) { width -> -width / 8 } +
                fadeOut(animationSpec = Motion.screenExit())
        },
        popEnterTransition = {
            slideInHorizontally(animationSpec = Motion.screenEnter()) { width -> -width / 8 } +
                fadeIn(animationSpec = Motion.screenEnter())
        },
        popExitTransition = {
            slideOutHorizontally(animationSpec = Motion.screenExit()) { width -> width / 5 } +
                fadeOut(animationSpec = Motion.screenExit())
        },
    ) {
        composable(Routes.Chats) {
            ChatsScreen(
                container = container,
                onOpenChat = { conversationId -> nav.navigate(Routes.chat(conversationId)) },
                onFindPeople = { nav.switchTab(Routes.People) },
            )
        }

        composable(Routes.People) {
            ContactsScreen(
                container = container,
                onOpenChat = { conversationId -> nav.navigate(Routes.chat(conversationId)) },
            )
        }

        composable(Routes.Settings) {
            SettingsScreen(
                container = container,
                onSignedOut = { nav.popBackStack(Routes.Chats, inclusive = false) },
            )
        }

        composable(
            route = Routes.Chat,
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
        ) { entry ->
            val conversationId = entry.arguments?.getString("conversationId").orEmpty()
            ChatScreen(
                container = container,
                conversationId = conversationId,
                onBack = { nav.popBackStack() },
            )
        }
    }
}

/** Tabs keep their scroll position and never stack up. */
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
