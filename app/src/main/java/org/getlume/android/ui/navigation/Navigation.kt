package org.getlume.android.ui.navigation

sealed class Screen(val route: String, val title: String) {
    object Search : Screen("search", "Search")
    object Home : Screen("home", "Home")
    object LiveTv : Screen("live_tv", "Live TV")
    object Movies : Screen("movies", "Movies")
    object Series : Screen("series", "Series")
    object Sports : Screen("sports", "Sports")
    object MultiView : Screen("multi_view", "Multi-View")
    object Dvr : Screen("dvr", "DVR")
    object Settings : Screen("settings", "Settings")
    object Player : Screen("player/{channelId}", "Player") {
        fun createRoute(channelId: String) = "player/$channelId"
    }
}
