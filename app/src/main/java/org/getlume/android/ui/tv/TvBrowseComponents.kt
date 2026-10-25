package org.getlume.android.ui.tv

import org.getlume.android.core.player.ActiveModalStore

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import org.getlume.android.core.data.entities.CategoryEntity
import org.getlume.android.core.data.entities.EpisodeEntity
import org.getlume.android.core.data.entities.MovieEntity
import org.getlume.android.core.data.entities.PlaylistEntity
import org.getlume.android.core.data.entities.SeriesEntity
import org.getlume.android.core.designsystem.*
import org.getlume.android.core.network.SeriesDetailsResult
import org.getlume.android.core.network.XtreamCodesClient
import java.util.Locale

/**
 * Shared Apple TV 1:1 browse components for Movies and Series tabs.
 * Adheres strictly to PosterCardMetrics and CategoryTileMetrics from Apple TV Lume.
 */

// MARK: - Poster Card

@Composable
fun TvPosterCard(
    title: String,
    posterUrl: String,
    rating: Float = 0f,
    progressFraction: Float? = null,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocused: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fillsWidth: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.08f else 1.0f, label = "posterCardScale")

    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocused?.invoke()
        }
    }

    val focusManager = LocalFocusManager.current
    val cardWidth = if (fillsWidth) Modifier.fillMaxWidth() else Modifier.width(135.dp)

    Column(
        modifier = modifier
            .then(cardWidth)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionUp -> {
                            if (onMoveUp != null) {
                                onMoveUp()
                                true
                            } else false
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            onClick()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .scale(scale)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF161A24))
                .border(
                    width = if (isFocused) 2.5.dp else 1.dp,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp)
                )
        ) {
            if (posterUrl.isNotBlank()) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title.take(2).uppercase(),
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Top-right Rating badge
            if (rating > 0f) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.Black.copy(alpha = 0.70f))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        String.format(Locale.US, "%.1f", rating),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Bottom Progress bar for Recently Watched
            if (progressFraction != null && progressFraction > 0f) {
                LinearProgressIndicator(
                    progress = { progressFraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                    color = LumeAccentBlue,
                    trackColor = Color.Black.copy(alpha = 0.5f)
                )
            }
        }

        // Title below poster (fixed height prevents horizontal siblings from being selected on DPAD_DOWN)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .padding(horizontal = 2.dp)
        ) {
            Text(
                text = title,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun TvPosterCardSkeleton(
    focusRequester: FocusRequester? = null,
    onMoveUp: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val focusMod = if (focusRequester != null) {
        Modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionUp -> {
                            if (onMoveUp != null) {
                                onMoveUp()
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
    } else Modifier

    Column(
        modifier = modifier
            .width(135.dp)
            .then(focusMod),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF161A24).copy(alpha = 0.6f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(10.dp)
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .padding(top = 4.dp, end = 45.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.08f))
        )
    }
}

// MARK: - Category Name Tile (CategoryTileMetrics: 320x100 on tvOS, glass plate brightening on focus)

@Composable
fun TvCategoryTile(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocused: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.06f else 1.0f, label = "catTileScale")

    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocused?.invoke()
        }
    }

    val focusManager = LocalFocusManager.current

    Box(
        modifier = modifier
            .scale(scale)
            .focusRequester(focusRequester)
            .height(58.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) Color.White else Color(0xFF1E222D).copy(alpha = 0.85f))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp)
            )
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            onClick()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            color = if (isFocused) Color.Black else Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// MARK: - Browse by Genre Grid Section (GenreGridSection.swift)

@Composable
fun TvGenreGridSection(
    title: String = "Browse by Genre",
    genres: List<String>,
    onSelectGenre: (String) -> Unit
) {
    if (genres.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = title,
            color = LumeTextSecondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 36.dp)
        )

        val chunked = remember(genres) { genres.chunked(4) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            for (rowGenres in chunked) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    for (genre in rowGenres) {
                        TvCategoryTile(
                            name = genre,
                            onClick = { onSelectGenre(genre) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowGenres.size < 4) {
                        repeat(4 - rowGenres.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

// MARK: - All Categories Grid Section (CategoryGridSection.swift)

@Composable
fun TvCategoryGridSection(
    title: String = "All Categories",
    categories: List<CategoryEntity>,
    onSelectCategory: (CategoryEntity) -> Unit
) {
    if (categories.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = title,
            color = LumeTextSecondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 36.dp)
        )

        val chunked = remember(categories) { categories.chunked(4) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            for (rowCats in chunked) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    for (cat in rowCats) {
                        TvCategoryTile(
                            name = cat.name,
                            onClick = { onSelectCategory(cat) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowCats.size < 4) {
                        repeat(4 - rowCats.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Browse Row with Header

@Composable
fun TvBrowseRow(
    title: String,
    hasMore: Boolean = false,
    onShowAll: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = LumeTextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }

        content()
    }
}

// MARK: - Dedicated TV Back/Close Pill

@Composable
fun TvClosePill(
    label: String = "Back",
    focusRequester: FocusRequester = remember { FocusRequester() },
    onMoveDown: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.06f else 1.0f, label = "closePillScale")

    Box(
        modifier = Modifier
            .scale(scale)
            .focusRequester(focusRequester)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isFocused) Color.White else Color.White.copy(alpha = 0.12f))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.16f),
                shape = RoundedCornerShape(14.dp)
            )
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            onClick()
                            true
                        }
                        Key.DirectionDown -> {
                            if (onMoveDown != null) {
                                onMoveDown()
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isFocused) Color.Black else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// MARK: - Full-Screen Category / Collection Poster Grid ("Show All") with Guaranteed Focus

@Composable
fun TvCategoryContentGridScreen(
    title: String,
    itemCount: Int,
    isLoading: Boolean = false,
    emptyMessage: String = "No titles found in this category",
    onBack: () -> Unit,
    content: @Composable (firstItemFocusRequester: FocusRequester) -> Unit
) {
    val firstItemFocusRequester = remember(title) { FocusRequester() }
    val backButtonFocusRequester = remember(title) { FocusRequester() }

    BackHandler(onBack = onBack)

    DisposableEffect(title, itemCount, isLoading) {
        ActiveModalStore.isModalOpen.value = true
        val target = if (itemCount > 0) firstItemFocusRequester else backButtonFocusRequester
        TvFocusRescue.register(target)
        onDispose {
            ActiveModalStore.isModalOpen.value = false
            TvFocusRescue.unregister(target)
        }
    }

    var hasRequestedInitialFocus by remember(title) { mutableStateOf(false) }

    LaunchedEffect(title, itemCount, isLoading) {
        if (!hasRequestedInitialFocus) {
            if (itemCount > 0) {
                var delayMs = 30L
                for (i in 1..10) {
                    delay(delayMs)
                    try {
                        firstItemFocusRequester.requestFocus()
                        hasRequestedInitialFocus = true
                        break
                    } catch (_: Exception) {
                        delayMs = (delayMs * 1.3).toLong()
                    }
                }
            } else if (!isLoading) {
                delay(50)
                try {
                    backButtonFocusRequester.requestFocus()
                    hasRequestedInitialFocus = true
                } catch (_: Exception) {}
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07090E))
            .padding(top = 64.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

            TvClosePill(
                label = "Back",
                focusRequester = backButtonFocusRequester,
                onMoveDown = {
                    try {
                        firstItemFocusRequester.requestFocus()
                    } catch (_: Exception) {}
                },
                onClick = onBack
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp)
        ) {
            if (isLoading && itemCount == 0) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LumeAccentBlue, modifier = Modifier.size(36.dp))
                }
            } else if (itemCount == 0) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = emptyMessage,
                            color = LumeTextSecondary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        TvClosePill(
                            label = "Back",
                            focusRequester = backButtonFocusRequester,
                            onClick = onBack
                        )
                    }
                }
            } else {
                content(firstItemFocusRequester)
            }
        }
    }
}

// MARK: - Full-Bleed Apple TV Movie Detail Page (TVMovieDetailView.swift)

@Composable
fun TvMovieDetailSheet(
    movie: MovieEntity,
    onPlay: () -> Unit,
    onDismiss: () -> Unit
) {
    val playInteraction = remember { MutableInteractionSource() }
    val isPlayFocused by playInteraction.collectIsFocusedAsState()
    val playFocusRequester = remember { FocusRequester() }

    BackHandler(onBack = onDismiss)

    DisposableEffect(Unit) {
        ActiveModalStore.isModalOpen.value = true
        TvFocusRescue.register(playFocusRequester)
        onDispose {
            ActiveModalStore.isModalOpen.value = false
            TvFocusRescue.unregister(playFocusRequester)
        }
    }

    LaunchedEffect(Unit) {
        delay(120)
        try {
            playFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Full-bleed backdrop hero with gradient
        val backdropImage = movie.backdropUrl.ifBlank { movie.posterUrl }
        if (backdropImage.isNotBlank()) {
            AsyncImage(
                model = backdropImage,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp)
                    .align(Alignment.TopCenter)
            )
        }

        // Gradient fade to black
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.25f),
                            Color.Black.copy(alpha = 0.65f),
                            Color(0xFF07090E),
                            Color(0xFF07090E)
                        )
                    )
                )
        )

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text = movie.name,
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 42.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Metadata row
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
                        Text(String.format(Locale.US, "%.1f", movie.rating), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (movie.releaseDate.isNotBlank()) {
                    Text(movie.releaseDate, color = LumeTextSecondary, fontSize = 15.sp)
                }

                if (movie.durationMinutes > 0) {
                    Text("${movie.durationMinutes} min", color = LumeTextSecondary, fontSize = 15.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Plot synopsis
            Text(
                text = if (movie.plot.isNotBlank()) movie.plot else "Watch this movie directly from your IPTV service on Lume.",
                color = Color.White.copy(alpha = 0.80f),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.75f)
            )

            if (movie.cast.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Starring: ${movie.cast}",
                    color = LumeTextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
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

// MARK: - Full-Bleed Apple TV Series Detail Page (TVSeriesDetailView.swift)

@Composable
fun TvSeriesDetailSheet(
    series: SeriesEntity,
    playlist: PlaylistEntity,
    onPlayEpisode: (EpisodeEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val client = remember(playlist) {
        XtreamCodesClient(playlist.serverUrl, playlist.username, playlist.password)
    }

    var detailsResult by remember { mutableStateOf<SeriesDetailsResult?>(null) }
    var selectedSeason by remember { mutableStateOf<Int?>(null) }
    var isLoadingEpisodes by remember { mutableStateOf(true) }

    val closeFocusRequester = remember { FocusRequester() }
    val firstEpFocusRequester = remember { FocusRequester() }
    val seasonFocusRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    var hasRequestedInitialEpFocus by remember { mutableStateOf(false) }

    val closeInteraction = remember { MutableInteractionSource() }
    val isCloseFocused by closeInteraction.collectIsFocusedAsState()

    BackHandler(onBack = onDismiss)

    DisposableEffect(Unit) {
        ActiveModalStore.isModalOpen.value = true
        TvFocusRescue.register(closeFocusRequester)
        onDispose {
            ActiveModalStore.isModalOpen.value = false
            TvFocusRescue.unregister(closeFocusRequester)
        }
    }

    // Request initial focus onto Close button so sheet grabs remote input immediately
    LaunchedEffect(Unit) {
        delay(80)
        try {
            closeFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    LaunchedEffect(series.seriesId) {
        isLoadingEpisodes = true
        hasRequestedInitialEpFocus = false
        detailsResult = client.fetchSeriesDetails(series.seriesId)
        selectedSeason = detailsResult?.seasons?.firstOrNull() ?: 1
        isLoadingEpisodes = false
    }

    val episodesForSeason = remember(detailsResult, selectedSeason) {
        detailsResult?.episodes?.filter { it.seasonNumber == (selectedSeason ?: 1) } ?: emptyList()
    }

    // Automatically focus the first episode once episodes finish loading
    LaunchedEffect(isLoadingEpisodes, episodesForSeason.size) {
        if (!isLoadingEpisodes && episodesForSeason.isNotEmpty() && !hasRequestedInitialEpFocus) {
            delay(120)
            try {
                firstEpFocusRequester.requestFocus()
                hasRequestedInitialEpFocus = true
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && (event.key == Key.Back || event.key == Key.Escape)) {
                    onDismiss()
                    true
                } else false
            }
    ) {
        // Full-bleed backdrop hero with gradient
        val backdropImage = series.backdropUrl.ifBlank { series.posterUrl }
        if (backdropImage.isNotBlank()) {
            AsyncImage(
                model = backdropImage,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .align(Alignment.TopCenter)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.25f),
                            Color.Black.copy(alpha = 0.70f),
                            Color(0xFF07090E),
                            Color(0xFF07090E)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Title, metadata, plot
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = series.name,
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 38.sp,
                        modifier = Modifier.weight(1f)
                    )

                    // Prominent Focusable Close Button with clear white highlight
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isCloseFocused) Color.White else Color.White.copy(alpha = 0.15f))
                            .border(
                                width = if (isCloseFocused) 2.dp else 1.dp,
                                color = if (isCloseFocused) Color.White else Color.White.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .focusRequester(closeFocusRequester)
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown) {
                                    when (event.key) {
                                        Key.DirectionDown -> {
                                            val seasons = detailsResult?.seasons ?: emptyList()
                                            if (seasons.isNotEmpty()) {
                                                val target = seasonFocusRequesters[selectedSeason] ?: seasonFocusRequesters[seasons.first()]
                                                target?.requestFocus()
                                                true
                                            } else if (episodesForSeason.isNotEmpty()) {
                                                firstEpFocusRequester.requestFocus()
                                                true
                                            } else false
                                        }
                                        Key.DirectionLeft -> {
                                            val seasons = detailsResult?.seasons ?: emptyList()
                                            if (seasons.isNotEmpty()) {
                                                val target = seasonFocusRequesters[selectedSeason] ?: seasonFocusRequesters[seasons.first()]
                                                target?.requestFocus()
                                                true
                                            } else true
                                        }
                                        Key.DirectionRight -> true // Consume: do not leak right
                                        Key.DirectionUp -> true    // Consume: do not leak into TopBar
                                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                            onDismiss()
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                            .focusable(interactionSource = closeInteraction)
                            .clickable(interactionSource = closeInteraction, indication = null, onClick = onDismiss)
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Close",
                            color = if (isCloseFocused) Color.Black else Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (series.rating > 0f) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
                            Text(String.format(Locale.US, "%.1f", series.rating), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (series.releaseDate.isNotBlank()) {
                        Text(series.releaseDate, color = LumeTextSecondary, fontSize = 15.sp)
                    }

                    val totalSeasons = detailsResult?.seasons?.size ?: 0
                    if (totalSeasons > 0) {
                        Text("$totalSeasons Season${if (totalSeasons > 1) "s" else ""}", color = LumeTextSecondary, fontSize = 15.sp)
                    }
                }

                Text(
                    text = if (series.plot.isNotBlank()) series.plot else detailsResult?.plot?.ifBlank { "Select an episode to begin watching." } ?: "Select an episode to begin watching.",
                    color = Color.White.copy(alpha = 0.80f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.75f)
                )

                // Season Selector Pills (Apple TV TVSeriesDetailView.swift)
                val seasons = detailsResult?.seasons ?: emptyList()
                if (seasons.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        itemsIndexed(seasons) { sIndex, seasonNum ->
                            val isSelected = selectedSeason == seasonNum
                            val interaction = remember { MutableInteractionSource() }
                            val isFocused by interaction.collectIsFocusedAsState()
                            val seasonRequester = seasonFocusRequesters.getOrPut(seasonNum) { FocusRequester() }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected || isFocused) Color.White else Color.White.copy(alpha = 0.12f))
                                    .border(
                                        width = if (isFocused) 2.dp else 0.dp,
                                        color = if (isFocused) LumeAccentBlue else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .focusRequester(seasonRequester)
                                    .onPreviewKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown) {
                                            when (event.key) {
                                                Key.DirectionUp -> {
                                                    closeFocusRequester.requestFocus()
                                                    true
                                                }
                                                Key.DirectionDown -> {
                                                    if (episodesForSeason.isNotEmpty()) {
                                                        firstEpFocusRequester.requestFocus()
                                                        true
                                                    } else true
                                                }
                                                Key.DirectionLeft -> {
                                                    if (sIndex == 0) true else false // Do not leak left
                                                }
                                                Key.DirectionRight -> {
                                                    if (sIndex == seasons.lastIndex) {
                                                        closeFocusRequester.requestFocus()
                                                        true
                                                    } else false
                                                }
                                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                                    selectedSeason = seasonNum
                                                    true
                                                }
                                                else -> false
                                            }
                                        } else false
                                    }
                                    .focusable(interactionSource = interaction)
                                    .clickable(interactionSource = interaction, indication = null) {
                                        selectedSeason = seasonNum
                                    }
                                    .padding(horizontal = 16.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = "Season $seasonNum",
                                    color = if (isSelected || isFocused) Color.Black else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Section: Horizontal rail of 16:9 Episode Cards (Apple TV TVEpisodeCard)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "EPISODES",
                    color = LumeTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                if (isLoadingEpisodes) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = LumeAccentBlue, modifier = Modifier.size(28.dp))
                    }
                } else if (episodesForSeason.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No episodes available for this season", color = LumeTextSecondary, fontSize = 13.sp)
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        itemsIndexed(episodesForSeason) { index, episode ->
                            val epInteraction = remember { MutableInteractionSource() }
                            val isEpFocused by epInteraction.collectIsFocusedAsState()
                            val epScale by animateFloatAsState(targetValue = if (isEpFocused) 1.06f else 1.0f, label = "epScale")
                            val epRequester = if (index == 0) firstEpFocusRequester else remember { FocusRequester() }

                            Column(
                                modifier = Modifier
                                    .width(220.dp)
                                    .scale(epScale)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF141824))
                                    .border(
                                        width = if (isEpFocused) 2.dp else 1.dp,
                                        color = if (isEpFocused) Color.White else Color.White.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .focusRequester(epRequester)
                                    .onPreviewKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown) {
                                            when (event.key) {
                                                Key.DirectionUp -> {
                                                    val seasons = detailsResult?.seasons ?: emptyList()
                                                    if (seasons.isNotEmpty()) {
                                                        val target = seasonFocusRequesters[selectedSeason] ?: seasonFocusRequesters[seasons.first()]
                                                        target?.requestFocus()
                                                        true
                                                    } else {
                                                        closeFocusRequester.requestFocus()
                                                        true
                                                    }
                                                }
                                                Key.DirectionLeft -> {
                                                    if (index == 0) true else false // Do not leak left
                                                }
                                                Key.DirectionRight -> {
                                                    if (index == episodesForSeason.lastIndex) true else false // Do not leak right
                                                }
                                                Key.DirectionDown -> {
                                                    true // Do not leak down
                                                }
                                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                                    onPlayEpisode(episode)
                                                    true
                                                }
                                                else -> false
                                            }
                                        } else false
                                    }
                                    .focusable(interactionSource = epInteraction)
                                    .clickable(interactionSource = epInteraction, indication = null) {
                                        onPlayEpisode(episode)
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(124.dp)
                                        .background(Color.White.copy(alpha = 0.05f))
                                ) {
                                    if (episode.thumbnail.isNotBlank()) {
                                        AsyncImage(
                                            model = episode.thumbnail,
                                            contentDescription = episode.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(32.dp))
                                        }
                                    }

                                    // Episode number pill
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(6.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color.Black.copy(alpha = 0.75f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("EP ${episode.episodeNumber}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "${episode.episodeNumber}. ${episode.title.ifBlank { "Episode ${episode.episodeNumber}" }}",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (episode.plot.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = episode.plot,
                                            color = LumeTextSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
