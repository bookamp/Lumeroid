package org.getlume.android.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.getlume.android.LumeApp
import org.getlume.android.core.data.entities.CategoryEntity
import org.getlume.android.core.data.entities.MovieEntity
import org.getlume.android.core.data.entities.SeriesEntity
import org.getlume.android.core.settings.AppSettings
import org.getlume.android.core.settings.ContentFilterCache

/**
 * 1:1 port of Apple TV GenreDerivation.swift.
 *
 * Runs off the main thread on Dispatchers.IO to derive distinct, frequency-ranked
 * genres for movies and series within the active playlist.
 *
 * Integrates deeply with ContentFilterCache and Content Language preferences.
 */
object GenreDerivation {

    private const val GENRE_SAMPLE_LIMIT = 5000

    /**
     * Derives distinct genres for movies in the active playlist.
     * Optionally takes [allowedCategoryIds] for precomputation during cache rebuild.
     */
    suspend fun movieGenres(playlistId: String, allowedCategoryIds: Set<String>? = null): List<String> = withContext(Dispatchers.IO) {
        if (playlistId.isBlank()) return@withContext emptyList()
        val db = LumeApp.instance.database
        val targetCategoryIds = allowedCategoryIds ?: if (ContentFilterCache.isFiltering.value) {
            ContentFilterCache.movieCategoryIds.value
        } else emptySet()

        val sampled = db.vodDao().getMovieGenreTokens(playlistId, limit = GENRE_SAMPLE_LIMIT)
        val rawGenres = mutableListOf<String?>()

        for (item in sampled) {
            if (targetCategoryIds.isNotEmpty() && !targetCategoryIds.contains(item.categoryId)) continue
            if (item.genre.isNotBlank()) {
                rawGenres.add(item.genre)
            }
        }

        val derived = GenreParser.distinctByFrequency(rawGenres)
        if (derived.isNotEmpty()) {
            return@withContext derived
        }

        // Fallback: extract recognized genre keywords from category names
        val allCategories = db.categoryDao().getAllCategories(playlistId).filter {
            it.type == "movie" && (targetCategoryIds.isEmpty() || targetCategoryIds.contains(it.id))
        }
        GenreParser.extractFromCategories(allCategories)
    }

    /**
     * Derives distinct genres for series in the active playlist.
     * Optionally takes [allowedCategoryIds] for precomputation during cache rebuild.
     */
    suspend fun seriesGenres(playlistId: String, allowedCategoryIds: Set<String>? = null): List<String> = withContext(Dispatchers.IO) {
        if (playlistId.isBlank()) return@withContext emptyList()
        val db = LumeApp.instance.database
        val targetCategoryIds = allowedCategoryIds ?: if (ContentFilterCache.isFiltering.value) {
            ContentFilterCache.seriesCategoryIds.value
        } else emptySet()

        val sampled = db.vodDao().getSeriesGenreTokens(playlistId, limit = GENRE_SAMPLE_LIMIT)
        val rawGenres = mutableListOf<String?>()

        for (item in sampled) {
            if (targetCategoryIds.isNotEmpty() && !targetCategoryIds.contains(item.categoryId)) continue
            if (item.genre.isNotBlank()) {
                rawGenres.add(item.genre)
            }
        }

        val derived = GenreParser.distinctByFrequency(rawGenres)
        if (derived.isNotEmpty()) {
            return@withContext derived
        }

        // Fallback: extract from series category names
        val allCategories = db.categoryDao().getAllCategories(playlistId).filter {
            it.type == "series" && (targetCategoryIds.isEmpty() || targetCategoryIds.contains(it.id))
        }
        GenreParser.extractFromCategories(allCategories)
    }
}
