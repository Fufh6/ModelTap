package com.wuyousheng.modeltap.navigation

sealed class AppRoute(val route: String) {
    data object Launch : AppRoute("launch")
    data object Sessions : AppRoute("sessions")
    data object Me : AppRoute("me")
    data object Config : AppRoute("config")
    data object Search : AppRoute("search")
    data object Settings : AppRoute("settings")
    data object Stats : AppRoute("stats")
    data object History : AppRoute("history")
    data object Favorites : AppRoute("favorites")
    data object ImageCreate : AppRoute("image_create")
    data object ImageGallery : AppRoute("image_gallery")
    data object OfficialWebsite : AppRoute("official_website")
    data object SessionDetail : AppRoute("session_detail/{sessionId}") {
        fun create(sessionId: Long): String = "session_detail/$sessionId"
    }
    data object Chat : AppRoute("chat/{sessionId}") {
        fun create(sessionId: Long): String = "chat/$sessionId"
    }
}
