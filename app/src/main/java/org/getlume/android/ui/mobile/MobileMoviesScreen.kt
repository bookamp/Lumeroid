package org.getlume.android.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.getlume.android.LumeApp
import org.getlume.android.core.data.BrowseCache
import org.getlume.android.core.data.entities.CategoryEntity
import org.getlume.android.core.data.entities.MovieEntity
import org.getlume.android.core.designsystem.*
import org.getlume.android.core.player.PlayableMedia
import org.getlume.android.core.player.toPlayableMedia
import org.getlume.android.core.settings.ContentFilterCache

@Composable
fun MobileMoviesScreen(
    onPlayMedia: (PlayableMedia) -> Unit
) {
    val db = LumeApp.instance.database
    val activePlaylist by db.playlistDao().observeActivePlaylist().collectAsState(initial = null)
    val playlistId = activePlaylist?.id ?: ""

    // 1. Live/Cached Movie Data from BrowseCache (Exact Shield TV Parity)
    val cachedRecentlyWatched by BrowseCache.recentlyWatchedMovies.collectAsState()
    val cachedFavorites by BrowseCache.favoriteMovies.collectAsState()
    val cachedRecentlyAdded by BrowseCache.recentlyAddedMovies.collectAsState()
    val previewCategoryMap by BrowseCache.previewCategoryMovies.collectAsState()

    // 2. Raw movies & categories for search / category drill-in
    val movies by db.vodDao().getMovies(playlistId).collectAsState(initial = emptyList())
    val categories by db.categoryDao().getCategories(playlistId, "movie").collectAsState(initial = emptyList())

    // 3. Language Content Filtering
    val isFiltering by ContentFilterCache.isFiltering.collectAsState()
    val allowedMovieCatIds by ContentFilterCache.movieCategoryIds.collectAsState()

    val visibleCategories = remember(categories, isFiltering, allowedMovieCatIds) {
        if (!isFiltering) categories
        else categories.filter { allowedMovieCatIds.contains(it.id) }
    }

    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredMovies = remember(movies, visibleCategories, selectedCategoryId, searchQuery, isFiltering, allowedMovieCatIds) {
        val baseMovies = if (!isFiltering) movies else movies.filter { allowedMovieCatIds.contains(it.categoryId) }
        baseMovies.filter { movie ->
            val matchesCategory = selectedCategoryId == null || movie.categoryId == selectedCategoryId
            val matchesSearch = searchQuery.isBlank() || movie.name.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090B10))
    ) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search movies...", color = LumeTextSecondary, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = LumeTextSecondary) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF141923),
                unfocusedContainerColor = Color(0xFF141923),
                focusedBorderColor = LumeAccentBlue,
                unfocusedBorderColor = Color.White.copy(alpha = 0.10f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Category Pills Row
        if (visibleCategories.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            ) {
                item {
                    val isSelected = selectedCategoryId == null
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) LumeAccentBlue else Color.White.copy(alpha = 0.08f))
                            .clickable { selectedCategoryId = null }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("All Movies", color = Color.White, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                items(visibleCategories, key = { it.id }) { cat ->
                    val isSelected = selectedCategoryId == cat.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) LumeAccentBlue else Color.White.copy(alpha = 0.08f))
                            .clickable { selectedCategoryId = cat.id }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(cat.name, color = Color.White, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Content Display: Browse Rails Mode vs Grid Drill-in Mode
        val isBrowseMode = selectedCategoryId == null && searchQuery.isBlank()

        if (isBrowseMode) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // 1. Recently Watched Rail (if any)
                if (cachedRecentlyWatched.isNotEmpty()) {
                    item(key = "movies_recently_watched") {
                        Column {
                            MobileMoviesSectionHeader(
                                title = "RECENTLY WATCHED",
                                actionLabel = "See All",
                                onAction = {}
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                items(cachedRecentlyWatched.take(20), key = { "rw_${it.second.id}" }) { (progress, movie) ->
                                    MobileMoviePosterCard(
                                        movie = movie,
                                        subtitle = if (movie.rating > 0) "★ %.1f".format(movie.rating) else "",
                                        onClick = { onPlayMedia(movie.toPlayableMedia()) }
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Favorites Rail (if any)
                if (cachedFavorites.isNotEmpty()) {
                    item(key = "movies_favorites") {
                        Column {
                            MobileMoviesSectionHeader(
                                title = "FAVORITES",
                                actionLabel = "See All",
                                onAction = {}
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                items(cachedFavorites.take(20), key = { "fav_${it.id}" }) { movie ->
                                    MobileMoviePosterCard(
                                        movie = movie,
                                        subtitle = if (movie.rating > 0) "★ %.1f".format(movie.rating) else "",
                                        onClick = { onPlayMedia(movie.toPlayableMedia()) }
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Recently Added Rail (1:1 Apple TV & Shield TV Parity)
                if (cachedRecentlyAdded.isNotEmpty()) {
                    item(key = "movies_recently_added") {
                        Column {
                            MobileMoviesSectionHeader(
                                title = "RECENTLY ADDED",
                                actionLabel = "New Releases",
                                onAction = {}
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                items(cachedRecentlyAdded.take(20), key = { "ra_${it.id}" }) { movie ->
                                    MobileMoviePosterCard(
                                        movie = movie,
                                        subtitle = if (movie.rating > 0) "★ %.1f".format(movie.rating) else movie.releaseDate.take(4),
                                        onClick = { onPlayMedia(movie.toPlayableMedia()) }
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Category Preview Rails (Top categories precomputed in BrowseCache)
                items(visibleCategories.take(6), key = { "cat_preview_${it.id}" }) { category ->
                    val preloaded = previewCategoryMap[category.id]
                    val catMovies = remember(preloaded, movies) {
                        preloaded ?: movies.filter { it.categoryId == category.id }.take(20)
                    }

                    if (catMovies.isNotEmpty()) {
                        Column {
                            MobileMoviesSectionHeader(
                                title = category.name.uppercase(),
                                actionLabel = "Browse All",
                                onAction = { selectedCategoryId = category.id }
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                items(catMovies.take(20), key = { "c_${category.id}_${it.id}" }) { movie ->
                                    MobileMoviePosterCard(
                                        movie = movie,
                                        subtitle = if (movie.rating > 0) "★ %.1f".format(movie.rating) else movie.releaseDate.take(4),
                                        onClick = { onPlayMedia(movie.toPlayableMedia()) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Category Drill-in Grid or Search Results
            if (filteredMovies.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No movies found", color = LumeTextSecondary, fontSize = 14.sp)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredMovies, key = { it.id }) { movie ->
                        MobileMoviePosterCard(
                            movie = movie,
                            subtitle = if (movie.rating > 0) "★ %.1f".format(movie.rating) else movie.releaseDate.take(4),
                            onClick = { onPlayMedia(movie.toPlayableMedia()) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileMoviesSectionHeader(
    title: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        if (actionLabel.isNotBlank()) {
            Text(
                text = actionLabel,
                color = LumeAccentBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onAction)
            )
        }
    }
}

@Composable
private fun MobileMoviePosterCard(
    movie: MovieEntity,
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
            if (movie.posterUrl.isNotBlank()) {
                AsyncImage(
                    model = movie.posterUrl,
                    contentDescription = movie.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = movie.name,
            color = Color.White,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                color = LumeTextSecondary,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}
