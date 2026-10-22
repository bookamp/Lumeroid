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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.getlume.android.LumeApp
import org.getlume.android.core.data.entities.ChannelEntity
import org.getlume.android.core.data.entities.MovieEntity
import org.getlume.android.core.data.entities.SeriesEntity
import org.getlume.android.core.data.entities.WatchProgressEntity
import org.getlume.android.core.designsystem.*
import org.getlume.android.core.player.PlayableMedia
import org.getlume.android.core.player.toPlayableMedia
import org.getlume.android.core.settings.AppSettings
import org.getlume.android.core.sync.ContentSyncManager
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
    val channel: ChannelEntity? = null
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
    heroFocusRequester: FocusRequester = remember { FocusRequester() },
    isTopBarFocused: Boolean = false,
    onMoveUpToTopBar: (() -> Unit)? = null
) {
    val db = LumeApp.instance.database
    val settings = remember { AppSettings.get() }
    val revision by settings.revision.collectAsState()
    val activePlaylist by db.playlistDao().observeActivePlaylist().collectAsState(initial = null)
    val playlistId = activePlaylist?.id ?: ""

    // Observe real content from active IPTV connection
    val channels by db.channelDao().observeChannelsByPlaylist(playlistId).collectAsState(initial = emptyList())
    val featuredMovies by db.vodDao().getFeaturedMovies(playlistId, 10).collectAsState(initial = emptyList())
    val featuredSeries by db.vodDao().getFeaturedSeries(playlistId, 10).collectAsState(initial = emptyList())
    val movies by db.vodDao().getMovies(playlistId, 40).collectAsState(initial = emptyList())
    val seriesList by db.vodDao().getSeries(playlistId, 40).collectAsState(initial = emptyList())
    val favoriteChannels by db.channelDao().getFavoriteChannels(playlistId).collectAsState(initial = emptyList())
    val favoriteMovies by db.vodDao().getFavoriteMovies(playlistId).collectAsState(initial = emptyList())
    val activeProfile by db.profileDao().observeActiveProfile().collectAsState(initial = null)
    val activeProfileId = activeProfile?.id ?: "default"
    val continueWatching by db.watchProgressDao().getContinueWatching(activeProfileId).collectAsState(initial = emptyList())

    // Detail Modal Sheet State
    var selectedMovie by remember { mutableStateOf<MovieEntity?>(null) }
    var selectedSeries by remember { mutableStateOf<SeriesEntity?>(null) }

    // Intercept Back button when modal sheets are open
    if (selectedMovie != null) {
        BackHandler { selectedMovie = null }
    }
    if (selectedSeries != null) {
        BackHandler { selectedSeries = null }
    }

    // Trigger initial catalog sync & background enrichment if database is empty
    LaunchedEffect(activePlaylist) {
        activePlaylist?.let { playlist ->
            if (movies.isEmpty() && seriesList.isEmpty()) {
                ContentSyncManager.syncInitialCatalog(playlist)
            } else {
                ContentSyncManager.enrichFeaturedMedia(playlist)
            }
        }
    }

    // Dynamic hero slides constructed strictly from real IPTV content, matching matchTrending in HomeView+Trending.swift
    val heroSlides = remember(featuredMovies, featuredSeries, channels) {
        val items = mutableListOf<DynamicHeroItem>()
        val maxCount = maxOf(featuredMovies.size, featuredSeries.size)
        for (i in 0 until maxCount) {
            if (i < featuredMovies.size) {
                val movie = featuredMovies[i]
                items.add(
                    DynamicHeroItem(
                        id = "movie-${movie.id}",
                        title = movie.name.trim(),
                        overview = if (movie.plot.isNotBlank()) movie.plot else "Watch this movie in high definition from your IPTV subscription.",
                        rating = if (movie.rating > 0f) String.format(Locale.US, "%.1f", movie.rating) else "8.5",
                        posterUrl = movie.posterUrl,
                        backdropUrl = movie.backdropUrl,
                        playableMedia = movie.toPlayableMedia(),
                        movie = movie
                    )
                )
            }
            if (i < featuredSeries.size) {
                val series = featuredSeries[i]
                items.add(
                    DynamicHeroItem(
                        id = "series-${series.id}",
                        title = series.name.trim(),
                        overview = if (series.plot.isNotBlank()) series.plot else "Stream all episodes of this series on demand from your IPTV subscription.",
                        rating = if (series.rating > 0f) String.format(Locale.US, "%.1f", series.rating) else "8.0",
                        posterUrl = series.posterUrl,
                        backdropUrl = series.backdropUrl,
                        playableMedia = null,
                        series = series
                    )
                )
            }
        }
        // Fall back to live channels if no movies/series exist yet
        if (items.isEmpty() && channels.isNotEmpty()) {
            channels.take(4).forEach { chan ->
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
    val homeCoroutineScope = rememberCoroutineScope()

    @OptIn(ExperimentalFoundationApi::class)
    val noOpBringIntoView = remember {
        object : BringIntoViewResponder {
            @ExperimentalFoundationApi
            override fun calculateRectForParent(localRect: Rect): Rect = Rect.Zero

            @ExperimentalFoundationApi
            override suspend fun bringChildIntoView(localRect: () -> Rect?) {}
        }
    }

    // When anywhere on Home screen (Hero showcase or content rails), Back smoothly retracts up to Home tab in TopBar
    BackHandler(enabled = !isTopBarFocused && selectedMovie == null && selectedSeries == null) {
        homeCoroutineScope.launch {
            scrollState.scrollTo(0)
        }
        onMoveUpToTopBar?.invoke()
    }
    val cwRowFocusRequester = remember { FocusRequester() }
    val favRowFocusRequester = remember { FocusRequester() }
    val moviesRowFocusRequester = remember { FocusRequester() }
    val seriesRowFocusRequester = remember { FocusRequester() }
    val channelsRowFocusRequester = remember { FocusRequester() }

    val cwListState = rememberLazyListState()
    val favListState = rememberLazyListState()
    val moviesListState = rememberLazyListState()
    val seriesListState = rememberLazyListState()
    val channelsListState = rememberLazyListState()

    var cwFocusedIndex by remember { mutableIntStateOf(0) }
    var favFocusedIndex by remember { mutableIntStateOf(0) }
    var moviesFocusedIndex by remember { mutableIntStateOf(0) }
    var seriesFocusedIndex by remember { mutableIntStateOf(0) }
    var channelsFocusedIndex by remember { mutableIntStateOf(0) }

    val showCw = settings.isHomeSectionEnabled("recentlyWatched") && settings.homeShowContinueWatching && continueWatching.isNotEmpty()
    val showFav = settings.isHomeSectionEnabled("favorites") && (favoriteChannels.isNotEmpty() || favoriteMovies.isNotEmpty())
    val showMovies = settings.isHomeSectionEnabled("trendingMovies") && settings.isPlaylistTabVisible(playlistId, "movies") && movies.isNotEmpty()
    val showSeries = settings.isHomeSectionEnabled("trendingSeries") && settings.isPlaylistTabVisible(playlistId, "series") && seriesList.isNotEmpty()
    val showChannels = settings.isHomeSectionEnabled("liveTv") && settings.isPlaylistTabVisible(playlistId, "livetv") && channels.isNotEmpty()

    val scrollToHeroAndFocus: () -> Unit = {
        homeCoroutineScope.launch {
            scrollState.animateScrollTo(0)
            try { heroFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    val focusRailSafe: (LazyListState, FocusRequester, () -> Unit) -> Unit = { listState, focusRequester, resetIndex ->
        homeCoroutineScope.launch {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {
                try {
                    resetIndex()
                    listState.scrollToItem(0)
                    delay(50)
                    focusRequester.requestFocus()
                } catch (_: Exception) {
                    scrollToHeroAndFocus()
                }
            }
        }
    }

    val focusFirstVisibleRail: () -> Boolean = {
        val target: Triple<LazyListState, FocusRequester, () -> Unit>? = when {
            showCw -> Triple(cwListState, cwRowFocusRequester) { cwFocusedIndex = 0 }
            showFav -> Triple(favListState, favRowFocusRequester) { favFocusedIndex = 0 }
            showMovies -> Triple(moviesListState, moviesRowFocusRequester) { moviesFocusedIndex = 0 }
            showSeries -> Triple(seriesListState, seriesRowFocusRequester) { seriesFocusedIndex = 0 }
            showChannels -> Triple(channelsListState, channelsRowFocusRequester) { channelsFocusedIndex = 0 }
            else -> null
        }
        if (target != null) {
            val (listState, focusRequester, resetIndex) = target
            homeCoroutineScope.launch {
                try {
                    focusRequester.requestFocus()
                } catch (_: Exception) {
                    try {
                        resetIndex()
                        listState.scrollToItem(0)
                        delay(50)
                        focusRequester.requestFocus()
                    } catch (_: Exception) {}
                }
            }
            true
        } else {
            false
        }
    }

    val moveUpFromCw: () -> Unit = {
        scrollToHeroAndFocus()
    }

    val moveUpFromFav: () -> Unit = {
        if (showCw) {
            focusRailSafe(cwListState, cwRowFocusRequester) { cwFocusedIndex = 0 }
        } else {
            scrollToHeroAndFocus()
        }
    }

    val moveUpFromMovies: () -> Unit = {
        when {
            showFav -> focusRailSafe(favListState, favRowFocusRequester) { favFocusedIndex = 0 }
            showCw -> focusRailSafe(cwListState, cwRowFocusRequester) { cwFocusedIndex = 0 }
            else -> scrollToHeroAndFocus()
        }
    }

    val moveUpFromSeries: () -> Unit = {
        when {
            showMovies -> focusRailSafe(moviesListState, moviesRowFocusRequester) { moviesFocusedIndex = 0 }
            showFav -> focusRailSafe(favListState, favRowFocusRequester) { favFocusedIndex = 0 }
            showCw -> focusRailSafe(cwListState, cwRowFocusRequester) { cwFocusedIndex = 0 }
            else -> scrollToHeroAndFocus()
        }
    }

    val moveUpFromChannels: () -> Unit = {
        when {
            showSeries -> focusRailSafe(seriesListState, seriesRowFocusRequester) { seriesFocusedIndex = 0 }
            showMovies -> focusRailSafe(moviesListState, moviesRowFocusRequester) { moviesFocusedIndex = 0 }
            showFav -> focusRailSafe(favListState, favRowFocusRequester) { favFocusedIndex = 0 }
            showCw -> focusRailSafe(cwListState, cwRowFocusRequester) { cwFocusedIndex = 0 }
            else -> scrollToHeroAndFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090B10))
    ) {
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

        // Frosted Gradient Scrims: Authentic Apple TV vignette
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = 0.55f),
                            0.35f to Color.Transparent,
                            0.60f to Color.Black.copy(alpha = 0.50f),
                            0.82f to Color(0xFF090B10).copy(alpha = 0.90f),
                            1.0f to Color(0xFF090B10)
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
                            0.0f to Color.Black.copy(alpha = 0.85f),
                            0.45f to Color.Black.copy(alpha = 0.40f),
                            0.80f to Color.Transparent
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
                    .padding(56.dp),
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
            ) {
                // SHOWCASE HERO SECTION (Apple TV TVHeroShowcase)
                if (currentHero != null) {
                    @OptIn(ExperimentalFoundationApi::class)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(440.dp)
                            .padding(top = 40.dp, start = 56.dp, end = 56.dp, bottom = 12.dp)
                            .bringIntoViewResponder(noOpBringIntoView),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Column(
                            modifier = Modifier.width(680.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Rating Badge
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                if (currentHero.rating == "LIVE") {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFFFF3B30))
                                    )
                                    Text("LIVE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(13.dp))
                                    Text(currentHero.rating, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Title
                            Text(
                                text = currentHero.title,
                                color = Color.White,
                                fontSize = 42.sp,
                                fontWeight = FontWeight.ExtraBold,
                                lineHeight = 48.sp,
                                letterSpacing = (-0.5).sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Synopsis
                            Text(
                                text = currentHero.overview,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            // Apple TV [ ⓘ Details ] Action Capsule Button with D-pad Left/Right Paging
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
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

                            Spacer(modifier = Modifier.height(4.dp))

                            // Carousel Dots Indicator (━ • • •)
                            if (heroSlides.size > 1) {
                                TvHeroPageDots(
                                    totalSlides = heroSlides.size,
                                    currentIndex = currentHeroIndex
                                )
                            }
                        }
                    }
                }

                // CONTENT RAILS (Apple TV HomeRows)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(36.dp)
                ) {
                    // Rail 1: Recently Watched (Continue Watching with progress bar)
                    if (showCw) {
                        TvContentRail(
                            title = "Recently Watched"
                        ) {
                            val itemsToShow = continueWatching.take(settings.homeItemsPerSection)
                            LazyRow(
                                state = cwListState,
                                contentPadding = PaddingValues(horizontal = 56.dp),
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(itemsToShow) { index, item ->
                                    val cardFocusRequester = if (index == cwFocusedIndex.coerceIn(0, (itemsToShow.size - 1).coerceAtLeast(0))) cwRowFocusRequester else remember { FocusRequester() }
                                    TvContinueWatchingRailCard(
                                        item = item,
                                        focusRequester = cardFocusRequester,
                                        onFocused = { cwFocusedIndex = index },
                                        onMoveUp = moveUpFromCw,
                                        onClick = {
                                            val media = PlayableMedia(
                                                id = item.contentId,
                                                title = item.title,
                                                streamUrl = "", // looked up during playback
                                                posterUrl = item.posterUrl,
                                                isLive = false,
                                                startPositionMs = item.positionMs
                                            )
                                            onPlayMedia(media)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Rail 2: Favorites (Channels & Movies)
                    if (showFav) {
                        TvContentRail(
                            title = "Favorites"
                        ) {
                            val chanItems = favoriteChannels.take(settings.homeItemsPerSection)
                            val favMovieItems = favoriteMovies.take(settings.homeItemsPerSection)
                            val hasSeeAll = true
                            val totalFavItems = chanItems.size + favMovieItems.size + (if (hasSeeAll) 1 else 0)
                            LazyRow(
                                state = favListState,
                                contentPadding = PaddingValues(horizontal = 56.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(chanItems) { index, channel ->
                                    val cardFocusRequester = if (index == favFocusedIndex.coerceIn(0, (totalFavItems - 1).coerceAtLeast(0))) favRowFocusRequester else remember { FocusRequester() }
                                    TvLiveChannelRailCard(
                                        channel = channel,
                                        focusRequester = cardFocusRequester,
                                        onFocused = { favFocusedIndex = index },
                                        onMoveUp = moveUpFromFav,
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
                                        onMoveUp = moveUpFromFav,
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
                                            modifier = Modifier.width(if (favMovieItems.isNotEmpty()) 150.dp else 180.dp),
                                            cardModifier = if (favMovieItems.isNotEmpty()) Modifier.fillMaxWidth().aspectRatio(2f / 3f) else Modifier.fillMaxWidth().height(110.dp),
                                            focusRequester = cardFocusRequester,
                                            onFocused = { favFocusedIndex = seeAllIndex },
                                            onMoveUp = moveUpFromFav,
                                            onClick = onOpenLiveTv
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Rail 3: Trending Movies (from active IPTV connection)
                    if (showMovies) {
                        TvContentRail(
                            title = "Trending Movies"
                        ) {
                            val itemsToShow = movies.take(settings.homeItemsPerSection)
                            val hasSeeAll = true
                            val totalMovieItems = itemsToShow.size + (if (hasSeeAll) 1 else 0)
                            LazyRow(
                                state = moviesListState,
                                contentPadding = PaddingValues(horizontal = 56.dp),
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(itemsToShow) { index, movie ->
                                    val cardFocusRequester = if (index == moviesFocusedIndex.coerceIn(0, (totalMovieItems - 1).coerceAtLeast(0))) moviesRowFocusRequester else remember { FocusRequester() }
                                    TvMovieRailCard(
                                        movie = movie,
                                        focusRequester = cardFocusRequester,
                                        onFocused = { moviesFocusedIndex = index },
                                        onMoveUp = moveUpFromMovies,
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
                                            modifier = Modifier.width(150.dp),
                                            cardModifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                                            focusRequester = cardFocusRequester,
                                            onFocused = { moviesFocusedIndex = seeAllIndex },
                                            onMoveUp = moveUpFromMovies,
                                            onClick = onOpenMovies
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Rail 4: Popular TV Series (from active IPTV connection)
                    if (showSeries) {
                        TvContentRail(
                            title = "Popular Series"
                        ) {
                            val itemsToShow = seriesList.take(settings.homeItemsPerSection)
                            val hasSeeAll = true
                            val totalSeriesItems = itemsToShow.size + (if (hasSeeAll) 1 else 0)
                            LazyRow(
                                state = seriesListState,
                                contentPadding = PaddingValues(horizontal = 56.dp),
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(itemsToShow) { index, series ->
                                    val cardFocusRequester = if (index == seriesFocusedIndex.coerceIn(0, (totalSeriesItems - 1).coerceAtLeast(0))) seriesRowFocusRequester else remember { FocusRequester() }
                                    TvSeriesRailCard(
                                        series = series,
                                        focusRequester = cardFocusRequester,
                                        onFocused = { seriesFocusedIndex = index },
                                        onMoveUp = moveUpFromSeries,
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
                                            modifier = Modifier.width(150.dp),
                                            cardModifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                                            focusRequester = cardFocusRequester,
                                            onFocused = { seriesFocusedIndex = seeAllIndex },
                                            onMoveUp = moveUpFromSeries,
                                            onClick = onOpenSeries
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Rail 5: Live Channels (On Air Now)
                    if (showChannels) {
                        TvContentRail(
                            title = "On Air Now"
                        ) {
                            val itemsToShow = channels.take(settings.homeItemsPerSection.coerceAtLeast(10))
                            val hasSeeAll = true
                            val totalChannelItems = itemsToShow.size + (if (hasSeeAll) 1 else 0)
                            LazyRow(
                                state = channelsListState,
                                contentPadding = PaddingValues(horizontal = 56.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(itemsToShow) { index, channel ->
                                    val cardFocusRequester = if (index == channelsFocusedIndex.coerceIn(0, (totalChannelItems - 1).coerceAtLeast(0))) channelsRowFocusRequester else remember { FocusRequester() }
                                    TvLiveChannelRailCard(
                                        channel = channel,
                                        focusRequester = cardFocusRequester,
                                        onFocused = { channelsFocusedIndex = index },
                                        onMoveUp = moveUpFromChannels,
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
                                            modifier = Modifier.width(180.dp),
                                            cardModifier = Modifier.fillMaxWidth().height(110.dp),
                                            focusRequester = cardFocusRequester,
                                            onFocused = { channelsFocusedIndex = seeAllIndex },
                                            onMoveUp = moveUpFromChannels,
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

        // INTERACTIVE DETAIL MODAL SHEETS (Apple TV Sheet Overlay)
        selectedMovie?.let { movie ->
            TvMovieDetailSheet(
                movie = movie,
                onPlay = {
                    onPlayMedia(movie.toPlayableMedia())
                },
                onDismiss = { selectedMovie = null }
            )
        }

        if (selectedSeries != null && activePlaylist != null) {
            val series = selectedSeries!!
            TvSeriesDetailSheet(
                series = series,
                playlist = activePlaylist!!,
                onPlayEpisode = { ep ->
                    onPlayMedia(ep.toPlayableMedia(series.name))
                },
                onDismiss = { selectedSeries = null }
            )
        }
    }
}

@Composable
private fun TvContentRail(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.90f),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        content()
    }
}

@Composable
private fun TvSeeAllRailCard(
    title: String = "See All",
    subtitle: String? = null,
    modifier: Modifier = Modifier.width(150.dp),
    cardModifier: Modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocused: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
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
            .scale(scale)
            .focusRequester(focusRequester)
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
            modifier = cardModifier
                .clip(RoundedCornerShape(14.dp))
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
                    width = if (isFocused) 2.5.dp else 1.dp,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
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
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = title,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.90f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = if (isFocused) Color.White.copy(alpha = 0.80f) else LumeTextSecondary,
                        fontSize = 11.sp,
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
            fontSize = 13.sp,
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
            .width(180.dp)
            .scale(scale)
            .focusRequester(focusRequester)
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
                .height(110.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isFocused) Color.White else Color(0xFF161A24))
                .border(
                    width = if (isFocused) 2.5.dp else 1.dp,
                    color = if (isFocused) LumeAccentBlue else Color.White.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(14.dp),
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
                    Icon(Icons.Default.Tv, contentDescription = null, tint = if (isFocused) Color.Black else Color.White, modifier = Modifier.size(28.dp))
                    Text("CH ${channel.channelNumber}", color = if (isFocused) Color.Black else LumeTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Red LIVE pill
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFFF3B30))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("LIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }

        Text(
            text = channel.name,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.88f),
            fontSize = 13.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TvMovieRailCard(
    movie: MovieEntity,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocused: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
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
            .width(150.dp)
            .scale(scale)
            .focusRequester(focusRequester)
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
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF161A24))
                .border(
                    width = if (isFocused) 2.5.dp else 1.dp,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(14.dp)
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
                    Text(movie.name.take(2).uppercase(), color = Color.White.copy(alpha = 0.5f), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (movie.rating > 0f) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(10.dp))
                    Text(String.format(Locale.US, "%.1f", movie.rating), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Text(
            text = movie.name,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.88f),
            fontSize = 13.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TvSeriesRailCard(
    series: SeriesEntity,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocused: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
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
            .width(150.dp)
            .scale(scale)
            .focusRequester(focusRequester)
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
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF161A24))
                .border(
                    width = if (isFocused) 2.5.dp else 1.dp,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(14.dp)
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
                    Text(series.name.take(2).uppercase(), color = Color.White.copy(alpha = 0.5f), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (series.rating > 0f) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(10.dp))
                    Text(String.format(Locale.US, "%.1f", series.rating), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
private fun TvContinueWatchingRailCard(
    item: WatchProgressEntity,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocused: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.08f else 1.0f, label = "cwScale")
    val progressFraction = if (item.durationMs > 0) (item.positionMs.toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f) else 0.5f

    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocused?.invoke()
        }
    }

    Column(
        modifier = Modifier
            .width(200.dp)
            .scale(scale)
            .focusRequester(focusRequester)
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
                .height(118.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF161A24))
                .border(
                    width = if (isFocused) 2.5.dp else 1.dp,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(14.dp)
                )
        ) {
            if (item.posterUrl.isNotBlank()) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(36.dp))
                }
            }

            // Apple TV linear resume bar at bottom
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.BottomCenter),
                color = LumeAccentBlue,
                trackColor = Color.White.copy(alpha = 0.25f)
            )
        }

        Text(
            text = item.title,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.88f),
            fontSize = 13.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
            .clip(RoundedCornerShape(24.dp))
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
            .padding(horizontal = 26.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isFocused) Color.Black else Color.White,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title,
                color = if (isFocused) Color.Black else Color.White,
                fontSize = 15.sp,
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
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until totalSlides) {
            val isCurrent = i == currentIndex
            Box(
                modifier = Modifier
                    .height(4.dp)
                    .width(if (isCurrent) 24.dp else 6.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isCurrent) Color.White else Color.White.copy(alpha = 0.35f))
            )
        }
    }
}
