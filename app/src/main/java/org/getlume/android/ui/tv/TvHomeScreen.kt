package org.getlume.android.ui.tv

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.getlume.android.LumeApp
import org.getlume.android.core.data.entities.CategoryEntity
import org.getlume.android.core.data.entities.ChannelEntity
import org.getlume.android.core.data.entities.MovieEntity
import org.getlume.android.core.data.entities.SeriesEntity
import org.getlume.android.core.data.entities.WatchProgressEntity
import org.getlume.android.core.designsystem.*
import org.getlume.android.core.player.PlayableMedia
import org.getlume.android.core.player.toPlayableMedia
import org.getlume.android.core.settings.AppSettings
import org.getlume.android.core.settings.ContentFilterCache
import org.getlume.android.core.sync.ContentSyncManager
import org.getlume.android.core.sync.HomeTrendingManager
import org.getlume.android.core.sync.PlaylistSyncManager
import java.util.Locale

/**
 * Authentic Apple TV home screen hero item, matching Lume's HeroItem.swift:
 * Pairs an IPTV Movie, Series, or Channel with wide artwork and synopsis.
 */
data class DynamicHeroItem(
    val id: String,
    val title: String,
    val overview: String,
    val rating: String,
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val playableMedia: PlayableMedia? = null,
    val movie: MovieEntity? = null,
    val series: SeriesEntity? = null,
    val channel: ChannelEntity? = null,
    val logoUrl: String = "",
    val isTmdbMatched: Boolean = false
)

/**
 * Authentic Apple TV home screen, 100% faithful to Lume's TVHomeScreen.swift:
 * - Dynamic hero carousel using real IPTV playlist content and backdrops
 * - Apple TV [ ⓘ Details ] capsule pill with manual D-pad Left/Right paging
 * - Interactive Movie & Series detail sheets with 1-click playback
 * - Content rails: Recently Watched (with progress bar), Favorites, Trending Movies, Popular Series, On Air Now
 * - Seamless remote focus navigation between TopBar, Hero showcase, and Content rails
 */
@Composable
fun TvHomeScreen(
    onPlayMedia: (PlayableMedia) -> Unit,
    onOpenLiveTv: () -> Unit,
    onOpenMovies: () -> Unit,
    onOpenSeries: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenSports: (() -> Unit)? = null,
    heroFocusRequester: FocusRequester = remember { FocusRequester() },
    isTopBarFocused: Boolean = false,
    onMoveUpToTopBar: (() -> Unit)? = null
) {
    val db = LumeApp.instance.database
    val settings = remember { AppSettings.get() }
    val revision by settings.revision.collectAsState()
    val activePlaylist by db.playlistDao().observeActivePlaylist().collectAsState(initial = null)
    val playlistId = activePlaylist?.id ?: ""

    // Observe categories to map categoryId -> categoryName for language matching
    val categories by db.categoryDao().observeCategories(playlistId).collectAsState(initial = emptyList())
    val categoryMap = remember(categories) { categories.associate { it.id to it.name } }

    // Observe real content from active IPTV connection
    val channels by db.channelDao().observeChannelsByPlaylist(playlistId).collectAsState(initial = emptyList())
    val featuredMovies by db.vodDao().getFeaturedMovies(playlistId, 40).collectAsState(initial = emptyList())
    val featuredSeries by db.vodDao().getFeaturedSeries(playlistId, 40).collectAsState(initial = emptyList())
    val movies by db.vodDao().getMovies(playlistId, 100).collectAsState(initial = emptyList())
    val seriesList by db.vodDao().getSeries(playlistId, 100).collectAsState(initial = emptyList())
    val favoriteChannels by db.channelDao().getFavoriteChannels(playlistId).collectAsState(initial = emptyList())
    val favoriteMovies by db.vodDao().getFavoriteMovies(playlistId).collectAsState(initial = emptyList())
    val activeProfile by db.profileDao().observeActiveProfile().collectAsState(initial = null)
    val activeProfileId = activeProfile?.id ?: "default"
    val continueWatching by db.watchProgressDao().getContinueWatching(activeProfileId).collectAsState(initial = emptyList())
    val recentlyAddedMovies by db.vodDao().getRecentlyAddedMovies(playlistId, 60).collectAsState(initial = emptyList())
    val recentlyAddedSeries by db.vodDao().getRecentlyAddedSeries(playlistId, 60).collectAsState(initial = emptyList())

    val isFiltering by ContentFilterCache.isFiltering.collectAsState()
    val allowedMovieCats by ContentFilterCache.movieCategoryIds.collectAsState()
    val allowedSeriesCats by ContentFilterCache.seriesCategoryIds.collectAsState()
    val allowedChannelIds by ContentFilterCache.matchingChannelIds.collectAsState()

    // Synchronous In-Memory Rails from BrowseCache (0ms lookup on frame 0, zero pop-in)
    val cachedFeaturedMovies by org.getlume.android.core.data.BrowseCache.featuredMovies.collectAsState()
    val cachedFeaturedSeries by org.getlume.android.core.data.BrowseCache.featuredSeries.collectAsState()
    val cachedHomeRecentlyAdded by org.getlume.android.core.data.BrowseCache.homeRecentlyAdded.collectAsState()
    val cachedContinueWatching by org.getlume.android.core.data.BrowseCache.continueWatching.collectAsState()
    val cachedTrendingMovies by org.getlume.android.core.data.BrowseCache.trendingMovies.collectAsState()
    val cachedTrendingSeries by org.getlume.android.core.data.BrowseCache.trendingSeries.collectAsState()
    val cachedFavoriteMovies by org.getlume.android.core.data.BrowseCache.favoriteMovies.collectAsState()

    val filteredMovies = remember(movies, categoryMap, isFiltering, allowedMovieCats, revision) {
        if (!isFiltering) movies
        else movies.filter { movie ->
            val catMatches = allowedMovieCats.isEmpty() || allowedMovieCats.contains(movie.categoryId)
            val langMatches = settings.matchesContentLanguage(movie.name, categoryMap[movie.categoryId] ?: "")
            catMatches && langMatches
        }
    }
    val filteredSeries = remember(seriesList, categoryMap, isFiltering, allowedSeriesCats, revision) {
        if (!isFiltering) seriesList
        else seriesList.filter { series ->
            val catMatches = allowedSeriesCats.isEmpty() || allowedSeriesCats.contains(series.categoryId)
            val langMatches = settings.matchesContentLanguage(series.name, categoryMap[series.categoryId] ?: "")
            catMatches && langMatches
        }
    }
    val filteredFeaturedMovies = remember(cachedFeaturedMovies, featuredMovies, categoryMap, isFiltering, allowedMovieCats, revision) {
        if (cachedFeaturedMovies.isNotEmpty()) cachedFeaturedMovies
        else if (!isFiltering) featuredMovies
        else featuredMovies.filter { movie ->
            val catMatches = allowedMovieCats.isEmpty() || allowedMovieCats.contains(movie.categoryId)
            val langMatches = settings.matchesContentLanguage(movie.name, categoryMap[movie.categoryId] ?: "")
            catMatches && langMatches
        }
    }
    val filteredFeaturedSeries = remember(cachedFeaturedSeries, featuredSeries, categoryMap, isFiltering, allowedSeriesCats, revision) {
        if (cachedFeaturedSeries.isNotEmpty()) cachedFeaturedSeries
        else if (!isFiltering) featuredSeries
        else featuredSeries.filter { series ->
            val catMatches = allowedSeriesCats.isEmpty() || allowedSeriesCats.contains(series.categoryId)
            val langMatches = settings.matchesContentLanguage(series.name, categoryMap[series.categoryId] ?: "")
            catMatches && langMatches
        }
    }

    val filteredChannels = remember(channels, isFiltering, allowedChannelIds) {
        if (!isFiltering) {
            channels.take(100)
        } else {
            val matched = ArrayList<ChannelEntity>(100)
            for (chan in channels) {
                if (allowedChannelIds.contains(chan.id)) {
                    matched.add(chan)
                    if (matched.size >= 100) break
                }
            }
            matched
        }
    }

    val filteredRecentlyAdded = remember(cachedHomeRecentlyAdded, recentlyAddedMovies, recentlyAddedSeries, categoryMap, isFiltering, allowedMovieCats, allowedSeriesCats, revision) {
        if (cachedHomeRecentlyAdded.isNotEmpty()) cachedHomeRecentlyAdded
        else {
            val filteredM = if (!isFiltering) recentlyAddedMovies
            else recentlyAddedMovies.filter { movie ->
                val catMatches = allowedMovieCats.isEmpty() || allowedMovieCats.contains(movie.categoryId)
                val langMatches = settings.matchesContentLanguage(movie.name, categoryMap[movie.categoryId] ?: "")
                catMatches && langMatches
            }
            val filteredS = if (!isFiltering) recentlyAddedSeries
            else recentlyAddedSeries.filter { series ->
                val catMatches = allowedSeriesCats.isEmpty() || allowedSeriesCats.contains(series.categoryId)
                val langMatches = settings.matchesContentLanguage(series.name, categoryMap[series.categoryId] ?: "")
                catMatches && langMatches
            }
            val combined = ArrayList<Any>(filteredM.size + filteredS.size)
            val maxLen = maxOf(filteredM.size, filteredS.size)
            for (i in 0 until maxLen) {
                if (i < filteredM.size) combined.add(filteredM[i])
                if (i < filteredS.size) combined.add(filteredS[i])
            }
            combined
        }
    }

    // USER HISTORY PRESERVATION (Per User Decision):
    // Recently Watched and Favorites remain unfiltered by language so user interactions are never hidden
    val filteredFavoriteChannels = remember(favoriteChannels) {
        favoriteChannels
    }
    val filteredFavoriteMovies = remember(favoriteMovies, cachedFavoriteMovies) {
        if (favoriteMovies.isNotEmpty()) favoriteMovies else cachedFavoriteMovies
    }
    val activeContinueWatching = if (continueWatching.isNotEmpty()) continueWatching else cachedContinueWatching
    val filteredContinueWatching = remember(activeContinueWatching, settings.homeRecentlyWatchedIncludesLiveTV, revision) {
        if (settings.homeRecentlyWatchedIncludesLiveTV) {
            activeContinueWatching
        } else {
            activeContinueWatching.filter { it.contentType != "channel" }
        }
    }
    val trendingResult by HomeTrendingManager.trendingResult.collectAsState()
    val activeTrendingMovies = remember(trendingResult.trendingMovies, cachedTrendingMovies) {
        if (trendingResult.trendingMovies.isNotEmpty()) trendingResult.trendingMovies else cachedTrendingMovies
    }
    val activeTrendingSeries = remember(trendingResult.trendingSeries, cachedTrendingSeries) {
        if (trendingResult.trendingSeries.isNotEmpty()) trendingResult.trendingSeries else cachedTrendingSeries
    }

    val homeCoroutineScope = rememberCoroutineScope()

    // Detail Modal Sheet State
    var selectedMovie by remember { mutableStateOf<MovieEntity?>(null) }
    var selectedSeries by remember { mutableStateOf<SeriesEntity?>(null) }

    // Intercept Back button when modal sheets are open and cleanly restore focus
    if (selectedMovie != null) {
        BackHandler {
            selectedMovie = null
            homeCoroutineScope.launch {
                var success = false
                for (i in 0..10) {
                    delay(25)
                    try {
                        if (heroFocusRequester.requestFocus()) {
                            success = true
                            break
                        }
                    } catch (_: Exception) {}
                }
                if (!success) onMoveUpToTopBar?.invoke()
            }
        }
    }
    if (selectedSeries != null) {
        BackHandler {
            selectedSeries = null
            homeCoroutineScope.launch {
                var success = false
                for (i in 0..10) {
                    delay(25)
                    try {
                        if (heroFocusRequester.requestFocus()) {
                            success = true
                            break
                        }
                    } catch (_: Exception) {}
                }
                if (!success) onMoveUpToTopBar?.invoke()
            }
        }
    }


    // Trigger initial catalog sync & background enrichment if database is empty or missing genres or tmdbId
    LaunchedEffect(activePlaylist, revision) {
        activePlaylist?.let { playlist ->
            val hasGenres = withContext(Dispatchers.IO) {
                db.vodDao().getSeriesGenreTokens(playlist.id, limit = 1).isNotEmpty()
            }
            val (movieCount, tmdbCount, addedCount) = withContext(Dispatchers.IO) {
                Triple(
                    db.vodDao().getMovieCount(playlist.id),
                    db.vodDao().getTmdbMappedCount(playlist.id),
                    db.vodDao().getMoviesWithAddedCount(playlist.id)
                )
            }
            if (movieCount == 0 || !hasGenres || tmdbCount < (movieCount / 2) || (movieCount > 0 && addedCount == 0)) {
                PlaylistSyncManager.syncPlaylistAsync(playlist)
            } else {
                ContentSyncManager.enrichFeaturedMedia(playlist)
                HomeTrendingManager.loadTrending(playlist.id)
            }
        }
    }

    // Dynamic hero slides: Prefer TMDB matched trending titles with transparent logo artwork, matching HomeView+Trending.swift
    val heroSlides = remember(trendingResult.heroItems, filteredFeaturedMovies, filteredFeaturedSeries, filteredChannels, isFiltering, allowedMovieCats, allowedSeriesCats) {
        val baseHeroItems = if (isFiltering) {
            trendingResult.heroItems.filter { item ->
                val movie = item.movie
                val series = item.series
                when {
                    movie != null -> (allowedMovieCats.isEmpty() || allowedMovieCats.contains(movie.categoryId)) && settings.matchesContentLanguage(movie.name, categoryMap[movie.categoryId] ?: "")
                    series != null -> (allowedSeriesCats.isEmpty() || allowedSeriesCats.contains(series.categoryId)) && settings.matchesContentLanguage(series.name, categoryMap[series.categoryId] ?: "")
                    else -> true
                }
            }
        } else {
            trendingResult.heroItems
        }

        if (baseHeroItems.isNotEmpty()) {
            baseHeroItems
        } else {
            val items = mutableListOf<DynamicHeroItem>()
            val maxCount = maxOf(filteredFeaturedMovies.size, filteredFeaturedSeries.size)
            for (i in 0 until maxCount) {
                if (i < filteredFeaturedMovies.size) {
                    val movie = filteredFeaturedMovies[i]
                    val cleanTitle = org.getlume.android.core.sync.HomeTrendingManager.cleanTitle(movie.name)
                    items.add(
                        DynamicHeroItem(
                            id = "movie-${movie.id}",
                            title = cleanTitle,
                            overview = if (movie.plot.isNotBlank()) movie.plot else "Watch this movie in high definition from your IPTV subscription.",
                            rating = if (movie.rating > 0f) String.format(Locale.US, "%.1f", movie.rating) else "8.5",
                            posterUrl = movie.posterUrl,
                            backdropUrl = movie.backdropUrl,
                            playableMedia = movie.toPlayableMedia(),
                            movie = movie,
                            logoUrl = movie.logoUrl
                        )
                    )
                }
                if (i < filteredFeaturedSeries.size) {
                    val series = filteredFeaturedSeries[i]
                    val cleanTitle = org.getlume.android.core.sync.HomeTrendingManager.cleanTitle(series.name)
                    items.add(
                        DynamicHeroItem(
                            id = "series-${series.id}",
                            title = cleanTitle,
                            overview = if (series.plot.isNotBlank()) series.plot else "Stream all episodes of this series on demand from your IPTV subscription.",
                            rating = if (series.rating > 0f) String.format(Locale.US, "%.1f", series.rating) else "8.0",
                            posterUrl = series.posterUrl,
                            backdropUrl = series.backdropUrl,
                            playableMedia = null,
                            series = series,
                            logoUrl = series.logoUrl
                        )
                    )
                }
            }
            // Fall back to live channels if no movies/series exist yet
            if (items.isEmpty() && filteredChannels.isNotEmpty()) {
                filteredChannels.take(4).forEach { chan ->
                    items.add(
                        DynamicHeroItem(
                            id = "channel-${chan.id}",
                            title = chan.name.trim(),
                            overview = "Live broadcast streaming now in high quality on Channel ${chan.channelNumber}.",
                            rating = "LIVE",
                            posterUrl = chan.logoUrl,
                            backdropUrl = "",
                            playableMedia = chan.toPlayableMedia(),
                            channel = chan
                        )
                    )
                }
            }
            items.take(8)
        }
    }

    var currentHeroIndex by remember { mutableStateOf(0) }
    var isCarouselPaused by remember { mutableStateOf(false) }

    // Auto-advance hero carousel every 7 seconds, matching Lume's autoAdvanceInterval
    LaunchedEffect(heroSlides.size, isCarouselPaused) {
        if (!isCarouselPaused && heroSlides.size > 1) {
            while (true) {
                delay(7000)
                if (!isCarouselPaused && heroSlides.isNotEmpty()) {
                    currentHeroIndex = (currentHeroIndex + 1) % heroSlides.size
                }
            }
        }
    }

    val currentHero = if (heroSlides.isNotEmpty()) {
        heroSlides.getOrElse(currentHeroIndex.coerceIn(0, heroSlides.size - 1)) { heroSlides.first() }
    } else null

    val scrollState = rememberScrollState()

    @OptIn(ExperimentalFoundationApi::class)
    val noOpBringIntoView = remember {
        object : BringIntoViewResponder {
            @ExperimentalFoundationApi
            override fun calculateRectForParent(localRect: Rect): Rect = Rect.Zero

            @ExperimentalFoundationApi
            override suspend fun bringChildIntoView(localRect: () -> Rect?) {}
        }
    }

    val isHomeScrolledDown by remember {
        derivedStateOf { scrollState.value > 20 }
    }

    // Single consolidated BackHandler prevents race conditions or exiting to TV launcher
    BackHandler(enabled = !isTopBarFocused && selectedMovie == null && selectedSeries == null) {
        if (isHomeScrolledDown) {
            homeCoroutineScope.launch {
                scrollState.scrollTo(0)
                var success = false
                for (i in 0..10) {
                    delay(25)
                    try {
                        if (heroFocusRequester.requestFocus()) {
                            success = true
                            break
                        }
                    } catch (_: Exception) {}
                }
                if (!success) {
                    onMoveUpToTopBar?.invoke()
                }
            }
        } else {
            onMoveUpToTopBar?.invoke()
        }
    }
    val cwRowFocusRequester = remember { FocusRequester() }
    val raRowFocusRequester = remember { FocusRequester() }
    val favRowFocusRequester = remember { FocusRequester() }
    val moviesRowFocusRequester = remember { FocusRequester() }
    val seriesRowFocusRequester = remember { FocusRequester() }
    val channelsRowFocusRequester = remember { FocusRequester() }
    val sportsRowFocusRequester = remember { FocusRequester() }
    val forYouRowFocusRequester = remember { FocusRequester() }

    val cwListState = rememberLazyListState()
    val raListState = rememberLazyListState()
    val favListState = rememberLazyListState()
    val moviesListState = rememberLazyListState()
    val seriesListState = rememberLazyListState()
    val channelsListState = rememberLazyListState()

    var cwFocusedIndex by remember { mutableIntStateOf(0) }
    var raFocusedIndex by remember { mutableIntStateOf(0) }
    var favFocusedIndex by remember { mutableIntStateOf(0) }
    var moviesFocusedIndex by remember { mutableIntStateOf(0) }
    var seriesFocusedIndex by remember { mutableIntStateOf(0) }
    var channelsFocusedIndex by remember { mutableIntStateOf(0) }

    val resolvedSectionKeys = remember(settings.homeSectionOrder, settings.homeDisabledSections, revision) {
        settings.getResolvedHomeSections()
    }

    DisposableEffect(heroFocusRequester) {
        TvFocusRescue.register(heroFocusRequester)
        onDispose {
            TvFocusRescue.unregister(heroFocusRequester)
        }
    }



    val activeVisibleSections = remember(
        resolvedSectionKeys,
        filteredContinueWatching,
        filteredRecentlyAdded,
        filteredFavoriteChannels,
        filteredFavoriteMovies,
        activeTrendingMovies,
        filteredMovies,
        activeTrendingSeries,
        filteredSeries,
        filteredChannels,
        settings.libraryShowSports,
        playlistId
    ) {
        resolvedSectionKeys.filter { key ->
            when (key) {
                "recentlyWatched" -> filteredContinueWatching.isNotEmpty()
                "recentlyAdded" -> filteredRecentlyAdded.isNotEmpty()
                "favorites" -> filteredFavoriteChannels.isNotEmpty() || filteredFavoriteMovies.isNotEmpty()
                "forYou" -> true
                "trendingMovies" -> settings.isPlaylistTabVisible(playlistId, "movies") && (activeTrendingMovies.isNotEmpty() || filteredMovies.isNotEmpty())
                "trendingSeries" -> settings.isPlaylistTabVisible(playlistId, "series") && (activeTrendingSeries.isNotEmpty() || filteredSeries.isNotEmpty())
                "sports" -> settings.libraryShowSports
                "liveTv" -> settings.isPlaylistTabVisible(playlistId, "livetv") && filteredChannels.isNotEmpty()
                else -> false
            }
        }
    }

    val scrollToHeroAndFocus: () -> Unit = {
        homeCoroutineScope.launch {
            scrollState.animateScrollTo(0)
            try { heroFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    val focusRail: (String) -> Unit = { sectionKey ->
        homeCoroutineScope.launch {
            when (sectionKey) {
                "recentlyWatched" -> {
                    try { cwRowFocusRequester.requestFocus() }
                    catch (_: Exception) {
                        cwFocusedIndex = 0
                        cwListState.scrollToItem(0)
                        delay(50)
                        try { cwRowFocusRequester.requestFocus() }
                        catch (_: Exception) {
                            delay(50)
                            try { cwRowFocusRequester.requestFocus() } catch (_: Exception) {}
                        }
                    }
                }
                "recentlyAdded" -> {
                    try { raRowFocusRequester.requestFocus() }
                    catch (_: Exception) {
                        raFocusedIndex = 0
                        raListState.scrollToItem(0)
                        delay(50)
                        try { raRowFocusRequester.requestFocus() }
                        catch (_: Exception) {
                            delay(50)
                            try { raRowFocusRequester.requestFocus() } catch (_: Exception) {}
                        }
                    }
                }
                "favorites" -> {
                    try { favRowFocusRequester.requestFocus() }
                    catch (_: Exception) {
                        favFocusedIndex = 0
                        favListState.scrollToItem(0)
                        delay(50)
                        try { favRowFocusRequester.requestFocus() }
                        catch (_: Exception) {
                            delay(50)
                            try { favRowFocusRequester.requestFocus() } catch (_: Exception) {}
                        }
                    }
                }
                "trendingMovies" -> {
                    try { moviesRowFocusRequester.requestFocus() }
                    catch (_: Exception) {
                        moviesFocusedIndex = 0
                        moviesListState.scrollToItem(0)
                        delay(50)
                        try { moviesRowFocusRequester.requestFocus() }
                        catch (_: Exception) {
                            delay(50)
                            try { moviesRowFocusRequester.requestFocus() } catch (_: Exception) {}
                        }
                    }
                }
                "trendingSeries" -> {
                    try { seriesRowFocusRequester.requestFocus() }
                    catch (_: Exception) {
                        seriesFocusedIndex = 0
                        seriesListState.scrollToItem(0)
                        delay(50)
                        try { seriesRowFocusRequester.requestFocus() }
                        catch (_: Exception) {
                            delay(50)
                            try { seriesRowFocusRequester.requestFocus() } catch (_: Exception) {}
                        }
                    }
                }
                "sports" -> {
                    try { sportsRowFocusRequester.requestFocus() }
                    catch (_: Exception) {
                        delay(50)
                        try { sportsRowFocusRequester.requestFocus() } catch (_: Exception) {}
                    }
                }
                "forYou" -> {
                    try { forYouRowFocusRequester.requestFocus() }
                    catch (_: Exception) {
                        delay(50)
                        try { forYouRowFocusRequester.requestFocus() } catch (_: Exception) {}
                    }
                }
                "liveTv" -> {
                    try { channelsRowFocusRequester.requestFocus() }
                    catch (_: Exception) {
                        channelsFocusedIndex = 0
                        channelsListState.scrollToItem(0)
                        delay(50)
                        try { channelsRowFocusRequester.requestFocus() }
                        catch (_: Exception) {
                            delay(50)
                            try { channelsRowFocusRequester.requestFocus() } catch (_: Exception) {}
                        }
                    }
                }
            }
        }
    }

    val focusFirstVisibleRail: () -> Boolean = {
        val firstKey = activeVisibleSections.firstOrNull()
        if (firstKey != null) {
            focusRail(firstKey)
            true
        } else {
            false
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090B10))
    ) {
        val screenHeight = maxHeight
        val heroSlotHeight = (screenHeight - 198.dp).coerceIn(340.dp, 480.dp)

        // FIXED FULL-BLEED BACKDROP ARTWORK SOURCED FROM ACTIVE IPTV CONTENT
        if (currentHero != null) {
            val artworkUrl = currentHero.backdropUrl.ifBlank { currentHero.posterUrl }
            Crossfade(
                targetState = artworkUrl,
                animationSpec = tween(600),
                label = "heroBackdropCrossfade",
                modifier = Modifier.fillMaxSize()
            ) { url ->
                if (url.isNotBlank()) {
                    AsyncImage(
                        model = url,
                        contentDescription = currentHero.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF090B10))
                    )
                }
            }
        }

        // Frosted Gradient Scrims: Authentic Apple TV vignette (translucent bottom to keep backdrop visible)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = 0.60f),
                            0.30f to Color.Transparent,
                            0.60f to Color.Black.copy(alpha = 0.40f),
                            0.85f to Color(0xFF090B10).copy(alpha = 0.75f),
                            1.0f to Color(0xFF090B10).copy(alpha = 0.85f)
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = 0.80f),
                            0.45f to Color.Black.copy(alpha = 0.35f),
                            0.85f to Color.Transparent
                        )
                    )
                )
        )

        // Native TV Scroll Container
        if (activePlaylist == null && heroSlides.isEmpty()) {
            // Empty state: No Playlists
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(64.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Default.Tv, contentDescription = null, tint = LumeTextSecondary, modifier = Modifier.size(64.dp))
                    Text("No Playlists Connected", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Add your IPTV playlist in Settings to populate your home feed.", color = LumeTextSecondary, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    TvShowcaseDetailsPill(
                        title = "Setup Playlist",
                        icon = Icons.Default.Info,
                        focusRequester = heroFocusRequester,
                        onMoveLeft = {},
                        onMoveRight = {},
                        onMoveUp = { onMoveUpToTopBar?.invoke() },
                        onMoveDown = { false },
                        onClick = onOpenSetup
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .focusProperties { canFocus = selectedMovie == null && selectedSeries == null }
            ) {
                // SHOWCASE HERO SECTION (Apple TV TVHeroShowcase)
                if (currentHero != null) {
                    @OptIn(ExperimentalFoundationApi::class)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(heroSlotHeight)
                            .padding(top = 64.dp, start = 36.dp, end = 36.dp, bottom = 12.dp)
                            .bringIntoViewResponder(noOpBringIntoView),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Column(
                            modifier = Modifier.widthIn(max = 500.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Title Artwork or Typography (matching TitleLogo in HeroInfo.swift)
                            if (currentHero.logoUrl.isNotBlank()) {
                                AsyncImage(
                                    model = currentHero.logoUrl,
                                    contentDescription = currentHero.title,
                                    contentScale = ContentScale.Fit,
                                    alignment = Alignment.BottomStart,
                                    modifier = Modifier
                                        .heightIn(max = 76.dp)
                                        .widthIn(max = 380.dp)
                                        .padding(bottom = 4.dp)
                                )
                            } else {
                                Text(
                                    text = currentHero.title,
                                    color = Color.White,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 30.sp,
                                    letterSpacing = (-0.3).sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Live badge (if live TV item)
                            if (currentHero.rating == "LIVE") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.White.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(Color(0xFFFF3B30))
                                    )
                                    Text("LIVE", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Synopsis
                            if (currentHero.overview.isNotBlank()) {
                                Text(
                                    text = currentHero.overview,
                                    color = Color.White.copy(alpha = 0.82f),
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            // Apple TV [ ⓘ Details ] Action Capsule Button with D-pad Left/Right Paging
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TvShowcaseDetailsPill(
                                    title = if (currentHero.channel != null) "Watch Live" else "Details",
                                    icon = if (currentHero.channel != null) Icons.Default.PlayArrow else Icons.Default.Info,
                                    focusRequester = heroFocusRequester,
                                    onMoveLeft = {
                                        isCarouselPaused = true
                                        if (heroSlides.isNotEmpty()) {
                                            currentHeroIndex = (currentHeroIndex - 1 + heroSlides.size) % heroSlides.size
                                        }
                                    },
                                    onMoveRight = {
                                        isCarouselPaused = true
                                        if (heroSlides.isNotEmpty()) {
                                            currentHeroIndex = (currentHeroIndex + 1) % heroSlides.size
                                        }
                                    },
                                    onMoveUp = {
                                        onMoveUpToTopBar?.invoke()
                                    },
                                    onMoveDown = focusFirstVisibleRail,
                                    onClick = {
                                        when {
                                            currentHero.movie != null -> selectedMovie = currentHero.movie
                                            currentHero.series != null -> selectedSeries = currentHero.series
                                            currentHero.channel != null -> onPlayMedia(currentHero.channel.toPlayableMedia())
                                            currentHero.playableMedia != null -> onPlayMedia(currentHero.playableMedia)
                                        }
                                    }
                                )
                            }
                        }

                        // Carousel Dots Indicator (━ • • •) horizontally centered at bottom of hero, matching Apple TV
                        if (heroSlides.size > 1) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 12.dp)
                            ) {
                                TvHeroPageDots(
                                    totalSlides = heroSlides.size,
                                    currentIndex = currentHeroIndex
                                )
                            }
                        }
                    }
                }

                // CONTENT RAILS (Apple TV HomeRows - Dynamic User Order)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    activeVisibleSections.forEachIndexed { sectionIndex, sectionKey ->
                        val onMoveUp: () -> Unit = if (sectionIndex == 0) {
                            scrollToHeroAndFocus
                        } else {
                            val prevKey = activeVisibleSections[sectionIndex - 1]
                            { focusRail(prevKey) }
                        }
                        val onMoveDown: (() -> Unit)? = if (sectionIndex < activeVisibleSections.size - 1) {
                            val nextKey = activeVisibleSections[sectionIndex + 1]
                            { focusRail(nextKey) }
                        } else null

                        when (sectionKey) {
                            "trendingMovies" -> {
                                TvContentRail(title = "Trending Movies") {
                                    val itemsToShow = remember(activeTrendingMovies, filteredMovies, settings.homeItemsPerSection) {
                                        if (activeTrendingMovies.isNotEmpty()) {
                                            activeTrendingMovies.take(settings.homeItemsPerSection)
                                        } else {
                                            filteredMovies.take(settings.homeItemsPerSection)
                                        }
                                    }
                                    val hasSeeAll = true
                                    val totalMovieItems = itemsToShow.size + (if (hasSeeAll) 1 else 0)
                                    LazyRow(
                                        state = moviesListState,
                                        contentPadding = PaddingValues(horizontal = 36.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        itemsIndexed(itemsToShow) { index, movie ->
                                            val cardFocusRequester = if (index == moviesFocusedIndex.coerceIn(0, (totalMovieItems - 1).coerceAtLeast(0))) moviesRowFocusRequester else remember { FocusRequester() }
                                            TvMovieRailCard(
                                                movie = movie,
                                                focusRequester = cardFocusRequester,
                                                onFocused = { moviesFocusedIndex = index },
                                                onMoveUp = onMoveUp,
                                                onMoveDown = onMoveDown,
                                                onClick = { selectedMovie = movie }
                                            )
                                        }
                                        if (hasSeeAll) {
                                            item {
                                                val seeAllIndex = itemsToShow.size
                                                val cardFocusRequester = if (seeAllIndex == moviesFocusedIndex.coerceIn(0, (totalMovieItems - 1).coerceAtLeast(0))) moviesRowFocusRequester else remember { FocusRequester() }
                                                TvSeeAllRailCard(
                                                    title = "See All",
                                                    subtitle = "Movies",
                                                    modifier = Modifier.width(120.dp),
                                                    cardModifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                                                    focusRequester = cardFocusRequester,
                                                    onFocused = { moviesFocusedIndex = seeAllIndex },
                                                    onMoveUp = onMoveUp,
                                                    onMoveDown = onMoveDown,
                                                    onClick = onOpenMovies
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            "sports" -> {
                                TvSportsHomeRail(
                                    focusRequester = sportsRowFocusRequester,
                                    onMoveUp = onMoveUp,
                                    onMoveDown = onMoveDown,
                                    onClick = {
                                        if (onOpenSports != null) {
                                            onOpenSports()
                                        } else {
                                            onOpenSetup()
                                        }
                                    }
                                )
                            }
                            "recentlyWatched" -> {
                                TvContentRail(title = "Recently Watched") {
                                    val itemsToShow = filteredContinueWatching.take(settings.homeItemsPerSection)
                                    LazyRow(
                                        state = cwListState,
                                        contentPadding = PaddingValues(horizontal = 36.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        itemsIndexed(itemsToShow, key = { _, item -> item.contentId }) { index, item ->
                                            val cardFocusRequester = if (index == cwFocusedIndex.coerceIn(0, (itemsToShow.size - 1).coerceAtLeast(0))) cwRowFocusRequester else remember { FocusRequester() }
                                            TvContinueWatchingRailCard(
                                                item = item,
                                                focusRequester = cardFocusRequester,
                                                onFocused = { cwFocusedIndex = index },
                                                onMoveUp = onMoveUp,
                                                onMoveDown = onMoveDown,
                                                onClick = {
                                                    val isLive = item.contentType == "channel" || item.durationMs <= 0
                                                    val media = PlayableMedia(
                                                        id = item.contentId,
                                                        title = item.title,
                                                        streamUrl = "", // looked up during playback
                                                        posterUrl = item.posterUrl,
                                                        isLive = isLive,
                                                        startPositionMs = if (isLive) 0L else item.positionMs
                                                    )
                                                    onPlayMedia(media)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            "recentlyAdded" -> {
                                TvContentRail(title = "Recently Added") {
                                    val itemsToShow = filteredRecentlyAdded.take(settings.homeItemsPerSection)
                                    val hasSeeAll = true
                                    val totalItems = itemsToShow.size + (if (hasSeeAll) 1 else 0)
                                    LazyRow(
                                        state = raListState,
                                        contentPadding = PaddingValues(horizontal = 36.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        itemsIndexed(itemsToShow) { index, item ->
                                            val cardFocusRequester = if (index == raFocusedIndex.coerceIn(0, (totalItems - 1).coerceAtLeast(0))) raRowFocusRequester else remember { FocusRequester() }
                                            when (item) {
                                                is MovieEntity -> {
                                                    TvMovieRailCard(
                                                        movie = item,
                                                        focusRequester = cardFocusRequester,
                                                        onFocused = { raFocusedIndex = index },
                                                        onMoveUp = onMoveUp,
                                                        onMoveDown = onMoveDown,
                                                        onClick = { selectedMovie = item }
                                                    )
                                                }
                                                is SeriesEntity -> {
                                                    TvSeriesRailCard(
                                                        series = item,
                                                        focusRequester = cardFocusRequester,
                                                        onFocused = { raFocusedIndex = index },
                                                        onMoveUp = onMoveUp,
                                                        onMoveDown = onMoveDown,
                                                        onClick = { selectedSeries = item }
                                                    )
                                                }
                                            }
                                        }
                                        if (hasSeeAll) {
                                            item {
                                                val seeAllIndex = itemsToShow.size
                                                val cardFocusRequester = if (seeAllIndex == raFocusedIndex.coerceIn(0, (totalItems - 1).coerceAtLeast(0))) raRowFocusRequester else remember { FocusRequester() }
                                                TvSeeAllRailCard(
                                                    title = "See All",
                                                    subtitle = "New Releases",
                                                    modifier = Modifier.width(120.dp),
                                                    cardModifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                                                    focusRequester = cardFocusRequester,
                                                    onFocused = { raFocusedIndex = seeAllIndex },
                                                    onMoveUp = onMoveUp,
                                                    onMoveDown = onMoveDown,
                                                    onClick = onOpenMovies
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            "favorites" -> {
                                TvContentRail(title = "Favorites") {
                                    val chanItems = filteredFavoriteChannels.take(settings.homeItemsPerSection)
                                    val favMovieItems = filteredFavoriteMovies.take(settings.homeItemsPerSection)
                                    val hasSeeAll = true
                                    val totalFavItems = chanItems.size + favMovieItems.size + (if (hasSeeAll) 1 else 0)
                                    LazyRow(
                                        state = favListState,
                                        contentPadding = PaddingValues(horizontal = 36.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        itemsIndexed(chanItems) { index, channel ->
                                            val cardFocusRequester = if (index == favFocusedIndex.coerceIn(0, (totalFavItems - 1).coerceAtLeast(0))) favRowFocusRequester else remember { FocusRequester() }
                                            TvLiveChannelRailCard(
                                                channel = channel,
                                                focusRequester = cardFocusRequester,
                                                onFocused = { favFocusedIndex = index },
                                                onMoveUp = onMoveUp,
                                                onMoveDown = onMoveDown,
                                                onClick = { onPlayMedia(channel.toPlayableMedia()) }
                                            )
                                        }
                                        itemsIndexed(favMovieItems) { mIdx, movie ->
                                            val actualIndex = chanItems.size + mIdx
                                            val cardFocusRequester = if (actualIndex == favFocusedIndex.coerceIn(0, (totalFavItems - 1).coerceAtLeast(0))) favRowFocusRequester else remember { FocusRequester() }
                                            TvMovieRailCard(
                                                movie = movie,
                                                focusRequester = cardFocusRequester,
                                                onFocused = { favFocusedIndex = actualIndex },
                                                onMoveUp = onMoveUp,
                                                onMoveDown = onMoveDown,
                                                onClick = { selectedMovie = movie }
                                            )
                                        }
                                        if (hasSeeAll) {
                                            item {
                                                val seeAllIndex = chanItems.size + favMovieItems.size
                                                val cardFocusRequester = if (seeAllIndex == favFocusedIndex.coerceIn(0, (totalFavItems - 1).coerceAtLeast(0))) favRowFocusRequester else remember { FocusRequester() }
                                                TvSeeAllRailCard(
                                                    title = "See All",
                                                    subtitle = "Favorites",
                                                    modifier = Modifier.width(if (favMovieItems.isNotEmpty()) 120.dp else 135.dp),
                                                    cardModifier = if (favMovieItems.isNotEmpty()) Modifier.fillMaxWidth().aspectRatio(2f / 3f) else Modifier.fillMaxWidth().height(84.dp),
                                                    focusRequester = cardFocusRequester,
                                                    onFocused = { favFocusedIndex = seeAllIndex },
                                                    onMoveUp = onMoveUp,
                                                    onMoveDown = onMoveDown,
                                                    onClick = onOpenLiveTv
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            "forYou" -> {
                                TvForYouHomeRail(
                                    focusRequester = forYouRowFocusRequester,
                                    onMoveUp = onMoveUp,
                                    onMoveDown = onMoveDown,
                                    onClick = onOpenMovies
                                )
                            }
                            "trendingSeries" -> {
                                TvContentRail(title = "Trending Series") {
                                    val itemsToShow = remember(activeTrendingSeries, filteredSeries, settings.homeItemsPerSection) {
                                        if (activeTrendingSeries.isNotEmpty()) {
                                            activeTrendingSeries.take(settings.homeItemsPerSection)
                                        } else {
                                            filteredSeries.take(settings.homeItemsPerSection)
                                        }
                                    }
                                    val hasSeeAll = true
                                    val totalSeriesItems = itemsToShow.size + (if (hasSeeAll) 1 else 0)
                                    LazyRow(
                                        state = seriesListState,
                                        contentPadding = PaddingValues(horizontal = 36.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        itemsIndexed(itemsToShow) { index, series ->
                                            val cardFocusRequester = if (index == seriesFocusedIndex.coerceIn(0, (totalSeriesItems - 1).coerceAtLeast(0))) seriesRowFocusRequester else remember { FocusRequester() }
                                            TvSeriesRailCard(
                                                series = series,
                                                focusRequester = cardFocusRequester,
                                                onFocused = { seriesFocusedIndex = index },
                                                onMoveUp = onMoveUp,
                                                onMoveDown = onMoveDown,
                                                onClick = { selectedSeries = series }
                                            )
                                        }
                                        if (hasSeeAll) {
                                            item {
                                                val seeAllIndex = itemsToShow.size
                                                val cardFocusRequester = if (seeAllIndex == seriesFocusedIndex.coerceIn(0, (totalSeriesItems - 1).coerceAtLeast(0))) seriesRowFocusRequester else remember { FocusRequester() }
                                                TvSeeAllRailCard(
                                                    title = "See All",
                                                    subtitle = "TV Shows",
                                                    modifier = Modifier.width(120.dp),
                                                    cardModifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                                                    focusRequester = cardFocusRequester,
                                                    onFocused = { seriesFocusedIndex = seeAllIndex },
                                                    onMoveUp = onMoveUp,
                                                    onMoveDown = onMoveDown,
                                                    onClick = onOpenSeries
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            "liveTv" -> {
                                TvContentRail(title = "On Air Now") {
                                    val itemsToShow = filteredChannels.take(settings.homeItemsPerSection.coerceAtLeast(10))
                                    val hasSeeAll = true
                                    val totalChannelItems = itemsToShow.size + (if (hasSeeAll) 1 else 0)
                                    LazyRow(
                                        state = channelsListState,
                                        contentPadding = PaddingValues(horizontal = 36.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        itemsIndexed(itemsToShow) { index, channel ->
                                            val cardFocusRequester = if (index == channelsFocusedIndex.coerceIn(0, (totalChannelItems - 1).coerceAtLeast(0))) channelsRowFocusRequester else remember { FocusRequester() }
                                            TvLiveChannelRailCard(
                                                channel = channel,
                                                focusRequester = cardFocusRequester,
                                                onFocused = { channelsFocusedIndex = index },
                                                onMoveUp = onMoveUp,
                                                onMoveDown = onMoveDown,
                                                onClick = { onPlayMedia(channel.toPlayableMedia()) }
                                            )
                                        }
                                        if (hasSeeAll) {
                                            item {
                                                val seeAllIndex = itemsToShow.size
                                                val cardFocusRequester = if (seeAllIndex == channelsFocusedIndex.coerceIn(0, (totalChannelItems - 1).coerceAtLeast(0))) channelsRowFocusRequester else remember { FocusRequester() }
                                                TvSeeAllRailCard(
                                                    title = "See All",
                                                    subtitle = "Channels",
                                                    modifier = Modifier.width(135.dp),
                                                    cardModifier = Modifier.fillMaxWidth().height(84.dp),
                                                    focusRequester = cardFocusRequester,
                                                    onFocused = { channelsFocusedIndex = seeAllIndex },
                                                    onMoveUp = onMoveUp,
                                                    onMoveDown = onMoveDown,
                                                    onClick = onOpenLiveTv
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // INTERACTIVE DETAIL MODAL SHEETS (Apple TV Sheet Overlay)
        selectedMovie?.let { movie ->
            TvMovieDetailSheet(
                movie = movie,
                onPlay = {
                    selectedMovie = null
                    onPlayMedia(movie.toPlayableMedia())
                },
                onDismiss = {
                    selectedMovie = null
                    homeCoroutineScope.launch {
                        delay(60)
                        try { heroFocusRequester.requestFocus() } catch (_: Exception) {}
                    }
                }
            )
        }

        if (selectedSeries != null && activePlaylist != null) {
            val series = selectedSeries!!
            TvSeriesDetailSheet(
                series = series,
                playlist = activePlaylist!!,
                onPlayEpisode = { ep ->
                    selectedSeries = null
                    onPlayMedia(ep.toPlayableMedia(series.name))
                },
                onDismiss = {
                    selectedSeries = null
                    homeCoroutineScope.launch {
                        delay(60)
                        try { heroFocusRequester.requestFocus() } catch (_: Exception) {}
                    }
                }
            )
        }
    }
}

@Composable
private fun TvContentRail(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.90f),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold
            )
        }

        content()
    }
}

@Composable
private fun TvSportsHomeRail(
    focusRequester: FocusRequester,
    onMoveUp: () -> Unit,
    onMoveDown: (() -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.02f else 1f, label = "sportsCardScale")

    TvContentRail(title = "Sports") {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp)
                .scale(scale)
                .clip(RoundedCornerShape(20.dp))
                .background(if (isFocused) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.08f))
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(20.dp)
                )
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.DirectionUp -> {
                                onMoveUp()
                                true
                            }
                            Key.DirectionDown -> {
                                if (onMoveDown != null) {
                                    onMoveDown()
                                    true
                                } else false
                            }
                            Key.DirectionCenter, Key.Enter -> {
                                onClick()
                                true
                            }
                            else -> false
                        }
                    } else false
                }
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SportsBasketball,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Follow Your Teams",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = "Add leagues and teams to see fixtures, live scores and standings, with one tap to the channel carrying the game.",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun TvForYouHomeRail(
    focusRequester: FocusRequester,
    onMoveUp: () -> Unit,
    onMoveDown: (() -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.02f else 1f, label = "forYouCardScale")

    TvContentRail(title = "For You") {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp)
                .scale(scale)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isFocused) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.08f))
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(14.dp)
                )
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.DirectionUp -> {
                                onMoveUp()
                                true
                            }
                            Key.DirectionDown -> {
                                if (onMoveDown != null) {
                                    onMoveDown()
                                    true
                                } else false
                            }
                            Key.DirectionCenter, Key.Enter -> {
                                onClick()
                                true
                            }
                            else -> false
                        }
                    } else false
                }
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Watch, favorite, or rate titles and we'll suggest more here.",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }
        }
    }
}

@Composable
private fun TvSeeAllRailCard(
    title: String = "See All",
    subtitle: String? = null,
    modifier: Modifier = Modifier.width(120.dp),
    cardModifier: Modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocused: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.08f else 1.0f, label = "seeAllScale")

    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocused?.invoke()
        }
    }

    Column(
        modifier = modifier
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionUp -> {
                            if (onMoveUp != null) {
                                onMoveUp()
                                true
                            } else false
                        }
                        Key.DirectionDown -> {
                            if (onMoveDown != null) {
                                onMoveDown()
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = cardModifier
                .scale(scale)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (isFocused) {
                        Brush.verticalGradient(
                            listOf(
                                LumeAccentBlue.copy(alpha = 0.35f),
                                Color(0xFF1B2333)
                            )
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF181C26),
                                Color(0xFF10131B)
                            )
                        )
                    }
                )
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp)
                )
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (isFocused) LumeAccentBlue else Color.White.copy(alpha = 0.08f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = if (isFocused) Color.White else Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Text(
                    text = title,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.90f),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = if (isFocused) Color.White.copy(alpha = 0.80f) else LumeTextSecondary,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }

        Text(
            text = "Browse All",
            color = if (isFocused) LumeAccentBlue else Color.White.copy(alpha = 0.70f),
            fontSize = 10.5.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TvLiveChannelRailCard(
    channel: ChannelEntity,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocused: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.08f else 1.0f, label = "chanScale")

    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocused?.invoke()
        }
    }

    Column(
        modifier = Modifier
            .width(135.dp)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionUp -> {
                            if (onMoveUp != null) {
                                onMoveUp()
                                true
                            } else false
                        }
                        Key.DirectionDown -> {
                            if (onMoveDown != null) {
                                onMoveDown()
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .scale(scale)
                .clip(RoundedCornerShape(6.dp))
                .background(if (isFocused) Color(0xFF242A38) else Color(0xFF161A24))
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp)
                )
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (channel.logoUrl.isNotBlank()) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Tv, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Text("CH ${channel.channelNumber}", color = LumeTextSecondary, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Red LIVE pill
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFFFF3B30))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text("LIVE", color = Color.White, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
        ) {
            Text(
                text = channel.name,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.88f),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TvMovieRailCard(
    movie: MovieEntity,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocused: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.08f else 1.0f, label = "movieScale")

    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocused?.invoke()
        }
    }

    Column(
        modifier = Modifier
            .width(120.dp)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionUp -> {
                            if (onMoveUp != null) {
                                onMoveUp()
                                true
                            } else false
                        }
                        Key.DirectionDown -> {
                            if (onMoveDown != null) {
                                onMoveDown()
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .scale(scale)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF161A24))
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp)
                )
        ) {
            if (movie.posterUrl.isNotBlank()) {
                AsyncImage(
                    model = movie.posterUrl,
                    contentDescription = movie.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(movie.name.take(2).uppercase(), color = Color.White.copy(alpha = 0.5f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (movie.rating > 0f) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(9.dp))
                    Text(String.format(Locale.US, "%.1f", movie.rating), color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
        ) {
            Text(
                text = movie.name,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.88f),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TvSeriesRailCard(
    series: SeriesEntity,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocused: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.08f else 1.0f, label = "seriesScale")

    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocused?.invoke()
        }
    }

    Column(
        modifier = Modifier
            .width(120.dp)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionUp -> {
                            if (onMoveUp != null) {
                                onMoveUp()
                                true
                            } else false
                        }
                        Key.DirectionDown -> {
                            if (onMoveDown != null) {
                                onMoveDown()
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .scale(scale)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF161A24))
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp)
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
                    Text(series.name.take(2).uppercase(), color = Color.White.copy(alpha = 0.5f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (series.rating > 0f) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(9.dp))
                    Text(String.format(Locale.US, "%.1f", series.rating), color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
        ) {
            Text(
                text = series.name,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.88f),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TvContinueWatchingRailCard(
    item: WatchProgressEntity,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocused: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.08f else 1.0f, label = "cwScale")
    val isLive = item.contentType == "channel" || item.durationMs <= 0
    val progressFraction = if (!isLive && item.durationMs > 0) {
        (item.positionMs.toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocused?.invoke()
        }
    }

    Column(
        modifier = Modifier
            .width(135.dp)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionUp -> {
                            if (onMoveUp != null) {
                                onMoveUp()
                                true
                            } else false
                        }
                        Key.DirectionDown -> {
                            if (onMoveDown != null) {
                                onMoveDown()
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .scale(scale)
                .clip(RoundedCornerShape(6.dp))
                .background(if (isFocused) Color(0xFF242A38) else Color(0xFF161A24))
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp)
                )
        ) {
            if (item.posterUrl.isNotBlank()) {
                if (isLive) {
                    // Live channel logo centered with comfortable margin
                    AsyncImage(
                        model = item.posterUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                    )
                } else {
                    // Movie/Series poster cropped to fill
                    AsyncImage(
                        model = item.posterUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isLive) Icons.Default.Tv else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Only show progress bar for movies/episodes with duration, NEVER on live channels
            if (!isLive && progressFraction > 0f) {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                    color = LumeAccentBlue,
                    trackColor = Color.White.copy(alpha = 0.25f)
                )
            }

            // Red LIVE badge for live channels
            if (isLive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFFFF3B30))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("LIVE", color = Color.White, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
        ) {
            Text(
                text = item.title,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.88f),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Authentic Apple TV [ ⓘ Details ] capsule button matching TVHomeScreen.swift's detailsPill.
 * Includes D-pad Left/Right navigation for hero carousel slide cycling.
 */
@Composable
private fun TvShowcaseDetailsPill(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    focusRequester: FocusRequester,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.06f else 1.0f, label = "pillScale")

    Box(
        modifier = Modifier
            .focusRequester(focusRequester)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isFocused) Color.White else Color.White.copy(alpha = 0.18f)
            )
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            onMoveLeft()
                            true
                        }
                        Key.DirectionRight -> {
                            onMoveRight()
                            true
                        }
                        Key.DirectionUp -> {
                            onMoveUp()
                            true
                        }
                        Key.DirectionDown -> {
                            onMoveDown()
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isFocused) Color.Black else Color.White,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = title,
                color = if (isFocused) Color.Black else Color.White,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun TvHeroPageDots(
    totalSlides: Int,
    currentIndex: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until totalSlides) {
            val isCurrent = i == currentIndex
            Box(
                modifier = Modifier
                    .height(3.dp)
                    .width(if (isCurrent) 18.dp else 4.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(if (isCurrent) Color.White else Color.White.copy(alpha = 0.35f))
            )
        }
    }
}
