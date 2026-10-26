package org.getlume.android

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
import org.getlume.android.core.designsystem.LumeBackground
import org.getlume.android.core.designsystem.LumeCyan
import org.getlume.android.core.designsystem.LumeTheme
import org.getlume.android.ui.mobile.MobileMainScreen
import org.getlume.android.ui.mobile.MobilePlayerScreen
import org.getlume.android.ui.tv.TvMainScreen
import org.getlume.android.ui.tv.TvMultiViewScreen
import org.getlume.android.ui.tv.TvPlayerScreen
import org.getlume.android.core.player.*

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen awake during playback (both TV and Mobile)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val isTv = LumeApp.instance.isRunningOnTv

        setContent {
            LumeTheme {
                val navController = rememberNavController()
                val db = LumeApp.instance.database
                val activePlaylist by db.playlistDao().observeActivePlaylist().collectAsState(initial = null)
                val allChannels by db.channelDao().observeChannelsByPlaylist(activePlaylist?.id ?: "").collectAsState(initial = emptyList())

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
                            val media = org.getlume.android.core.player.ActivePlaybackStore.currentMedia
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
                                    modifier = Modifier.fillMaxSize().background(LumeBackground),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = LumeCyan)
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
                                onPlayChannel = { channel ->
                                    navController.navigate("mobile_player/${channel.id}")
                                },
                                onOpenMultiView = {
                                    navController.navigate("mobile_multiview")
                                }
                            )
                        }

                        composable(
                            route = "mobile_player/{channelId}",
                            arguments = listOf(navArgument("channelId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val channelId = backStackEntry.arguments?.getString("channelId") ?: ""
                            val initialChannel = allChannels.find { it.id == channelId }

                            if (initialChannel != null) {
                                MobilePlayerScreen(
                                    initialChannel = initialChannel,
                                    channelLineup = allChannels,
                                    onBack = { navController.popBackStack() }
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(LumeBackground),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = LumeCyan)
                                }
                            }
                        }

                        composable("mobile_multiview") {
                            TvMultiViewScreen(
                                initialChannel = allChannels.firstOrNull(),
                                onBack = { navController.popBackStack() }
                            )
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
                        val rescued = org.getlume.android.core.designsystem.TvFocusRescue.rescueFocus()
                        if (rescued) return true
                    }
                }
            }
        }
        return handled
    }

}
