package org.getlumeroid.android.ui.tv

import kotlinx.coroutines.delay

import androidx.compose.ui.focus.focusProperties
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import org.getlumeroid.android.core.data.entities.MovieEntity
import org.getlumeroid.android.core.data.entities.WatchProgressEntity
import org.getlumeroid.android.core.designsystem.*
import org.getlumeroid.android.core.player.PlayableMedia
import org.getlumeroid.android.core.player.toPlayableMedia
import org.getlumeroid.android.core.settings.AppSettings
import org.getlumeroid.android.core.settings.ContentFilterCache
import org.getlumeroid.android.core.sync.ContentSyncManager

/**
 * Authentic Apple TV Movies Tab for Lumeroid on Android TV.
 * 100% faithful to Apple TV Lume's MoviesView.swift:
 * - Edge-to-edge vertical scrolling browse screen (NO sidebars)
 * - Horizontal browse rails: Recently Watched (with progress), Favorites, Recently Added
 * - First 4 categories rendered as full horizontal preview rows with "Show All" links
 * - Remaining categories rendered as lightweight 320x100 name tiles ("All Categories")
 * - "Show All" and category tile drill-ins navigate to full-screen Category Content Grid
 * - Clicking any poster opens the Apple TV full-bleed cinematic Movie Detail view
 * - Fully precomputed via BrowseCache on frame 0 (0ms, zero pop-in, zero layout thrashing)
 */
@Composable
fun TvMoviesScreen(
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
    val categories by db.categoryDao().getCategories(playlistId, "movie").collectAsState(initial = emptyList())
    val categoryMap = remember(categories) { categories.associate { it.id to it.name } }

    // Content Language filtering integration
    val isFiltering by ContentFilterCache.isFiltering.collectAsState()
    val allowedMovieCatIds by ContentFilterCache.movieCategoryIds.collectAsState()

    val visibleCategories = remember(categories, isFiltering, allowedMovieCatIds) {
        if (!isFiltering) categories
        else categories.filter { allowedMovieCatIds.contains(it.id) }
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
    val filteredRecentlyWatched by BrowseCache.recentlyWatchedMovies.collectAsState()
    val filteredFavorites by BrowseCache.favoriteMovies.collectAsState()
    val filteredRecentlyAdded by BrowseCache.recentlyAddedMovies.collectAsState()
    val previewCategoryMap by BrowseCache.previewCategoryMovies.collectAsState()

    // Observe precomputed genres from ContentFilterCache (0ms synchronous lookup)
    val cachedMovieGenres by ContentFilterCache.movieGenres.collectAsState()
    var genres by remember(cachedMovieGenres) { mutableStateOf(cachedMovieGenres) }
    LaunchedEffect(cachedMovieGenres, playlistId) {
        if (genres.isEmpty() && playlistId.isNotEmpty()) {
            genres = org.getlumeroid.android.core.data.GenreDerivation.movieGenres(playlistId)
        }
    }

    // Navigation Sub-screen state
    var selectedMovie by remember { mutableStateOf<MovieEntity?>(null) }
    var activeGridTitle by remember { mutableStateOf<String?>(null) }
    var activeGridCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var activeGridGenre by remember { mutableStateOf<String?>(null) }
    var activeGridCollectionItems by remember { mutableStateOf<List<MovieEntity>?>(null) }

    // Last focused card requester so focus is cleanly restored when detail sheets or grids are dismissed
    var lastFocusedMovieRequester by remember { mutableStateOf<FocusRequester?>(null) }
    var lastFocusedBrowseRequester by remember { mutableStateOf<FocusRequester?>(null) }
    var isReturningFromGrid by remember { mutableStateOf(false) }
    val genreFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val categoryFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    var lastOpenedGenre by remember { mutableStateOf<String?>(null) }
    var lastOpenedCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    DisposableEffect(selectedMovie, activeGridTitle, lastFocusedBrowseRequester) {
        if (selectedMovie == null && activeGridTitle == null) {
            val target = lastFocusedBrowseRequester ?: firstItemFocusRequester
            TvFocusRescue.register(target)
            onDispose { TvFocusRescue.unregister(target) }
        } else {
            onDispose {}
        }
    }

    val isScrolledDown by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 20
        }
    }

    // Breadcrumb tree back handling:
    // Single consolidated BackHandler prevents race conditions or exiting to TV launcher.
    // When scrolled down on browse feed, Back scrolls to Row 0 and securely focuses first item.
    // When at Row 0, Back smoothly transfers focus up to TopBar.
    BackHandler(enabled = !isTopBarFocused && selectedMovie == null && activeGridTitle == null && !isReturningFromGrid) {
        android.util.Log.d("TvNav", "TvMoviesScreen BackHandler invoked! isScrolledDown=" + isScrolledDown + " isTopBarFocused=" + isTopBarFocused)
        if (isScrolledDown) {
            coroutineScope.launch {
                listState.scrollToItem(0)
                var success = false
                for (i in 0..25) {
                    delay(30)
                    try {
                        if (firstItemFocusRequester.requestFocus()) {
                            success = true
                            break
                        }
                    } catch (_: Exception) {}
                }
                if (!success) {
                    TvFocusRescue.rescueFocus()
                }
            }
        } else {
            coroutineScope.launch {
                listState.scrollToItem(0)
            }
            onMoveUpToTopBar?.invoke()
        }
    }

    var hasAcquiredInitialFocus by remember { mutableStateOf(false) }



    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07090E))
    ) {
        if (activePlaylist == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No Playlists", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Add a playlist in Settings to start browsing movies", color = LumeroidTextSecondary, fontSize = 11.5.sp)
                }
            }
        } else if (categories.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LumeroidAccentBlue, modifier = Modifier.size(36.dp))
            }
        } else if (visibleCategories.isEmpty() && isFiltering) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No Movies for Selected Languages", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .focusProperties { canFocus = selectedMovie == null && activeGridTitle == null },
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
                                    
                            ) {
                                itemsIndexed(filteredRecentlyWatched.take(20), key = { _, (prog, movie) -> "rw_${movie.id}" }) { index, (progress, movie) ->
                                    val requester = if (index == 0) firstItemFocusRequester else remember { FocusRequester() }
                                    val progressFraction = if (progress.durationMs > 0) progress.positionMs.toFloat() / progress.durationMs.toFloat() else null

                                    TvPosterCard(
                                        title = movie.name,
                                        posterUrl = movie.posterUrl,
                                        rating = movie.rating,
                                        progressFraction = progressFraction,
                                        focusRequester = requester,
                                        onMoveUp = onMoveUpToTopBar,
                                        onFocused = {
                                            lastFocusedBrowseRequester = requester
                                            TvFocusRescue.register(requester); onContentFocused?.invoke()
                                        },
                                        onClick = {
                                            lastFocusedMovieRequester = requester
                                            selectedMovie = movie
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
                                    
                            ) {
                                itemsIndexed(filteredFavorites.take(20), key = { _, movie -> "fav_${movie.id}" }) { index, movie ->
                                    val requester = if (index == 0 && filteredRecentlyWatched.isEmpty()) firstItemFocusRequester else remember { FocusRequester() }

                                    TvPosterCard(
                                        title = movie.name,
                                        posterUrl = movie.posterUrl,
                                        rating = movie.rating,
                                        focusRequester = requester,
                                        onMoveUp = if (filteredRecentlyWatched.isEmpty()) onMoveUpToTopBar else null,
                                        onFocused = {
                                            lastFocusedBrowseRequester = requester
                                            TvFocusRescue.register(requester); onContentFocused?.invoke()
                                        },
                                        onClick = {
                                            lastFocusedMovieRequester = requester
                                            selectedMovie = movie
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Recently Added Rail (1:1 Apple TV MovieCollectionRow .recentlyAdded)
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
                                    
                            ) {
                                itemsIndexed(filteredRecentlyAdded.take(20), key = { _, movie -> "ra_${movie.id}" }) { index, movie ->
                                    val requester = if (index == 0 && isTopRail) firstItemFocusRequester else remember { FocusRequester() }

                                    TvPosterCard(
                                        title = movie.name,
                                        posterUrl = movie.posterUrl,
                                        rating = movie.rating,
                                        focusRequester = requester,
                                        onMoveUp = if (isTopRail) onMoveUpToTopBar else null,
                                        onFocused = {
                                            lastFocusedBrowseRequester = requester
                                            TvFocusRescue.register(requester); onContentFocused?.invoke()
                                        },
                                        onClick = {
                                            lastFocusedMovieRequester = requester
                                            selectedMovie = movie
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

                    MovieCategoryPreviewRow(
                        category = category,
                        playlistId = playlistId,
                        categoryMap = categoryMap,
                        preloadedMovies = previewCategoryMap[category.id],
                        firstItemRequester = if (isFirstRailOverall) firstItemFocusRequester else null,
                        onMoveUpToTopBar = if (isFirstRailOverall) onMoveUpToTopBar else null,
                        onFocusedMovie = { req ->
                            lastFocusedBrowseRequester = req
                            TvFocusRescue.register(req); onContentFocused?.invoke()
                        },
                        onSelectMovie = { movie, req ->
                            lastFocusedMovieRequester = req
                            selectedMovie = movie
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
                    itemsIndexed(genreChunks, key = { idx, _ -> "genre_chunk_$idx" }) { idx, rowGenres ->
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
                                        lastFocusedBrowseRequester = tileReq
                                        lastOpenedGenre = genre
                                        TvFocusRescue.register(tileReq)
                                        onContentFocused?.invoke()
                                    },
                                    onClick = {
                                        lastFocusedBrowseRequester = tileReq
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
                    itemsIndexed(catChunks, key = { idx, _ -> "cat_chunk_$idx" }) { idx, rowCats ->
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
                                        lastFocusedBrowseRequester = tileReq
                                        lastOpenedCategory = cat
                                        TvFocusRescue.register(tileReq)
                                        onContentFocused?.invoke()
                                    },
                                    onClick = {
                                        lastFocusedBrowseRequester = tileReq
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
            val presetMovies = activeGridCollectionItems

            val rawGenreMovies by if (genreTarget != null) {
                db.vodDao().getMoviesByGenre(playlistId, genreTarget).collectAsState(initial = emptyList())
            } else {
                remember { mutableStateOf(emptyList()) }
            }

            val rawCatMovies by if (cat != null) {
                db.vodDao().getMoviesByCategory(playlistId, cat.id).collectAsState(initial = emptyList())
            } else {
                remember { mutableStateOf(emptyList()) }
            }

            if (cat != null) {
                LaunchedEffect(cat.id) {
                    if (rawCatMovies.isEmpty()) {
                        activePlaylist?.let { pl ->
                            ContentSyncManager.loadCategoryMovies(pl, cat)
                        }
                    }
                }
            }

            val displayMovies = remember(presetMovies, rawGenreMovies, rawCatMovies, genreTarget, cat, revision) {
                when {
                    presetMovies != null -> presetMovies
                    genreTarget != null -> {
                        if (settings.homeContentLanguages.isEmpty()) rawGenreMovies
                        else rawGenreMovies.filter { movie ->
                            val catName = categoryMap[movie.categoryId] ?: ""
                            (allowedMovieCatIds.isEmpty() || allowedMovieCatIds.contains(movie.categoryId)) &&
                                    settings.matchesContentLanguage(movie.name, catName)
                        }
                    }
                    cat != null -> {
                        if (settings.homeContentLanguages.isEmpty()) rawCatMovies
                        else rawCatMovies.filter { movie ->
                            settings.matchesContentLanguage(movie.name, cat.name)
                        }
                    }
                    else -> emptyList()
                }
            }

            val isGridLoading = when {
                presetMovies != null -> false
                genreTarget != null -> rawGenreMovies.isEmpty()
                cat != null -> rawCatMovies.isEmpty()
                else -> false
            }

            TvCategoryContentGridScreen(
                title = title,
                itemCount = displayMovies.size,
                isLoading = isGridLoading,
                emptyMessage = "No movies found in $title",
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
                            else -> lastFocusedBrowseRequester
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
                    itemsIndexed(displayMovies, key = { _, m -> "grid_${m.id}" }) { index, movie ->
                        val req = if (index == 0) firstItemRequester else gridRequesters.getOrPut(movie.id) { FocusRequester() }
                        TvPosterCard(
                            title = movie.name,
                            posterUrl = movie.posterUrl,
                            rating = movie.rating,
                            fillsWidth = true,
                            focusRequester = req,
                            onClick = { selectedMovie = movie }
                        )
                    }
                }
            }
        }

        // Sub-screen: Apple TV Full-Bleed Movie Detail Page
        selectedMovie?.let { movie ->
            TvMovieDetailSheet(
                movie = movie,
                onPlay = {
                    selectedMovie = null
                    onPlayMedia(movie.toPlayableMedia())
                },
                onDismiss = {
                    selectedMovie = null
                    coroutineScope.launch {
                        var success = false
                        for (i in 0..10) {
                            delay(25)
                            try {
                                if ((lastFocusedMovieRequester ?: firstItemFocusRequester).requestFocus()) {
                                    success = true
                                    break
                                }
                            } catch (_: Exception) {}
                        }
                        if (!success) {
                            try { firstItemFocusRequester.requestFocus() } catch (_: Exception) {}
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun MovieCategoryPreviewRow(
    category: CategoryEntity,
    playlistId: String,
    categoryMap: Map<String, String>,
    preloadedMovies: List<MovieEntity>? = null,
    firstItemRequester: FocusRequester? = null,
    onMoveUpToTopBar: (() -> Unit)? = null,
    onFocusedMovie: ((FocusRequester) -> Unit)? = null,
    onSelectMovie: (MovieEntity, FocusRequester) -> Unit,
    onShowAll: () -> Unit
) {
    val db = LumeroidApp.instance.database
    val settings = remember { AppSettings.get() }
    val revision by settings.revision.collectAsState()

    val rawMovies by db.vodDao().getMoviesByCategoryLimited(playlistId, category.id, 21).collectAsState(initial = emptyList())

    // Auto-fetch if not cached
    LaunchedEffect(category.id, rawMovies.size, preloadedMovies?.size) {
        if (rawMovies.isEmpty() && (preloadedMovies == null || preloadedMovies.isEmpty())) {
            val pl = db.playlistDao().getActivePlaylist()
            if (pl != null) {
                ContentSyncManager.loadCategoryMovies(pl, category)
            }
        }
    }

    val displayMovies = remember(preloadedMovies, rawMovies, revision) {
        if (!preloadedMovies.isNullOrEmpty()) {
            preloadedMovies
        } else if (settings.homeContentLanguages.isEmpty()) {
            rawMovies
        } else {
            rawMovies.filter { movie ->
                settings.matchesContentLanguage(movie.name, category.name)
            }
        }
    }

    TvBrowseRow(
        title = category.name,
        hasMore = displayMovies.size > 20,
        onShowAll = onShowAll
    ) {
        if (displayMovies.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 36.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    
            ) {
                itemsIndexed(displayMovies.take(20), key = { _, movie -> "${category.id}_${movie.id}" }) { index, movie ->
                    val requester = if (index == 0 && firstItemRequester != null) firstItemRequester else remember { FocusRequester() }

                    TvPosterCard(
                        title = movie.name,
                        posterUrl = movie.posterUrl,
                        rating = movie.rating,
                        focusRequester = requester,
                        onMoveUp = onMoveUpToTopBar,
                        onFocused = { onFocusedMovie?.invoke(requester) },
                        onClick = { onSelectMovie(movie, requester) }
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
