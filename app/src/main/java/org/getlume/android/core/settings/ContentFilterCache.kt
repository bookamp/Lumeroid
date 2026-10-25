package org.getlume.android.core.settings

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.getlume.android.LumeApp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * High-performance, locally-persisted cache for Content Language filtering.
 *
 * Precomputes and caches matching category IDs and channel IDs:
 * - When Content Languages selection changes in Settings
 * - When a Playlist is refreshed / synchronized
 * - When EPG data is updated
 * - On app startup (restored instantly from disk JSON cache)
 *
 * Guarantees 0ms main-thread compute, 0 allocations during tab navigation,
 * and eliminates dynamic category layout jumping.
 */
object ContentFilterCache {
    private const val TAG = "ContentFilterCache"
    private const val CACHE_FILE_PREFIX = "content_filter_cache_"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val rebuildMutex = Mutex()

    private val _isFiltering = MutableStateFlow(false)
    val isFiltering: StateFlow<Boolean> = _isFiltering.asStateFlow()

    private val _liveCategoryIds = MutableStateFlow<Set<String>>(emptySet())
    val liveCategoryIds: StateFlow<Set<String>> = _liveCategoryIds.asStateFlow()

    private val _movieCategoryIds = MutableStateFlow<Set<String>>(emptySet())
    val movieCategoryIds: StateFlow<Set<String>> = _movieCategoryIds.asStateFlow()

    private val _seriesCategoryIds = MutableStateFlow<Set<String>>(emptySet())
    val seriesCategoryIds: StateFlow<Set<String>> = _seriesCategoryIds.asStateFlow()

    private val _matchingChannelIds = MutableStateFlow<Set<String>>(emptySet())
    val matchingChannelIds: StateFlow<Set<String>> = _matchingChannelIds.asStateFlow()

    private val _movieGenres = MutableStateFlow<List<String>>(emptyList())
    val movieGenres: StateFlow<List<String>> = _movieGenres.asStateFlow()

    private val _seriesGenres = MutableStateFlow<List<String>>(emptyList())
    val seriesGenres: StateFlow<List<String>> = _seriesGenres.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    @Volatile
    private var activePlaylistId: String = ""

    /**
     * Initializes the cache from local disk storage on app startup.
     * Restores filtered IDs synchronously if matching active languages,
     * or triggers a background rebuild if missing/stale.
     */
    fun init(context: Context) {
        // Synchronously load disk cache BEFORE first compose frame
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

        scope.launch {
            try {
                val db = LumeApp.instance.database
                val activePl = db.playlistDao().getActivePlaylist() ?: return@launch
                activePlaylistId = activePl.id
                AppSettings.get().lastActivePlaylistId = activePl.id

                val loaded = if (!_isFiltering.value && _liveCategoryIds.value.isEmpty()) loadDiskCache(context, activePl.id) else true
                val settings = AppSettings.get()
                val currentLangs = settings.homeContentLanguages

                if (!loaded || _movieGenres.value.isEmpty() || _seriesGenres.value.isEmpty()) {
                    rebuild(activePl.id)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing ContentFilterCache: ${e.message}", e)
            }
        }
    }

    /**
     * Triggers a non-blocking background rebuild of the content filter cache.
     */
    fun rebuildAsync(playlistId: String? = null) {
        scope.launch {
            try {
                rebuild(playlistId)
            } catch (e: Exception) {
                Log.e(TAG, "Error in rebuildAsync: ${e.message}", e)
            }
        }
    }

    /**
     * Synchronously computes and saves the filtered categories, channels, and genres.
     * Protected by Mutex to prevent duplicate parallel runs.
     */
    suspend fun rebuild(playlistId: String? = null) = withContext(Dispatchers.Default) {
        rebuildMutex.withLock {
            val db = LumeApp.instance.database
            val plId = playlistId ?: activePlaylistId.ifEmpty {
                db.playlistDao().getActivePlaylist()?.id ?: return@withLock
            }
            activePlaylistId = plId
            val settings = AppSettings.get()
            val languages = settings.homeContentLanguages

            if (languages.isEmpty()) {
                _isFiltering.value = false
                _liveCategoryIds.value = emptySet()
                _movieCategoryIds.value = emptySet()
                _seriesCategoryIds.value = emptySet()
                _matchingChannelIds.value = emptySet()

                // Precompute full playlist genres (unfiltered)
                val computedMovieGenres = org.getlume.android.core.data.GenreDerivation.movieGenres(plId, emptySet())
                val computedSeriesGenres = org.getlume.android.core.data.GenreDerivation.seriesGenres(plId, emptySet())
                _movieGenres.value = computedMovieGenres
                _seriesGenres.value = computedSeriesGenres

                _isReady.value = true
                saveDiskCache(
                    context = LumeApp.instance,
                    playlistId = plId,
                    languages = languages,
                    liveCatIds = emptySet(),
                    movieCatIds = emptySet(),
                    seriesCatIds = emptySet(),
                    channelIds = emptySet(),
                    movieGenres = computedMovieGenres,
                    seriesGenres = computedSeriesGenres
                )
                return@withLock
            }

            _isFiltering.value = true
            Log.d(TAG, "Rebuilding ContentFilterCache for playlist=$plId, langs=$languages...")

            // 1. Fetch categories from Room
            val allCategories = db.categoryDao().getAllCategories(plId)
            val catMap = allCategories.associate { it.id to it.name }

            // 2. Fetch all live channels and pre-filter
            val allLiveChannels = db.channelDao().getAllChannels(plId)
            val matchingChannels = ArrayList<String>(allLiveChannels.size)
            val matchingLiveCats = HashSet<String>()

            for (i in allLiveChannels.indices) {
                val chan = allLiveChannels[i]
                val catName = catMap[chan.categoryId] ?: ""
                if (settings.matchesContentLanguage(chan.name, catName)) {
                    matchingChannels.add(chan.id)
                    if (chan.categoryId.isNotEmpty()) {
                        matchingLiveCats.add(chan.categoryId)
                    }
                }
            }

            // Fallback: If channels haven't synced yet, match live categories directly
            if (matchingLiveCats.isEmpty()) {
                for (i in allCategories.indices) {
                    val cat = allCategories[i]
                    if (cat.type == "live" && settings.matchesContentLanguage("", cat.name)) {
                        matchingLiveCats.add(cat.id)
                    }
                }
            }

            // 3. Movie Categories
            val matchingMovieCats = HashSet<String>()
            for (i in allCategories.indices) {
                val cat = allCategories[i]
                if (cat.type == "movie" && settings.matchesContentLanguage("", cat.name)) {
                    matchingMovieCats.add(cat.id)
                }
            }

            // 4. Series Categories
            val matchingSeriesCats = HashSet<String>()
            for (i in allCategories.indices) {
                val cat = allCategories[i]
                if (cat.type == "series" && settings.matchesContentLanguage("", cat.name)) {
                    matchingSeriesCats.add(cat.id)
                }
            }

            val matchingChannelSet = matchingChannels.toSet()

            // 5. Precompute Derived Genres scoped to allowed categories (0ms on UI navigation)
            val computedMovieGenres = org.getlume.android.core.data.GenreDerivation.movieGenres(plId, matchingMovieCats)
            val computedSeriesGenres = org.getlume.android.core.data.GenreDerivation.seriesGenres(plId, matchingSeriesCats)

            // Update in-memory state
            _liveCategoryIds.value = matchingLiveCats
            _movieCategoryIds.value = matchingMovieCats
            _seriesCategoryIds.value = matchingSeriesCats
            _matchingChannelIds.value = matchingChannelSet
            _movieGenres.value = computedMovieGenres
            _seriesGenres.value = computedSeriesGenres
            _isReady.value = true
            org.getlume.android.core.data.BrowseCache.rebuildAsync(plId)

            // Persist to local disk
            saveDiskCache(
                context = LumeApp.instance,
                playlistId = plId,
                languages = languages,
                liveCatIds = matchingLiveCats,
                movieCatIds = matchingMovieCats,
                seriesCatIds = matchingSeriesCats,
                channelIds = matchingChannelSet,
                movieGenres = computedMovieGenres,
                seriesGenres = computedSeriesGenres
            )

            Log.d(TAG, "ContentFilterCache rebuild complete: ${matchingLiveCats.size} live cats, ${matchingMovieCats.size} movie cats, ${matchingSeriesCats.size} series cats, ${matchingChannelSet.size} channels, ${computedMovieGenres.size} movie genres, ${computedSeriesGenres.size} series genres.")
        }
    }

    private fun getCacheFile(context: Context, playlistId: String): File {
        return File(context.filesDir, "$CACHE_FILE_PREFIX${playlistId}.json")
    }

    private fun loadDiskCache(context: Context, playlistId: String): Boolean {
        return try {
            val file = getCacheFile(context, playlistId)
            if (!file.exists()) return false

            val json = JSONObject(file.readText())
            val cachedLangs = json.optJSONArray("languages")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }.toSet()
            } ?: emptySet()

            val currentLangs = AppSettings.get().homeContentLanguages
            if (cachedLangs != currentLangs) {
                Log.d(TAG, "Disk cache languages mismatch ($cachedLangs vs $currentLangs). Needs rebuild.")
                return false
            }

            val liveCats = json.optJSONArray("liveCategoryIds")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }.toSet()
            } ?: emptySet()

            val movieCats = json.optJSONArray("movieCategoryIds")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }.toSet()
            } ?: emptySet()

            val seriesCats = json.optJSONArray("seriesCategoryIds")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }.toSet()
            } ?: emptySet()

            val channels = json.optJSONArray("matchingChannelIds")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }.toSet()
            } ?: emptySet()

            val movieGenresList = json.optJSONArray("movieGenres")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList()

            val seriesGenresList = json.optJSONArray("seriesGenres")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList()

            _liveCategoryIds.value = liveCats
            _movieCategoryIds.value = movieCats
            _seriesCategoryIds.value = seriesCats
            _matchingChannelIds.value = channels
            _movieGenres.value = movieGenresList
            _seriesGenres.value = seriesGenresList
            _isFiltering.value = currentLangs.isNotEmpty()
            _isReady.value = true
            org.getlume.android.core.data.BrowseCache.rebuildAsync(playlistId)

            Log.d(TAG, "Loaded ContentFilterCache from disk: ${liveCats.size} live cats, ${movieCats.size} movie cats, ${seriesCats.size} series cats, ${channels.size} channels, ${movieGenresList.size} movie genres, ${seriesGenresList.size} series genres.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read disk filter cache: ${e.message}")
            false
        }
    }

    private fun saveDiskCache(
        context: Context,
        playlistId: String,
        languages: Set<String>,
        liveCatIds: Set<String>,
        movieCatIds: Set<String>,
        seriesCatIds: Set<String>,
        channelIds: Set<String>,
        movieGenres: List<String> = emptyList(),
        seriesGenres: List<String> = emptyList()
    ) {
        try {
            val file = getCacheFile(context, playlistId)
            val json = JSONObject().apply {
                put("playlistId", playlistId)
                put("languages", JSONArray(languages.toList()))
                put("liveCategoryIds", JSONArray(liveCatIds.toList()))
                put("movieCategoryIds", JSONArray(movieCatIds.toList()))
                put("seriesCategoryIds", JSONArray(seriesCatIds.toList()))
                put("matchingChannelIds", JSONArray(channelIds.toList()))
                put("movieGenres", JSONArray(movieGenres))
                put("seriesGenres", JSONArray(seriesGenres))
                put("timestamp", System.currentTimeMillis())
            }
            file.writeText(json.toString())
            Log.d(TAG, "Saved ContentFilterCache to disk (${file.length()} bytes, ${movieGenres.size} movie genres, ${seriesGenres.size} series genres).")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save disk filter cache: ${e.message}")
        }
    }
}
