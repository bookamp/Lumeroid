package org.getlume.android.ui.tv

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import org.getlume.android.core.settings.AppSettings
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.getlume.android.LumeApp
import org.getlume.android.core.data.entities.ChannelEntity
import org.getlume.android.core.data.entities.MovieEntity
import org.getlume.android.core.data.entities.SeriesEntity
import org.getlume.android.core.designsystem.*
import org.getlume.android.core.player.PlayableMedia
import org.getlume.android.core.player.toPlayableMedia
import java.util.Locale

/**
 * Filter options matching Lume's ContentFilter in SearchView.swift:
 * All, Movies, Series, Live TV
 */
enum class SearchContentFilter(val label: String) {
    ALL("All"),
    MOVIES("Movies"),
    SERIES("Series"),
    LIVE_TV("Live TV")
}

/**
 * Universal search result item mirroring SearchResult in SearchView.swift
 */
sealed class SearchResultItem {
    abstract val id: String
    abstract val title: String
    abstract val subtitle: String
    abstract val imageUrl: String

    data class Movie(val movie: MovieEntity) : SearchResultItem() {
        override val id: String = "movie-${movie.id}"
        override val title: String = movie.name.trim()
        override val subtitle: String = if (movie.releaseDate.isNotBlank()) movie.releaseDate else if (movie.durationMinutes > 0) "${movie.durationMinutes}m" else "Movie"
        override val imageUrl: String = movie.posterUrl
    }

    data class Series(val series: SeriesEntity) : SearchResultItem() {
        override val id: String = "series-${series.id}"
        override val title: String = series.name.trim()
        override val subtitle: String = if (series.releaseDate.isNotBlank()) series.releaseDate else "Series"
        override val imageUrl: String = series.posterUrl
    }

    data class Channel(val channel: ChannelEntity) : SearchResultItem() {
        override val id: String = "channel-${channel.id}"
        override val title: String = channel.name.trim()
        override val subtitle: String = "Live"
        override val imageUrl: String = channel.logoUrl
    }
}

/**
 * Apple TV Faithful Global Search Screen, mirroring SearchView.swift:
 * - Search bar with prompt "Movies, Series, Live TV..."
 * - Segmented ContentFilter pills (All, Movies, Series, Live TV)
 * - 500ms remote-friendly debounce
 * - Asynchronous parallel Room DB querying across Movies, Series, and Channels
 * - Interactive detail modal sheets for Movies & Series, and 1-click channel playback
 * - Breadcrumb tree navigation (Player -> Details -> Results -> Filters/Input -> TopBar -> Home)
 */
@Composable
fun TvSearchScreen(
    onPlayMedia: (PlayableMedia) -> Unit,
    firstItemFocusRequester: FocusRequester = remember { FocusRequester() },
    onMoveUpToTopBar: (() -> Unit)? = null
) {
    val db = LumeApp.instance.database
    val activePlaylist by db.playlistDao().observeActivePlaylist().collectAsState(initial = null)
    val playlistId = activePlaylist?.id ?: ""

    var searchText by remember { mutableStateOf("") }
    var debouncedSearchText by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(SearchContentFilter.ALL) }
    var isLoading by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<SearchResultItem>>(emptyList()) }

    var isInResultsGrid by remember { mutableStateOf(false) }
    var selectedMovie by remember { mutableStateOf<MovieEntity?>(null) }
    var selectedSeries by remember { mutableStateOf<SeriesEntity?>(null) }

    val filterPillsFocusRequester = remember { FocusRequester() }
    val resultsGridFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // Debounce search input (500ms remote pause, matching tvOS debounce in SearchView.swift)
    LaunchedEffect(searchText) {
        val trimmed = searchText.trim()
        if (trimmed.isEmpty()) {
            debouncedSearchText = ""
            searchResults = emptyList()
            isLoading = false
        } else {
            isLoading = true
            delay(500)
            debouncedSearchText = trimmed
        }
    }

    val settings = remember { AppSettings.get() }
    val revision by settings.revision.collectAsState()

    // Execute multi-type search when query, filter, playlist, or settings change
    LaunchedEffect(debouncedSearchText, selectedFilter, playlistId, revision) {
        val query = debouncedSearchText.trim()
        if (query.isEmpty()) {
            searchResults = emptyList()
            isLoading = false
            return@LaunchedEffect
        }

        isLoading = true
        withContext(Dispatchers.IO) {
            val wantMovies = selectedFilter == SearchContentFilter.ALL || selectedFilter == SearchContentFilter.MOVIES
            val wantSeries = selectedFilter == SearchContentFilter.ALL || selectedFilter == SearchContentFilter.SERIES
            val wantLive = selectedFilter == SearchContentFilter.ALL || selectedFilter == SearchContentFilter.LIVE_TV

            val searchPlaylistId = if (settings.searchAllPlaylists) "" else playlistId

            val movies = if (wantMovies) db.vodDao().searchMovies(searchPlaylistId, query) else emptyList()
            val series = if (wantSeries) db.vodDao().searchSeries(searchPlaylistId, query) else emptyList()
            val channels = if (wantLive) db.channelDao().searchChannels(searchPlaylistId, query) else emptyList()

            val combined = mutableListOf<SearchResultItem>()
            movies.forEach { combined.add(SearchResultItem.Movie(it)) }
            series.forEach { combined.add(SearchResultItem.Series(it)) }
            channels.forEach { combined.add(SearchResultItem.Channel(it)) }

            withContext(Dispatchers.Main) {
                searchResults = combined
                isLoading = false
            }
        }
    }

    // Breadcrumb BackHandler 1: Dismiss detail modal sheets first
    if (selectedMovie != null) {
        BackHandler { selectedMovie = null }
    }
    if (selectedSeries != null) {
        BackHandler { selectedSeries = null }
    }

    // Breadcrumb BackHandler 2: When in results grid, Back moves focus back up to the search input / filter
    BackHandler(enabled = isInResultsGrid && selectedMovie == null && selectedSeries == null) {
        try {
            firstItemFocusRequester.requestFocus()
        } catch (_: Exception) {}
        isInResultsGrid = false
    }

    // Breadcrumb BackHandler 3: When at top level (search bar/filter), Back clears query or retracts to TopBar
    BackHandler(enabled = !isInResultsGrid && selectedMovie == null && selectedSeries == null) {
        if (searchText.isNotEmpty()) {
            searchText = ""
        } else {
            onMoveUpToTopBar?.invoke()
        }
    }

    TvAmbientBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 80.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar Input Field
                TvSearchInputBar(
                    text = searchText,
                    onTextChange = { searchText = it },
                    focusRequester = firstItemFocusRequester,
                    onMoveUp = onMoveUpToTopBar,
                    onMoveDown = {
                        try {
                            filterPillsFocusRequester.requestFocus()
                            true
                        } catch (_: Exception) {
                            false
                        }
                    },
                    onClear = { searchText = "" }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Segmented Content Filter Pills [ All ] [ Movies ] [ Series ] [ Live TV ]
                TvSearchFilterSegmentedRow(
                    selectedFilter = selectedFilter,
                    focusRequester = filterPillsFocusRequester,
                    onSelectFilter = { selectedFilter = it },
                    onMoveUp = {
                        try {
                            firstItemFocusRequester.requestFocus()
                            true
                        } catch (_: Exception) {
                            false
                        }
                    },
                    onMoveDown = {
                        if (searchResults.isNotEmpty()) {
                            try {
                                resultsGridFocusRequester.requestFocus()
                                true
                            } catch (_: Exception) {
                                false
                            }
                        } else false
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Main Content Body: Empty State, Loading, No Results, or Results Grid
                when {
                    debouncedSearchText.isEmpty() && searchText.isEmpty() -> {
                        // Apple TV ContentUnavailableView ("Search for movies, series, or live TV channels")
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.25f),
                                    modifier = Modifier.size(72.dp)
                                )
                                Text(
                                    text = "Search",
                                    color = Color.White,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Search for movies, series, or live TV channels",
                                    color = LumeTextSecondary,
                                    fontSize = 15.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = LumeAccentBlue, modifier = Modifier.size(36.dp))
                        }
                    }

                    searchResults.isEmpty() -> {
                        // No Results State
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.20f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Text(
                                    text = "No Results",
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "No matches found for \"$debouncedSearchText\"",
                                    color = LumeTextSecondary,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }

                    else -> {
                        // Results Section
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .onFocusChanged { if (it.hasFocus) isInResultsGrid = true }
                        ) {
                            Text(
                                text = "${searchResults.size} Results",
                                color = LumeTextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(6),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp),
                                contentPadding = PaddingValues(bottom = 48.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(searchResults) { index, item ->
                                    val itemFocusRequester = if (index == 0) resultsGridFocusRequester else null

                                    TvSearchResultCard(
                                        item = item,
                                        focusRequester = itemFocusRequester,
                                        onMoveUp = if (index < 6) {
                                            {
                                                try {
                                                    filterPillsFocusRequester.requestFocus()
                                                    isInResultsGrid = false
                                                    true
                                                } catch (_: Exception) {
                                                    false
                                                }
                                            }
                                        } else null,
                                        onClick = {
                                            when (item) {
                                                is SearchResultItem.Movie -> selectedMovie = item.movie
                                                is SearchResultItem.Series -> selectedSeries = item.series
                                                is SearchResultItem.Channel -> onPlayMedia(item.channel.toPlayableMedia())
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // In-Place Movie Detail Modal Sheet
            selectedMovie?.let { movie ->
                TvMovieDetailSheet(
                    movie = movie,
                    onPlay = {
                        onPlayMedia(movie.toPlayableMedia())
                    },
                    onDismiss = { selectedMovie = null }
                )
            }

            // In-Place Series Detail Modal Sheet
            selectedSeries?.let { series ->
                activePlaylist?.let { playlist ->
                    TvSeriesDetailSheet(
                        series = series,
                        playlist = playlist,
                        onPlayEpisode = { episode ->
                            onPlayMedia(episode.toPlayableMedia(series.name))
                        },
                        onDismiss = { selectedSeries = null }
                    )
                }
            }
        }
    }
}

/**
 * Apple TV Frosted Glass Search Input Bar
 */
@Composable
private fun TvSearchInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Boolean)? = null,
    onClear: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(targetValue = if (isFocused) 1.02f else 1.0f, label = "searchBarScale")
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) Color.White else Color.White.copy(alpha = 0.16f),
        animationSpec = tween(150),
        label = "searchBorder"
    )

    Row(
        modifier = Modifier
            .width(580.dp)
            .height(50.dp)
            .scale(scale)
            .clip(RoundedCornerShape(25.dp))
            .background(Color.White.copy(alpha = if (isFocused) 0.18f else 0.10f))
            .border(if (isFocused) 2.dp else 1.dp, borderColor, RoundedCornerShape(25.dp))
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            onMoveUp?.invoke()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            onMoveDown?.invoke() ?: false
                        }
                        else -> false
                    }
                } else false
            }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = if (isFocused) Color.White else Color.White.copy(alpha = 0.60f),
            modifier = Modifier.size(22.dp)
        )

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (text.isEmpty()) {
                Text(
                    text = "Movies, Series, Live TV...",
                    color = Color.White.copy(alpha = 0.40f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal
                )
            }

            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(LumeAccentBlue),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    onMoveDown?.invoke()
                }),
                interactionSource = interactionSource,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (text.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.20f))
                    .clickable(onClick = onClear),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * Apple TV Segmented Filter Control: [ All ] [ Movies ] [ Series ] [ Live TV ]
 */
@Composable
private fun TvSearchFilterSegmentedRow(
    selectedFilter: SearchContentFilter,
    focusRequester: FocusRequester,
    onSelectFilter: (SearchContentFilter) -> Unit,
    onMoveUp: () -> Boolean,
    onMoveDown: () -> Boolean
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.40f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchContentFilter.values().forEachIndexed { index, filter ->
            val isSelected = selectedFilter == filter
            val interactionSource = remember { MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()
            val scale by animateFloatAsState(targetValue = if (isFocused) 1.08f else 1.0f, label = "filterScale")

            val bg = when {
                isFocused -> Color.White
                isSelected -> LumeAccentBlue
                else -> Color.Transparent
            }

            val textCol = when {
                isFocused -> Color.Black
                isSelected -> Color.White
                else -> Color.White.copy(alpha = 0.70f)
            }

            val itemModifier = if (index == 0) Modifier.focusRequester(focusRequester) else Modifier

            Box(
                modifier = Modifier
                    .then(itemModifier)
                    .scale(scale)
                    .clip(RoundedCornerShape(16.dp))
                    .background(bg)
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_CENTER,
                                KeyEvent.KEYCODE_ENTER,
                                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                    onSelectFilter(filter)
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_UP -> onMoveUp()
                                KeyEvent.KEYCODE_DPAD_DOWN -> onMoveDown()
                                else -> false
                            }
                        } else false
                    }
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onSelectFilter(filter) }
                    )
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = filter.label,
                    color = textCol,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Universal Search Result Poster Card for Movies, Series, and Live TV Channels
 */
@Composable
private fun TvSearchResultCard(
    item: SearchResultItem,
    focusRequester: FocusRequester? = null,
    onMoveUp: (() -> Boolean)? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.08f else 1.0f, label = "resultCardScale")

    val focusMod = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier

    Column(
        modifier = Modifier
            .then(focusMod)
            .scale(scale)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    onMoveUp?.invoke() ?: false
                } else false
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 2:3 Aspect Ratio Card with Focus Highlight Border
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
            if (item.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.title,
                    contentScale = if (item is SearchResultItem.Channel) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().padding(if (item is SearchResultItem.Channel) 16.dp else 0.dp)
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (item is SearchResultItem.Channel) Icons.Default.Tv else Icons.Default.Search,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.30f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Top-Right Badge: Type Capsule (MOVIE, SERIES, LIVE TV)
            val badgeColor = when (item) {
                is SearchResultItem.Movie -> LumeAccentBlue
                is SearchResultItem.Series -> Color(0xFF9D4EDD)
                is SearchResultItem.Channel -> Color(0xFF38B000)
            }
            val badgeText = when (item) {
                is SearchResultItem.Movie -> "MOVIE"
                is SearchResultItem.Series -> "SERIES"
                is SearchResultItem.Channel -> "LIVE"
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(badgeColor.copy(alpha = 0.85f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badgeText,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Title
        Text(
            text = item.title,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Subtitle (Genre, Date, or Live)
        if (item.subtitle.isNotBlank()) {
            Text(
                text = item.subtitle,
                color = LumeTextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
