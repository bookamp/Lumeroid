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
import org.getlume.android.core.sync.PlaylistSyncManager
import org.getlume.android.core.settings.AppSettings
import org.getlume.android.ui.navigation.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val settings = remember { AppSettings.get() }
    val revision by settings.revision.collectAsState()
    val db = remember { org.getlume.android.LumeApp.instance.database }
    val activePlaylist by db.playlistDao().observeActivePlaylist().collectAsState(initial = null)
    val playlistId = activePlaylist?.id ?: ""
    val coroutineScope = rememberCoroutineScope()
    var selectedScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    var showAddPlaylistModal by remember { mutableStateOf(false) }
    var isTopBarFocused by remember { mutableStateOf(true) }

    // If currently selected screen is hidden via Library settings, fall back to Home
    LaunchedEffect(revision, selectedScreen, playlistId) {
        val isHidden = when (selectedScreen) {
            Screen.Movies -> !settings.isPlaylistTabVisible(playlistId, "movies")
            Screen.Series -> !settings.isPlaylistTabVisible(playlistId, "series")
            Screen.LiveTv -> !settings.isPlaylistTabVisible(playlistId, "livetv")
            Screen.Sports -> !settings.libraryShowSports || !settings.sportsHubEnabled
            else -> false
        }
        if (isHidden) {
            selectedScreen = Screen.Home
        }
    }

    // Authentic Apple TV Auto-Sync gate:
    // On app startup, checks if the active playlist is due for an automatic refresh
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            PlaylistSyncManager.checkAndAutoSync()
        }
    }

    // Authentic Apple TV playback overlay:
    // When playing media, TvPlayerScreen is presented as an in-place full-screen overlay
    // over the tab container. The active tab, categories, selections, and detail sheets
    // remain fully mounted and alive underneath.
    var activePlaybackMedia by remember { mutableStateOf<PlayableMedia?>(null) }

    fun switchScreen(newScreen: Screen) {
        if (newScreen != selectedScreen) {
            selectedScreen = newScreen
        }
    }

    // Bidirectional TopBar <-> Content focus requesters
    val topBarFocusRequester = remember { FocusRequester() }
    val searchFirstFocusRequester = remember { FocusRequester() }
    val homeHeroFocusRequester = remember { FocusRequester() }
    val moviesFirstFocusRequester = remember { FocusRequester() }
    val seriesFirstFocusRequester = remember { FocusRequester() }
    val liveTvFirstFocusRequester = remember { FocusRequester() }
    val sportsFirstFocusRequester = remember { FocusRequester() }
    val settingsFirstFocusRequester = remember { FocusRequester() }

    // Branch-level BackHandler: active only when NO playback overlay is showing.
    // In accordance with the tree navigation model, Home is the root parent branch.
    // When focused on the TopBar of any branch, Back retracts directly to Home.
    if (activePlaybackMedia == null) {
        if (showAddPlaylistModal) {
            BackHandler {
                showAddPlaylistModal = false
            }
        } else if (isTopBarFocused && selectedScreen != Screen.Home) {
            BackHandler {
                selectedScreen = Screen.Home
                try {
                    topBarFocusRequester.requestFocus()
                } catch (_: Exception) {}
            }
        }
    }

    TvAmbientBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            // Main Content Area based on selected tab
            when (selectedScreen) {
                Screen.Search -> {
                    TvSearchScreen(
                        onPlayMedia = { media -> activePlaybackMedia = media },
                        firstItemFocusRequester = searchFirstFocusRequester,
                        onMoveUpToTopBar = {
                            isTopBarFocused = true
                            try { topBarFocusRequester.requestFocus() } catch (_: Exception) {}
                        }
                    )
                }
                Screen.Home -> {
                    TvHomeScreen(
                        onPlayMedia = { media -> activePlaybackMedia = media },
                        onOpenLiveTv = { switchScreen(Screen.LiveTv) },
                        onOpenMovies = { switchScreen(Screen.Movies) },
                        onOpenSeries = { switchScreen(Screen.Series) },
                        onOpenSetup = { switchScreen(Screen.Settings) },
                        heroFocusRequester = homeHeroFocusRequester,
                        isTopBarFocused = isTopBarFocused,
                        onMoveUpToTopBar = {
                            isTopBarFocused = true
                            try { topBarFocusRequester.requestFocus() } catch (_: Exception) {}
                        }
                    )
                }
                Screen.Movies -> {
                    TvMoviesScreen(
                        onPlayMedia = { media -> activePlaybackMedia = media },
                        firstItemFocusRequester = moviesFirstFocusRequester,
                        isTopBarFocused = isTopBarFocused,
                        onContentFocused = { isTopBarFocused = false },
                        onMoveUpToTopBar = {
                            isTopBarFocused = true
                            try { topBarFocusRequester.requestFocus() } catch (_: Exception) {}
                        }
                    )
                }
                Screen.Series -> {
                    TvSeriesScreen(
                        onPlayMedia = { media -> activePlaybackMedia = media },
                        firstItemFocusRequester = seriesFirstFocusRequester,
                        isTopBarFocused = isTopBarFocused,
                        onContentFocused = { isTopBarFocused = false },
                        onMoveUpToTopBar = {
                            isTopBarFocused = true
                            try { topBarFocusRequester.requestFocus() } catch (_: Exception) {}
                        }
                    )
                }
                Screen.LiveTv -> {
                    TvLiveTvScreen(
                        onPlayMedia = { media -> activePlaybackMedia = media },
                        firstItemFocusRequester = liveTvFirstFocusRequester,
                        onMoveUpToTopBar = {
                            isTopBarFocused = true
                            try { topBarFocusRequester.requestFocus() } catch (_: Exception) {}
                        },
                        onStartMultiView = { channel ->
                            onOpenMultiView()
                        }
                    )
                }
                Screen.Sports -> {
                    TvSportsScreen(
                        onPlayMedia = { media -> activePlaybackMedia = media },
                        firstItemFocusRequester = sportsFirstFocusRequester,
                        onMoveUpToTopBar = {
                            isTopBarFocused = true
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
                                isTopBarFocused = true
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
                    isTopBarFocused = isTopBarFocused,
                    focusRequester = topBarFocusRequester,
                    onFocusChanged = { isTopBarFocused = it },
                    onSelectScreen = { screen ->
                        switchScreen(screen)
                    },
                    onOpenSearch = {
                        switchScreen(Screen.Search)
                    },
                    onOpenSettings = {
                        switchScreen(Screen.Settings)
                    },
                    onMoveDown = {
                        isTopBarFocused = false
                        try {
                            when (selectedScreen) {
                                Screen.Search -> searchFirstFocusRequester.requestFocus()
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
                        coroutineScope.launch {
                            kotlinx.coroutines.delay(50)
                            try {
                                when (selectedScreen) {
                                    Screen.Search -> searchFirstFocusRequester.requestFocus()
                                    Screen.Home -> homeHeroFocusRequester.requestFocus()
                                    Screen.Movies -> moviesFirstFocusRequester.requestFocus()
                                    Screen.Series -> seriesFirstFocusRequester.requestFocus()
                                    Screen.LiveTv -> liveTvFirstFocusRequester.requestFocus()
                                    Screen.Sports -> sportsFirstFocusRequester.requestFocus()
                                    Screen.Settings -> settingsFirstFocusRequester.requestFocus()
                                    else -> {}
                                }
                            } catch (_: Exception) {
                                TvFocusRescue.rescueFocus()
                            }
                        }
                    }
                )
            }
        }
    }
}
