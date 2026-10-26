package org.getlume.android.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
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
import org.getlume.android.LumeApp
import org.getlume.android.core.data.BrowseCache
import org.getlume.android.core.data.entities.CategoryEntity
import org.getlume.android.core.data.entities.SeriesEntity
import org.getlume.android.core.designsystem.*
import org.getlume.android.core.player.PlayableMedia
import org.getlume.android.core.player.toPlayableMedia
import org.getlume.android.core.settings.ContentFilterCache

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileSeriesScreen(
    onPlayMedia: (PlayableMedia) -> Unit
) {
    val db = LumeApp.instance.database
    val activePlaylist by db.playlistDao().observeActivePlaylist().collectAsState(initial = null)
    val playlistId = activePlaylist?.id ?: ""

    // 1. Live/Cached Series Data from BrowseCache (Exact Shield TV Parity)
    val cachedRecentlyWatched by BrowseCache.recentlyWatchedSeries.collectAsState()
    val cachedFavorites by BrowseCache.favoriteSeries.collectAsState()
    val cachedRecentlyAdded by BrowseCache.recentlyAddedSeries.collectAsState()
    val previewCategoryMap by BrowseCache.previewCategorySeries.collectAsState()

    // 2. Raw series & categories for search / category drill-in
    val seriesList by db.vodDao().getSeries(playlistId).collectAsState(initial = emptyList())
    val categories by db.categoryDao().getCategories(playlistId, "series").collectAsState(initial = emptyList())

    // 3. Language Content Filtering
    val isFiltering by ContentFilterCache.isFiltering.collectAsState()
    val allowedSeriesCatIds by ContentFilterCache.seriesCategoryIds.collectAsState()

    val visibleCategories = remember(categories, isFiltering, allowedSeriesCatIds) {
        if (!isFiltering) categories
        else categories.filter { allowedSeriesCatIds.contains(it.id) }
    }

    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedSeriesForEpisodes by remember { mutableStateOf<SeriesEntity?>(null) }

    val filteredSeries = remember(seriesList, visibleCategories, selectedCategoryId, searchQuery, isFiltering, allowedSeriesCatIds) {
        val baseSeries = if (!isFiltering) seriesList else seriesList.filter { allowedSeriesCatIds.contains(it.categoryId) }
        baseSeries.filter { s ->
            val matchesCategory = selectedCategoryId == null || s.categoryId == selectedCategoryId
            val matchesSearch = searchQuery.isBlank() || s.name.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090B10))
    ) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search series...", color = LumeTextSecondary, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = LumeTextSecondary) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF141923),
                unfocusedContainerColor = Color(0xFF141923),
                focusedBorderColor = LumeAccentBlue,
                unfocusedBorderColor = Color.White.copy(alpha = 0.10f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Category Pills Row
        if (visibleCategories.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            ) {
                item {
                    val isSelected = selectedCategoryId == null
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) LumeAccentBlue else Color.White.copy(alpha = 0.08f))
                            .clickable { selectedCategoryId = null }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("All Series", color = Color.White, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                items(visibleCategories, key = { it.id }) { cat ->
                    val isSelected = selectedCategoryId == cat.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) LumeAccentBlue else Color.White.copy(alpha = 0.08f))
                            .clickable { selectedCategoryId = cat.id }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(cat.name, color = Color.White, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Content Display: Browse Rails Mode vs Grid Drill-in Mode
        val isBrowseMode = selectedCategoryId == null && searchQuery.isBlank()

        if (isBrowseMode) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // 1. Recently Watched Rail (if any)
                if (cachedRecentlyWatched.isNotEmpty()) {
                    item(key = "series_recently_watched") {
                        Column {
                            MobileSeriesSectionHeader(
                                title = "RECENTLY WATCHED",
                                actionLabel = "See All",
                                onAction = {}
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                items(cachedRecentlyWatched.take(20), key = { "rw_s_${it.second.id}" }) { (progress, s) ->
                                    MobileSeriesPosterCard(
                                        series = s,
                                        subtitle = if (s.rating > 0) "★ %.1f".format(s.rating) else "",
                                        onClick = { selectedSeriesForEpisodes = s }
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Favorites Rail (if any)
                if (cachedFavorites.isNotEmpty()) {
                    item(key = "series_favorites") {
                        Column {
                            MobileSeriesSectionHeader(
                                title = "FAVORITES",
                                actionLabel = "See All",
                                onAction = {}
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                items(cachedFavorites.take(20), key = { "fav_s_${it.id}" }) { s ->
                                    MobileSeriesPosterCard(
                                        series = s,
                                        subtitle = if (s.rating > 0) "★ %.1f".format(s.rating) else "",
                                        onClick = { selectedSeriesForEpisodes = s }
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Recently Added Rail (1:1 Apple TV & Shield TV Parity)
                if (cachedRecentlyAdded.isNotEmpty()) {
                    item(key = "series_recently_added") {
                        Column {
                            MobileSeriesSectionHeader(
                                title = "RECENTLY ADDED",
                                actionLabel = "New Releases",
                                onAction = {}
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                items(cachedRecentlyAdded.take(20), key = { "ra_s_${it.id}" }) { s ->
                                    MobileSeriesPosterCard(
                                        series = s,
                                        subtitle = if (s.rating > 0) "★ %.1f".format(s.rating) else s.releaseDate.take(4),
                                        onClick = { selectedSeriesForEpisodes = s }
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Category Preview Rails (Top categories precomputed in BrowseCache)
                items(visibleCategories.take(6), key = { "cat_preview_s_${it.id}" }) { category ->
                    val preloaded = previewCategoryMap[category.id]
                    val catSeries = remember(preloaded, seriesList) {
                        preloaded ?: seriesList.filter { it.categoryId == category.id }.take(20)
                    }

                    if (catSeries.isNotEmpty()) {
                        Column {
                            MobileSeriesSectionHeader(
                                title = category.name.uppercase(),
                                actionLabel = "Browse All",
                                onAction = { selectedCategoryId = category.id }
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                items(catSeries.take(20), key = { "cs_${category.id}_${it.id}" }) { s ->
                                    MobileSeriesPosterCard(
                                        series = s,
                                        subtitle = if (s.rating > 0) "★ %.1f".format(s.rating) else s.releaseDate.take(4),
                                        onClick = { selectedSeriesForEpisodes = s }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Category Drill-in Grid or Search Results
            if (filteredSeries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No series found", color = LumeTextSecondary, fontSize = 14.sp)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredSeries, key = { it.id }) { s ->
                        MobileSeriesPosterCard(
                            series = s,
                            subtitle = if (s.rating > 0) "★ %.1f".format(s.rating) else s.releaseDate.take(4),
                            onClick = { selectedSeriesForEpisodes = s }
                        )
                    }
                }
            }
        }
    }

    // Episode Picker Bottom Sheet
    selectedSeriesForEpisodes?.let { series ->
        val episodes by db.vodDao().getEpisodesForSeries(series.seriesId).collectAsState(initial = emptyList())

        ModalBottomSheet(
            onDismissRequest = { selectedSeriesForEpisodes = null },
            containerColor = Color(0xFF141923),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(series.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (series.plot.isNotBlank()) {
                    Text(series.plot, color = LumeTextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text("EPISODES", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)

                if (episodes.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No episodes available", color = LumeTextSecondary, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 350.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(episodes, key = { it.id }) { ep ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .clickable {
                                        selectedSeriesForEpisodes = null
                                        onPlayMedia(ep.toPlayableMedia(seriesTitle = series.name, seriesPosterUrl = series.posterUrl, fullSeriesId = series.id))
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("S${ep.seasonNumber} E${ep.episodeNumber} • ${ep.title.ifBlank { "Episode ${ep.episodeNumber}" }}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play Episode", tint = LumeAccentBlue, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileSeriesSectionHeader(
    title: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        if (actionLabel.isNotBlank()) {
            Text(
                text = actionLabel,
                color = LumeAccentBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onAction)
            )
        }
    }
}

@Composable
private fun MobileSeriesPosterCard(
    series: SeriesEntity,
    subtitle: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(110.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .width(110.dp)
                .height(160.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF141923))
        ) {
            if (series.posterUrl.isNotBlank()) {
                AsyncImage(
                    model = series.posterUrl,
                    contentDescription = series.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = series.name,
            color = Color.White,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                color = LumeTextSecondary,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}
