package org.getlume.android.ui.mobile

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.getlume.android.LumeApp
import org.getlume.android.core.data.entities.CategoryEntity
import org.getlume.android.core.data.entities.ChannelEntity
import org.getlume.android.core.designsystem.*
import org.getlume.android.core.network.ESPNClient
import org.getlume.android.core.network.SportsFixture
import org.getlume.android.ui.navigation.Screen

@Composable
fun MobileMainScreen(
    onPlayChannel: (ChannelEntity) -> Unit,
    onOpenMultiView: () -> Unit
) {
    var selectedTab by remember { mutableStateOf<Screen>(Screen.LiveTv) }

    Scaffold(
        containerColor = LumeBackground,
        bottomBar = {
            NavigationBar(
                containerColor = LumeSurfaceElevated,
                contentColor = Color.White
            ) {
                val navItems = listOf(
                    Triple(Screen.LiveTv, Icons.Default.Tv, "Live TV"),
                    Triple(Screen.Sports, Icons.Default.SportsBasketball, "Sports"),
                    Triple(Screen.MultiView, Icons.Default.GridView, "Multi-View"),
                    Triple(Screen.Settings, Icons.Default.Settings, "Settings")
                )

                navItems.forEach { (screen, icon, label) ->
                    val selected = selectedTab == screen
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (screen == Screen.MultiView) {
                                onOpenMultiView()
                            } else {
                                selectedTab = screen
                            }
                        },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = LumeCyan,
                            selectedTextColor = LumeCyan,
                            unselectedIconColor = LumeTextSecondary,
                            unselectedTextColor = LumeTextSecondary,
                            indicatorColor = LumeSurfaceHighlight
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
                Screen.LiveTv -> MobileLiveTvTab(onPlayChannel = onPlayChannel)
                Screen.Sports -> MobileSportsTab(onPlayChannel = onPlayChannel)
                Screen.Settings -> MobileSettingsTab()
                else -> Unit
            }
        }
    }
}

@Composable
private fun MobileLiveTvTab(
    onPlayChannel: (ChannelEntity) -> Unit
) {
    val db = LumeApp.instance.database
    val activePlaylist by db.playlistDao().observeActivePlaylist().collectAsState(initial = null)
    val categories by db.categoryDao().observeCategories(activePlaylist?.id ?: "").collectAsState(initial = emptyList())

    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val allChannels by db.channelDao().observeChannelsByPlaylist(activePlaylist?.id ?: "").collectAsState(initial = emptyList())

    val filteredChannels = remember(allChannels, selectedCategoryId, searchQuery) {
        allChannels.filter { channel ->
            val matchesCategory = selectedCategoryId == null || channel.categoryId == selectedCategoryId
            val matchesSearch = searchQuery.isBlank() || channel.name.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // App Header & Search
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LumeSurfaceElevated)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Lume",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(LumeCyan)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("MOBILE", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Text(
                    "${filteredChannels.size} Channels",
                    color = LumeTextSecondary,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search live channels...", color = LumeTextSecondary, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = LumeTextSecondary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = LumeTextSecondary)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = LumeSurface,
                    unfocusedContainerColor = LumeSurface,
                    focusedBorderColor = LumeCyan,
                    unfocusedBorderColor = LumeSurfaceBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        }

        // Category Filter Chips
        if (categories.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedCategoryId == null,
                        onClick = { selectedCategoryId = null },
                        label = { Text("All Channels") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LumePrimary,
                            selectedLabelColor = Color.White,
                            containerColor = LumeSurfaceHighlight,
                            labelColor = LumeTextSecondary
                        )
                    )
                }
                items(categories) { category ->
                    FilterChip(
                        selected = selectedCategoryId == category.id,
                        onClick = { selectedCategoryId = category.id },
                        label = { Text(category.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LumePrimary,
                            selectedLabelColor = Color.White,
                            containerColor = LumeSurfaceHighlight,
                            labelColor = LumeTextSecondary
                        )
                    )
                }
            }
        }

        // Channel List
        if (filteredChannels.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.TvOff, contentDescription = null, tint = LumeTextSecondary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No channels found", color = LumeTextSecondary, fontSize = 15.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredChannels, key = { it.id }) { channel ->
                    MobileChannelCard(channel = channel, onClick = { onPlayChannel(channel) })
                }
            }
        }
    }
}

@Composable
private fun MobileChannelCard(
    channel: ChannelEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = LumeSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Channel Number Badge or Logo
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(LumeSurfaceHighlight),
                contentAlignment = Alignment.Center
            ) {
                if (channel.logoUrl != null) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(4.dp)
                    )
                } else {
                    Text(
                        text = channel.channelNumber.toString(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            // Channel Name & Category
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Live Stream",
                    color = LumeTextSecondary,
                    fontSize = 12.sp
                )
            }

            Icon(
                Icons.Default.PlayCircleOutline,
                contentDescription = "Play",
                tint = LumeCyan,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun MobileSportsTab(
    onPlayChannel: (ChannelEntity) -> Unit
) {
    val espnClient = remember { ESPNClient() }
    var fixtures by remember { mutableStateOf<List<SportsFixture>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoading = true
        fixtures = espnClient.fetchLiveScoreboards()
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Sports Hub",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Live scores & automatic IPTV channel matching",
            color = LumeTextSecondary,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LumeCyan)
            }
        } else if (fixtures.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No live sports fixtures right now", color = LumeTextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(fixtures) { fixture ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = LumeSurface)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    fixture.competition,
                                    color = LumeCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    fixture.status,
                                    color = if (fixture.status.contains("Live", ignoreCase = true)) LumeLiveRed else LumeTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(fixture.homeTeam, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                Text(fixture.homeScore ?: "-", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(fixture.awayTeam, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                Text(fixture.awayScore ?: "-", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }

                            if (fixture.broadcaster != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Airing on: ${fixture.broadcaster}",
                                    color = LumeTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileSettingsTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Settings", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = LumeSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Lume Android v1.0.0", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(
                    "Engineered for Google TV, NVIDIA Shield TV, Fire TV 4K, and Android Mobile.",
                    color = LumeTextSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}
