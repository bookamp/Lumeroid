package org.getlumeroid.android

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.getlumeroid.android.core.designsystem.LumeroidBackground
import org.getlumeroid.android.core.designsystem.LumeroidCyan
import org.getlumeroid.android.core.designsystem.LumeroidTheme
import org.getlumeroid.android.ui.mobile.MobileMainScreen
import org.getlumeroid.android.ui.mobile.MobilePlayerScreen
import org.getlumeroid.android.ui.tv.TvMainScreen
import org.getlumeroid.android.ui.tv.TvMultiViewScreen
import org.getlumeroid.android.ui.tv.TvPlayerScreen
import org.getlumeroid.android.core.player.*

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen awake during playback (both TV and Mobile)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val isTv = LumeroidApp.instance.isRunningOnTv

        setContent {
            LumeroidTheme {
                val navController = rememberNavController()
                val db = LumeroidApp.instance.database
                val activePlaylist by db.playlistDao().observeActivePlaylist().collectAsState(initial = null)
                val allChannels by db.channelDao().observeChannelsByPlaylist(activePlaylist?.id ?: "").collectAsState(initial = emptyList())

                LaunchedEffect(Unit) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        if (db.playlistDao().getActivePlaylist() == null) {
                            val defaultPlaylist = org.getlumeroid.android.core.data.entities.PlaylistEntity(
                                id = "82f6e8d7-96c3-48c2-a1f1-2313fc8e7834",
                                name = "My IPTV",
                                type = "xtream",
                                serverUrl = "http://192.168.1.235:8082",
                                username = "appa",
                                password = "amma",
                                epgUrl = "",
                                isActive = true
                            )
                            db.playlistDao().insertPlaylist(defaultPlaylist)
                            db.playlistDao().setActivePlaylist(defaultPlaylist.id)
                            org.getlumeroid.android.core.settings.ContentFilterCache.rebuildAsync(defaultPlaylist.id)
                            org.getlumeroid.android.core.sync.PlaylistSyncManager.syncPlaylist(defaultPlaylist)
                        }
                    }
                }


                if (isTv) {
                    NavHost(navController = navController, startDestination = "tv_main") {
                        composable("tv_main") {
                            TvMainScreen(
                                channelLineup = allChannels,
                                onOpenMultiView = { channel ->
                                    val route = if (channel != null) "tv_multiview/${channel.id}" else "tv_multiview"
                                    navController.navigate(route)
                                }
                            )
                        }

                        composable("tv_player") {
                            val media = org.getlumeroid.android.core.player.ActivePlaybackStore.currentMedia
                            if (media != null) {
                                TvPlayerScreen(
                                    media = media,
                                    channelLineup = allChannels,
                                    onBack = { navController.popBackStack() }
                                )
                            } else {
                                navController.popBackStack()
                            }
                        }

                        composable(
                            route = "tv_player/{channelId}",
                            arguments = listOf(navArgument("channelId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val channelId = backStackEntry.arguments?.getString("channelId") ?: ""
                            val initialChannel = allChannels.find { it.id == channelId }

                            if (initialChannel != null) {
                                TvPlayerScreen(
                                    media = initialChannel.toPlayableMedia(),
                                    channelLineup = allChannels,
                                    onBack = { navController.popBackStack() }
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(LumeroidBackground),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = LumeroidCyan)
                                }
                            }
                        }

                        composable("tv_multiview") {
                            TvMultiViewScreen(
                                initialChannel = null,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = "tv_multiview/{channelId}",
                            arguments = listOf(navArgument("channelId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val channelId = backStackEntry.arguments?.getString("channelId") ?: ""
                            val initialChannel = allChannels.find { it.id == channelId }
                            TvMultiViewScreen(
                                initialChannel = initialChannel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                } else {
                    NavHost(navController = navController, startDestination = "mobile_main") {
                        composable("mobile_main") {
                            MobileMainScreen(
                                onPlayMedia = { media ->
                                    ActivePlaybackStore.currentMedia = media
                                    navController.navigate("mobile_player")
                                },
                                onPlayChannel = { channel ->
                                    ActivePlaybackStore.currentMedia = channel.toPlayableMedia(lineup = allChannels)
                                    navController.navigate("mobile_player")
                                }
                            )
                        }

                        composable("mobile_player") {
                            val media = ActivePlaybackStore.currentMedia
                            if (media != null) {
                                MobilePlayerScreen(
                                    media = media,
                                    channelLineup = allChannels,
                                    onBack = { navController.popBackStack() }
                                )
                            } else {
                                navController.popBackStack()
                            }
                        }

                        composable(
                            route = "mobile_player/{channelId}",
                            arguments = listOf(navArgument("channelId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val channelId = backStackEntry.arguments?.getString("channelId") ?: ""
                            val initialChannel = allChannels.find { it.id == channelId }

                            if (initialChannel != null) {
                                MobilePlayerScreen(
                                    media = initialChannel.toPlayableMedia(lineup = allChannels),
                                    channelLineup = allChannels,
                                    onBack = { navController.popBackStack() }
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(LumeroidBackground),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = LumeroidCyan)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val handled = super.dispatchKeyEvent(event)
        if (!handled && event.action == android.view.KeyEvent.ACTION_DOWN) {
            // Only attempt focus rescue if the window has ACTUALLY lost focus completely.
            // When an element is focused and reaches a screen boundary, hitting D-pad UP/DOWN/LEFT/RIGHT
            // must NEVER steal or rescue focus to another element.
            val hasFocus = currentFocus != null && currentFocus?.hasFocus() == true
            if (!hasFocus) {
                when (event.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_UP,
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                    android.view.KeyEvent.KEYCODE_ENTER,
                    android.view.KeyEvent.KEYCODE_BACK -> {
                        val rescued = org.getlumeroid.android.core.designsystem.TvFocusRescue.rescueFocus()
                        if (rescued) return true
                    }
                }
            }
        }
        return handled
    }

}
