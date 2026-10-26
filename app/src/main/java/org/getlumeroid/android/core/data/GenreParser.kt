package org.getlumeroid.android.core.data

import org.getlumeroid.android.core.data.entities.CategoryEntity
import org.getlumeroid.android.core.data.entities.MovieEntity
import org.getlumeroid.android.core.data.entities.SeriesEntity
import org.getlumeroid.android.core.settings.AppSettings
import org.getlumeroid.android.core.settings.ContentFilterCache
import java.util.Locale

/**
 * 1:1 port of Apple TV Genre.swift and GenreParser.
 *
 * Genre is stored on Movie and Series as a single string joining several genre
 * names (e.g. "Action, Sci-Fi", "Drama / Crime", "Comedy | Romance").
 * These helpers turn that string into individual genre tokens so browse and
 * search surfaces can group and filter by genre.
 */
object GenreParser {
    private val SEPARATORS = charArrayOf(',', '|', '/')

    /**
     * Splits a provider or TMDB genre string into distinct, trimmed tokens,
     * preserving original casing for display.
     */
    fun tokens(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val seen = mutableSetOf<String>()
        val result = mutableListOf<String>()
        val pieces = raw.split(*SEPARATORS)
        for (piece in pieces) {
            val token = piece.trim()
            if (token.isNotEmpty() && seen.add(token.lowercase(Locale.ROOT))) {
                result.add(token)
            }
        }
        return result
    }

    /**
     * Checks if [raw] contains [genre] as a whole token (case-insensitive).
     */
    fun contains(raw: String?, genre: String): Boolean {
        if (raw.isNullOrBlank()) return false
        val needle = genre.lowercase(Locale.ROOT).trim()
        return tokens(raw).any { it.lowercase(Locale.ROOT) == needle }
    }

    /**
     * Distinct genres across [raws], ordered most-common first with ties broken
     * alphabetically. Preserves first-seen display casing.
     */
    fun distinctByFrequency(raws: List<String?>): List<String> {
        val counts = mutableMapOf<String, Int>()
        val display = mutableMapOf<String, String>()

        for (raw in raws) {
            for (token in tokens(raw)) {
                val key = token.lowercase(Locale.ROOT)
                counts[key] = (counts[key] ?: 0) + 1
                if (!display.containsKey(key)) {
                    display[key] = token
                }
            }
        }

        return counts.keys.sortedWith { lhs, rhs ->
            val lhsCount = counts[lhs] ?: 0
            val rhsCount = counts[rhs] ?: 0
            if (lhsCount != rhsCount) {
                rhsCount.compareTo(lhsCount) // descending by frequency
            } else {
                val lhsName = display[lhs] ?: ""
                val rhsName = display[rhs] ?: ""
                lhsName.compareTo(rhsName, ignoreCase = true)
            }
        }.mapNotNull { display[it] }
    }

    /**
     * Common international genre keywords used to detect genres from category names
     * when stream metadata lacks explicit genre fields.
     */
    private val COMMON_GENRE_KEYWORDS = listOf(
        "Action", "Adventure", "Animation", "Anime", "Comedy", "Crime", "Documentary",
        "Drama", "Family", "Fantasy", "History", "Horror", "Kids", "Music", "Mystery",
        "Romance", "Sci-Fi", "Science Fiction", "Sport", "Thriller", "War", "Western"
    )

    /**
     * Extracts recognized genre names from category names as a fallback when
     * individual stream items have not yet undergone TMDB enrichment.
     */
    fun extractFromCategories(categories: List<CategoryEntity>): List<String> {
        val found = mutableSetOf<String>()
        val result = mutableListOf<String>()

        for (cat in categories) {
            val catName = cat.name
            for (keyword in COMMON_GENRE_KEYWORDS) {
                val regex = Regex("\\b${Regex.escape(keyword)}\\b", RegexOption.IGNORE_CASE)
                if (regex.containsMatchIn(catName)) {
                    if (found.add(keyword.lowercase(Locale.ROOT))) {
                        result.add(keyword)
                    }
                }
            }
        }
        return result
    }
}
