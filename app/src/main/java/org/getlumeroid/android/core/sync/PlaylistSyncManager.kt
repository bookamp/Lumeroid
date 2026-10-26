package org.getlumeroid.android.core.sync

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.getlumeroid.android.LumeroidApp
import org.getlumeroid.android.core.data.entities.PlaylistEntity
import org.getlumeroid.android.core.network.M3UParser
import org.getlumeroid.android.core.network.XtreamCodesClient
import org.getlumeroid.android.core.settings.AppSettings
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Authentic Apple TV Playlist & Content Sync Manager.
 * Replicates Lume's ContentSyncManager and AutoSync from tvOS:
 * - Live TV, VOD Movies, and Series catalog synchronization
 * - Respects per-playlist `syncEnabled` opt-in
 * - Respects global `playlistSyncFrequency` (Every 6h, 1d, 3d, 1w)
 * - Automatic background checks on app launch & active playlist switch
 * - Live progress observables for UI indicators
 */
object PlaylistSyncManager {
    private const val TAG = "PlaylistSyncManager"

    private val syncMutex = Mutex()
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncingPlaylistId = MutableStateFlow<String?>(null)
    val syncingPlaylistId: StateFlow<String?> = _syncingPlaylistId.asStateFlow()

    private val _syncProgressMessage = MutableStateFlow<String?>(null)
    val syncProgressMessage: StateFlow<String?> = _syncProgressMessage.asStateFlow()

    /**
     * Non-cancellable asynchronous trigger for UI components.
     * Prevents sync from being aborted if the calling Composable unmounts.
     */
    fun syncPlaylistAsync(
        playlist: PlaylistEntity,
        onProgress: ((String) -> Unit)? = null
    ): Job = syncScope.launch {
        syncPlaylist(playlist, onProgress)
    }

    /**
     * Synchronizes a single playlist (Live Channels, Categories, and VOD if Xtream).
     */
    suspend fun syncPlaylist(
        playlist: PlaylistEntity,
        onProgress: ((String) -> Unit)? = null
    ): Result<Int> = withContext(NonCancellable) {
        if (!syncMutex.tryLock()) {
            return@withContext Result.failure(IllegalStateException("Sync already in progress"))
        }

        val db = LumeroidApp.instance.database
        _isSyncing.value = true
        _syncingPlaylistId.value = playlist.id

        fun updateProgress(msg: String) {
            _syncProgressMessage.value = msg
            onProgress?.invoke(msg)
            Log.d(TAG, "[${playlist.name}] $msg")
        }

        try {
            var totalItems = 0
            // Purge orphaned items from deleted playlists
            db.channelDao().deleteOrphanedChannels()
            db.categoryDao().deleteOrphanedCategories()
            db.vodDao().deleteOrphanedMovies()
            db.vodDao().deleteOrphanedSeries()

            if (playlist.type.equals("xtream", ignoreCase = true)) {
                val client = XtreamCodesClient(playlist.serverUrl, playlist.username, playlist.password)

                // 1. Live TV Channels & Categories
                updateProgress("Syncing live TV channels…")
                val liveCats = client.fetchLiveCategories(playlist.id)
                val liveStreams = client.fetchLiveStreams(playlist.id)
                if (liveCats.isNotEmpty()) db.categoryDao().insertCategories(liveCats)
                if (liveStreams.isNotEmpty()) db.channelDao().insertChannels(liveStreams)
                totalItems += liveStreams.size

                // 2. VOD Movies & Categories
                updateProgress("Syncing movies…")
                val vodCats = client.fetchVodCategories(playlist.id)
                val vodStreams = client.fetchVodStreams(playlist.id)
                if (vodCats.isNotEmpty() || vodStreams.isNotEmpty()) {
                    db.categoryDao().deleteCategories(playlist.id, "vod")
                    if (vodCats.isNotEmpty()) db.categoryDao().insertCategories(vodCats)
                    db.vodDao().deleteMoviesForPlaylist(playlist.id)
                    if (vodStreams.isNotEmpty()) {
                        vodStreams.chunked(1000).forEach { batch ->
                            db.vodDao().insertMovies(batch)
                        }
                    }
                }
                totalItems += vodStreams.size

                // 3. TV Series & Categories
                updateProgress("Syncing series…")
                val seriesCats = client.fetchSeriesCategories(playlist.id)
                val seriesList = client.fetchSeries(playlist.id)
                if (seriesCats.isNotEmpty() || seriesList.isNotEmpty()) {
                    db.categoryDao().deleteCategories(playlist.id, "series")
                    if (seriesCats.isNotEmpty()) db.categoryDao().insertCategories(seriesCats)
                    db.vodDao().deleteSeriesForPlaylist(playlist.id)
                    if (seriesList.isNotEmpty()) {
                        seriesList.chunked(1000).forEach { batch ->
                            db.vodDao().insertSeries(batch)
                        }
                    }
                }
                totalItems += seriesList.size

            } else {
                // M3U Playlist Sync
                updateProgress("Downloading M3U playlist…")
                val stream: InputStream = if (playlist.serverUrl.startsWith("http://") || playlist.serverUrl.startsWith("https://")) {
                    val conn = (URL(playlist.serverUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 30000
                        setRequestProperty("User-Agent", "Lume/1.0 (AppleTV; tvOS 18.0)")
                    }
                    conn.inputStream
                } else {
                    java.io.File(playlist.serverUrl).inputStream()
                }

                updateProgress("Parsing channels…")
                val parseResult = stream.use { M3UParser.parse(it, playlist.id) }
                if (parseResult.categories.isNotEmpty()) {
                    db.categoryDao().insertCategories(parseResult.categories)
                }
                if (parseResult.channels.isNotEmpty()) {
                    db.channelDao().insertChannels(parseResult.channels)
                }
                totalItems += parseResult.channels.size
            }

            // Update timestamp
            val now = System.currentTimeMillis()
            db.playlistDao().updateLastSyncDate(playlist.id, now)
            org.getlumeroid.android.core.settings.ContentFilterCache.rebuildAsync(playlist.id)
            HomeTrendingManager.clearCache()
            HomeTrendingManager.loadTrending(playlist.id)
            updateProgress("Sync completed ($totalItems items)")

            Result.success(totalItems)
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed for playlist ${playlist.name}: ${e.message}", e)
            Result.failure(e)
        } finally {
            _isSyncing.value = false
            _syncingPlaylistId.value = null
            _syncProgressMessage.value = null
            syncMutex.unlock()
        }
    }

    /**
     * Synchronizes all playlists that have `syncEnabled == true`.
     */
    suspend fun syncAllPlaylists(
        onProgress: ((String) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        val db = LumeroidApp.instance.database
        val playlists = db.playlistDao().getAllPlaylists().first().filter { it.syncEnabled }
        if (playlists.isEmpty()) return@withContext Result.success(0)

        var totalItems = 0
        for ((index, playlist) in playlists.withIndex()) {
            onProgress?.invoke("Syncing ${playlist.name} (${index + 1}/${playlists.size})…")
            val res = syncPlaylist(playlist, onProgress)
            res.onSuccess { totalItems += it }
        }
        Result.success(totalItems)
    }

    /**
     * Automatic startup / playlist-switch gate (mirrors AutoSync.shouldSync in tvOS).
     * If the active playlist is enabled for sync and is past its interval, refresh it.
     */
    suspend fun checkAndAutoSync() = withContext(Dispatchers.IO) {
        val db = LumeroidApp.instance.database
        val settings = AppSettings.get()
        val activePlaylist = db.playlistDao().getActivePlaylist() ?: return@withContext

        if (!activePlaylist.syncEnabled) {
            Log.d(TAG, "AutoSync skipped: syncEnabled is false for ${activePlaylist.name}")
            return@withContext
        }

        val frequency = settings.playlistSyncFrequency
        val isDue = frequency.isDue(activePlaylist.lastSyncDate)

        val movieCount = db.vodDao().getMovieCount(activePlaylist.id)
        val tmdbMovieCount = db.vodDao().getTmdbMappedCount(activePlaylist.id)
        val seriesCount = db.vodDao().getSeriesCount(activePlaylist.id)
        val tmdbSeriesCount = db.vodDao().getSeriesTmdbMappedCount(activePlaylist.id)
        val needsTmdbBackfill = (movieCount > 0 && tmdbMovieCount < (movieCount / 2)) ||
                                (seriesCount > 0 && tmdbSeriesCount < (seriesCount / 2))

        if (isDue || needsTmdbBackfill) {
            Log.i(TAG, "AutoSync triggered for ${activePlaylist.name} (interval: ${frequency.title}, isDue=$isDue, needsTmdbBackfill=$needsTmdbBackfill, movies=$tmdbMovieCount/$movieCount, series=$tmdbSeriesCount/$seriesCount)")
            syncPlaylist(activePlaylist)
        } else {
            Log.d(TAG, "AutoSync not due for ${activePlaylist.name} (last: ${formatLastSync(activePlaylist.lastSyncDate)})")
        }
    }

    /**
     * Relative human-readable format for "Last Synced" (matching Apple TV display).
     */
    fun formatLastSync(lastSyncDate: Long, now: Long = System.currentTimeMillis()): String {
        if (lastSyncDate <= 0L) return "Never"
        val diff = (now - lastSyncDate).coerceAtLeast(0L)
        return when {
            diff < 60_000L -> "Just now"
            diff < 3_600_000L -> "${diff / 60_000L} minutes ago"
            diff < 7_200_000L -> "1 hour ago"
            diff < 86_400_000L -> "${diff / 3_600_000L} hours ago"
            diff < 172_800_000L -> "Yesterday"
            else -> "${diff / 86_400_000L} days ago"
        }
    }
}
