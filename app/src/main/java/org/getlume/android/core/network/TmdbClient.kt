package org.getlume.android.core.network

import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.getlume.android.core.settings.AppSettings
import java.util.concurrent.ConcurrentHashMap

data class TmdbTrendingItem(
    val id: Int,
    val title: String,
    val overview: String,
    val backdropPath: String? = null,
    val posterPath: String? = null,
    val voteAverage: Float = 0f,
    val mediaType: String = "", // "movie" or "tv"
    val originalLanguage: String? = null
) {
    val fullBackdropUrl: String
        get() = if (!backdropPath.isNullOrBlank()) "https://image.tmdb.org/t/p/w1920$backdropPath" else ""
    val fullPosterUrl: String
        get() = if (!posterPath.isNullOrBlank()) "https://image.tmdb.org/t/p/w500$posterPath" else ""
}

/**
 * Authentic TMDB API client matching Lume's TMDBClient.swift:
 * Powers the Home screen's TMDB Trending rails and hero artwork enrichment.
 */
object TmdbClient {
    private const val TAG = "TmdbClient"
    private const val BASE_URL = "https://api.themoviedb.org/3"
    const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/"

    // Official Lume v4 Read Access Token from tvOS bundle
    private const val DEFAULT_TOKEN =
        "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiIyYTMxNzEwOTkwYjRlOTcxNTEwZmJlZWJkOGI4OTkyNCIsIm5iZiI6MTczNjgxMDgyOC42NjMsInN1YiI6IjY3ODVhMTRjZmUyOTRhMGI0NzRlNWJjMCIsInNjb3BlcyI6WyJhcGlfcmVhZCJdLCJ2ZXJzaW9uIjoxfQ.wSXS0pAkv2VDumCI-FeE9Tz1JDw5SBhz_iX6CrJxZvo"

    private val client = HttpClient(Android) {
        engine {
            connectTimeout = 8_000
            socketTimeout = 10_000
        }
    }
    private val json = Json { ignoreUnknownKeys = true }

    // 30-minute in-memory cache for trending feeds
    private val feedCache = ConcurrentHashMap<String, Pair<Long, List<TmdbTrendingItem>>>()
    private const val FEED_CACHE_TTL_MS = 30 * 60 * 1000L

    // In-memory logo cache
    private val logoCache = ConcurrentHashMap<String, Pair<Long, String?>>()
    private const val LOGO_CACHE_TTL_MS = 24 * 60 * 60 * 1000L

    private fun getActiveToken(): String {
        val customKey = AppSettings.get().tmdbCustomApiKey.trim()
        return if (customKey.isNotBlank()) customKey else DEFAULT_TOKEN
    }

    /**
     * Fetches trending items for movies or tv shows across multiple pages (up to 5 pages / 100 items).
     * @param mediaType "movie" or "tv"
     * @param timeWindow "day" or "week" (tvOS uses "week")
     * @param pages Number of pages (default 5, matching Lume's HomeView+Trending.swift)
     */
    suspend fun fetchTrending(
        mediaType: String,
        timeWindow: String = "week",
        pages: Int = 5
    ): List<TmdbTrendingItem> = withContext(Dispatchers.IO) {
        val cacheKey = "$mediaType-$timeWindow-$pages"
        val cached = feedCache[cacheKey]
        val now = System.currentTimeMillis()
        if (cached != null && (now - cached.first) < FEED_CACHE_TTL_MS) {
            return@withContext cached.second
        }

        try {
            val token = getActiveToken()
            val firstPageUrl = "$BASE_URL/trending/$mediaType/$timeWindow?page=1"
            val firstResponseText = client.get(firstPageUrl) {
                header("Authorization", "Bearer $token")
                header("Accept", "application/json")
            }.bodyAsText()

            val firstRoot = json.parseToJsonElement(firstResponseText).jsonObject
            val totalPages = firstRoot["total_pages"]?.jsonPrimitive?.intOrNull ?: 1
            val pageCount = minOf(pages.coerceAtLeast(1), totalPages.coerceAtLeast(1))

            val firstItems = parseTrendingResults(firstRoot, mediaType)
            val allItems = mutableListOf<TmdbTrendingItem>()
            allItems.addAll(firstItems)

            if (pageCount > 1) {
                val remainingPages = (2..pageCount).toList()
                val pageResults = coroutineScope {
                    remainingPages.map { page ->
                        async {
                            try {
                                val url = "$BASE_URL/trending/$mediaType/$timeWindow?page=$page"
                                val text = client.get(url) {
                                    header("Authorization", "Bearer $token")
                                    header("Accept", "application/json")
                                }.bodyAsText()
                                val root = json.parseToJsonElement(text).jsonObject
                                parseTrendingResults(root, mediaType)
                            } catch (e: Exception) {
                                emptyList()
                            }
                        }
                    }.awaitAll()
                }
                pageResults.forEach { allItems.addAll(it) }
            }

            feedCache[cacheKey] = Pair(now, allItems)
            allItems
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching trending for $mediaType: ${e.message}")
            cached?.second ?: emptyList()
        }
    }


    /**
     * Fetches top-rated / trending items for a specific language via TMDB Discover.
     * @param mediaType "movie" or "tv"
     * @param languageCode ISO 639-1 code (e.g. "hi", "ta", "es", "ko", "fr")
     * @param pages Number of pages to fetch (default 2)
     */
    suspend fun fetchLanguageTrending(
        mediaType: String,
        languageCode: String,
        pages: Int = 2
    ): List<TmdbTrendingItem> = withContext(Dispatchers.IO) {
        val lang = languageCode.lowercase().trim()
        val cacheKey = "$mediaType-lang-$lang-$pages"
        val cached = feedCache[cacheKey]
        val now = System.currentTimeMillis()
        if (cached != null && (now - cached.first) < FEED_CACHE_TTL_MS) {
            return@withContext cached.second
        }

        try {
            val token = getActiveToken()
            val allItems = mutableListOf<TmdbTrendingItem>()
            val pageCount = pages.coerceIn(1, 5)

            val pageResults = coroutineScope {
                (1..pageCount).map { page ->
                    async {
                        try {
                            val url = "$BASE_URL/discover/$mediaType?with_original_language=$lang&sort_by=popularity.desc&vote_count.gte=3&page=$page"
                            val text = client.get(url) {
                                header("Authorization", "Bearer $token")
                                header("Accept", "application/json")
                            }.bodyAsText()
                            val root = json.parseToJsonElement(text).jsonObject
                            parseTrendingResults(root, mediaType)
                        } catch (e: Exception) {
                            Log.w(TAG, "Error fetching page $page for $mediaType language $lang: ${e.message}")
                            emptyList()
                        }
                    }
                }.awaitAll()
            }
            pageResults.forEach { allItems.addAll(it) }

            feedCache[cacheKey] = Pair(now, allItems)
            allItems
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching language trending for $mediaType / $lang: ${e.message}")
            cached?.second ?: emptyList()
        }
    }

    /**
     * Dynamically fetches trending titles for ANY combination of active content languages.
     * - If activeLanguages is empty ("All Languages" / no filter): defaults to Global/English weekly trending.
     * - If specific languages are selected:
     *   - Fetches global/English trending if "en" is selected.
     *   - Concurrently queries TMDB language discovery for all non-English selected languages.
     *   - Interleaves the results fairly so all active languages are represented.
     */
    suspend fun fetchTrendingForLanguages(
        mediaType: String,
        activeLanguages: Set<String>
    ): List<TmdbTrendingItem> = withContext(Dispatchers.IO) {
        val cleanLangs = activeLanguages.map { it.lowercase().trim() }.filter { it.isNotBlank() }.toSet()
        if (cleanLangs.isEmpty()) {
            // Default to Global weekly trending (5 pages = 100 items)
            return@withContext fetchTrending(mediaType, timeWindow = "week", pages = 5)
        }

        coroutineScope {
            val jobs = mutableListOf<kotlinx.coroutines.Deferred<List<TmdbTrendingItem>>>()

            if (cleanLangs.contains("en")) {
                jobs.add(async { fetchTrending(mediaType, timeWindow = "week", pages = 3) })
            }

            for (lang in cleanLangs) {
                if (lang != "en") {
                    jobs.add(async { fetchLanguageTrending(mediaType, lang, pages = 2) })
                }
            }

            val langResults = jobs.awaitAll()
            // Interleave items evenly across all language streams
            val combined = mutableListOf<TmdbTrendingItem>()
            val seenIds = mutableSetOf<Int>()
            val maxListSize = langResults.maxOfOrNull { it.size } ?: 0

            for (i in 0 until maxListSize) {
                for (list in langResults) {
                    if (i < list.size) {
                        val item = list[i]
                        if (seenIds.add(item.id)) {
                            combined.add(item)
                        }
                    }
                }
            }
            combined
        }
    }

    private fun parseTrendingResults(root: JsonObject, defaultMediaType: String): List<TmdbTrendingItem> {
        val results = root["results"]?.jsonArray ?: return emptyList()
        val items = mutableListOf<TmdbTrendingItem>()
        for (elem in results) {
            val obj = elem.jsonObject
            val id = obj["id"]?.jsonPrimitive?.intOrNull ?: continue
            val title = obj["title"]?.jsonPrimitive?.contentOrNull
                ?: obj["name"]?.jsonPrimitive?.contentOrNull
                ?: continue
            val overview = obj["overview"]?.jsonPrimitive?.contentOrNull ?: ""
            val backdropPath = obj["backdrop_path"]?.jsonPrimitive?.contentOrNull
            val posterPath = obj["poster_path"]?.jsonPrimitive?.contentOrNull
            val voteAverage = obj["vote_average"]?.jsonPrimitive?.floatOrNull ?: 0f
            val mediaType = obj["media_type"]?.jsonPrimitive?.contentOrNull ?: defaultMediaType
            val originalLanguage = obj["original_language"]?.jsonPrimitive?.contentOrNull

            items.add(
                TmdbTrendingItem(
                    id = id,
                    title = title,
                    overview = overview,
                    backdropPath = backdropPath,
                    posterPath = posterPath,
                    voteAverage = voteAverage,
                    mediaType = mediaType,
                    originalLanguage = originalLanguage
                )
            )
        }
        return items
    }

    /**
     * Enriches a title by fetching its transparent PNG logo artwork.
     * Matches Lume's enrichHeroLogos() and TMDBClient.swift logo ranking.
     */
    suspend fun fetchTitleLogo(mediaType: String, tmdbId: Int): String? = withContext(Dispatchers.IO) {
        val cacheKey = "$mediaType:$tmdbId"
        val cached = logoCache[cacheKey]
        val now = System.currentTimeMillis()
        if (cached != null && (now - cached.first) < LOGO_CACHE_TTL_MS) {
            return@withContext cached.second
        }

        try {
            val token = getActiveToken()
            val url = "$BASE_URL/$mediaType/$tmdbId/images?include_image_language=en,null"
            val responseText = client.get(url) {
                header("Authorization", "Bearer $token")
                header("Accept", "application/json")
            }.bodyAsText()

            val root = json.parseToJsonElement(responseText).jsonObject
            val logos = root["logos"]?.jsonArray
            var bestLogoPath: String? = null
            if (logos != null && logos.isNotEmpty()) {
                // Pick highest voted English or neutral logo
                for (logoElem in logos) {
                    val logoObj = logoElem.jsonObject
                    val filePath = logoObj["file_path"]?.jsonPrimitive?.contentOrNull
                    if (!filePath.isNullOrBlank()) {
                        bestLogoPath = "https://image.tmdb.org/t/p/w500$filePath"
                        break
                    }
                }
            }

            logoCache[cacheKey] = Pair(now, bestLogoPath)
            bestLogoPath
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching logo for $mediaType/$tmdbId: ${e.message}")
            null
        }
    }
}
