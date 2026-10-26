package org.getlumeroid.android.core.sync

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.getlumeroid.android.LumeroidApp
import org.getlumeroid.android.core.data.entities.PlaylistEntity
import org.getlumeroid.android.core.network.XMLTVParser
import org.getlumeroid.android.core.settings.AppSettings
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Authentic Apple TV EPG Guide Sync Manager.
 * Handles downloading XMLTV guides (gzip and raw XML), parsing programmes into Room DB,
 * and updating sync timestamps.
 */
object EpgSyncManager {
    private const val TAG = "EpgSyncManager"
    private val syncMutex = Mutex()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    suspend fun syncNow(targetUrl: String? = null, playlist: PlaylistEntity? = null): Result<Int> = withContext(Dispatchers.IO) {
        if (!syncMutex.tryLock()) {
            return@withContext Result.failure(IllegalStateException("EPG sync already in progress"))
        }

        val settings = AppSettings.get()
        val urlString = targetUrl
            ?: playlist?.epgUrl?.takeIf { it.isNotBlank() }
            ?: run {
                if (playlist != null && playlist.serverUrl.isNotBlank() && playlist.username.isNotBlank()) {
                    val base = playlist.serverUrl.trimEnd('/')
                    "$base/xmltv.php?username=${playlist.username}&password=${playlist.password}"
                } else null
            }
            ?: settings.customEpgUrl.takeIf { it.isNotBlank() }

        if (urlString.isNullOrBlank()) {
            syncMutex.unlock()
            return@withContext Result.failure(IllegalArgumentException("No EPG URL configured"))
        }

        _isSyncing.value = true
        _syncMessage.value = "Connecting to TV Guide feed…"

        try {
            Log.d(TAG, "Starting EPG sync from: $urlString")
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.setRequestProperty("User-Agent", "Lume-Android/1.0 (AppleTV-Like)")
            connection.setRequestProperty("Accept-Encoding", "gzip")

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IllegalStateException("HTTP error $responseCode from EPG server")
            }

            _syncMessage.value = "Downloading TV guide data…"
            val contentEncoding = connection.getHeaderField("Content-Encoding")
            val isGzip = "gzip".equals(contentEncoding, ignoreCase = true) || urlString.endsWith(".gz", ignoreCase = true)

            var rawStream: InputStream = connection.inputStream
            if (isGzip) {
                rawStream = GZIPInputStream(rawStream)
            }

            _syncMessage.value = "Parsing programme listings…"
            val listings = XMLTVParser.parse(rawStream)
            rawStream.close()
            connection.disconnect()

            _syncMessage.value = "Saving ${listings.size} listings to database…"
            val db = LumeroidApp.instance.database
            // Purge listings older than 24 hours
            val cutoff = System.currentTimeMillis() - 86400000L
            db.epgDao().purgeOldListings(cutoff)

            // Insert in batches of 500 to keep memory low
            listings.chunked(500).forEach { chunk ->
                db.epgDao().insertEpgListings(chunk)
            }

            val now = System.currentTimeMillis()
            settings.epgLastSyncDate = now
            _syncMessage.value = "Synced ${listings.size} guide listings"
            Log.d(TAG, "Successfully synced ${listings.size} listings")
            org.getlumeroid.android.core.settings.ContentFilterCache.rebuildAsync()
            Result.success(listings.size)
        } catch (e: Exception) {
            Log.e(TAG, "EPG sync failed", e)
            _syncMessage.value = "Failed: ${e.message ?: "Unknown error"}"
            Result.failure(e)
        } finally {
            _isSyncing.value = false
            syncMutex.unlock()
        }
    }
}
