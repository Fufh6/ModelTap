package com.wuyousheng.modeltap.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wuyousheng.modeltap.data.repository.ChatRepositoryProvider
import com.wuyousheng.modeltap.ui.screens.chat.ChatScreen
import com.wuyousheng.modeltap.ui.screens.config.ConfigScreen
import com.wuyousheng.modeltap.ui.screens.history.HistoryScreen
import com.wuyousheng.modeltap.ui.screens.imagecreate.ImageCreateScreen
import com.wuyousheng.modeltap.ui.screens.imagecreate.ImageGalleryScreen
import com.wuyousheng.modeltap.ui.screens.me.MeScreen
import com.wuyousheng.modeltap.ui.screens.search.SearchSettingsScreen
import com.wuyousheng.modeltap.ui.screens.sessiondetail.SessionDetailScreen
import com.wuyousheng.modeltap.ui.screens.sessions.SessionListScreen
import com.wuyousheng.modeltap.ui.screens.settings.ModelSettingsScreen
import com.wuyousheng.modeltap.ui.screens.stats.StatsScreen
import com.wuyousheng.modeltap.ui.screens.web.WebViewScreen

@Composable
fun AppNavGraph(context: Context = LocalContext.current) {
    val navController = rememberNavController()
    val repository = remember(context) { ChatRepositoryProvider.get(context) }

    LaunchedEffect(repository) {
        repository.migrateApiKeyStorage()
    }

    NavHost(
        navController = navController,
        startDestination = AppRoute.Launch.route
    ) {
        composable(AppRoute.Launch.route) {
            LaunchedEffect(Unit) {
                navController.navigate(AppRoute.Chat.create(0L)) {
                    popUpTo(AppRoute.Launch.route) { inclusive = true }
                }
            }
        }
        composable(AppRoute.Sessions.route) {
            SessionListScreen(
                repository = repository,
                onOpenSession = { sessionId ->
                    navController.navigate(AppRoute.Chat.create(sessionId)) {
                        popUpTo(AppRoute.Sessions.route)
                    }
                },
                onOpenDraft = {
                    navController.navigate(AppRoute.Chat.create(0L)) {
                        popUpTo(AppRoute.Sessions.route)
                    }
                },
                onOpenHistory = { navController.navigate(AppRoute.History.route) },
                onOpenStats = { navController.navigate(AppRoute.Stats.route) },
                onOpenConfig = { navController.navigate(AppRoute.Config.route) },
                onOpenCreate = { navController.navigate(AppRoute.ImageCreate.route) },
                onOpenSettings = { navController.navigate(AppRoute.Settings.route) },
                onOpenMe = { navController.navigate(AppRoute.Me.route) }
            )
        }
        composable(AppRoute.Me.route) {
            MeScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
                onOpenHistory = { navController.navigate(AppRoute.History.route) },
                onOpenStats = { navController.navigate(AppRoute.Stats.route) },
                onOpenFavorites = { navController.navigate(AppRoute.Favorites.route) },
                onOpenGallery = { navController.navigate(AppRoute.ImageGallery.route) },
                onOpenSettings = { navController.navigate(AppRoute.Settings.route) },
                onOpenOfficialWebsite = { navController.navigate(AppRoute.OfficialWebsite.route) }
            )
        }
        composable(AppRoute.OfficialWebsite.route) {
            WebViewScreen(
                title = "官方网站",
                url = "https://www.modeltap.cn",
                onBack = { navController.popBackStack() }
            )
        }
        composable(AppRoute.Config.route) {
            ConfigScreen(repository = repository, onBack = { navController.popBackStack() })
        }
        composable(AppRoute.Search.route) {
            SearchSettingsScreen(repository = repository, onBack = { navController.popBackStack() })
        }
        composable(AppRoute.Settings.route) {
            ModelSettingsScreen(repository = repository, onBack = { navController.popBackStack() })
        }
        composable(AppRoute.Stats.route) {
            StatsScreen(repository = repository, onBack = { navController.popBackStack() })
        }
        composable(AppRoute.History.route) {
            HistoryScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
                onOpenSession = { sessionId ->
                    navController.navigate(AppRoute.Chat.create(sessionId)) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(AppRoute.Favorites.route) {
            HistoryScreen(
                repository = repository,
                initialSelectedTab = "收藏",
                onBack = { navController.popBackStack() },
                onOpenSession = { sessionId ->
                    navController.navigate(AppRoute.Chat.create(sessionId)) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(AppRoute.ImageCreate.route) {
            ImageCreateScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
                onOpenHistory = { navController.navigate(AppRoute.History.route) },
                onOpenGallery = { navController.navigate(AppRoute.ImageGallery.route) }
            )
        }
        composable(AppRoute.ImageGallery.route) {
            ImageGalleryScreen(
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = AppRoute.SessionDetail.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getLong("sessionId")
            if (sessionId == null || sessionId <= 0L) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                SessionDetailScreen(
                    repository = repository,
                    sessionId = sessionId,
                    onBack = { navController.popBackStack() },
                    onDeleted = {
                        navController.navigate(AppRoute.Sessions.route) {
                            popUpTo(AppRoute.Chat.create(sessionId)) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
        composable(
            route = AppRoute.Chat.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getLong("sessionId")
            if (sessionId == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                ChatScreen(
                    repository = repository,
                    sessionId = sessionId,
                    onBack = {
                        navController.navigate(AppRoute.Sessions.route) {
                            launchSingleTop = true
                        }
                    },
                    onOpenHome = {
                        navController.navigate(AppRoute.Sessions.route) {
                            launchSingleTop = true
                        }
                    },
                    onOpenSessionDetail = {
                        if (sessionId > 0L) {
                            navController.navigate(AppRoute.SessionDetail.create(sessionId))
                        }
                    },
                    onOpenHistory = {
                        navController.navigate(AppRoute.History.route)
                    },
                    onOpenImageCreate = {
                        navController.navigate(AppRoute.ImageCreate.route) {
                            launchSingleTop = true
                        }
                    },
                    onOpenSession = { targetSessionId ->
                        navController.navigate(AppRoute.Chat.create(targetSessionId)) {
                            popUpTo(AppRoute.Chat.create(sessionId)) { inclusive = true }
                        }
                    },
                    onCreateSession = {
                        navController.navigate(AppRoute.Chat.create(0L)) {
                            popUpTo(AppRoute.Chat.create(sessionId)) { inclusive = true }
                        }
                    },
                    onDraftSessionCreated = { newSessionId ->
                        navController.navigate(AppRoute.Chat.create(newSessionId)) {
                            popUpTo(AppRoute.Chat.create(sessionId)) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
