package org.getlume.android.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.key.KeyEvent
import org.getlume.android.LumeApp
import org.getlume.android.core.data.entities.CategoryEntity
import org.getlume.android.core.data.entities.EpisodeEntity
import org.getlume.android.core.data.entities.SeriesEntity
import org.getlume.android.core.designsystem.*
import org.getlume.android.core.network.SeriesDetailsResult
import org.getlume.android.core.network.XtreamCodesClient
import org.getlume.android.core.player.PlayableMedia
import org.getlume.android.core.player.toPlayableMedia
import org.getlume.android.core.sync.ContentSyncManager

/**
 * Dedicated Series Tab for Apple TV Lume on Android TV.
 * Categories sidebar on the left, series grid on the right, and interactive Series Details & Episode selector modal.
 */
@Composable
fun TvSeriesScreen(
    onPlayMedia: (PlayableMedia) -> Unit,
    firstItemFocusRequester: FocusRequester = remember { FocusRequester() },
    onMoveUpToTopBar: (() -> Unit)? = null
) {
    val db = LumeApp.instance.database
    val activePlaylist by db.playlistDao().observeActivePlaylist().collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()

    val categories by db.categoryDao().getCategories(activePlaylist?.id ?: "", "series").collectAsState(initial = emptyList())
    var selectedCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var selectedSeries by remember { mutableStateOf<SeriesEntity?>(null) }
    var isLoadingCategory by remember { mutableStateOf(false) }

    // Default to first category
    LaunchedEffect(categories) {
        if (selectedCategory == null && categories.isNotEmpty()) {
            selectedCategory = categories.first()
        }
    }

    // Initial check: if no categories, fetch Series categories
    LaunchedEffect(activePlaylist) {
        activePlaylist?.let { playlist ->
            if (categories.isEmpty()) {
                ContentSyncManager.syncAllCategories(playlist)
            }
        }
    }

    // Observe series for the selected category
    val seriesForCategory by if (selectedCategory != null) {
        db.vodDao().getSeriesByCategory(activePlaylist?.id ?: "", selectedCategory!!.id).collectAsState(initial = emptyList())
    } else {
        db.vodDao().getSeries(activePlaylist?.id ?: "", 100).collectAsState(initial = emptyList())
    }

    // Auto-fetch series for category if not yet cached in Room
    LaunchedEffect(selectedCategory) {
        val cat = selectedCategory
        val pl = activePlaylist
        if (cat != null && pl != null && seriesForCategory.isEmpty()) {
            isLoadingCategory = true
            ContentSyncManager.loadCategorySeries(pl, cat)
            isLoadingCategory = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07090E))
            .padding(top = 80.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Column: Series Categories Sidebar
            Column(
                modifier = Modifier
                    .width(260.dp)
                    .fillMaxHeight()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(vertical = 16.dp, horizontal = 16.dp)
            ) {
                Text(
                    text = "CATEGORIES",
                    color = LumeTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )

                if (categories.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = LumeAccentBlue, modifier = Modifier.size(32.dp))
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        itemsIndexed(categories) { index, category ->
                            val isSelected = selectedCategory?.id == category.id
                            TvSeriesCategoryItem(
                                category = category,
                                isSelected = isSelected,
                                focusRequester = if (index == 0) firstItemFocusRequester else null,
                                onMoveUp = if (index == 0) onMoveUpToTopBar else null,
                                onClick = { selectedCategory = category }
                            )
                        }
                    }
                }
            }

            // Right Column: Series 2:3 Poster Grid
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                // Header: Category Title & Count
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = selectedCategory?.name ?: "All Series",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${seriesForCategory.size} shows",
                        color = LumeTextSecondary,
                        fontSize = 14.sp
                    )
                }

                if (isLoadingCategory && seriesForCategory.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = LumeAccentBlue)
                            Text("Loading series...", color = LumeTextSecondary, fontSize = 14.sp)
                        }
                    }
                } else if (seriesForCategory.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No series found in this category", color = LumeTextSecondary, fontSize = 16.sp)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 36.dp)
                    ) {
                        itemsIndexed(seriesForCategory) { index, series ->
                            TvSeriesCard(
                                series = series,
                                onMoveUp = if (index < 5) onMoveUpToTopBar else null,
                                onClick = { selectedSeries = series }
                            )
                        }
                    }
                }
            }
        }

        // Apple TV Faithful Series Detail Modal Sheet with Episodes
        selectedSeries?.let { series ->
            BackHandler { selectedSeries = null }
            activePlaylist?.let { playlist ->
                TvSeriesDetailSheet(
                    series = series,
                    playlist = playlist,
                    onPlayEpisode = { episode ->
                        // Keep selectedSeries active so returning from playback lands on the series details sheet
                        onPlayMedia(episode.toPlayableMedia(series.name))
                    },
                    onDismiss = { selectedSeries = null }
                )
            }
        }
    }
}

@Composable
private fun TvSeriesCategoryItem(
    category: CategoryEntity,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    onMoveUp: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.04f else 1.0f, label = "catScale")

    val bgColor = when {
        isFocused -> Color.White
        isSelected -> LumeAccentBlue.copy(alpha = 0.25f)
        else -> Color.Transparent
    }

    val textColor = when {
        isFocused -> Color.Black
        isSelected -> LumeAccentBlue
        else -> Color.White.copy(alpha = 0.85f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionUp) {
                    if (onMoveUp != null) {
                        onMoveUp()
                        true
                    } else false
                } else false
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Text(
            text = category.name,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TvSeriesCard(
    series: SeriesEntity,
    onMoveUp: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.08f else 1.0f, label = "cardScale")

    Column(
        modifier = Modifier
            .width(140.dp)
            .scale(scale)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionUp) {
                    if (onMoveUp != null) {
                        onMoveUp()
                        true
                    } else false
                } else false
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(
                    width = if (isFocused) 2.5.dp else 1.dp,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            if (series.posterUrl.isNotBlank()) {
                AsyncImage(
                    model = series.posterUrl,
                    contentDescription = series.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = series.name.take(2).uppercase(),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (series.rating > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(11.dp))
                        Text(
                            text = String.format("%.1f", series.rating),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Text(
            text = series.name,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.88f),
            fontSize = 13.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun TvSeriesDetailSheet(
    series: SeriesEntity,
    playlist: org.getlume.android.core.data.entities.PlaylistEntity,
    onPlayEpisode: (EpisodeEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val client = remember(playlist) {
        XtreamCodesClient(playlist.serverUrl, playlist.username, playlist.password)
    }

    var detailsResult by remember { mutableStateOf<SeriesDetailsResult?>(null) }
    var selectedSeason by remember { mutableStateOf<Int?>(null) }
    var isLoadingEpisodes by remember { mutableStateOf(true) }

    LaunchedEffect(series.seriesId) {
        isLoadingEpisodes = true
        detailsResult = client.fetchSeriesDetails(series.seriesId)
        selectedSeason = detailsResult?.seasons?.firstOrNull() ?: 1
        isLoadingEpisodes = false
    }

    val episodesForSeason = remember(detailsResult, selectedSeason) {
        detailsResult?.episodes?.filter { it.seasonNumber == (selectedSeason ?: 1) } ?: emptyList()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.90f))
            .padding(42.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF131722))
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(24.dp))
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Top Section: Poster & Overview
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.05f))
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

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = series.name,
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )

                        TvSettingsActionButton(
                            title = "Close",
                            onClick = onDismiss
                        )
                    }

                    if (series.rating > 0f) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
                            Text(String.format("%.1f", series.rating), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text(
                        text = if (series.plot.isNotBlank()) series.plot else detailsResult?.plot?.ifBlank { "Select an episode to begin watching." } ?: "Select an episode to begin watching.",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Season Selector Tabs
                    val seasons = detailsResult?.seasons ?: emptyList()
                    if (seasons.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            items(seasons) { seasonNum ->
                                val isSelected = selectedSeason == seasonNum
                                val interaction = remember { MutableInteractionSource() }
                                val isFocused by interaction.collectIsFocusedAsState()

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected || isFocused) LumeAccentBlue else Color.White.copy(alpha = 0.10f))
                                        .clickable(interactionSource = interaction, indication = null) {
                                            selectedSeason = seasonNum
                                        }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "Season $seasonNum",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Section: Episodes List
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "EPISODES",
                    color = LumeTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (isLoadingEpisodes) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = LumeAccentBlue)
                    }
                } else if (episodesForSeason.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No episodes found for this season", color = LumeTextSecondary)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(episodesForSeason) { episode ->
                            TvEpisodeRow(
                                episode = episode,
                                onClick = { onPlayEpisode(episode) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvEpisodeRow(
    episode: EpisodeEntity,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.02f else 1.0f, label = "epScale")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) Color.White else Color.White.copy(alpha = 0.05f))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) LumeAccentBlue else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Episode Number Badge
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isFocused) LumeAccentBlue else Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${episode.episodeNumber}",
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Title and Duration
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title,
                color = if (isFocused) Color.Black else Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (episode.plot.isNotBlank()) {
                Text(
                    text = episode.plot,
                    color = if (isFocused) Color.Black.copy(alpha = 0.7f) else LumeTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (episode.durationMinutes > 0) {
            Text(
                text = "${episode.durationMinutes}m",
                color = if (isFocused) Color.Black.copy(alpha = 0.7f) else LumeTextSecondary,
                fontSize = 13.sp
            )
        }

        Icon(
            Icons.Default.PlayArrow,
            contentDescription = "Play",
            tint = if (isFocused) Color.Black else LumeAccentBlue,
            modifier = Modifier.size(22.dp)
        )
    }
}
