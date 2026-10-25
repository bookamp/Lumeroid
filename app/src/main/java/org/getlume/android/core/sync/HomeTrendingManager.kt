package org.getlume.android.core.sync

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.getlume.android.LumeApp
import org.getlume.android.core.data.entities.MovieEntity
import org.getlume.android.core.data.entities.SeriesEntity
import org.getlume.android.core.network.TmdbClient
import org.getlume.android.core.network.TmdbTrendingItem
import org.getlume.android.core.player.toPlayableMedia
import org.getlume.android.ui.tv.DynamicHeroItem
import org.getlume.android.core.settings.AppSettings
import org.getlume.android.core.settings.ContentFilterCache
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class HomeTrendingResult(
    val heroItems: List<DynamicHeroItem> = emptyList(),
    val trendingMovies: List<MovieEntity> = emptyList(),
    val trendingSeries: List<SeriesEntity> = emptyList()
)

/**
 * Authentic Home Trending Manager matching Lume's HomeView+Trending.swift:
 * - Dynamically fetches trending titles from TMDB for ANY active language selection (or Global/English default)
 * - Matches them against the user's active playlist catalog by tmdbId
 * - Bypasses misleading IPTV provider language prefixes (e.g. "AR - Superman" when English is active)
 * - Formats all matched titles with clean canonical names without IPTV tags
 * - Enriches hero items with official transparent PNG logos
 * - Maintains in-memory session cache for 0ms tab-switch restores
 */
object HomeTrendingManager {
    private const val TAG = "HomeTrendingManager"

    private val sessionCache = ConcurrentHashMap<String, HomeTrendingResult>()

    private val _trendingResult = MutableStateFlow<HomeTrendingResult>(HomeTrendingResult())
    val trendingResult: StateFlow<HomeTrendingResult> = _trendingResult.asStateFlow()

    fun setCachedTrending(movies: List<MovieEntity>, series: List<SeriesEntity>) {
        if (_trendingResult.value.trendingMovies.isEmpty() && _trendingResult.value.trendingSeries.isEmpty()) {
            if (movies.isNotEmpty() || series.isNotEmpty()) {
                _trendingResult.value = HomeTrendingResult(
                    heroItems = emptyList(),
                    trendingMovies = movies,
                    trendingSeries = series
                )
            }
        }
    }


    private var enrichmentJob: Job? = null

    private val PREFIX_REGEX = Regex("^[A-Z0-9]{2,5}\\s*[-:|]\\s*")
    private val RESOLUTION_TAG_REGEX = Regex("\\s*[(|\\[](?:FHD|HDCAM|CAM|4K|UHD|HEVC|1080P|720P)[)|\\]]\\s*", RegexOption.IGNORE_CASE)
    private val MULTI_SPACE_REGEX = Regex("\\s+")

    /**
     * Cleans raw IPTV provider names by removing language prefix tags and resolution clutter.
     */
    fun cleanTitle(rawName: String): String {
        var s = rawName.trim()
        s = PREFIX_REGEX.replace(s, "")
        s = RESOLUTION_TAG_REGEX.replace(s, " ")
        return MULTI_SPACE_REGEX.replace(s, " ").trim()
    }

    /**
     * Loads trending items for the active playlist.
     * Returns cached result instantly if available, then refreshes if necessary.
     */
    suspend fun loadTrending(playlistId: String): HomeTrendingResult = withContext(Dispatchers.IO) {
        if (playlistId.isBlank()) return@withContext HomeTrendingResult()

        val settings = AppSettings.get()
        val activeLanguages = settings.homeContentLanguages
        val activeLanguageCodes = activeLanguages.map { it.lowercase().trim() }.filter { it.isNotBlank() }.toSet()
        val langsKey = activeLanguages.sorted().joinToString(",")
        val cacheKey = "$playlistId|$langsKey"

        // 1. Session cache check (instant restore on tab switches)
        sessionCache[cacheKey]?.let { cached ->
            Log.d(TAG, "Returning cached trending result for $cacheKey: ${cached.heroItems.size} heroes, ${cached.trendingMovies.size} movies, ${cached.trendingSeries.size} series")
            _trendingResult.value = cached
            return@withContext cached
        }

        Log.d(TAG, "Loading fresh trending data for playlist $playlistId (languages: $langsKey)...")
        val db = LumeApp.instance.database
        try {
            // 2. Fetch TMDB trending titles dynamically based on active language selection
            val (tmdbMovies, tmdbSeries) = coroutineScope {
                val moviesDeferred = async { TmdbClient.fetchTrendingForLanguages("movie", activeLanguages) }
                val seriesDeferred = async { TmdbClient.fetchTrendingForLanguages("tv", activeLanguages) }
                Pair(moviesDeferred.await(), seriesDeferred.await())
            }

            Log.d(TAG, "Fetched TMDB trending for languages ($langsKey): ${tmdbMovies.size} movies, ${tmdbSeries.size} series")
            val movieIds = tmdbMovies.map { it.id }
            val seriesIds = tmdbSeries.map { it.id }

            val tmdbMoviesMap = tmdbMovies.associateBy { it.id }
            val tmdbSeriesMap = tmdbSeries.associateBy { it.id }

            // 3. Batched queries against local Room catalog
            val localMovies = if (movieIds.isNotEmpty()) db.vodDao().getMoviesByTmdbIds(playlistId, movieIds) else emptyList()
            val localSeries = if (seriesIds.isNotEmpty()) db.vodDao().getSeriesByTmdbIds(playlistId, seriesIds) else emptyList()

            val isFiltering = ContentFilterCache.isFiltering.value && activeLanguageCodes.isNotEmpty()
            val allowedMovieCats = ContentFilterCache.movieCategoryIds.value
            val allowedSeriesCats = ContentFilterCache.seriesCategoryIds.value

            val allCategories = db.categoryDao().getAllCategories(playlistId)
            val catMap = allCategories.associate { it.id to it.name }

            // Dynamic Content Language filtering with false-positive provider prefix bypass:
            val filteredLocalMovies = localMovies.filter { movie ->
                if (!isFiltering) true
                else {
                    val tmdbItem = movie.tmdbId?.let { tmdbMoviesMap[it] }
                    val tmdbLang = tmdbItem?.originalLanguage?.lowercase()?.trim()
                    val isAuthenticTmdbLanguageMatch = tmdbLang != null && activeLanguageCodes.contains(tmdbLang)

                    if (isAuthenticTmdbLanguageMatch) {
                        // TMDB proves this title is authentically in the user's selected languages.
                        // Bypass misleading provider prefixes (such as "AR - " on Hollywood/Bollywood/Kollywood releases).
                        true
                    } else {
                        val catName = catMap[movie.categoryId] ?: ""
                        val catMatches = allowedMovieCats.isEmpty() || allowedMovieCats.contains(movie.categoryId)
                        val titleMatches = settings.matchesContentLanguage(movie.name, catName)
                        catMatches && titleMatches
                    }
                }
            }

            val filteredLocalSeries = localSeries.filter { series ->
                if (!isFiltering) true
                else {
                    val tmdbItem = series.tmdbId?.let { tmdbSeriesMap[it] }
                    val tmdbLang = tmdbItem?.originalLanguage?.lowercase()?.trim()
                    val isAuthenticTmdbLanguageMatch = tmdbLang != null && activeLanguageCodes.contains(tmdbLang)

                    if (isAuthenticTmdbLanguageMatch) {
                        true
                    } else {
                        val catName = catMap[series.categoryId] ?: ""
                        val catMatches = allowedSeriesCats.isEmpty() || allowedSeriesCats.contains(series.categoryId)
                        val titleMatches = settings.matchesContentLanguage(series.name, catName)
                        catMatches && titleMatches
                    }
                }
            }

            Log.d(TAG, "Matched against local catalog (post-filter): ${filteredLocalMovies.size}/${localMovies.size} movies, ${filteredLocalSeries.size}/${localSeries.size} series")

            val moviesByTmdbId = filteredLocalMovies.filter { it.tmdbId != null }.associateBy { it.tmdbId!! }
            val seriesByTmdbId = filteredLocalSeries.filter { it.tmdbId != null }.associateBy { it.tmdbId!! }

            // 4. Interleave matched titles for Hero Carousel (up to 8) and Trending rows (up to 20 each) with clean titles
            val matchedMovies = mutableListOf<MovieEntity>()
            val matchedSeries = mutableListOf<SeriesEntity>()
            val heroCandidates = mutableListOf<DynamicHeroItem>()

            val maxCount = maxOf(tmdbMovies.size, tmdbSeries.size)
            for (i in 0 until maxCount) {
                if (i < tmdbMovies.size) {
                    val tmdbItem = tmdbMovies[i]
                    val movie = moviesByTmdbId[tmdbItem.id]
                    if (movie != null) {
                        val canonicalTitle = tmdbItem.title.ifBlank { cleanTitle(movie.name) }
                        val cleanMovie = movie.copy(name = canonicalTitle)
                        matchedMovies.add(cleanMovie)

                        val backdrop = tmdbItem.fullBackdropUrl.ifBlank { cleanMovie.backdropUrl.ifBlank { cleanMovie.posterUrl } }
                        val overview = tmdbItem.overview.ifBlank { cleanMovie.plot.ifBlank { "Watch this trending movie on demand." } }
                        val ratingVal = if (tmdbItem.voteAverage > 0f) tmdbItem.voteAverage else cleanMovie.rating
                        val ratingStr = if (ratingVal > 0f) String.format(Locale.US, "%.1f", ratingVal) else "8.5"

                        heroCandidates.add(
                            DynamicHeroItem(
                                id = "movie-${cleanMovie.id}",
                                title = canonicalTitle,
                                overview = overview,
                                rating = ratingStr,
                                posterUrl = tmdbItem.fullPosterUrl.ifBlank { cleanMovie.posterUrl },
                                backdropUrl = backdrop,
                                playableMedia = cleanMovie.toPlayableMedia(),
                                movie = cleanMovie,
                                logoUrl = cleanMovie.logoUrl,
                                isTmdbMatched = true
                            )
                        )
                    }
                }

                if (i < tmdbSeries.size) {
                    val tmdbItem = tmdbSeries[i]
                    val series = seriesByTmdbId[tmdbItem.id]
                    if (series != null) {
                        val canonicalTitle = tmdbItem.title.ifBlank { cleanTitle(series.name) }
                        val cleanSeries = series.copy(name = canonicalTitle)
                        matchedSeries.add(cleanSeries)

                        val backdrop = tmdbItem.fullBackdropUrl.ifBlank { cleanSeries.backdropUrl.ifBlank { cleanSeries.posterUrl } }
                        val overview = tmdbItem.overview.ifBlank { cleanSeries.plot.ifBlank { "Stream all episodes on demand." } }
                        val ratingVal = if (tmdbItem.voteAverage > 0f) tmdbItem.voteAverage else cleanSeries.rating
                        val ratingStr = if (ratingVal > 0f) String.format(Locale.US, "%.1f", ratingVal) else "8.0"

                        heroCandidates.add(
                            DynamicHeroItem(
                                id = "series-${cleanSeries.id}",
                                title = canonicalTitle,
                                overview = overview,
                                rating = ratingStr,
                                posterUrl = tmdbItem.fullPosterUrl.ifBlank { cleanSeries.posterUrl },
                                backdropUrl = backdrop,
                                playableMedia = null,
                                series = cleanSeries,
                                logoUrl = cleanSeries.logoUrl,
                                isTmdbMatched = true
                            )
                        )
                    }
                }
            }

            // Multi-Language Catalog Backfill:
            // When specific languages or local playlist titles have fewer matches from TMDB,
            // backfill the trending rails and hero showcase with top-rated titles from
            // the user's allowed categories so all selected languages are vibrantly represented.
            if (matchedMovies.size < 20 && allowedMovieCats.isNotEmpty()) {
                val backfillCandidates = db.vodDao().getTopMoviesByCategories(playlistId, allowedMovieCats.toList(), limit = 60)
                val existingMovieIds = matchedMovies.map { it.id }.toSet()
                for (movie in backfillCandidates) {
                    if (!existingMovieIds.contains(movie.id)) {
                        val catName = catMap[movie.categoryId] ?: ""
                        if (!isFiltering || settings.matchesContentLanguage(movie.name, catName)) {
                            val cleanMovie = movie.copy(name = cleanTitle(movie.name))
                            matchedMovies.add(cleanMovie)
                            if (heroCandidates.size < 8 && (cleanMovie.backdropUrl.isNotBlank() || cleanMovie.posterUrl.isNotBlank())) {
                                val ratingStr = if (cleanMovie.rating > 0f) String.format(Locale.US, "%.1f", cleanMovie.rating) else "8.5"
                                heroCandidates.add(
                                    DynamicHeroItem(
                                        id = "movie-${cleanMovie.id}",
                                        title = cleanMovie.name,
                                        overview = cleanMovie.plot.ifBlank { "Watch this top-rated title on demand." },
                                        rating = ratingStr,
                                        posterUrl = cleanMovie.posterUrl,
                                        backdropUrl = cleanMovie.backdropUrl.ifBlank { cleanMovie.posterUrl },
                                        playableMedia = cleanMovie.toPlayableMedia(),
                                        movie = cleanMovie,
                                        logoUrl = cleanMovie.logoUrl,
                                        isTmdbMatched = cleanMovie.tmdbId != null
                                    )
                                )
                            }
                            if (matchedMovies.size >= 20) break
                        }
                    }
                }
            }

            if (matchedSeries.size < 20 && allowedSeriesCats.isNotEmpty()) {
                val backfillCandidates = db.vodDao().getTopSeriesByCategories(playlistId, allowedSeriesCats.toList(), limit = 60)
                val existingSeriesIds = matchedSeries.map { it.id }.toSet()
                for (series in backfillCandidates) {
                    if (!existingSeriesIds.contains(series.id)) {
                        val catName = catMap[series.categoryId] ?: ""
                        if (!isFiltering || settings.matchesContentLanguage(series.name, catName)) {
                            val cleanSeries = series.copy(name = cleanTitle(series.name))
                            matchedSeries.add(cleanSeries)
                            if (heroCandidates.size < 8 && (series.backdropUrl.isNotBlank() || series.posterUrl.isNotBlank())) {
                                val ratingStr = if (cleanSeries.rating > 0f) String.format(Locale.US, "%.1f", cleanSeries.rating) else "8.0"
                                heroCandidates.add(
                                    DynamicHeroItem(
                                        id = "series-${cleanSeries.id}",
                                        title = cleanSeries.name,
                                        overview = cleanSeries.plot.ifBlank { "Stream all episodes on demand." },
                                        rating = ratingStr,
                                        posterUrl = cleanSeries.posterUrl,
                                        backdropUrl = cleanSeries.backdropUrl.ifBlank { cleanSeries.posterUrl },
                                        playableMedia = null,
                                        series = cleanSeries,
                                        logoUrl = cleanSeries.logoUrl,
                                        isTmdbMatched = cleanSeries.tmdbId != null
                                    )
                                )
                            }
                            if (matchedSeries.size >= 20) break
                        }
                    }
                }
            }

            val finalHeroes = heroCandidates.take(8)
            val finalMovies = matchedMovies.take(20)
            val finalSeries = matchedSeries.take(20)
            Log.d(TAG, "Built trending result: ${heroCandidates.size} hero candidates, ${finalMovies.size} movies, ${finalSeries.size} series")

            val result = HomeTrendingResult(
                heroItems = finalHeroes,
                trendingMovies = finalMovies,
                trendingSeries = finalSeries
            )

            sessionCache[cacheKey] = result
            _trendingResult.value = result

            // Also persist to BrowseCache disk for instant frame 0 restore on next app launch
            org.getlume.android.core.data.BrowseCache.updateTrending(finalMovies, finalSeries)

            // 5. Asynchronously enrich visible hero titles with official TMDB transparent PNG logos
            enrichHeroLogos(cacheKey, finalHeroes)

            result
        } catch (e: Exception) {
            Log.e(TAG, "Error in loadTrending: ${e.message}", e)
            HomeTrendingResult()
        }
    }

    /**
     * Enriches hero items with official transparent title logos from TMDB,
     * mirroring enrichHeroLogos() in HomeView+Trending.swift.
     */
    private fun enrichHeroLogos(cacheKey: String, heroes: List<DynamicHeroItem>) {
        enrichmentJob?.cancel()
        enrichmentJob = CoroutineScope(Dispatchers.IO).launch {
            val db = LumeApp.instance.database
            var updatedAny = false
            Log.d(TAG, "enrichHeroLogos: Processing ${heroes.size} heroes...")
            val enrichedHeroes = heroes.map { hero ->
                if (hero.logoUrl.isNotBlank()) {
                    hero
                } else {
                    val movie = hero.movie
                    val series = hero.series
                    var logoUrl: String? = null
                    when {
                        movie != null && movie.tmdbId != null -> {
                            logoUrl = TmdbClient.fetchTitleLogo("movie", movie.tmdbId)
                            if (!logoUrl.isNullOrBlank()) {
                                db.vodDao().updateMovieLogo(movie.id, logoUrl)
                                updatedAny = true
                            }
                        }
                        series != null && series.tmdbId != null -> {
                            logoUrl = TmdbClient.fetchTitleLogo("tv", series.tmdbId)
                            if (!logoUrl.isNullOrBlank()) {
                                db.vodDao().updateSeriesLogo(series.id, logoUrl)
                                updatedAny = true
                            }
                        }
                    }
                    if (!logoUrl.isNullOrBlank()) {
                        hero.copy(logoUrl = logoUrl)
                    } else {
                        hero
                    }
                }
            }

            if (updatedAny) {
                val current = _trendingResult.value
                val updatedResult = current.copy(heroItems = enrichedHeroes)
                sessionCache[cacheKey] = updatedResult
                _trendingResult.value = updatedResult
            }
        }
    }

    /**
     * Clears the session cache (e.g. on playlist switch or manual catalog sync).
     */
    fun clearCache() {
        sessionCache.clear()
        _trendingResult.value = HomeTrendingResult()
    }
}
