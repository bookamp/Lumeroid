package org.getlume.android.core.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.getlume.android.LumeApp
import org.getlume.android.core.data.entities.CategoryEntity
import org.getlume.android.core.data.entities.MovieEntity
import org.getlume.android.core.data.entities.SeriesEntity
import org.getlume.android.core.data.entities.WatchProgressEntity
import org.getlume.android.core.settings.AppSettings
import org.getlume.android.core.settings.ContentFilterCache
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Global Persistent Browse Cache.
 * Precomputes, persists to disk (JSON), and holds in-memory rails (Recently Watched, Favorites, Recently Added, Preview Categories)
 * so that when TvMoviesScreen, TvSeriesScreen, or TvHomeScreen are composed,
 * data is immediately available on frame 0 (0ms, no pop-in, no dynamic shifts, zero layout thrashing).
 */
object BrowseCache {
    private const val TAG = "BrowseCache"
    private const val CACHE_FILE_PREFIX = "browse_cache_"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val rebuildMutex = Mutex()

    // --- Movies Rails ---
    private val _recentlyWatchedMovies = MutableStateFlow<List<Pair<WatchProgressEntity, MovieEntity>>>(emptyList())
    val recentlyWatchedMovies: StateFlow<List<Pair<WatchProgressEntity, MovieEntity>>> = _recentlyWatchedMovies.asStateFlow()

    private val _favoriteMovies = MutableStateFlow<List<MovieEntity>>(emptyList())
    val favoriteMovies: StateFlow<List<MovieEntity>> = _favoriteMovies.asStateFlow()

    private val _recentlyAddedMovies = MutableStateFlow<List<MovieEntity>>(emptyList())
    val recentlyAddedMovies: StateFlow<List<MovieEntity>> = _recentlyAddedMovies.asStateFlow()

    private val _previewCategoryMovies = MutableStateFlow<Map<String, List<MovieEntity>>>(emptyMap())
    val previewCategoryMovies: StateFlow<Map<String, List<MovieEntity>>> = _previewCategoryMovies.asStateFlow()

    // --- Series Rails ---
    private val _recentlyWatchedSeries = MutableStateFlow<List<Pair<WatchProgressEntity, SeriesEntity>>>(emptyList())
    val recentlyWatchedSeries: StateFlow<List<Pair<WatchProgressEntity, SeriesEntity>>> = _recentlyWatchedSeries.asStateFlow()

    private val _favoriteSeries = MutableStateFlow<List<SeriesEntity>>(emptyList())
    val favoriteSeries: StateFlow<List<SeriesEntity>> = _favoriteSeries.asStateFlow()

    private val _recentlyAddedSeries = MutableStateFlow<List<SeriesEntity>>(emptyList())
    val recentlyAddedSeries: StateFlow<List<SeriesEntity>> = _recentlyAddedSeries.asStateFlow()

    private val _previewCategorySeries = MutableStateFlow<Map<String, List<SeriesEntity>>>(emptyMap())
    val previewCategorySeries: StateFlow<Map<String, List<SeriesEntity>>> = _previewCategorySeries.asStateFlow()

    // --- Home Rails ---
    private val _continueWatching = MutableStateFlow<List<WatchProgressEntity>>(emptyList())
    val continueWatching: StateFlow<List<WatchProgressEntity>> = _continueWatching.asStateFlow()

    private val _trendingMovies = MutableStateFlow<List<MovieEntity>>(emptyList())
    val trendingMovies: StateFlow<List<MovieEntity>> = _trendingMovies.asStateFlow()

    private val _trendingSeries = MutableStateFlow<List<SeriesEntity>>(emptyList())
    val trendingSeries: StateFlow<List<SeriesEntity>> = _trendingSeries.asStateFlow()

    private val _featuredMovies = MutableStateFlow<List<MovieEntity>>(emptyList())
    val featuredMovies: StateFlow<List<MovieEntity>> = _featuredMovies.asStateFlow()

    private val _featuredSeries = MutableStateFlow<List<SeriesEntity>>(emptyList())
    val featuredSeries: StateFlow<List<SeriesEntity>> = _featuredSeries.asStateFlow()

    private val _homeRecentlyAdded = MutableStateFlow<List<Any>>(emptyList())
    val homeRecentlyAdded: StateFlow<List<Any>> = _homeRecentlyAdded.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private var initialized = false

    @Volatile
    private var activePlaylistId: String = ""

    fun init(context: Context) {
        if (initialized) return
        initialized = true

        // 1. Synchronously load disk cache BEFORE first compose frame
        try {
            val settings = AppSettings.get()
            var plId = settings.lastActivePlaylistId
            var loaded = false
            if (plId.isNotBlank()) {
                activePlaylistId = plId
                loaded = loadDiskCache(context, plId)
            }
            if (!loaded) {
                val cacheFiles = context.filesDir.listFiles { _, name ->
                    name.startsWith(CACHE_FILE_PREFIX) && name.endsWith(".json")
                }
                val newest = cacheFiles?.maxByOrNull { it.lastModified() }
                if (newest != null) {
                    plId = newest.name.removePrefix(CACHE_FILE_PREFIX).removeSuffix(".json")
                    activePlaylistId = plId
                    loadDiskCache(context, plId)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Synchronous disk cache load error: ${e.message}")
        }

        // 2. Background observers to keep cache updated
        scope.launch {
            try {
                val db = LumeApp.instance.database
                val activePl = db.playlistDao().getActivePlaylist()
                if (activePl != null) {
                    activePlaylistId = activePl.id
                    AppSettings.get().lastActivePlaylistId = activePl.id
                    if (!_isReady.value) {
                        loadDiskCache(context, activePl.id)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Initial playlist check error: ${e.message}")
            }

            // Rebuild whenever settings revision changes
            launch {
                AppSettings.get().revision.collectLatest {
                    rebuild()
                }
            }

            // Also observe watch progress flow to instantly keep recently watched up-to-date
            launch {
                val db = LumeApp.instance.database
                val activeProfileId = db.profileDao().getActiveProfile()?.id ?: "default"
                db.watchProgressDao().getContinueWatching(activeProfileId).collectLatest {
                    refreshRecentlyWatched()
                }
            }
        }
    }

    fun rebuildAsync(playlistId: String? = null) {
        scope.launch {
            rebuild(playlistId)
        }
    }

    fun updateTrending(movies: List<MovieEntity>, series: List<SeriesEntity>) {
        if (movies.isEmpty() && series.isEmpty()) return
        _trendingMovies.value = movies
        _trendingSeries.value = series
        if (activePlaylistId.isNotBlank()) {
            scope.launch {
                saveDiskCache(LumeApp.instance, activePlaylistId)
            }
        }
    }

    suspend fun rebuild(playlistIdParam: String? = null) {
        rebuildMutex.withLock {
            try {
                val db = LumeApp.instance.database
                val settings = AppSettings.get()
                val activePlaylist = if (playlistIdParam != null) {
                    db.playlistDao().getPlaylistById(playlistIdParam)
                } else {
                    db.playlistDao().getActivePlaylist()
                } ?: return

                val plId = activePlaylist.id
                activePlaylistId = plId
                settings.lastActivePlaylistId = plId

                val activeProfileId = db.profileDao().getActiveProfile()?.id ?: "default"
                val allCategories = db.categoryDao().getAllCategories(plId)
                val catMap = allCategories.associate { it.id to it.name }

                val isFiltering = ContentFilterCache.isFiltering.value
                val allowedMovieCatIds = ContentFilterCache.movieCategoryIds.value
                val allowedSeriesCatIds = ContentFilterCache.seriesCategoryIds.value

                // 1. Compute Unified Continue Watching & Recently Watched Movies & Series
                val continueWatching = db.watchProgressDao().getContinueWatchingDirect(activeProfileId)
                _continueWatching.value = continueWatching

                val movieWatchList = mutableListOf<Pair<WatchProgressEntity, MovieEntity>>()
                val seriesWatchList = mutableListOf<Pair<WatchProgressEntity, SeriesEntity>>()

                for (progress in continueWatching) {
                    if (progress.contentType == "movie") {
                        val movie = db.vodDao().getMovieById(progress.contentId)
                        if (movie != null) {
                            if (movie.playlistId == plId || plId.isEmpty()) {
                                movieWatchList.add(progress to movie)
                            }
                        } else {
                            // Backward compatibility: If saved as movie but was actually series or episode
                            var series = db.vodDao().getSeriesById(progress.contentId)
                            if (series == null) {
                                val ep = db.vodDao().getEpisodeById(progress.contentId)
                                if (ep != null) {
                                    series = db.vodDao().getSeriesById(ep.seriesId)
                                }
                            }
                            if (series != null && (series.playlistId == plId || plId.isEmpty())) {
                                seriesWatchList.add(progress to series)
                            }
                        }
                    } else if (progress.contentType == "episode") {
                        var series = db.vodDao().getSeriesById(progress.contentId)
                        if (series == null) {
                            val ep = db.vodDao().getEpisodeById(progress.contentId)
                            if (ep != null) {
                                series = db.vodDao().getSeriesById(ep.seriesId)
                            }
                        }
                        if (series != null && (series.playlistId == plId || plId.isEmpty())) {
                            seriesWatchList.add(progress to series)
                        }
                    }
                }
                _recentlyWatchedMovies.value = movieWatchList
                _recentlyWatchedSeries.value = seriesWatchList

                // 2. Favorites
                val rawFavMovies = db.vodDao().getFavoriteMoviesDirect(plId)
                val filteredFavMovies = if (!isFiltering && settings.homeContentLanguages.isEmpty()) rawFavMovies
                else rawFavMovies.filter { movie ->
                    val catName = catMap[movie.categoryId] ?: ""
                    (!isFiltering || allowedMovieCatIds.contains(movie.categoryId)) &&
                    (settings.homeContentLanguages.isEmpty() || settings.matchesContentLanguage(movie.name, catName))
                }
                _favoriteMovies.value = filteredFavMovies

                val rawFavSeries = db.vodDao().getFavoriteSeriesDirect(plId)
                val filteredFavSeries = if (!isFiltering && settings.homeContentLanguages.isEmpty()) rawFavSeries
                else rawFavSeries.filter { s ->
                    val catName = catMap[s.categoryId] ?: ""
                    (!isFiltering || allowedSeriesCatIds.contains(s.categoryId)) &&
                    (settings.homeContentLanguages.isEmpty() || settings.matchesContentLanguage(s.name, catName))
                }
                _favoriteSeries.value = filteredFavSeries

                // 3. Recently Added
                val rawRecentMovies = db.vodDao().getRecentlyAddedMoviesDirect(plId, 100)
                val filteredRecentMovies = if (!isFiltering && settings.homeContentLanguages.isEmpty()) rawRecentMovies
                else rawRecentMovies.filter { movie ->
                    val catName = catMap[movie.categoryId] ?: ""
                    (!isFiltering || allowedMovieCatIds.contains(movie.categoryId)) &&
                    (settings.homeContentLanguages.isEmpty() || settings.matchesContentLanguage(movie.name, catName))
                }
                _recentlyAddedMovies.value = filteredRecentMovies

                val rawRecentSeries = db.vodDao().getRecentlyAddedSeriesDirect(plId, 100)
                val filteredRecentSeries = if (!isFiltering && settings.homeContentLanguages.isEmpty()) rawRecentSeries
                else rawRecentSeries.filter { s ->
                    val catName = catMap[s.categoryId] ?: ""
                    (!isFiltering || allowedSeriesCatIds.contains(s.categoryId)) &&
                    (settings.homeContentLanguages.isEmpty() || settings.matchesContentLanguage(s.name, catName))
                }
                _recentlyAddedSeries.value = filteredRecentSeries

                // 4. Pre-cache Top 4 Movie Categories
                val rawMovieCats = db.categoryDao().getCategoriesDirect(plId, "movie")
                val visibleMovieCats = if (!isFiltering) rawMovieCats else rawMovieCats.filter { allowedMovieCatIds.contains(it.id) }
                val topMovieCats = visibleMovieCats.take(4)

                val movieCategoryMap = mutableMapOf<String, List<MovieEntity>>()
                for (cat in topMovieCats) {
                    val rawMovies = db.vodDao().getMoviesByCategoryLimitedDirect(plId, cat.id, 21)
                    val filtered = if (settings.homeContentLanguages.isEmpty()) rawMovies
                    else rawMovies.filter { settings.matchesContentLanguage(it.name, cat.name) }
                    movieCategoryMap[cat.id] = filtered
                }
                _previewCategoryMovies.value = movieCategoryMap

                // 5. Pre-cache Top 4 Series Categories
                val rawSeriesCats = db.categoryDao().getCategoriesDirect(plId, "series")
                val visibleSeriesCats = if (!isFiltering) rawSeriesCats else rawSeriesCats.filter { allowedSeriesCatIds.contains(it.id) }
                val topSeriesCats = visibleSeriesCats.take(4)

                val seriesCategoryMap = mutableMapOf<String, List<SeriesEntity>>()
                for (cat in topSeriesCats) {
                    val rawSeries = db.vodDao().getSeriesByCategoryLimitedDirect(plId, cat.id, 21)
                    val filtered = if (settings.homeContentLanguages.isEmpty()) rawSeries
                    else rawSeries.filter { settings.matchesContentLanguage(it.name, cat.name) }
                    seriesCategoryMap[cat.id] = filtered
                }
                _previewCategorySeries.value = seriesCategoryMap

                // 6. Featured & Home Recently Added
                val rawFeatMovies = db.vodDao().getFeaturedMoviesDirect(plId, 12)
                _featuredMovies.value = if (!isFiltering && settings.homeContentLanguages.isEmpty()) rawFeatMovies
                else rawFeatMovies.filter { movie ->
                    val catName = catMap[movie.categoryId] ?: ""
                    (!isFiltering || allowedMovieCatIds.contains(movie.categoryId)) &&
                    (settings.homeContentLanguages.isEmpty() || settings.matchesContentLanguage(movie.name, catName))
                }

                val rawFeatSeries = db.vodDao().getFeaturedSeriesDirect(plId, 12)
                _featuredSeries.value = if (!isFiltering && settings.homeContentLanguages.isEmpty()) rawFeatSeries
                else rawFeatSeries.filter { s ->
                    val catName = catMap[s.categoryId] ?: ""
                    (!isFiltering || allowedSeriesCatIds.contains(s.categoryId)) &&
                    (settings.homeContentLanguages.isEmpty() || settings.matchesContentLanguage(s.name, catName))
                }

                // Interleaved Home Recently Added
                val combined = mutableListOf<Any>()
                val maxItems = maxOf(filteredRecentMovies.size, filteredRecentSeries.size)
                for (i in 0 until minOf(maxItems, 10)) {
                    if (i < filteredRecentMovies.size) combined.add(filteredRecentMovies[i])
                    if (i < filteredRecentSeries.size) combined.add(filteredRecentSeries[i])
                }
                _homeRecentlyAdded.value = combined
                _isReady.value = true

                // Save to persistent disk cache
                saveDiskCache(LumeApp.instance, plId)

                Log.d(TAG, "BrowseCache complete: ${continueWatching.size} continue watching, ${filteredRecentMovies.size} recent movies, ${movieCategoryMap.size} preview cats.")
            } catch (e: Exception) {
                Log.e(TAG, "Error rebuilding BrowseCache: ${e.message}", e)
            }
        }
    }

    suspend fun refreshRecentlyWatched() {
        try {
            val db = LumeApp.instance.database
            val activePlaylist = db.playlistDao().getActivePlaylist() ?: return
            val plId = activePlaylist.id
            val activeProfileId = db.profileDao().getActiveProfile()?.id ?: "default"

            val continueWatching = db.watchProgressDao().getContinueWatchingDirect(activeProfileId)
            _continueWatching.value = continueWatching

            val movieWatchList = mutableListOf<Pair<WatchProgressEntity, MovieEntity>>()
            val seriesWatchList = mutableListOf<Pair<WatchProgressEntity, SeriesEntity>>()

            for (progress in continueWatching) {
                if (progress.contentType == "movie") {
                    val movie = db.vodDao().getMovieById(progress.contentId)
                    if (movie != null) {
                        if (movie.playlistId == plId || plId.isEmpty()) {
                            movieWatchList.add(progress to movie)
                        }
                    } else {
                        // Backward compatibility: If saved as movie but was actually series or episode
                        var series = db.vodDao().getSeriesById(progress.contentId)
                        if (series == null) {
                            val ep = db.vodDao().getEpisodeById(progress.contentId)
                            if (ep != null) {
                                series = db.vodDao().getSeriesById(ep.seriesId)
                            }
                        }
                        if (series != null && (series.playlistId == plId || plId.isEmpty())) {
                            seriesWatchList.add(progress to series)
                        }
                    }
                } else if (progress.contentType == "episode") {
                    var series = db.vodDao().getSeriesById(progress.contentId)
                    if (series == null) {
                        val ep = db.vodDao().getEpisodeById(progress.contentId)
                        if (ep != null) {
                            series = db.vodDao().getSeriesById(ep.seriesId)
                        }
                    }
                    if (series != null && (series.playlistId == plId || plId.isEmpty())) {
                        seriesWatchList.add(progress to series)
                    }
                }
            }

            _recentlyWatchedMovies.value = movieWatchList
            _recentlyWatchedSeries.value = seriesWatchList

            // Also update disk cache with updated watched items
            saveDiskCache(LumeApp.instance, plId)
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing recently watched: ${e.message}")
        }
    }

    private fun getCacheFile(context: Context, playlistId: String): File {
        return File(context.filesDir, "$CACHE_FILE_PREFIX$playlistId.json")
    }

    fun loadDiskCache(context: Context, playlistId: String): Boolean {
        return try {
            val file = getCacheFile(context, playlistId)
            if (!file.exists()) return false

            val json = JSONObject(file.readText())

            // Continue Watching
            val cwArr = json.optJSONArray("continueWatching") ?: JSONArray()
            val cwList = (0 until cwArr.length()).mapNotNull {
                try { jsonToProgress(cwArr.getJSONObject(it)) } catch (_: Exception) { null }
            }

            // Trending
            val tmArr = json.optJSONArray("trendingMovies") ?: JSONArray()
            val tmList = (0 until tmArr.length()).mapNotNull {
                try { jsonToMovie(tmArr.getJSONObject(it)) } catch (_: Exception) { null }
            }

            val tsArr = json.optJSONArray("trendingSeries") ?: JSONArray()
            val tsList = (0 until tsArr.length()).mapNotNull {
                try { jsonToSeries(tsArr.getJSONObject(it)) } catch (_: Exception) { null }
            }

            // Movies
            val rwMoviesArr = json.optJSONArray("recentlyWatchedMovies") ?: JSONArray()
            val rwMovies = (0 until rwMoviesArr.length()).mapNotNull { i ->
                try {
                    val obj = rwMoviesArr.getJSONObject(i)
                    val prog = jsonToProgress(obj.getJSONObject("progress"))
                    val movie = jsonToMovie(obj.getJSONObject("movie"))
                    prog to movie
                } catch (_: Exception) { null }
            }

            val favMoviesArr = json.optJSONArray("favoriteMovies") ?: JSONArray()
            val favMovies = (0 until favMoviesArr.length()).mapNotNull {
                try { jsonToMovie(favMoviesArr.getJSONObject(it)) } catch (_: Exception) { null }
            }

            val recMoviesArr = json.optJSONArray("recentlyAddedMovies") ?: JSONArray()
            val recMovies = (0 until recMoviesArr.length()).mapNotNull {
                try { jsonToMovie(recMoviesArr.getJSONObject(it)) } catch (_: Exception) { null }
            }

            val prevMoviesObj = json.optJSONObject("previewCategoryMovies") ?: JSONObject()
            val prevMoviesMap = mutableMapOf<String, List<MovieEntity>>()
            val mKeys = prevMoviesObj.keys()
            while (mKeys.hasNext()) {
                val catId = mKeys.next()
                val arr = prevMoviesObj.optJSONArray(catId) ?: JSONArray()
                prevMoviesMap[catId] = (0 until arr.length()).mapNotNull {
                    try { jsonToMovie(arr.getJSONObject(it)) } catch (_: Exception) { null }
                }
            }

            // Series
            val rwSeriesArr = json.optJSONArray("recentlyWatchedSeries") ?: JSONArray()
            val rwSeries = (0 until rwSeriesArr.length()).mapNotNull { i ->
                try {
                    val obj = rwSeriesArr.getJSONObject(i)
                    val prog = jsonToProgress(obj.getJSONObject("progress"))
                    val series = jsonToSeries(obj.getJSONObject("series"))
                    prog to series
                } catch (_: Exception) { null }
            }

            val favSeriesArr = json.optJSONArray("favoriteSeries") ?: JSONArray()
            val favSeries = (0 until favSeriesArr.length()).mapNotNull {
                try { jsonToSeries(favSeriesArr.getJSONObject(it)) } catch (_: Exception) { null }
            }

            val recSeriesArr = json.optJSONArray("recentlyAddedSeries") ?: JSONArray()
            val recSeries = (0 until recSeriesArr.length()).mapNotNull {
                try { jsonToSeries(recSeriesArr.getJSONObject(it)) } catch (_: Exception) { null }
            }

            val prevSeriesObj = json.optJSONObject("previewCategorySeries") ?: JSONObject()
            val prevSeriesMap = mutableMapOf<String, List<SeriesEntity>>()
            val sKeys = prevSeriesObj.keys()
            while (sKeys.hasNext()) {
                val catId = sKeys.next()
                val arr = prevSeriesObj.optJSONArray(catId) ?: JSONArray()
                prevSeriesMap[catId] = (0 until arr.length()).mapNotNull {
                    try { jsonToSeries(arr.getJSONObject(it)) } catch (_: Exception) { null }
                }
            }

            // Featured
            val featMoviesArr = json.optJSONArray("featuredMovies") ?: JSONArray()
            val featMovies = (0 until featMoviesArr.length()).mapNotNull {
                try { jsonToMovie(featMoviesArr.getJSONObject(it)) } catch (_: Exception) { null }
            }

            val featSeriesArr = json.optJSONArray("featuredSeries") ?: JSONArray()
            val featSeries = (0 until featSeriesArr.length()).mapNotNull {
                try { jsonToSeries(featSeriesArr.getJSONObject(it)) } catch (_: Exception) { null }
            }

            // Apply directly into StateFlows
            _continueWatching.value = cwList
            _trendingMovies.value = tmList
            _trendingSeries.value = tsList
            org.getlume.android.core.sync.HomeTrendingManager.setCachedTrending(tmList, tsList)

            _recentlyWatchedMovies.value = rwMovies
            _favoriteMovies.value = favMovies
            _recentlyAddedMovies.value = recMovies
            _previewCategoryMovies.value = prevMoviesMap

            _recentlyWatchedSeries.value = rwSeries
            _favoriteSeries.value = favSeries
            _recentlyAddedSeries.value = recSeries
            _previewCategorySeries.value = prevSeriesMap

            _featuredMovies.value = featMovies
            _featuredSeries.value = featSeries

            // Interleave home recently added
            val combined = mutableListOf<Any>()
            val maxItems = maxOf(recMovies.size, recSeries.size)
            for (i in 0 until minOf(maxItems, 10)) {
                if (i < recMovies.size) combined.add(recMovies[i])
                if (i < recSeries.size) combined.add(recSeries[i])
            }
            _homeRecentlyAdded.value = combined
            _isReady.value = true

            Log.d(TAG, "Loaded BrowseCache from disk: ${cwList.size} continue watching, ${rwMovies.size} watched movies, ${recMovies.size} recent movies, ${tmList.size} trending movies.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load BrowseCache from disk: ${e.message}", e)
            false
        }
    }

    private fun saveDiskCache(context: Context, playlistId: String) {
        try {
            val file = getCacheFile(context, playlistId)
            val json = JSONObject().apply {
                put("playlistId", playlistId)
                put("timestamp", System.currentTimeMillis())

                // Continue Watching
                val cwArr = JSONArray()
                _continueWatching.value.forEach { cwArr.put(progressToJson(it)) }
                put("continueWatching", cwArr)

                // Trending
                val tmArr = JSONArray()
                _trendingMovies.value.forEach { tmArr.put(movieToJson(it)) }
                put("trendingMovies", tmArr)

                val tsArr = JSONArray()
                _trendingSeries.value.forEach { tsArr.put(seriesToJson(it)) }
                put("trendingSeries", tsArr)

                // Movies
                val rwMoviesArr = JSONArray()
                _recentlyWatchedMovies.value.forEach { (prog, movie) ->
                    rwMoviesArr.put(JSONObject().apply {
                        put("progress", progressToJson(prog))
                        put("movie", movieToJson(movie))
                    })
                }
                put("recentlyWatchedMovies", rwMoviesArr)

                val favMoviesArr = JSONArray()
                _favoriteMovies.value.forEach { favMoviesArr.put(movieToJson(it)) }
                put("favoriteMovies", favMoviesArr)

                val recMoviesArr = JSONArray()
                _recentlyAddedMovies.value.take(100).forEach { recMoviesArr.put(movieToJson(it)) }
                put("recentlyAddedMovies", recMoviesArr)

                val prevMoviesObj = JSONObject()
                _previewCategoryMovies.value.forEach { (catId, movies) ->
                    val arr = JSONArray()
                    movies.take(21).forEach { arr.put(movieToJson(it)) }
                    prevMoviesObj.put(catId, arr)
                }
                put("previewCategoryMovies", prevMoviesObj)

                // Series
                val rwSeriesArr = JSONArray()
                _recentlyWatchedSeries.value.forEach { (prog, series) ->
                    rwSeriesArr.put(JSONObject().apply {
                        put("progress", progressToJson(prog))
                        put("series", seriesToJson(series))
                    })
                }
                put("recentlyWatchedSeries", rwSeriesArr)

                val favSeriesArr = JSONArray()
                _favoriteSeries.value.forEach { favSeriesArr.put(seriesToJson(it)) }
                put("favoriteSeries", favSeriesArr)

                val recSeriesArr = JSONArray()
                _recentlyAddedSeries.value.take(100).forEach { recSeriesArr.put(seriesToJson(it)) }
                put("recentlyAddedSeries", recSeriesArr)

                val prevSeriesObj = JSONObject()
                _previewCategorySeries.value.forEach { (catId, seriesList) ->
                    val arr = JSONArray()
                    seriesList.take(21).forEach { arr.put(seriesToJson(it)) }
                    prevSeriesObj.put(catId, arr)
                }
                put("previewCategorySeries", prevSeriesObj)

                // Featured
                val featMoviesArr = JSONArray()
                _featuredMovies.value.take(12).forEach { featMoviesArr.put(movieToJson(it)) }
                put("featuredMovies", featMoviesArr)

                val featSeriesArr = JSONArray()
                _featuredSeries.value.take(12).forEach { featSeriesArr.put(seriesToJson(it)) }
                put("featuredSeries", featSeriesArr)
            }

            file.writeText(json.toString())
            Log.d(TAG, "Saved BrowseCache to disk (${file.length()} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save BrowseCache to disk: ${e.message}", e)
        }
    }

    private fun movieToJson(m: MovieEntity): JSONObject = JSONObject().apply {
        put("id", m.id)
        put("playlistId", m.playlistId)
        put("categoryId", m.categoryId)
        put("streamId", m.streamId)
        put("name", m.name)
        put("posterUrl", m.posterUrl)
        put("backdropUrl", m.backdropUrl)
        put("plot", m.plot)
        put("cast", m.cast)
        put("director", m.director)
        put("releaseDate", m.releaseDate)
        put("durationMinutes", m.durationMinutes)
        put("rating", m.rating.toDouble())
        put("streamUrl", m.streamUrl)
        put("extension", m.extension)
        put("isFavorite", m.isFavorite)
        put("genre", m.genre)
        m.tmdbId?.let { put("tmdbId", it) }
        put("logoUrl", m.logoUrl)
        m.added?.let { put("added", it) }
    }

    private fun jsonToMovie(json: JSONObject): MovieEntity = MovieEntity(
        id = json.optString("id"),
        playlistId = json.optString("playlistId"),
        categoryId = json.optString("categoryId"),
        streamId = json.optString("streamId"),
        name = json.optString("name"),
        posterUrl = json.optString("posterUrl"),
        backdropUrl = json.optString("backdropUrl"),
        plot = json.optString("plot"),
        cast = json.optString("cast"),
        director = json.optString("director"),
        releaseDate = json.optString("releaseDate"),
        durationMinutes = json.optInt("durationMinutes", 0),
        rating = json.optDouble("rating", 0.0).toFloat(),
        streamUrl = json.optString("streamUrl"),
        extension = json.optString("extension", "mp4"),
        isFavorite = json.optBoolean("isFavorite", false),
        genre = json.optString("genre"),
        tmdbId = if (json.has("tmdbId") && !json.isNull("tmdbId")) json.optInt("tmdbId") else null,
        logoUrl = json.optString("logoUrl"),
        added = if (json.has("added") && !json.isNull("added")) json.optString("added") else null
    )

    private fun seriesToJson(s: SeriesEntity): JSONObject = JSONObject().apply {
        put("id", s.id)
        put("playlistId", s.playlistId)
        put("categoryId", s.categoryId)
        put("seriesId", s.seriesId)
        put("name", s.name)
        put("posterUrl", s.posterUrl)
        put("backdropUrl", s.backdropUrl)
        put("plot", s.plot)
        put("cast", s.cast)
        put("releaseDate", s.releaseDate)
        put("rating", s.rating.toDouble())
        put("isFavorite", s.isFavorite)
        put("genre", s.genre)
        s.tmdbId?.let { put("tmdbId", it) }
        put("logoUrl", s.logoUrl)
        s.lastModified?.let { put("lastModified", it) }
    }

    private fun jsonToSeries(json: JSONObject): SeriesEntity = SeriesEntity(
        id = json.optString("id"),
        playlistId = json.optString("playlistId"),
        categoryId = json.optString("categoryId"),
        seriesId = json.optString("seriesId"),
        name = json.optString("name"),
        posterUrl = json.optString("posterUrl"),
        backdropUrl = json.optString("backdropUrl"),
        plot = json.optString("plot"),
        cast = json.optString("cast"),
        releaseDate = json.optString("releaseDate"),
        rating = json.optDouble("rating", 0.0).toFloat(),
        isFavorite = json.optBoolean("isFavorite", false),
        genre = json.optString("genre"),
        tmdbId = if (json.has("tmdbId") && !json.isNull("tmdbId")) json.optInt("tmdbId") else null,
        logoUrl = json.optString("logoUrl"),
        lastModified = if (json.has("lastModified") && !json.isNull("lastModified")) json.optString("lastModified") else null
    )

    private fun progressToJson(p: WatchProgressEntity): JSONObject = JSONObject().apply {
        put("contentId", p.contentId)
        put("profileId", p.profileId)
        put("title", p.title)
        put("posterUrl", p.posterUrl)
        put("contentType", p.contentType)
        put("positionMs", p.positionMs)
        put("durationMs", p.durationMs)
        put("lastWatchedAt", p.lastWatchedAt)
        put("isCompleted", p.isCompleted)
    }

    private fun jsonToProgress(json: JSONObject): WatchProgressEntity = WatchProgressEntity(
        contentId = json.optString("contentId"),
        profileId = json.optString("profileId"),
        title = json.optString("title"),
        posterUrl = json.optString("posterUrl"),
        contentType = json.optString("contentType"),
        positionMs = json.optLong("positionMs", 0L),
        durationMs = json.optLong("durationMs", 0L),
        lastWatchedAt = json.optLong("lastWatchedAt", System.currentTimeMillis()),
        isCompleted = json.optBoolean("isCompleted", false)
    )
}
