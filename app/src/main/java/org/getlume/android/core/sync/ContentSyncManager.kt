package org.getlume.android.core.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.getlume.android.LumeApp
import org.getlume.android.core.data.entities.CategoryEntity
import org.getlume.android.core.data.entities.MovieEntity
import org.getlume.android.core.data.entities.PlaylistEntity
import org.getlume.android.core.data.entities.SeriesEntity
import org.getlume.android.core.network.XtreamCodesClient

object ContentSyncManager {

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncProgress = MutableStateFlow("Idle")
    val syncProgress: StateFlow<String> = _syncProgress.asStateFlow()

    suspend fun syncAllCategories(playlist: PlaylistEntity) = withContext(Dispatchers.IO) {
        if (playlist.type != "xtream") return@withContext
        val db = LumeApp.instance.database
        val client = XtreamCodesClient(playlist.serverUrl, playlist.username, playlist.password)

        _isSyncing.value = true
        _syncProgress.value = "Syncing categories..."

        // 1. Live categories
        val liveCats = client.fetchLiveCategories(playlist.id)
        if (liveCats.isNotEmpty()) {
            db.categoryDao().insertCategories(liveCats)
        }

        // 2. VOD categories
        val vodCats = client.fetchVodCategories(playlist.id)
        if (vodCats.isNotEmpty()) {
            db.categoryDao().insertCategories(vodCats)
        }

        // 3. Series categories
        val seriesCats = client.fetchSeriesCategories(playlist.id)
        if (seriesCats.isNotEmpty()) {
            db.categoryDao().insertCategories(seriesCats)
        }

        _syncProgress.value = "Categories up to date"
        _isSyncing.value = false
        org.getlume.android.core.settings.ContentFilterCache.rebuildAsync(playlist.id)
    }

    suspend fun syncInitialCatalog(playlist: PlaylistEntity) = withContext(Dispatchers.IO) {
        if (playlist.type != "xtream") return@withContext
        val db = LumeApp.instance.database
        val client = XtreamCodesClient(playlist.serverUrl, playlist.username, playlist.password)

        _isSyncing.value = true
        try {
            android.util.Log.d("ContentSyncManager", "Starting initial sync for ${playlist.name} (${playlist.id})...")
            // First ensure categories exist
            syncAllCategories(playlist)

            // Purge orphaned items and previous catalog for this playlist
            db.channelDao().deleteOrphanedChannels()
            db.categoryDao().deleteOrphanedCategories()
            db.vodDao().deleteOrphanedMovies()
            db.vodDao().deleteOrphanedSeries()

            // 1. Bulk sync all movies across entire playlist in batches
            _syncProgress.value = "Syncing full movies catalog..."
            val allMovies = client.fetchVodStreams(playlist.id)
            android.util.Log.d("ContentSyncManager", "Fetched ${allMovies.size} movies. Starting batch insertion...")
            if (allMovies.isNotEmpty()) {
                db.vodDao().deleteMoviesForPlaylist(playlist.id)
                allMovies.chunked(500).forEach { batch ->
                    db.vodDao().insertMovies(batch)
                }
            }
            android.util.Log.d("ContentSyncManager", "Finished inserting movies. Fetching series...")

            // 2. Bulk sync all series across entire playlist in batches
            _syncProgress.value = "Syncing full series catalog..."
            val allSeries = client.fetchSeries(playlist.id)
            android.util.Log.d("ContentSyncManager", "Fetched ${allSeries.size} series. Starting batch insertion...")
            if (allSeries.isNotEmpty()) {
                db.vodDao().deleteSeriesForPlaylist(playlist.id)
                allSeries.chunked(500).forEach { batch ->
                    db.vodDao().insertSeries(batch)
                }
            }
            android.util.Log.d("ContentSyncManager", "Finished inserting series. Enriching featured media...")

            enrichFeaturedMedia(playlist)
            _syncProgress.value = "Catalog ready"
            org.getlume.android.core.settings.ContentFilterCache.rebuildAsync(playlist.id)
            android.util.Log.d("ContentSyncManager", "Sync complete. Triggering HomeTrendingManager.loadTrending...")
            HomeTrendingManager.clearCache()
            HomeTrendingManager.loadTrending(playlist.id)
        } catch (e: Exception) {
            android.util.Log.e("ContentSyncManager", "Sync failed: ${e.message}", e)
            _syncProgress.value = "Sync error: ${e.message}"
        } finally {
            _isSyncing.value = false
        }
    }

    suspend fun enrichFeaturedMedia(playlist: PlaylistEntity) = withContext(Dispatchers.IO) {
        if (playlist.type != "xtream") return@withContext
        val db = LumeApp.instance.database
        val client = XtreamCodesClient(playlist.serverUrl, playlist.username, playlist.password)

        try {
            val topMovies = db.vodDao().searchMovies(playlist.id, "").filter { it.posterUrl.isNotBlank() }
                .sortedByDescending { it.rating }
                .take(6)
            for (movie in topMovies) {
                if (movie.backdropUrl.isBlank() || movie.plot.isBlank() || movie.genre.isBlank()) {
                    val details = client.fetchVodDetails(movie.streamId)
                    if (details.backdropUrl.isNotBlank() || details.plot.isNotBlank() || details.genre.isNotBlank()) {
                        db.vodDao().updateMovieEnrichment(
                            id = movie.id,
                            backdropUrl = details.backdropUrl,
                            plot = details.plot,
                            rating = if (details.rating > 0f) details.rating else movie.rating,
                            genre = details.genre
                        )
                    }
                }
            }

            val topSeries = db.vodDao().searchSeries(playlist.id, "").filter { it.posterUrl.isNotBlank() }
                .sortedByDescending { it.rating }
                .take(6)
            for (series in topSeries) {
                if (series.backdropUrl.isBlank() || series.plot.isBlank() || series.genre.isBlank()) {
                    val details = client.fetchSeriesDetails(series.seriesId)
                    if (details.backdropUrl.isNotBlank() || details.plot.isNotBlank() || details.genre.isNotBlank()) {
                        db.vodDao().updateSeriesEnrichment(
                            id = series.id,
                            backdropUrl = details.backdropUrl,
                            plot = details.plot,
                            rating = if (details.rating > 0f) details.rating else series.rating,
                            genre = details.genre
                        )
                    }
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun loadCategoryMovies(playlist: PlaylistEntity, category: CategoryEntity): List<MovieEntity> = withContext(Dispatchers.IO) {
        if (playlist.type != "xtream") return@withContext emptyList()
        val db = LumeApp.instance.database
        val client = XtreamCodesClient(playlist.serverUrl, playlist.username, playlist.password)

        val catRawId = category.id.substringAfterLast("_")
        val movies = client.fetchVodStreams(playlist.id, catRawId)
        if (movies.isNotEmpty()) {
            db.vodDao().insertMovies(movies)
        }
        movies
    }

    suspend fun loadCategorySeries(playlist: PlaylistEntity, category: CategoryEntity): List<SeriesEntity> = withContext(Dispatchers.IO) {
        if (playlist.type != "xtream") return@withContext emptyList()
        val db = LumeApp.instance.database
        val client = XtreamCodesClient(playlist.serverUrl, playlist.username, playlist.password)

        val catRawId = category.id.substringAfterLast("_")
        val seriesList = client.fetchSeries(playlist.id, catRawId)
        if (seriesList.isNotEmpty()) {
            db.vodDao().insertSeries(seriesList)
        }
        seriesList
    }
}
