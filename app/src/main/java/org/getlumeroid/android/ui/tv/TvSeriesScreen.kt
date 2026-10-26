package org.getlumeroid.android.ui.tv

import kotlinx.coroutines.delay

import androidx.compose.ui.focus.focusProperties
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.getlumeroid.android.LumeroidApp
import org.getlumeroid.android.core.data.BrowseCache
import org.getlumeroid.android.core.data.entities.CategoryEntity
import org.getlumeroid.android.core.data.entities.SeriesEntity
import org.getlumeroid.android.core.data.entities.WatchProgressEntity
import org.getlumeroid.android.core.designsystem.*
import org.getlumeroid.android.core.player.PlayableMedia
import org.getlumeroid.android.core.player.toPlayableMedia
import org.getlumeroid.android.core.settings.AppSettings
import org.getlumeroid.android.core.settings.ContentFilterCache
import org.getlumeroid.android.core.sync.ContentSyncManager

/**
 * Authentic Apple TV Series Tab for Lumeroid on Android TV.
 * 100% faithful to Apple TV Lume's SeriesView.swift:
 * - Edge-to-edge vertical scrolling browse screen (NO sidebars)
 * - Horizontal browse rails: Recently Watched (with progress), Favorites, Recently Added
 * - First 4 categories rendered as full horizontal preview rows with "Show All" links
 * - Remaining categories rendered as lightweight 320x100 name tiles ("All Categories")
 * - "Show All" and category tile drill-ins navigate to full-screen Category Content Grid
 * - Clicking any poster opens the Apple TV full-bleed cinematic Series Detail view
 * - Fully precomputed via BrowseCache on frame 0 (0ms, zero pop-in, zero layout thrashing)
 */
@Composable
fun TvSeriesScreen(
    onPlayMedia: (PlayableMedia) -> Unit,
    firstItemFocusRequester: FocusRequester = remember { FocusRequester() },
    isTopBarFocused: Boolean = false,
    onContentFocused: (() -> Unit)? = null,
    onMoveUpToTopBar: (() -> Unit)? = null
) {
    val db = LumeroidApp.instance.database
    val settings = remember { AppSettings.get() }
    val revision by settings.revision.collectAsState()
    val activePlaylist by db.playlistDao().observeActivePlaylist().collectAsState(initial = null)
    val playlistId = activePlaylist?.id ?: ""

    // Observe categories
    val categories by db.categoryDao().getCategories(playlistId, "series").collectAsState(initial = emptyList())
    val categoryMap = remember(categories) { categories.associate { it.id to it.name } }

    // Content Language filtering integration
    val isFiltering by ContentFilterCache.isFiltering.collectAsState()
    val allowedSeriesCatIds by ContentFilterCache.seriesCategoryIds.collectAsState()

    val visibleCategories = remember(categories, isFiltering, allowedSeriesCatIds) {
        if (!isFiltering) categories
        else categories.filter { allowedSeriesCatIds.contains(it.id) }
    }

    // Auto-sync categories if empty
    LaunchedEffect(activePlaylist) {
        activePlaylist?.let { playlist ->
            if (categories.isEmpty()) {
                ContentSyncManager.syncAllCategories(playlist)
            }
        }
    }

    // 0ms Synchronous In-Memory Rails from BrowseCache (eliminates dynamic insertion pop-in completely)
    val filteredRecentlyWatched by BrowseCache.recentlyWatchedSeries.collectAsState()
    val filteredFavorites by BrowseCache.favoriteSeries.collectAsState()
    val filteredRecentlyAdded by BrowseCache.recentlyAddedSeries.collectAsState()
    val previewCategoryMap by BrowseCache.previewCategorySeries.collectAsState()

    // Observe precomputed genres from ContentFilterCache (0ms synchronous lookup)
    val cachedSeriesGenres by ContentFilterCache.seriesGenres.collectAsState()
    var genres by remember(cachedSeriesGenres) { mutableStateOf(cachedSeriesGenres) }
    LaunchedEffect(cachedSeriesGenres, playlistId) {
        if (genres.isEmpty() && playlistId.isNotEmpty()) {
            genres = org.getlumeroid.android.core.data.GenreDerivation.seriesGenres(playlistId)
        }
    }

    // Navigation Sub-screen state
    var selectedSeries by remember { mutableStateOf<SeriesEntity?>(null) }
    var activeGridTitle by remember { mutableStateOf<String?>(null) }
    var activeGridCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var activeGridGenre by remember { mutableStateOf<String?>(null) }
    var activeGridCollectionItems by remember { mutableStateOf<List<SeriesEntity>?>(null) }

    // Last focused card requester so focus is cleanly restored when detail sheets or grids are dismissed
    var lastFocusedSeriesRequester by remember { mutableStateOf<FocusRequester?>(null) }
    var isReturningFromGrid by remember { mutableStateOf(false) }
    val genreFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val categoryFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    var lastOpenedGenre by remember { mutableStateOf<String?>(null) }
    var lastOpenedCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Breadcrumb tree back handling
    if (selectedSeries == null && activeGridTitle == null && !isReturningFromGrid) {
        BackHandler {
            onMoveUpToTopBar?.invoke()
        }
    }



    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07090E))
    ) {
        if (activePlaylist == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No Playlists", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Add a playlist in Settings to start browsing series", color = LumeroidTextSecondary, fontSize = 11.5.sp)
                }
            }
        } else if (categories.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LumeroidAccentBlue, modifier = Modifier.size(36.dp))
            }
        } else if (visibleCategories.isEmpty() && isFiltering) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No Series for Selected Languages", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Adjust your Content Languages in Library Settings", color = LumeroidTextSecondary, fontSize = 11.5.sp)
                }
            }
        } else {
            val previewCategories = remember(visibleCategories) { visibleCategories.take(4) }
            val remainingCategories = remember(visibleCategories) { visibleCategories.drop(4) }
            val genreChunks = remember(genres) { genres.chunked(4) }
            val catChunks = remember(remainingCategories) { remainingCategories.chunked(4) }

            // Root Edge-to-Edge Vertical Scrolling Browse Screen
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .focusProperties { canFocus = selectedSeries == null },
                contentPadding = PaddingValues(top = 96.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                // 1. Recently Watched Rail (with progress bar)
                if (filteredRecentlyWatched.isNotEmpty()) {
                    item(key = "recently_watched") {
                        TvBrowseRow(
                            title = "Recently Watched",
                            hasMore = filteredRecentlyWatched.size > 20,
                            onShowAll = {
                                activeGridTitle = "Recently Watched"
                                activeGridCollectionItems = filteredRecentlyWatched.map { it.second }
                            }
                        ) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 36.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusGroup()
                                    
                            ) {
                                itemsIndexed(filteredRecentlyWatched.take(20)) { index, (progress, series) ->
                                    val requester = if (index == 0) firstItemFocusRequester else remember { FocusRequester() }
                                    val progressFraction = if (progress.durationMs > 0) progress.positionMs.toFloat() / progress.durationMs.toFloat() else null

                                    TvPosterCard(
                                        title = series.name,
                                        posterUrl = series.posterUrl,
                                        rating = series.rating,
                                        progressFraction = progressFraction,
                                        focusRequester = requester,
                                        onMoveUp = onMoveUpToTopBar,
                                        onClick = {
                                            lastFocusedSeriesRequester = requester
                                            selectedSeries = series
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Favorites Rail
                if (filteredFavorites.isNotEmpty()) {
                    item(key = "favorites") {
                        TvBrowseRow(
                            title = "Favorites",
                            hasMore = filteredFavorites.size > 20,
                            onShowAll = {
                                activeGridTitle = "Favorites"
                                activeGridCollectionItems = filteredFavorites
                            }
                        ) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 36.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusGroup()
                                    
                            ) {
                                itemsIndexed(filteredFavorites.take(20)) { index, series ->
                                    val requester = if (index == 0 && filteredRecentlyWatched.isEmpty()) firstItemFocusRequester else remember { FocusRequester() }

                                    TvPosterCard(
                                        title = series.name,
                                        posterUrl = series.posterUrl,
                                        rating = series.rating,
                                        focusRequester = requester,
                                        onMoveUp = if (filteredRecentlyWatched.isEmpty()) onMoveUpToTopBar else null,
                                        onClick = {
                                            lastFocusedSeriesRequester = requester
                                            selectedSeries = series
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Recently Added Rail (1:1 Apple TV SeriesCollectionRow .recentlyAdded)
                item(key = "recently_added") {
                    val isTopRail = filteredRecentlyWatched.isEmpty() && filteredFavorites.isEmpty()
                    TvBrowseRow(
                        title = "Recently Added",
                        hasMore = filteredRecentlyAdded.size > 20,
                        onShowAll = {
                            activeGridTitle = "Recently Added"
                            activeGridCollectionItems = filteredRecentlyAdded
                        }
                    ) {
                        if (filteredRecentlyAdded.isNotEmpty()) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 36.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusGroup()
                                    
                            ) {
                                itemsIndexed(filteredRecentlyAdded.take(20)) { index, series ->
                                    val requester = if (index == 0 && isTopRail) firstItemFocusRequester else remember { FocusRequester() }

                                    TvPosterCard(
                                        title = series.name,
                                        posterUrl = series.posterUrl,
                                        rating = series.rating,
                                        focusRequester = requester,
                                        onMoveUp = if (isTopRail) onMoveUpToTopBar else null,
                                        onClick = {
                                            lastFocusedSeriesRequester = requester
                                            selectedSeries = series
                                        }
                                    )
                                }
                            }
                        } else {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 36.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(6) { idx ->
                                    TvPosterCardSkeleton(
                                        focusRequester = if (idx == 0 && isTopRail) firstItemFocusRequester else null,
                                        onMoveUp = if (isTopRail) onMoveUpToTopBar else null
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. First 4 Categories rendered as full inline horizontal preview rows
                itemsIndexed(previewCategories, key = { _, cat -> cat.id }) { catIdx, category ->
                    val isFirstRailOverall = catIdx == 0 && filteredRecentlyWatched.isEmpty() && filteredFavorites.isEmpty() && filteredRecentlyAdded.isEmpty()

                    SeriesCategoryPreviewRow(
                        category = category,
                        playlistId = playlistId,
                        categoryMap = categoryMap,
                        preloadedSeries = previewCategoryMap[category.id],
                        firstItemRequester = if (isFirstRailOverall) firstItemFocusRequester else null,
                        onMoveUpToTopBar = if (isFirstRailOverall) onMoveUpToTopBar else null,
                        onSelectSeries = { series, req ->
                            lastFocusedSeriesRequester = req
                            selectedSeries = series
                        },
                        onShowAll = {
                            activeGridTitle = category.name
                            activeGridCategory = category
                        }
                    )
                }

                // 5. Browse by Genre (1:1 Apple TV GenreGridSection, row items for native vertical scrolling)
                if (genres.isNotEmpty()) {
                    item(key = "browse_by_genre_header") {
                        Text(
                            text = "Browse by Genre",
                            color = LumeroidTextSecondary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(horizontal = 36.dp)
                        )
                    }
                    itemsIndexed(genreChunks, key = { idx, _ -> "genre_chunk_$idx" }) { _, rowGenres ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 36.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            for (genre in rowGenres) {
                                val tileReq = genreFocusRequesters.getOrPut(genre) { FocusRequester() }
                                TvCategoryTile(
                                    name = genre,
                                    focusRequester = tileReq,
                                    onFocused = {
                                        lastFocusedSeriesRequester = tileReq
                                        lastOpenedGenre = genre
                                        TvFocusRescue.register(tileReq)
                                        onContentFocused?.invoke()
                                    },
                                    onClick = {
                                        lastFocusedSeriesRequester = tileReq
                                        lastOpenedGenre = genre
                                        lastOpenedCategory = null
                                        activeGridTitle = genre
                                        activeGridGenre = genre
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowGenres.size < 4) {
                                repeat(4 - rowGenres.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                // 6. Remaining Categories rendered as lightweight name tiles (All Categories grid, chunked rows)
                if (remainingCategories.isNotEmpty()) {
                    item(key = "all_categories_header") {
                        Text(
                            text = "All Categories",
                            color = LumeroidTextSecondary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier
                                .padding(horizontal = 36.dp)
                                .padding(top = 12.dp)
                        )
                    }
                    itemsIndexed(catChunks, key = { idx, _ -> "cat_chunk_$idx" }) { _, rowCats ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 36.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            for (cat in rowCats) {
                                val tileReq = categoryFocusRequesters.getOrPut(cat.id) { FocusRequester() }
                                TvCategoryTile(
                                    name = cat.name,
                                    focusRequester = tileReq,
                                    onFocused = {
                                        lastFocusedSeriesRequester = tileReq
                                        lastOpenedCategory = cat
                                        TvFocusRescue.register(tileReq)
                                        onContentFocused?.invoke()
                                    },
                                    onClick = {
                                        lastFocusedSeriesRequester = tileReq
                                        lastOpenedCategory = cat
                                        lastOpenedGenre = null
                                        activeGridTitle = cat.name
                                        activeGridCategory = cat
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowCats.size < 4) {
                                repeat(4 - rowCats.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sub-screen: Category / Collection / Genre Poster Grid ("Show All") with Guaranteed Focus
        if (activeGridTitle != null) {
            val title = activeGridTitle ?: ""
            val cat = activeGridCategory
            val genreTarget = activeGridGenre
            val presetSeries = activeGridCollectionItems

            val rawGenreSeries by if (genreTarget != null) {
                db.vodDao().getSeriesByGenre(playlistId, genreTarget).collectAsState(initial = emptyList())
            } else {
                remember { mutableStateOf(emptyList<SeriesEntity>()) }
            }

            val rawCatSeries by if (cat != null) {
                db.vodDao().getSeriesByCategory(playlistId, cat.id).collectAsState(initial = emptyList())
            } else {
                remember { mutableStateOf(emptyList<SeriesEntity>()) }
            }

            if (cat != null) {
                LaunchedEffect(cat.id) {
                    if (rawCatSeries.isEmpty()) {
                        activePlaylist?.let { pl ->
                            ContentSyncManager.loadCategorySeries(pl, cat)
                        }
                    }
                }
            }

            val displaySeries = remember(presetSeries, rawGenreSeries, rawCatSeries, genreTarget, cat, revision) {
                when {
                    presetSeries != null -> presetSeries
                    genreTarget != null -> {
                        if (settings.homeContentLanguages.isEmpty()) rawGenreSeries
                        else rawGenreSeries.filter { series ->
                            val catName = categoryMap[series.categoryId] ?: ""
                            (allowedSeriesCatIds.isEmpty() || allowedSeriesCatIds.contains(series.categoryId)) &&
                                    settings.matchesContentLanguage(series.name, catName)
                        }
                    }
                    cat != null -> {
                        if (settings.homeContentLanguages.isEmpty()) rawCatSeries
                        else rawCatSeries.filter { series ->
                            settings.matchesContentLanguage(series.name, cat.name)
                        }
                    }
                    else -> emptyList()
                }
            }

            val isGridLoading = when {
                presetSeries != null -> false
                genreTarget != null -> rawGenreSeries.isEmpty()
                cat != null -> rawCatSeries.isEmpty()
                else -> false
            }

            TvCategoryContentGridScreen(
                title = title,
                itemCount = displaySeries.size,
                isLoading = isGridLoading,
                emptyMessage = "No series found in $title",
                onBack = {
                    val genreToRestore = lastOpenedGenre
                    val catToRestore = lastOpenedCategory
                    isReturningFromGrid = true
                    activeGridTitle = null
                    activeGridCategory = null
                    activeGridGenre = null
                    activeGridCollectionItems = null
                    coroutineScope.launch {
                        val targetReq = when {
                            genreToRestore != null -> genreFocusRequesters[genreToRestore]
                            catToRestore != null -> categoryFocusRequesters[catToRestore.id]
                            else -> lastFocusedSeriesRequester
                        }
                        for (i in 0..25) {
                            delay(25)
                            try {
                                if (targetReq != null && targetReq.requestFocus()) {
                                    break
                                }
                            } catch (_: Exception) {}
                        }
                        delay(250)
                        isReturningFromGrid = false
                    }
                }
            ) { firstItemRequester ->
                val gridRequesters = remember(title) { mutableMapOf<String, FocusRequester>() }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    contentPadding = PaddingValues(horizontal = 36.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(displaySeries, key = { _, m -> "grid_${m.id}" }) { index, series ->
                        val req = if (index == 0) firstItemRequester else gridRequesters.getOrPut(series.id) { FocusRequester() }
                        TvPosterCard(
                            title = series.name,
                            posterUrl = series.posterUrl,
                            rating = series.rating,
                            fillsWidth = true,
                            focusRequester = req,
                            onClick = { selectedSeries = series }
                        )
                    }
                }
            }
        }

        // Sub-screen: Apple TV Full-Bleed Series Detail Page with Seasons and Episodes
        selectedSeries?.let { series ->
            activePlaylist?.let { pl ->
                TvSeriesDetailSheet(
                    series = series,
                    playlist = pl,
                    onPlayEpisode = { episode ->
                        selectedSeries = null
                        onPlayMedia(
                            episode.toPlayableMedia(
                                seriesTitle = series.name,
                                seriesPosterUrl = series.posterUrl,
                                fullSeriesId = series.id
                            )
                        )
                    },
                    onDismiss = {
                        selectedSeries = null
                        try {
                            (lastFocusedSeriesRequester ?: firstItemFocusRequester).requestFocus()
                        } catch (_: Exception) {}
                    }
                )
            }
        }
    }
}

@Composable
private fun SeriesCategoryPreviewRow(
    category: CategoryEntity,
    playlistId: String,
    categoryMap: Map<String, String>,
    preloadedSeries: List<SeriesEntity>? = null,
    firstItemRequester: FocusRequester? = null,
    onMoveUpToTopBar: (() -> Unit)? = null,
    onSelectSeries: (SeriesEntity, FocusRequester) -> Unit,
    onShowAll: () -> Unit
) {
    val db = LumeroidApp.instance.database
    val settings = remember { AppSettings.get() }
    val revision by settings.revision.collectAsState()

    val rawSeries by db.vodDao().getSeriesByCategoryLimited(playlistId, category.id, 21).collectAsState(initial = emptyList())

    LaunchedEffect(category.id, rawSeries.size, preloadedSeries?.size) {
        if (rawSeries.isEmpty() && (preloadedSeries == null || preloadedSeries.isEmpty())) {
            val pl = db.playlistDao().getActivePlaylist()
            if (pl != null) {
                ContentSyncManager.loadCategorySeries(pl, category)
            }
        }
    }

    val displaySeries = remember(preloadedSeries, rawSeries, revision) {
        if (!preloadedSeries.isNullOrEmpty()) {
            preloadedSeries
        } else if (settings.homeContentLanguages.isEmpty()) {
            rawSeries
        } else {
            rawSeries.filter { series ->
                settings.matchesContentLanguage(series.name, category.name)
            }
        }
    }

    TvBrowseRow(
        title = category.name,
        hasMore = displaySeries.size > 20,
        onShowAll = onShowAll
    ) {
        if (displaySeries.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 36.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup()
                    
            ) {
                itemsIndexed(displaySeries.take(20)) { index, series ->
                    val requester = if (index == 0 && firstItemRequester != null) firstItemRequester else remember { FocusRequester() }

                    TvPosterCard(
                        title = series.name,
                        posterUrl = series.posterUrl,
                        rating = series.rating,
                        focusRequester = requester,
                        onMoveUp = onMoveUpToTopBar,
                        onClick = { onSelectSeries(series, requester) }
                    )
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 36.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(6) { idx ->
                    TvPosterCardSkeleton(
                        focusRequester = if (idx == 0 && firstItemRequester != null) firstItemRequester else null,
                        onMoveUp = onMoveUpToTopBar
                    )
                }
            }
        }
    }
}
