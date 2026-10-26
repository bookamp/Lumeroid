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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.getlume.android.LumeApp
import org.getlume.android.core.data.entities.CategoryEntity
import org.getlume.android.core.data.entities.ChannelEntity
import org.getlume.android.core.data.entities.WatchProgressEntity
import org.getlume.android.core.designsystem.*
import org.getlume.android.core.player.PlayableMedia
import org.getlume.android.core.player.toPlayableMedia
import org.getlume.android.core.settings.AppSettings
import org.getlume.android.core.settings.ContentFilterCache

sealed class MobileLiveSection(val id: String, val title: String) {
    object Favorites : MobileLiveSection("favorites", "Favorites")
    object RecentlyWatched : MobileLiveSection("recent", "Recently Watched")
    data class Category(val category: CategoryEntity) : MobileLiveSection(category.id, category.name)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileLiveTvScreen(
    onPlayMedia: (PlayableMedia) -> Unit
) {
    val db = remember { LumeApp.instance.database }
    val scope = rememberCoroutineScope()
    val settings = remember { AppSettings.get() }

    val activePlaylist by db.playlistDao().observeActivePlaylist().collectAsState(initial = null)
    val playlistId = activePlaylist?.id ?: ""
    val activeProfileId = settings.activeProfileId

    // 1. Live categories ONLY (type = 'live') in provider sortOrder
    val categories by db.categoryDao().getCategories(playlistId, "live").collectAsState(initial = emptyList())
    val favoriteChannels by db.channelDao().getFavoriteChannels(playlistId).collectAsState(initial = emptyList())
    val recentChannels by db.channelDao().getRecentlyWatchedChannels(playlistId, activeProfileId).collectAsState(initial = emptyList())
    val allChannels by db.channelDao().observeChannelsByPlaylist(playlistId).collectAsState(initial = emptyList())

    // 2. Language content filtering
    val isFiltering by ContentFilterCache.isFiltering.collectAsState()
    val allowedLiveCatIds by ContentFilterCache.liveCategoryIds.collectAsState()
    val allowedChannelIds by ContentFilterCache.matchingChannelIds.collectAsState()

    val visibleCategories = remember(categories, isFiltering, allowedLiveCatIds) {
        if (!isFiltering) categories
        else categories.filter { allowedLiveCatIds.contains(it.id) }
    }

    val visibleFavoriteChannels = remember(favoriteChannels, isFiltering, allowedChannelIds) {
        if (!isFiltering) favoriteChannels
        else favoriteChannels.filter { allowedChannelIds.contains(it.id) }
    }

    val visibleRecentChannels = remember(recentChannels, isFiltering, allowedChannelIds) {
        if (!isFiltering) recentChannels
        else recentChannels.filter { allowedChannelIds.contains(it.id) }
    }

    // 3. Selection state
    var selectedSection by remember { mutableStateOf<MobileLiveSection>(MobileLiveSection.Favorites) }
    var searchQuery by remember { mutableStateOf("") }
    var showCategorySheet by remember { mutableStateOf(false) }

    // Seed initial section: Favorites -> Recently Watched -> First Category
    LaunchedEffect(visibleCategories, visibleFavoriteChannels, visibleRecentChannels) {
        if (selectedSection == MobileLiveSection.Favorites && visibleFavoriteChannels.isEmpty()) {
            selectedSection = when {
                visibleRecentChannels.isNotEmpty() -> MobileLiveSection.RecentlyWatched
                visibleCategories.isNotEmpty() -> MobileLiveSection.Category(visibleCategories.first())
                else -> MobileLiveSection.Favorites
            }
        }
    }

    // 4. Resolve channels for current selection
    val activeChannels = remember(
        selectedSection,
        allChannels,
        visibleFavoriteChannels,
        visibleRecentChannels,
        isFiltering,
        allowedChannelIds
    ) {
        when (val section = selectedSection) {
            is MobileLiveSection.Favorites -> visibleFavoriteChannels
            is MobileLiveSection.RecentlyWatched -> visibleRecentChannels
            is MobileLiveSection.Category -> {
                val catChannels = allChannels.filter { it.categoryId == section.category.id && !it.isHidden }
                if (!isFiltering) catChannels
                else catChannels.filter { allowedChannelIds.contains(it.id) }
            }
        }
    }

    val displayedChannels = remember(activeChannels, searchQuery) {
        if (searchQuery.isBlank()) activeChannels
        else activeChannels.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090B10))
    ) {
        // iOS-style Category Selector Bar (Dropdown Button)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF141923))
                .clickable { showCategorySheet = true }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                val icon = when (selectedSection) {
                    is MobileLiveSection.Favorites -> Icons.Default.Favorite
                    is MobileLiveSection.RecentlyWatched -> Icons.Default.History
                    is MobileLiveSection.Category -> Icons.Default.Tv
                }
                val iconTint = when (selectedSection) {
                    is MobileLiveSection.Favorites -> LumeLiveRed
                    is MobileLiveSection.RecentlyWatched -> LumeCyan
                    is MobileLiveSection.Category -> LumeAccentBlue
                }

                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))

                Column {
                    Text(
                        text = selectedSection.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${displayedChannels.size} Channels",
                        color = LumeTextSecondary,
                        fontSize = 11.5.sp
                    )
                }
            }

            Icon(
                Icons.Default.UnfoldMore,
                contentDescription = "Select Category",
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }

        // Quick Category Pills Bar (Horizontal scroll)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            // Favorites Pill
            item {
                CategoryPill(
                    label = "Favorites (${visibleFavoriteChannels.size})",
                    icon = Icons.Default.Favorite,
                    iconTint = if (selectedSection is MobileLiveSection.Favorites) Color.White else LumeLiveRed,
                    isSelected = selectedSection is MobileLiveSection.Favorites,
                    onClick = { selectedSection = MobileLiveSection.Favorites }
                )
            }

            // Recently Watched Pill
            item {
                CategoryPill(
                    label = "Recently Watched (${visibleRecentChannels.size})",
                    icon = Icons.Default.History,
                    iconTint = if (selectedSection is MobileLiveSection.RecentlyWatched) Color.White else LumeCyan,
                    isSelected = selectedSection is MobileLiveSection.RecentlyWatched,
                    onClick = { selectedSection = MobileLiveSection.RecentlyWatched }
                )
            }

            // Real Live Categories in provider sortOrder
            items(visibleCategories, key = { it.id }) { cat ->
                CategoryPill(
                    label = cat.name,
                    isSelected = (selectedSection as? MobileLiveSection.Category)?.category?.id == cat.id,
                    onClick = { selectedSection = MobileLiveSection.Category(cat) }
                )
            }
        }

        // Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Filter ${selectedSection.title} channels...", color = LumeTextSecondary, fontSize = 12.5.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = LumeTextSecondary, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF141923),
                unfocusedContainerColor = Color(0xFF141923),
                focusedBorderColor = LumeAccentBlue,
                unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        )

        // Recently Watched Clear Action Bar
        if (selectedSection is MobileLiveSection.RecentlyWatched && visibleRecentChannels.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Watch History", color = LumeTextSecondary, fontSize = 11.5.sp)
                TextButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            db.watchProgressDao().clearRecentlyWatchedChannels(activeProfileId)
                        }
                    },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Clear All", color = LumeLiveRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Channel Lineup List
        if (displayedChannels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        if (selectedSection is MobileLiveSection.Favorites) Icons.Default.FavoriteBorder else Icons.Default.TvOff,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = if (selectedSection is MobileLiveSection.Favorites) "No Favorites Starred Yet"
                        else if (selectedSection is MobileLiveSection.RecentlyWatched) "No Recently Watched Channels"
                        else "No channels found in ${selectedSection.title}",
                        color = LumeTextSecondary,
                        fontSize = 13.5.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(displayedChannels, key = { it.id }) { channel ->
                    MobileChannelCard(
                        channel = channel,
                        onPlay = {
                            // Record watch history
                            scope.launch(Dispatchers.IO) {
                                db.watchProgressDao().saveProgress(
                                    WatchProgressEntity(
                                        contentId = channel.id,
                                        profileId = activeProfileId,
                                        title = channel.name,
                                        posterUrl = channel.logoUrl,
                                        contentType = "channel",
                                        positionMs = 0L,
                                        durationMs = 0L,
                                        lastWatchedAt = System.currentTimeMillis(),
                                        isCompleted = false
                                    )
                                )
                            }
                            onPlayMedia(channel.toPlayableMedia(lineup = displayedChannels))
                        },
                        onToggleFavorite = {
                            scope.launch(Dispatchers.IO) {
                                db.channelDao().updateFavorite(channel.id, !channel.isFavorite)
                            }
                        }
                    )
                }
            }
        }
    }

    // Category Selector Bottom Sheet
    if (showCategorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showCategorySheet = false },
            containerColor = Color(0xFF141923),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) }
        ) {
            var sheetSearch by remember { mutableStateOf("") }
            val sheetFilteredCategories = remember(visibleCategories, sheetSearch) {
                if (sheetSearch.isBlank()) visibleCategories
                else visibleCategories.filter { it.name.contains(sheetSearch, ignoreCase = true) }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Select Category", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = sheetSearch,
                    onValueChange = { sheetSearch = it },
                    placeholder = { Text("Search categories...", color = LumeTextSecondary, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = LumeTextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Favorites row
                    item {
                        CategorySheetRow(
                            title = "Favorites",
                            icon = Icons.Default.Favorite,
                            iconTint = LumeLiveRed,
                            count = visibleFavoriteChannels.size,
                            isSelected = selectedSection is MobileLiveSection.Favorites,
                            onClick = {
                                selectedSection = MobileLiveSection.Favorites
                                showCategorySheet = false
                            }
                        )
                    }

                    // Recently watched row
                    item {
                        CategorySheetRow(
                            title = "Recently Watched",
                            icon = Icons.Default.History,
                            iconTint = LumeCyan,
                            count = visibleRecentChannels.size,
                            isSelected = selectedSection is MobileLiveSection.RecentlyWatched,
                            onClick = {
                                selectedSection = MobileLiveSection.RecentlyWatched
                                showCategorySheet = false
                            }
                        )
                    }

                    // Provider live categories in order
                    items(sheetFilteredCategories, key = { it.id }) { cat ->
                        CategorySheetRow(
                            title = cat.name,
                            icon = Icons.Default.Tv,
                            iconTint = LumeAccentBlue,
                            count = null,
                            isSelected = (selectedSection as? MobileLiveSection.Category)?.category?.id == cat.id,
                            onClick = {
                                selectedSection = MobileLiveSection.Category(cat)
                                showCategorySheet = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    iconTint: Color = Color.White,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) LumeAccentBlue else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(14.dp))
            }
            Text(
                text = label,
                color = if (isSelected) Color.White else LumeTextSecondary,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun CategorySheetRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    count: Int? = null,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) LumeAccentBlue.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            Text(
                text = title,
                color = if (isSelected) LumeAccentBlue else Color.White,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
        if (count != null) {
            Text("$count", color = LumeTextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MobileChannelCard(
    channel: ChannelEntity,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onPlay),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141923)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Channel Logo or Number Badge
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.06f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (channel.logoUrl.isNotBlank()) {
                        AsyncImage(
                            model = channel.logoUrl,
                            contentDescription = channel.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(4.dp)
                        )
                    } else {
                        Text(
                            text = if (channel.channelNumber > 0) "#${channel.channelNumber}" else "TV",
                            color = LumeTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Name & Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(LumeLiveRed.copy(alpha = 0.2f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text("LIVE", color = LumeLiveRed, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                        }
                        if (channel.channelNumber > 0) {
                            Text("CH ${channel.channelNumber}", color = LumeTextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }

            // Actions: Star Favorite & Play Icon
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (channel.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (channel.isFavorite) Color(0xFFFFB800) else Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = LumeAccentBlue,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
