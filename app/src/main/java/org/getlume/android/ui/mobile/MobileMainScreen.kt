package org.getlume.android.ui.mobile

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.getlume.android.LumeApp
import org.getlume.android.core.data.entities.ChannelEntity
import org.getlume.android.core.designsystem.*
import org.getlume.android.core.player.PlayableMedia
import org.getlume.android.core.player.toPlayableMedia

enum class MobileTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home),
    Movies("Movies", Icons.Default.Movie),
    Series("Series", Icons.Default.Tv),
    LiveTv("Live TV", Icons.Default.LiveTv),
    Sports("Sports", Icons.Default.SportsSoccer)
}

@Composable
fun MobileMainScreen(
    onPlayMedia: (PlayableMedia) -> Unit,
    onPlayChannel: ((ChannelEntity) -> Unit)? = null,
    onOpenMultiView: (() -> Unit)? = null
) {
    var selectedTab by remember { mutableStateOf(MobileTab.Home) }
    var showSearch by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val db = LumeApp.instance.database
    val activePlaylist by db.playlistDao().observeActivePlaylist().collectAsState(initial = null)

    val handlePlay: (PlayableMedia) -> Unit = { media ->
        onPlayMedia(media)
    }

    if (showSearch) {
        MobileSearchScreen(
            onPlayMedia = handlePlay,
            onClose = { showSearch = false }
        )
    } else if (showSettings) {
        MobileSettingsScreen(
            onDismiss = { showSettings = false }
        )
    } else {
        Scaffold(
            containerColor = Color(0xFF090B10),
            topBar = {
                // Top App Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0E121A))
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Lume",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                        if (activePlaylist != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = activePlaylist!!.name,
                                    color = LumeTextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                        }
                    }
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = Color(0xFF0E121A),
                    contentColor = Color.White,
                    tonalElevation = 0.dp
                ) {
                    MobileTab.values().forEach { tab ->
                        val isSelected = selectedTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { selectedTab = tab },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label
                                )
                            },
                            label = {
                                Text(
                                    text = tab.label,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = Color.White,
                                unselectedIconColor = Color.White.copy(alpha = 0.45f),
                                unselectedTextColor = Color.White.copy(alpha = 0.45f),
                                indicatorColor = LumeAccentBlue
                            )
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (selectedTab) {
                    MobileTab.Home -> {
                        MobileHomeScreen(
                            onPlayMedia = handlePlay,
                            onNavigateToMovies = { selectedTab = MobileTab.Movies },
                            onNavigateToSeries = { selectedTab = MobileTab.Series },
                            onNavigateToLiveTv = { selectedTab = MobileTab.LiveTv },
                            onNavigateToSports = { selectedTab = MobileTab.Sports }
                        )
                    }
                    MobileTab.Movies -> {
                        MobileMoviesScreen(onPlayMedia = handlePlay)
                    }
                    MobileTab.Series -> {
                        MobileSeriesScreen(onPlayMedia = handlePlay)
                    }
                    MobileTab.LiveTv -> {
                        MobileLiveTvScreen(onPlayMedia = handlePlay)
                    }
                    MobileTab.Sports -> {
                        MobileSportsScreen(onPlayMedia = handlePlay)
                    }
                }
            }
        }
    }
}
