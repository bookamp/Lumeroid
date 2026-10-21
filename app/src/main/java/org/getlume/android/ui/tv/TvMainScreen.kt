package org.getlume.android.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.getlume.android.core.data.entities.ChannelEntity
import org.getlume.android.core.designsystem.*
import org.getlume.android.core.player.PlayableMedia
import org.getlume.android.ui.navigation.Screen

/**
 * Authentic Apple TV main container for Lume on Android TV.
 * Features a floating frosted-glass top capsule bar (TvTopBar) over
 * edge-to-edge cinematic screens (Home, Movies, Series, Live TV, Sports, Settings).
 *
 * Playback is hosted as an in-place modal overlay over the tabs (matching Apple TV
 * .fullScreenCover architecture), so all categories, drill-ins, and scroll positions
 * are 100% preserved when returning from playback.
 */
@Composable
fun TvMainScreen(
    channelLineup: List<ChannelEntity> = emptyList(),
    onOpenMultiView: () -> Unit
) {
    var selectedScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    val screenBackStack = remember { mutableStateListOf<Screen>() }
    var showAddPlaylistModal by remember { mutableStateOf(false) }

    // Authentic Apple TV playback overlay:
    // When playing media, TvPlayerScreen is presented as an in-place full-screen overlay
    // over the tab container. The active tab, categories, selections, and detail sheets
    // remain fully mounted and alive underneath.
    var activePlaybackMedia by remember { mutableStateOf<PlayableMedia?>(null) }

    fun switchScreen(newScreen: Screen) {
        if (newScreen != selectedScreen) {
            screenBackStack.add(selectedScreen)
            selectedScreen = newScreen
        }
    }

    // Tab-level BackHandler: active only when NO playback overlay is showing.
    // Inner composables (Settings drill-in, Series/Movie detail sheets, Home scroll)
    // register their own enabled BackHandlers which Compose prioritizes first.
    if (activePlaybackMedia == null) {
        if (showAddPlaylistModal) {
            BackHandler {
                showAddPlaylistModal = false
            }
        } else if (screenBackStack.isNotEmpty() || selectedScreen != Screen.Home) {
            BackHandler {
                if (screenBackStack.isNotEmpty()) {
                    selectedScreen = screenBackStack.removeAt(screenBackStack.size - 1)
                } else {
                    selectedScreen = Screen.Home
                }
            }
        }
    }

    // Bidirectional TopBar <-> Content focus requesters
    val topBarFocusRequester = remember { FocusRequester() }
    val homeHeroFocusRequester = remember { FocusRequester() }
    val moviesFirstFocusRequester = remember { FocusRequester() }
    val seriesFirstFocusRequester = remember { FocusRequester() }
    val liveTvFirstFocusRequester = remember { FocusRequester() }
    val sportsFirstFocusRequester = remember { FocusRequester() }
    val settingsFirstFocusRequester = remember { FocusRequester() }

    TvAmbientBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            // Main Content Area based on selected tab
            when (selectedScreen) {
                Screen.Home -> {
                    TvHomeScreen(
                        onPlayMedia = { media -> activePlaybackMedia = media },
                        onOpenLiveTv = { switchScreen(Screen.LiveTv) },
                        onOpenMovies = { switchScreen(Screen.Movies) },
                        onOpenSeries = { switchScreen(Screen.Series) },
                        onOpenSetup = { switchScreen(Screen.Settings) },
                        heroFocusRequester = homeHeroFocusRequester,
                        onMoveUpToTopBar = {
                            try { topBarFocusRequester.requestFocus() } catch (_: Exception) {}
                        }
                    )
                }
                Screen.Movies -> {
                    TvMoviesScreen(
                        onPlayMedia = { media -> activePlaybackMedia = media },
                        firstItemFocusRequester = moviesFirstFocusRequester,
                        onMoveUpToTopBar = {
                            try { topBarFocusRequester.requestFocus() } catch (_: Exception) {}
                        }
                    )
                }
                Screen.Series -> {
                    TvSeriesScreen(
                        onPlayMedia = { media -> activePlaybackMedia = media },
                        firstItemFocusRequester = seriesFirstFocusRequester,
                        onMoveUpToTopBar = {
                            try { topBarFocusRequester.requestFocus() } catch (_: Exception) {}
                        }
                    )
                }
                Screen.LiveTv -> {
                    TvLiveTvScreen(
                        onPlayMedia = { media -> activePlaybackMedia = media },
                        firstItemFocusRequester = liveTvFirstFocusRequester,
                        onMoveUpToTopBar = {
                            try { topBarFocusRequester.requestFocus() } catch (_: Exception) {}
                        }
                    )
                }
                Screen.Sports -> {
                    TvSportsScreen(
                        onPlayMedia = { media -> activePlaybackMedia = media },
                        firstItemFocusRequester = sportsFirstFocusRequester,
                        onMoveUpToTopBar = {
                            try { topBarFocusRequester.requestFocus() } catch (_: Exception) {}
                        }
                    )
                }
                Screen.MultiView -> {
                    LaunchedEffect(Unit) {
                        onOpenMultiView()
                        switchScreen(Screen.Home)
                    }
                }
                Screen.Settings -> {
                    if (showAddPlaylistModal) {
                        TvLoginView(
                            onPlaylistAdded = {
                                showAddPlaylistModal = false
                            },
                            onDismiss = {
                                showAddPlaylistModal = false
                            }
                        )
                    } else {
                        TvSettingsScreen(
                            onOpenAddPlaylist = {
                                showAddPlaylistModal = true
                            },
                            firstItemFocusRequester = settingsFirstFocusRequester,
                            onMoveUpToTopBar = {
                                try { topBarFocusRequester.requestFocus() } catch (_: Exception) {}
                            }
                        )
                    }
                }
                else -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(selectedScreen.title, color = LumeTextSecondary, fontSize = 24.sp)
                    }
                }
            }

            // Apple TV Floating Top Capsule Bar (hidden when modal Add Playlist or active player is open)
            if (!showAddPlaylistModal && activePlaybackMedia == null) {
                TvTopBar(
                    currentScreen = selectedScreen,
                    focusRequester = topBarFocusRequester,
                    onSelectScreen = { screen ->
                        switchScreen(screen)
                    },
                    onOpenSearch = {
                        // Future search feature
                    },
                    onOpenSettings = {
                        switchScreen(Screen.Settings)
                    },
                    onMoveDown = {
                        try {
                            when (selectedScreen) {
                                Screen.Home -> homeHeroFocusRequester.requestFocus()
                                Screen.Movies -> moviesFirstFocusRequester.requestFocus()
                                Screen.Series -> seriesFirstFocusRequester.requestFocus()
                                Screen.LiveTv -> liveTvFirstFocusRequester.requestFocus()
                                Screen.Sports -> sportsFirstFocusRequester.requestFocus()
                                Screen.Settings -> settingsFirstFocusRequester.requestFocus()
                                else -> false
                            }
                            true
                        } catch (_: Exception) {
                            false
                        }
                    }
                )
            }

            // Apple TV In-Place Full-Screen Player Overlay
            activePlaybackMedia?.let { media ->
                TvPlayerScreen(
                    media = media,
                    channelLineup = channelLineup,
                    onBack = {
                        activePlaybackMedia = null
                    }
                )
            }
        }
    }
}
