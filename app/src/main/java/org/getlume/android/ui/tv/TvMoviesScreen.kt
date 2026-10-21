package org.getlume.android.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.getlume.android.LumeApp
import org.getlume.android.core.data.entities.CategoryEntity
import org.getlume.android.core.data.entities.MovieEntity
import org.getlume.android.core.designsystem.*
import org.getlume.android.core.player.PlayableMedia
import org.getlume.android.core.player.toPlayableMedia
import org.getlume.android.core.sync.ContentSyncManager

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.input.key.*

/**
 * Dedicated Movies Tab for Apple TV Lume on Android TV.
 * Category rail on the left, 2:3 poster grid on the right, and interactive Movie Detail modal.
 */
@Composable
fun TvMoviesScreen(
    onPlayMedia: (PlayableMedia) -> Unit,
    firstItemFocusRequester: FocusRequester = remember { FocusRequester() },
    onMoveUpToTopBar: (() -> Unit)? = null
) {
    val db = LumeApp.instance.database
    val activePlaylist by db.playlistDao().observeActivePlaylist().collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()

    val categories by db.categoryDao().getCategories(activePlaylist?.id ?: "", "movie").collectAsState(initial = emptyList())
    var selectedCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var selectedMovie by remember { mutableStateOf<MovieEntity?>(null) }
    var isLoadingCategory by remember { mutableStateOf(false) }
    var isInGridPane by remember { mutableStateOf(false) }

    // Breadcrumb BackHandler: When in poster grid, Back retracts focus to the Category sidebar
    BackHandler(enabled = isInGridPane && selectedMovie == null) {
        try {
            firstItemFocusRequester.requestFocus()
        } catch (_: Exception) {}
        isInGridPane = false
    }

    // Breadcrumb BackHandler: When in Category sidebar, Back retracts focus up to the TopBar
    BackHandler(enabled = !isInGridPane && selectedMovie == null) {
        onMoveUpToTopBar?.invoke()
    }

    // Default to first category
    LaunchedEffect(categories) {
        if (selectedCategory == null && categories.isNotEmpty()) {
            selectedCategory = categories.first()
        }
    }

    // Initial check: if no categories, fetch VOD categories in background
    LaunchedEffect(activePlaylist) {
        activePlaylist?.let { playlist ->
            if (categories.isEmpty()) {
                ContentSyncManager.syncAllCategories(playlist)
            }
        }
    }

    // Observe movies for the selected category
    val moviesForCategory by if (selectedCategory != null) {
        db.vodDao().getMoviesByCategory(activePlaylist?.id ?: "", selectedCategory!!.id).collectAsState(initial = emptyList())
    } else {
        db.vodDao().getMovies(activePlaylist?.id ?: "", 100).collectAsState(initial = emptyList())
    }

    // Auto-fetch movies for category if not yet cached in Room
    LaunchedEffect(selectedCategory) {
        val cat = selectedCategory
        val pl = activePlaylist
        if (cat != null && pl != null && moviesForCategory.isEmpty()) {
            isLoadingCategory = true
            ContentSyncManager.loadCategoryMovies(pl, cat)
            isLoadingCategory = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07090E))
            .padding(top = 80.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Column: VOD Categories Sidebar
            Column(
                modifier = Modifier
                    .width(260.dp)
                    .fillMaxHeight()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .onFocusChanged { if (it.hasFocus) isInGridPane = false }
                    .padding(vertical = 16.dp, horizontal = 16.dp)
            ) {
                Text(
                    text = "CATEGORIES",
                    color = LumeTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )

                if (categories.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = LumeAccentBlue, modifier = Modifier.size(32.dp))
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        itemsIndexed(categories) { index, category ->
                            val isSelected = selectedCategory?.id == category.id
                            val itemFocusRequester = if (isSelected || (selectedCategory == null && index == 0)) {
                                firstItemFocusRequester
                            } else null

                            TvMovieCategoryItem(
                                category = category,
                                isSelected = isSelected,
                                focusRequester = itemFocusRequester,
                                onMoveUp = if (index == 0) onMoveUpToTopBar else null,
                                onClick = { selectedCategory = category }
                            )
                        }
                    }
                }
            }

            // Right Column: Movies 2:3 Poster Grid
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onFocusChanged { if (it.hasFocus) isInGridPane = true }
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                // Header: Category Title & Count
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = selectedCategory?.name ?: "All Movies",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${moviesForCategory.size} titles",
                        color = LumeTextSecondary,
                        fontSize = 14.sp
                    )
                }

                if (isLoadingCategory && moviesForCategory.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = LumeAccentBlue)
                            Text("Loading movies...", color = LumeTextSecondary, fontSize = 14.sp)
                        }
                    }
                } else if (moviesForCategory.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No movies found in this category", color = LumeTextSecondary, fontSize = 16.sp)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 36.dp)
                    ) {
                        itemsIndexed(moviesForCategory) { index, movie ->
                            TvMovieCard(
                                movie = movie,
                                onMoveUp = if (index < 5) onMoveUpToTopBar else null,
                                onClick = { selectedMovie = movie }
                            )
                        }
                    }
                }
            }
        }

        // Apple TV Faithful Movie Detail Modal Sheet
        selectedMovie?.let { movie ->
            BackHandler { selectedMovie = null }
            TvMovieDetailSheet(
                movie = movie,
                onPlay = {
                    onPlayMedia(movie.toPlayableMedia())
                },
                onDismiss = { selectedMovie = null }
            )
        }
    }
}

@Composable
private fun TvMovieCategoryItem(
    category: CategoryEntity,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    onMoveUp: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.04f else 1.0f, label = "catScale")

    val bgColor = when {
        isFocused -> Color.White
        isSelected -> LumeAccentBlue.copy(alpha = 0.25f)
        else -> Color.Transparent
    }

    val textColor = when {
        isFocused -> Color.Black
        isSelected -> LumeAccentBlue
        else -> Color.White.copy(alpha = 0.85f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionUp) {
                    if (onMoveUp != null) {
                        onMoveUp()
                        true
                    } else false
                } else false
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Text(
            text = category.name,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TvMovieCard(
    movie: MovieEntity,
    onMoveUp: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.08f else 1.0f, label = "cardScale")

    Column(
        modifier = Modifier
            .width(140.dp)
            .scale(scale)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionUp) {
                    if (onMoveUp != null) {
                        onMoveUp()
                        true
                    } else false
                } else false
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 2:3 Aspect Ratio Poster with Focus Border
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(
                    width = if (isFocused) 2.5.dp else 1.dp,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            if (movie.posterUrl.isNotBlank()) {
                AsyncImage(
                    model = movie.posterUrl,
                    contentDescription = movie.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = movie.name.take(2).uppercase(),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Rating Badge in top-right
            if (movie.rating > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(11.dp))
                        Text(
                            text = String.format("%.1f", movie.rating),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Movie Title & Year
        Text(
            text = movie.name,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.88f),
            fontSize = 13.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun TvMovieDetailSheet(
    movie: MovieEntity,
    onPlay: () -> Unit,
    onDismiss: () -> Unit
) {
    val playInteraction = remember { MutableInteractionSource() }
    val isPlayFocused by playInteraction.collectIsFocusedAsState()
    val playFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        try {
            playFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .padding(54.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF131722))
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(24.dp))
                .padding(36.dp),
            horizontalArrangement = Arrangement.spacedBy(36.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Large 2:3 Poster
            Box(
                modifier = Modifier
                    .width(220.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.05f))
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

            // Info & Metadata
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = movie.name,
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 38.sp
                )

                // Subtitle metadata row (Year, Rating, Duration)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (movie.rating > 0f) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
                            Text(String.format("%.1f", movie.rating), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (movie.releaseDate.isNotBlank()) {
                        Text(movie.releaseDate, color = LumeTextSecondary, fontSize = 15.sp)
                    }

                    if (movie.durationMinutes > 0) {
                        Text("${movie.durationMinutes} min", color = LumeTextSecondary, fontSize = 15.sp)
                    }
                }

                // Plot synopsis
                Text(
                    text = if (movie.plot.isNotBlank()) movie.plot else "Watch this movie directly from your IPTV service on Lume.",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )

                if (movie.cast.isNotBlank()) {
                    Text(
                        text = "Starring: ${movie.cast}",
                        color = LumeTextSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Play Button
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        modifier = Modifier
                            .scale(if (isPlayFocused) 1.06f else 1.0f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isPlayFocused) Color.White else LumeAccentBlue)
                            .focusRequester(playFocusRequester)
                            .focusable(interactionSource = playInteraction)
                            .clickable(interactionSource = playInteraction, indication = null, onClick = onPlay)
                            .padding(horizontal = 28.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = if (isPlayFocused) Color.Black else Color.White
                            )
                            Text(
                                text = "Play Movie",
                                color = if (isPlayFocused) Color.Black else Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    TvSettingsActionButton(
                        title = "Close",
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}
