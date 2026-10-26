package org.getlumeroid.android.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.getlumeroid.android.LumeroidApp
import org.getlumeroid.android.core.data.BrowseCache
import org.getlumeroid.android.core.data.entities.ChannelEntity
import org.getlumeroid.android.core.data.entities.MovieEntity
import org.getlumeroid.android.core.data.entities.SeriesEntity
import org.getlumeroid.android.core.data.entities.WatchProgressEntity
import org.getlumeroid.android.core.designsystem.*
import org.getlumeroid.android.core.network.ESPNClient
import org.getlumeroid.android.core.network.SportsFixture
import org.getlumeroid.android.core.player.PlayableMedia
import org.getlumeroid.android.core.player.toPlayableMedia
import org.getlumeroid.android.core.settings.AppSettings
import org.getlumeroid.android.core.settings.ContentFilterCache
import org.getlumeroid.android.core.sports.SportsChannelResolver
import org.getlumeroid.android.core.sync.HomeTrendingManager
import org.getlumeroid.android.ui.tv.DynamicHeroItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileHomeScreen(
    onPlayMedia: (PlayableMedia) -> Unit,
    onNavigateToMovies: () -> Unit,
    onNavigateToSeries: () -> Unit,
    onNavigateToLiveTv: () -> Unit,
    onNavigateToSports: () -> Unit
) {
    val db = LumeroidApp.instance.database
    val scope = rememberCoroutineScope()
    val settings = remember { AppSettings.get() }
    val activePlaylist by db.playlistDao().observeActivePlaylist().collectAsState(initial = null)
    val playlistId = activePlaylist?.id ?: ""

    var selectedSeriesForEpisodes by remember { mutableStateOf<SeriesEntity?>(null) }

    // 1. Kick off HomeTrendingManager & BrowseCache if needed
    LaunchedEffect(playlistId) {
        if (playlistId.isNotBlank()) {
            HomeTrendingManager.loadTrending(playlistId)
            BrowseCache.rebuildAsync(playlistId)
        }
    }

    // 2. Observe In-Memory Cached Rails from BrowseCache (Exact parity with Shield TV & original Lume)
    val cachedFeaturedMovies by BrowseCache.featuredMovies.collectAsState()
    val cachedFeaturedSeries by BrowseCache.featuredSeries.collectAsState()
    val cachedHomeRecentlyAdded by BrowseCache.homeRecentlyAdded.collectAsState()
    val cachedContinueWatching by BrowseCache.continueWatching.collectAsState()
    val cachedTrendingMovies by BrowseCache.trendingMovies.collectAsState()
    val cachedTrendingSeries by BrowseCache.trendingSeries.collectAsState()
    val cachedFavoriteMovies by BrowseCache.favoriteMovies.collectAsState()
    val cachedHeroItems by BrowseCache.heroItems.collectAsState()
    val cachedRecentlyWatchedMovies by BrowseCache.recentlyWatchedMovies.collectAsState()

    // 3. Observe Trending Enrichment from HomeTrendingManager
    val trendingResult by HomeTrendingManager.trendingResult.collectAsState()

    // 4. Live Channels & Sports
    val channels by db.channelDao().observeChannelsByPlaylist(playlistId).collectAsState(initial = emptyList())
    val espnClient = remember { ESPNClient() }
    var liveFixtures by remember { mutableStateOf<List<SportsFixture>>(emptyList()) }

    LaunchedEffect(Unit) {
        val fetched = espnClient.fetchLiveScoreboards()
        liveFixtures = fetched.filter { it.isLive }
    }

    // 5. Language Content Filtering
    val isFiltering by ContentFilterCache.isFiltering.collectAsState()
    val allowedMovieCats by ContentFilterCache.movieCategoryIds.collectAsState()
    val allowedSeriesCats by ContentFilterCache.seriesCategoryIds.collectAsState()
    val allowedChannelIds by ContentFilterCache.matchingChannelIds.collectAsState()

    // Resolve Hero Slides (Interactive Carousel matching HomeHeroCarousel.swift)
    val heroCandidates = if (trendingResult.heroItems.isNotEmpty()) trendingResult.heroItems else cachedHeroItems
    val heroSlides = remember(heroCandidates, cachedFeaturedMovies, cachedFeaturedSeries) {
        if (heroCandidates.isNotEmpty()) heroCandidates
        else {
            val fallback = mutableListOf<DynamicHeroItem>()
            cachedFeaturedMovies.take(3).forEach { m ->
                fallback.add(
                    DynamicHeroItem(
                        id = m.id,
                        title = m.name,
                        overview = m.plot,
                        rating = if (m.rating > 0) "%.1f".format(m.rating) else "",
                        posterUrl = m.posterUrl,
                        backdropUrl = m.backdropUrl,
                        movie = m
                    )
                )
            }
            cachedFeaturedSeries.take(2).forEach { s ->
                fallback.add(
                    DynamicHeroItem(
                        id = s.id,
                        title = s.name,
                        overview = s.plot,
                        rating = if (s.rating > 0) "%.1f".format(s.rating) else "",
                        posterUrl = s.posterUrl,
                        backdropUrl = s.backdropUrl,
                        series = s
                    )
                )
            }
            fallback
        }
    }

    // Resolve Trending Movies List (matches Shield TvHomeScreen: trendingResult -> cachedTrending -> featuredMovies)
    val trendingMoviesList = remember(trendingResult.trendingMovies, cachedTrendingMovies, cachedFeaturedMovies) {
        if (trendingResult.trendingMovies.isNotEmpty()) trendingResult.trendingMovies
        else if (cachedTrendingMovies.isNotEmpty()) cachedTrendingMovies
        else cachedFeaturedMovies
    }

    // Resolve Trending Series List
    val trendingSeriesList = remember(trendingResult.trendingSeries, cachedTrendingSeries, cachedFeaturedSeries) {
        if (trendingResult.trendingSeries.isNotEmpty()) trendingResult.trendingSeries
        else if (cachedTrendingSeries.isNotEmpty()) cachedTrendingSeries
        else cachedFeaturedSeries
    }

    // Filter Top Channels
    val filteredChannels = remember(channels, isFiltering, allowedChannelIds) {
        if (!isFiltering) channels.take(30)
        else {
            val matched = channels.filter { allowedChannelIds.contains(it.id) }
            if (matched.isNotEmpty()) matched.take(30) else channels.take(30)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090B10)),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        // 1. Featured Hero Carousel (Multi-slide carousel with auto-advance and indicators)
        if (heroSlides.isNotEmpty()) {
            item(key = "hero_carousel") {
                MobileHeroCarousel(
                    heroes = heroSlides,
                    onSelectHero = { hero ->
                        when {
                            hero.movie != null -> onPlayMedia(hero.movie.toPlayableMedia())
                            hero.series != null -> selectedSeriesForEpisodes = hero.series
                            hero.channel != null -> onPlayMedia(hero.channel.toPlayableMedia())
                            hero.playableMedia != null -> onPlayMedia(hero.playableMedia)
                        }
                    }
                )
            }
        }

        // 2. Continue Watching Rail (Unified Movies/Series/Episodes)
        if (cachedContinueWatching.isNotEmpty()) {
            item(key = "home_continue_watching") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp)
                ) {
                    MobileSectionHeader(
                        title = "CONTINUE WATCHING",
                        actionLabel = "",
                        onAction = {}
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        items(cachedContinueWatching.take(15), key = { it.contentId }) { progress ->
                            MobileContinueWatchingCard(
                                progress = progress,
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        when (progress.contentType) {
                                            "movie" -> {
                                                val movie = db.vodDao().getMovieById(progress.contentId)
                                                if (movie != null) {
                                                    onPlayMedia(movie.toPlayableMedia().copy(startPositionMs = progress.positionMs))
                                                }
                                            }
                                            "channel" -> {
                                                val channel = db.channelDao().getChannelById(progress.contentId)
                                                if (channel != null) {
                                                    onPlayMedia(channel.toPlayableMedia())
                                                }
                                            }
                                            "episode" -> {
                                                val ep = db.vodDao().getEpisodeById(progress.contentId)
                                                if (ep != null) {
                                                    val s = db.vodDao().getSeriesById(ep.seriesId)
                                                    onPlayMedia(ep.toPlayableMedia(seriesTitle = s?.name ?: "", seriesPosterUrl = s?.posterUrl ?: "", fullSeriesId = s?.id ?: "").copy(startPositionMs = progress.positionMs))
                                                }
                                            }
                                            else -> {
                                                val movie = db.vodDao().getMovieById(progress.contentId)
                                                if (movie != null) {
                                                    onPlayMedia(movie.toPlayableMedia().copy(startPositionMs = progress.positionMs))
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // 3. Trending Movies Rail (Exact Match to Shield TV)
        if (trendingMoviesList.isNotEmpty()) {
            item(key = "home_trending_movies") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                ) {
                    MobileSectionHeader(
                        title = "TRENDING MOVIES",
                        actionLabel = "Browse All",
                        onAction = onNavigateToMovies
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        items(trendingMoviesList.take(20), key = { "trend_m_${it.id}" }) { movie ->
                            MobilePosterCard(
                                title = movie.name,
                                posterUrl = movie.posterUrl,
                                subtitle = if (movie.rating > 0) "★ %.1f".format(movie.rating) else movie.releaseDate.take(4),
                                onClick = { onPlayMedia(movie.toPlayableMedia()) }
                            )
                        }
                    }
                }
            }
        }

        // 4. Recently Watched Movies Rail (if any)
        if (cachedRecentlyWatchedMovies.isNotEmpty()) {
            item(key = "home_recently_watched_movies") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                ) {
                    MobileSectionHeader(
                        title = "RECENTLY WATCHED",
                        actionLabel = "Browse All",
                        onAction = onNavigateToMovies
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        items(cachedRecentlyWatchedMovies.take(15), key = { "rw_m_${it.second.id}" }) { (progress, movie) ->
                            MobilePosterCard(
                                title = movie.name,
                                posterUrl = movie.posterUrl,
                                subtitle = if (movie.rating > 0) "★ %.1f".format(movie.rating) else "",
                                onClick = { onPlayMedia(movie.toPlayableMedia()) }
                            )
                        }
                    }
                }
            }
        }

        // 5. Recently Added Rail (Interleaved New Releases from BrowseCache)
        if (cachedHomeRecentlyAdded.isNotEmpty()) {
            item(key = "home_recently_added") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                ) {
                    MobileSectionHeader(
                        title = "RECENTLY ADDED",
                        actionLabel = "New Releases",
                        onAction = onNavigateToMovies
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        items(cachedHomeRecentlyAdded.take(20)) { item ->
                            when (item) {
                                is MovieEntity -> {
                                    MobilePosterCard(
                                        title = item.name,
                                        posterUrl = item.posterUrl,
                                        subtitle = if (item.rating > 0) "★ %.1f".format(item.rating) else "Movie",
                                        onClick = { onPlayMedia(item.toPlayableMedia()) }
                                    )
                                }
                                is SeriesEntity -> {
                                    MobilePosterCard(
                                        title = item.name,
                                        posterUrl = item.posterUrl,
                                        subtitle = if (item.rating > 0) "★ %.1f".format(item.rating) else "Series",
                                        onClick = { selectedSeriesForEpisodes = item }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. Favorites Rail (if any)
        if (cachedFavoriteMovies.isNotEmpty()) {
            item(key = "home_favorite_movies") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                ) {
                    MobileSectionHeader(
                        title = "FAVORITES",
                        actionLabel = "See All",
                        onAction = onNavigateToMovies
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        items(cachedFavoriteMovies.take(15), key = { "fav_m_${it.id}" }) { movie ->
                            MobilePosterCard(
                                title = movie.name,
                                posterUrl = movie.posterUrl,
                                subtitle = if (movie.rating > 0) "★ %.1f".format(movie.rating) else "",
                                onClick = { onPlayMedia(movie.toPlayableMedia()) }
                            )
                        }
                    }
                }
            }
        }

        // 7. Trending Series Rail (Exact Match to Shield TV)
        if (trendingSeriesList.isNotEmpty()) {
            item(key = "home_trending_series") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                ) {
                    MobileSectionHeader(
                        title = "POPULAR SERIES",
                        actionLabel = "Browse All",
                        onAction = onNavigateToSeries
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        items(trendingSeriesList.take(20), key = { "trend_s_${it.id}" }) { s ->
                            MobilePosterCard(
                                title = s.name,
                                posterUrl = s.posterUrl,
                                subtitle = if (s.rating > 0) "★ %.1f".format(s.rating) else s.releaseDate.take(4),
                                onClick = { selectedSeriesForEpisodes = s }
                            )
                        }
                    }
                }
            }
        }

        // 8. Live Sports Rail
        if (liveFixtures.isNotEmpty()) {
            item(key = "home_sports") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                ) {
                    MobileSectionHeader(
                        title = "LIVE SPORTS",
                        actionLabel = "See All",
                        isLive = true,
                        onAction = onNavigateToSports
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        items(liveFixtures, key = { it.id }) { fixture ->
                            val matchedChannels = remember(fixture, channels) {
                                SportsChannelResolver.resolveChannelsForFixture(fixture, channels, limit = 2)
                            }
                            MobileLiveSportsCard(
                                fixture = fixture,
                                matchedChannel = matchedChannels.firstOrNull(),
                                onWatch = {
                                    matchedChannels.firstOrNull()?.let { ch ->
                                        onPlayMedia(ch.toPlayableMedia())
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // 9. On Air Now / Top Channels Rail
        if (filteredChannels.isNotEmpty()) {
            item(key = "home_channels") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                ) {
                    MobileSectionHeader(
                        title = "ON AIR NOW",
                        actionLabel = "Live Guide",
                        onAction = onNavigateToLiveTv
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        items(filteredChannels, key = { it.id }) { channel ->
                            MobileChannelCard(
                                channel = channel,
                                onClick = { onPlayMedia(channel.toPlayableMedia(lineup = filteredChannels)) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Series Episode Bottom Sheet Picker
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
                    Text(series.plot, color = LumeroidTextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text("EPISODES", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)

                if (episodes.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No episodes available", color = LumeroidTextSecondary, fontSize = 13.sp)
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
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play Episode", tint = LumeroidAccentBlue, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileHeroCarousel(
    heroes: List<DynamicHeroItem>,
    onSelectHero: (DynamicHeroItem) -> Unit
) {
    if (heroes.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { heroes.size })

    // Auto-advance every 6 seconds, paused when user is dragging
    LaunchedEffect(pagerState.isScrollInProgress, heroes.size) {
        if (!pagerState.isScrollInProgress && heroes.size > 1) {
            while (true) {
                delay(6000)
                val nextPage = (pagerState.currentPage + 1) % heroes.size
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val hero = heroes[page]
            val imageModel = hero.backdropUrl.ifBlank { hero.posterUrl }

            Box(modifier = Modifier.fillMaxSize()) {
                if (imageModel.isNotBlank()) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = hero.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Apple TV / iOS cinematic gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xFF090B10).copy(alpha = 0.45f),
                                    Color(0xFF090B10)
                                )
                            )
                        )
                )

                // Hero Info Overlay
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(LumeroidAccentBlue)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("FEATURED", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        if (hero.rating.isNotBlank()) {
                            Text("★ ${hero.rating}", color = Color(0xFFFFD700), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (hero.logoUrl.isNotBlank()) {
                        AsyncImage(
                            model = hero.logoUrl,
                            contentDescription = hero.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .height(38.dp)
                                .widthIn(max = 200.dp)
                        )
                    } else {
                        Text(
                            text = hero.title,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (hero.overview.isNotBlank()) {
                        Text(
                            text = hero.overview,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 11.5.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = { onSelectHero(hero) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Watch Now", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Pager Indicators (matching Apple TV / iOS HeroPageDots)
        if (heroes.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 26.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(heroes.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .height(4.dp)
                            .width(if (isSelected) 18.dp else 6.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isSelected) Color.White else Color.White.copy(alpha = 0.35f))
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileContinueWatchingCard(
    progress: WatchProgressEntity,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .width(140.dp)
                .height(84.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF141923))
        ) {
            if (progress.posterUrl.isNotBlank()) {
                AsyncImage(
                    model = progress.posterUrl,
                    contentDescription = progress.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Progress bar
            val fraction = if (progress.durationMs > 0) (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f) else 0.5f
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(LumeroidAccentBlue)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = progress.title,
            color = Color.White,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Resume",
            color = LumeroidAccentBlue,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun MobileSectionHeader(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
    isLive: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isLive) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(LumeroidLiveRed)
                )
            }
            Text(
                text = title,
                color = if (isLive) LumeroidLiveRed else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
        if (actionLabel.isNotBlank()) {
            Text(
                text = actionLabel,
                color = LumeroidAccentBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onAction)
            )
        }
    }
}

@Composable
private fun MobilePosterCard(
    title: String,
    posterUrl: String,
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
            if (posterUrl.isNotBlank()) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                color = LumeroidTextSecondary,
                fontSize = 10.5.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MobileLiveSportsCard(
    fixture: SportsFixture,
    matchedChannel: ChannelEntity?,
    onWatch: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(200.dp)
            .height(110.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF161B24))
            .clickable(onClick = onWatch)
            .padding(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(fixture.leagueName.take(14).uppercase(), color = Color.White.copy(alpha = 0.45f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(LumeroidLiveRed)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text("LIVE", color = Color.White, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(fixture.homeTeamName, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                Text(fixture.homeTeamScore.ifBlank { "0" }, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(fixture.awayTeamName, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                Text(fixture.awayTeamScore.ifBlank { "0" }, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
            }

            if (matchedChannel != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = LumeroidAccentBlue, modifier = Modifier.size(10.dp))
                    Text(matchedChannel.name, color = Color.White.copy(alpha = 0.85f), fontSize = 8.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    Box(
        modifier = Modifier
            .width(130.dp)
            .height(80.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF141923))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            if (channel.logoUrl.isNotBlank()) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    modifier = Modifier.size(28.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(channel.name.take(2).uppercase(), color = Color.White, fontSize = 10.sp)
                }
            }
            Text(
                text = channel.name,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
