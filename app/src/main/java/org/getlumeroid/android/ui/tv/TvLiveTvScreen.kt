package org.getlumeroid.android.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.runtime.snapshotFlow
import androidx.media3.common.PlaybackException
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.getlumeroid.android.LumeroidApp
import org.getlumeroid.android.core.data.entities.CategoryEntity
import org.getlumeroid.android.core.designsystem.TvFocusRescue
import org.getlumeroid.android.core.data.entities.ChannelEntity
import org.getlumeroid.android.core.data.entities.EpgListingEntity
import org.getlumeroid.android.core.designsystem.*
import org.getlumeroid.android.core.player.PlayableMedia
import org.getlumeroid.android.core.player.toCatchupPlayableMedia
import org.getlumeroid.android.core.player.toPlayableMedia
import org.getlumeroid.android.core.settings.AppSettings
import org.getlumeroid.android.core.settings.ContentFilterCache
import org.getlumeroid.android.core.settings.LiveTVLayoutMode
import java.text.SimpleDateFormat
import java.util.*

/**
 * A selectable entry in the Live TV category sidebar.
 * 100% feature-parity with Lume's LiveTVSection.swift:
 * Supports virtual collections (Favorites & Recently Watched) as well as synced categories.
 */
sealed class LiveTvSection(val id: String, val title: String) {
    object Favorites : LiveTvSection("lume.liveSection.favorites", "Favorites")
    object RecentlyWatched : LiveTvSection("lume.liveSection.recentlyWatched", "Recently Watched")
    data class Category(val category: CategoryEntity) : LiveTvSection(category.id, category.name)
}

/**
 * Now / Next snapshot for a single live channel.
 * Feature-parity with ChannelEPGSnapshot.swift in original Lume.
 */
data class ChannelEpgData(
    val current: EpgListingEntity? = null,
    val next: EpgListingEntity? = null,
    val programs: List<EpgListingEntity> = emptyList()
)

/**
 * Authentic Apple TV Live TV screen matching Lume's TVLiveTVScreen.swift:
 * - Categories rail with virtual collections (Favorites ❤️ and Recently Watched 🕒)
 * - Dual layout modes: Guide (EPG Timeline Grid) vs List (Scannable TV Channel Cards)
 * - Real indexed EPG now/next broadcast listings
 * - Interactive Channel Context Action Sheet (Play, Toggle Favorite, Multi-View, Info)
 * - Clear Recently Watched flow
 * - Seamless remote D-pad focus traversal
 */
@Composable
fun TvLiveTvScreen(
    onPlayMedia: (PlayableMedia) -> Unit,
    firstItemFocusRequester: FocusRequester = remember { FocusRequester() },
    isTopBarFocused: Boolean = false,
    onMoveUpToTopBar: (() -> Unit)? = null,
    onStartMultiView: ((ChannelEntity) -> Unit)? = null
) {
    val db = LumeroidApp.instance.database
    val settings = remember { AppSettings.get() }
    val scope = rememberCoroutineScope()
    val activePlaylist by db.playlistDao().observeActivePlaylist().collectAsState(initial = null)
    val playlistId = activePlaylist?.id ?: ""
    val activeProfileId = settings.activeProfileId

    // 1. Observe real categories & virtual collections
    val categories by db.categoryDao().getCategories(playlistId, "live").collectAsState(initial = emptyList())
    val favoriteChannels by db.channelDao().getFavoriteChannels(playlistId).collectAsState(initial = emptyList())
    val recentChannels by db.channelDao().getRecentlyWatchedChannels(playlistId, activeProfileId).collectAsState(initial = emptyList())

    // Observe precomputed content filter cache
    val isFiltering by ContentFilterCache.isFiltering.collectAsState()
    val allowedLiveCatIds by ContentFilterCache.liveCategoryIds.collectAsState()
    val allowedChannelIds by ContentFilterCache.matchingChannelIds.collectAsState()

    // Filter categories: synchronous instant lookup, 0ms compute, no layout flicker
    val visibleCategories = remember(categories, isFiltering, allowedLiveCatIds) {
        if (!isFiltering) categories
        else categories.filter { allowedLiveCatIds.contains(it.id) }
    }

    // 2. State management
    var selectedSection by remember { mutableStateOf<LiveTvSection?>(null) }
    var layoutMode by remember { mutableStateOf(settings.liveTVLayoutMode) }
    var isInChannelsPane by remember { mutableStateOf(false) }
    var selectedChannelForMenu by remember { mutableStateOf<ChannelEntity?>(null) }
    val switcherFocusRequester = remember { FocusRequester() }
    val channelFirstFocusRequester = remember { FocusRequester() }

    val visibleFavoriteChannels = remember(favoriteChannels, isFiltering, allowedChannelIds) {
        if (!isFiltering) favoriteChannels
        else favoriteChannels.filter { allowedChannelIds.contains(it.id) }
    }

    val visibleRecentChannels = remember(recentChannels, isFiltering, allowedChannelIds) {
        if (!isFiltering) recentChannels
        else recentChannels.filter { allowedChannelIds.contains(it.id) }
    }

    // Seed default selection if null or if current selected category was filtered out
    LaunchedEffect(visibleCategories, visibleFavoriteChannels, visibleRecentChannels) {
        val currentSec = selectedSection
        if (currentSec == null || (currentSec is LiveTvSection.Category && visibleCategories.none { it.id == currentSec.category.id })) {
            selectedSection = when {
                visibleFavoriteChannels.isNotEmpty() -> LiveTvSection.Favorites
                visibleRecentChannels.isNotEmpty() -> LiveTvSection.RecentlyWatched
                visibleCategories.isNotEmpty() -> LiveTvSection.Category(visibleCategories.first())
                else -> null
            }
        }
    }

    // Auto-sync TV Guide listings if empty or stale (> 4 hours)
    LaunchedEffect(activePlaylist?.id) {
        val pl = activePlaylist ?: return@LaunchedEffect
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val count = db.epgDao().getCount()
            val lastSync = settings.epgLastSyncDate
            val nowTime = System.currentTimeMillis()
            if (count == 0 || (nowTime - lastSync) > 4 * 3600 * 1000L) {
                org.getlumeroid.android.core.sync.EpgSyncManager.syncNow(playlist = pl)
            }
        }
    }

    // 3. Load channels for the active selection
    val displayChannels by produceState<List<ChannelEntity>>(
        initialValue = emptyList(),
        selectedSection,
        playlistId,
        visibleFavoriteChannels,
        visibleRecentChannels,
        visibleCategories,
        isFiltering,
        allowedChannelIds
    ) {
        when (val sec = selectedSection) {
            is LiveTvSection.Favorites -> {
                this.value = visibleFavoriteChannels
            }
            is LiveTvSection.RecentlyWatched -> {
                this.value = visibleRecentChannels
            }
            is LiveTvSection.Category -> {
                db.channelDao().getChannelsByCategory(playlistId, sec.category.id).collect { list ->
                    this@produceState.value = if (!isFiltering) list else list.filter { allowedChannelIds.contains(it.id) }
                }
            }
            null -> {
                if (visibleCategories.isNotEmpty()) {
                    val firstCat = visibleCategories.first()
                    db.channelDao().getChannelsByCategory(playlistId, firstCat.id).collect { list ->
                        this@produceState.value = if (!isFiltering) list else list.filter { allowedChannelIds.contains(it.id) }
                    }
                } else {
                    this.value = emptyList()
                }
            }
        }
    }

    // 4. Batch indexed EPG resolution matching ChannelEPGLoader.swift (12-hour horizon)
    val epgDataMap by produceState<Map<String, ChannelEpgData>>(
        initialValue = emptyMap(),
        displayChannels
    ) {
        if (displayChannels.isEmpty()) {
            this.value = emptyMap()
            return@produceState
        }
        val tvgIds = displayChannels.map { it.tvgId.ifBlank { it.streamId } }.filter { it.isNotBlank() }
        if (tvgIds.isEmpty()) {
            this.value = emptyMap()
            return@produceState
        }
        val now = System.currentTimeMillis()
        val windowStart = now - 4 * 3600 * 1000L
        val horizon = now + 28 * 3600 * 1000L
        db.epgDao().observeProgramsForChannels(tvgIds, windowStart, horizon).collect { listings ->
            val grouped = listings.groupBy { it.channelTvgId }
            val map = mutableMapOf<String, ChannelEpgData>()
            for (chan in displayChannels) {
                val key = chan.tvgId.ifBlank { chan.streamId }
                val progs = grouped[key] ?: emptyList()
                val current = progs.firstOrNull { it.startTime <= now && now < it.endTime }
                val next = progs.firstOrNull { it.startTime > now }
                map[chan.id] = ChannelEpgData(current = current, next = next, programs = progs)
            }
            this@produceState.value = map
        }
    }

    // 5. Currently focused channel and program for live preview & metadata hero
    var focusedChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    var focusedProgram by remember { mutableStateOf<EpgListingEntity?>(null) }

    LaunchedEffect(displayChannels) {
        if (focusedChannel == null || displayChannels.none { it.id == focusedChannel?.id }) {
            focusedChannel = displayChannels.firstOrNull()
            focusedProgram = null
        }
    }

    val channelListState = rememberLazyListState()
    val guideListState = rememberLazyListState()
    var lastFocusedSidebarRequester by remember { mutableStateOf<FocusRequester?>(null) }
    var lastFocusedChannelRequester by remember { mutableStateOf<FocusRequester?>(null) }

    DisposableEffect(isInChannelsPane, firstItemFocusRequester, lastFocusedChannelRequester, lastFocusedSidebarRequester) {
        val target = if (isInChannelsPane) {
            lastFocusedChannelRequester ?: channelFirstFocusRequester
        } else {
            lastFocusedSidebarRequester ?: firstItemFocusRequester
        }
        TvFocusRescue.register(target)
        onDispose {
            TvFocusRescue.unregister(target)
        }
    }

    val restoreSidebarFocus: () -> Unit = {
        isInChannelsPane = false
        val req = lastFocusedSidebarRequester ?: firstItemFocusRequester
        TvFocusRescue.register(req)
        try {
            req.requestFocus()
        } catch (_: Exception) {}
    }

    LaunchedEffect(selectedSection) {
        lastFocusedChannelRequester = null
    }

    val enterChannelsPane: (LiveTvSection) -> Unit = { sec ->
        selectedSection = sec
        isInChannelsPane = true
        scope.launch {
            for (i in 0..12) {
                try {
                    val target = lastFocusedChannelRequester ?: channelFirstFocusRequester
                    if (target.requestFocus()) {
                        TvFocusRescue.register(target)
                        break
                    }
                } catch (_: Exception) {}
                kotlinx.coroutines.delay(25)
            }
        }
    }

    // 6. Breadcrumb remote navigation (consolidated single handler)
    BackHandler(enabled = !isTopBarFocused) {
        if (isInChannelsPane) {
            isInChannelsPane = false
            restoreSidebarFocus()
        } else {
            onMoveUpToTopBar?.invoke()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF141D2D), Color(0xFF07090E)),
                    radius = 1400f
                )
            )
            .padding(top = 72.dp)
    ) {
        // ==========================================
        // LEFT PANE: Floating Category Sidebar (matching Apple TV TVCategoryRail.swift)
        // ==========================================
        Column(
            modifier = Modifier
                .padding(start = 24.dp, top = 0.dp, bottom = 12.dp)
                .width(170.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(16.dp)
                )
                .onFocusChanged { if (it.hasFocus) isInChannelsPane = false }
                .padding(horizontal = 6.dp, vertical = 6.dp)
        ) {
            val categoryRequesters = remember { mutableMapOf<String, FocusRequester>() }
            val firstCat = visibleCategories.firstOrNull()
            val favRequester = if (visibleFavoriteChannels.isNotEmpty()) firstItemFocusRequester else remember { FocusRequester() }
            val recentsRequester = if (visibleFavoriteChannels.isEmpty() && visibleRecentChannels.isNotEmpty()) firstItemFocusRequester else remember { FocusRequester() }
            if (visibleFavoriteChannels.isEmpty() && visibleRecentChannels.isEmpty() && firstCat != null) {
                categoryRequesters.getOrPut(firstCat.id) { firstItemFocusRequester }
            }

            val channelTargetRequester = lastFocusedChannelRequester ?: channelFirstFocusRequester

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(1.5.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                // Pinned Virtual Section 1: Favorites (matching Apple TV TVCategoryRail.swift)
                val isFavSelected = selectedSection is LiveTvSection.Favorites
                item {
                    TvSidebarRailItem(
                        title = "Favorites",
                        icon = Icons.Default.Favorite,
                        iconTint = Color.White.copy(alpha = 0.8f),
                        isSelected = isFavSelected,
                        focusRequester = favRequester,
                        rightFocusRequester = channelTargetRequester,
                        onMoveUp = onMoveUpToTopBar,
                        onFocused = {
                            lastFocusedSidebarRequester = favRequester
                            if (visibleFavoriteChannels.isNotEmpty()) {
                                selectedSection = LiveTvSection.Favorites
                            }
                        },
                        onClick = { enterChannelsPane(LiveTvSection.Favorites) }
                    )
                }

                // Pinned Virtual Section 2: Recently Watched
                val isRecentsSelected = selectedSection is LiveTvSection.RecentlyWatched
                item {
                    TvSidebarRailItem(
                        title = "Recently Watched",
                        icon = Icons.Default.History,
                        iconTint = Color.White.copy(alpha = 0.8f),
                        isSelected = isRecentsSelected,
                        focusRequester = recentsRequester,
                        rightFocusRequester = channelTargetRequester,
                        onMoveUp = null,
                        onFocused = {
                            lastFocusedSidebarRequester = recentsRequester
                            if (visibleRecentChannels.isNotEmpty()) {
                                selectedSection = LiveTvSection.RecentlyWatched
                            }
                        },
                        onClick = { enterChannelsPane(LiveTvSection.RecentlyWatched) }
                    )
                }

                // Pinned Virtual Section 3: Recordings
                item {
                    TvSidebarRailItem(
                        title = "Recordings",
                        icon = Icons.Default.Voicemail,
                        iconTint = Color.White.copy(alpha = 0.8f),
                        isSelected = false,
                        focusRequester = null,
                        rightFocusRequester = channelTargetRequester,
                        onMoveUp = null,
                        onFocused = { },
                        onClick = { }
                    )
                }

                // Synced Playlist Categories
                itemsIndexed(visibleCategories) { _, category ->
                    val isCatSelected = (selectedSection as? LiveTvSection.Category)?.category?.id == category.id
                    val catRequester = categoryRequesters.getOrPut(category.id) { FocusRequester() }

                    TvSidebarRailItem(
                        title = category.name,
                        isSelected = isCatSelected,
                        focusRequester = catRequester,
                        rightFocusRequester = channelTargetRequester,
                        onMoveUp = null,
                        onFocused = {
                            selectedSection = LiveTvSection.Category(category)
                            lastFocusedSidebarRequester = catRequester
                        },
                        onClick = { enterChannelsPane(LiveTvSection.Category(category)) }
                    )
                }
            }
        }

        // ==========================================
        // RIGHT PANE: Channel Detail & Guide Area (matching Apple TV)
        // ==========================================
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 16.dp, end = 24.dp, top = 0.dp, bottom = 12.dp)
                .onFocusChanged { if (it.hasFocus) isInChannelsPane = true }
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                if (displayChannels.isEmpty()) {
                    val emptyBoxInteractionSource = remember { MutableInteractionSource() }
                    val isEmptyFocused by emptyBoxInteractionSource.collectIsFocusedAsState()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(channelFirstFocusRequester)
                            .focusable(interactionSource = emptyBoxInteractionSource)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isEmptyFocused) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                            .border(
                                width = if (isEmptyFocused) 2.dp else 0.dp,
                                color = if (isEmptyFocused) LumeroidAccentBlue else Color.Transparent,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionLeft) {
                                    restoreSidebarFocus()
                                    true
                                } else false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.Tv,
                                contentDescription = null,
                                tint = if (isEmptyFocused) Color.White else LumeroidTextSecondary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "No Channels in this section",
                                color = if (isEmptyFocused) Color.White else LumeroidTextSecondary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Press Left to return to Categories",
                                color = LumeroidTextSecondary.copy(alpha = 0.7f),
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    val currentSectionName = when (val sec = selectedSection) {
                        is LiveTvSection.Category -> sec.category.name
                        is LiveTvSection.Favorites -> "Favorites"
                        is LiveTvSection.RecentlyWatched -> "Recently Watched"
                        null -> ""
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        // 100% Feature-Parity: Live Video Hero Preview Pane (EPGPreviewPane / EPGHeroVideoFigure)
                        TvGuideHeroPreviewPane(
                            focusedChannel = focusedChannel ?: displayChannels.firstOrNull(),
                            focusedProgram = focusedProgram,
                            epgData = epgDataMap[focusedChannel?.id ?: displayChannels.firstOrNull()?.id],
                            onPlay = { chan -> onPlayMedia(chan.toPlayableMedia(lineup = displayChannels, categoryName = currentSectionName)) }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            if (layoutMode == LiveTVLayoutMode.LIST) {
                                // ----------------------------------------------------
                                // MODE 1: Scannable TV Channel Cards (TVChannelsList)
                                // ----------------------------------------------------
                                TvChannelsListView(
                                    channels = displayChannels,
                                    epgDataMap = epgDataMap,
                                    isRecentlyWatched = selectedSection is LiveTvSection.RecentlyWatched,
                                    onClearRecentlyWatched = {
                                        scope.launch {
                                            db.watchProgressDao().clearRecentlyWatchedChannels(activeProfileId)
                                        }
                                    },
                                    onPlay = { chan -> onPlayMedia(chan.toPlayableMedia(lineup = displayChannels, categoryName = currentSectionName)) },
                                    onOpenMenu = { chan -> selectedChannelForMenu = chan },
                                    onFocusChannel = { chan ->
                                        focusedChannel = chan
                                        focusedProgram = null
                                    },
                                    listState = channelListState,
                                    firstItemFocusRequester = channelFirstFocusRequester,
                                    onFocusedRequester = { req ->
                                        lastFocusedChannelRequester = req
                                        TvFocusRescue.register(req)
                                    },
                                    onMoveLeftToSidebar = restoreSidebarFocus,
                                    onMoveUpToSwitcher = {
                                        onMoveUpToTopBar?.invoke()
                                    }
                                )
                            } else {
                                // ----------------------------------------------------
                                // MODE 2: EPG Timeline Guide (EPGGuideView / EPGGrid)
                                // ----------------------------------------------------
                                TvEpgGuideView(
                                    channels = displayChannels,
                                    epgDataMap = epgDataMap,
                                    onPlay = { chan -> onPlayMedia(chan.toPlayableMedia(lineup = displayChannels, categoryName = currentSectionName)) },
                                    onPlayCatchup = { chan, prog -> onPlayMedia(chan.toCatchupPlayableMedia(prog, lineup = displayChannels, categoryName = currentSectionName)) },
                                    onOpenMenu = { chan -> selectedChannelForMenu = chan },
                                    onFocusChannel = { chan -> focusedChannel = chan },
                                    onFocusProgram = { prog -> focusedProgram = prog },
                                    listState = guideListState,
                                    firstItemFocusRequester = channelFirstFocusRequester,
                                    onFocusedRequester = { req ->
                                        lastFocusedChannelRequester = req
                                        TvFocusRescue.register(req)
                                    },
                                    onMoveLeftToSidebar = restoreSidebarFocus,
                                    onMoveUpToSwitcher = {
                                        onMoveUpToTopBar?.invoke()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // ACTION SHEET MODAL: Live Channel Quick Menu
    // ==========================================
    selectedChannelForMenu?.let { channel ->
        val isFav = channel.isFavorite
        val epg = epgDataMap[channel.id]
        val currentSectionName = when (val sec = selectedSection) {
            is LiveTvSection.Category -> sec.category.name
            is LiveTvSection.Favorites -> "Favorites"
            is LiveTvSection.RecentlyWatched -> "Recently Watched"
            null -> ""
        }
        TvLiveChannelActionSheet(
            channel = channel,
            currentEpg = epg?.current,
            onPlay = {
                selectedChannelForMenu = null
                onPlayMedia(channel.toPlayableMedia(lineup = displayChannels, categoryName = currentSectionName))
            },
            onToggleFavorite = {
                scope.launch {
                    db.channelDao().updateFavorite(channel.id, !isFav)
                    selectedChannelForMenu = null
                }
            },
            onStartMultiView = {
                selectedChannelForMenu = null
                onStartMultiView?.invoke(channel)
            },
            onDismiss = { selectedChannelForMenu = null }
        )
    }
}

// =========================================================================
// SUBCOMPONENTS: Apple TV Sidebar Rail Items
// =========================================================================

@Composable
private fun TvSidebarRailItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    iconTint: Color = Color.White,
    count: Int? = null,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    rightFocusRequester: FocusRequester? = null,
    onMoveUp: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.03f else 1.0f, label = "railItemScale")

    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocused?.invoke()
        }
    }

    val bgColor = when {
        isFocused -> Color.White.copy(alpha = 0.96f)
        isSelected -> Color.White.copy(alpha = 0.16f)
        else -> Color.Transparent
    }

    val border = when {
        isSelected && !isFocused -> Color.White.copy(alpha = 0.22f)
        else -> Color.Transparent
    }

    val textColor = when {
        isFocused -> Color(0xFF0B0D12)
        isSelected -> Color.White
        else -> Color.White.copy(alpha = 0.82f)
    }

    val finalIconTint = when {
        isFocused -> Color(0xFF0B0D12)
        isSelected -> Color.White
        else -> Color.White.copy(alpha = 0.75f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .scale(scale)
            .clip(RoundedCornerShape(9.dp))
            .background(bgColor)
            .border(1.dp, border, RoundedCornerShape(9.dp))
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
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 3.5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = textColor,
            fontSize = 10.5.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )

        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = finalIconTint,
                modifier = Modifier.size(12.5.dp)
            )
        }
    }
}

// =========================================================================
// SUBCOMPONENTS: Layout Mode Switcher [ List | Guide ]
// =========================================================================

@Composable
private fun TvLayoutModeSwitcher(
    currentMode: LiveTVLayoutMode,
    onModeChange: (LiveTVLayoutMode) -> Unit,
    focusRequester: FocusRequester? = null,
    onMoveUpToTopBar: (() -> Unit)? = null,
    onMoveDownToChannels: (() -> Unit)? = null,
    onMoveLeftToSidebar: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.05f else 1.0f, label = "switcherScale")

    val listActive = currentMode == LiveTVLayoutMode.LIST
    val guideActive = currentMode == LiveTVLayoutMode.GUIDE

    Row(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) Color.White else Color.White.copy(alpha = 0.08f))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) LumeroidAccentBlue else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp)
            )
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionUp -> {
                            onMoveUpToTopBar?.invoke()
                            true
                        }
                        Key.DirectionDown -> {
                            onMoveDownToChannels?.invoke()
                            true
                        }
                        Key.DirectionLeft -> {
                            onMoveLeftToSidebar?.invoke()
                            true
                        }
                        Key.DirectionRight -> {
                            val nextMode = if (currentMode == LiveTVLayoutMode.GUIDE) LiveTVLayoutMode.LIST else LiveTVLayoutMode.GUIDE
                            onModeChange(nextMode)
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    val nextMode = if (currentMode == LiveTVLayoutMode.GUIDE) LiveTVLayoutMode.LIST else LiveTVLayoutMode.GUIDE
                    onModeChange(nextMode)
                }
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ViewAgenda,
                contentDescription = null,
                tint = when {
                    isFocused && listActive -> Color.Black
                    isFocused -> Color.Black.copy(alpha = 0.4f)
                    listActive -> LumeroidAccentBlue
                    else -> Color.White.copy(alpha = 0.4f)
                },
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "List",
                color = when {
                    isFocused && listActive -> Color.Black
                    isFocused -> Color.Black.copy(alpha = 0.4f)
                    listActive -> Color.White
                    else -> Color.White.copy(alpha = 0.4f)
                },
                fontSize = 12.sp,
                fontWeight = if (listActive) FontWeight.Bold else FontWeight.Medium
            )
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(14.dp)
                .background(if (isFocused) Color.Black.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.2f))
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.GridView,
                contentDescription = null,
                tint = when {
                    isFocused && guideActive -> Color.Black
                    isFocused -> Color.Black.copy(alpha = 0.4f)
                    guideActive -> LumeroidAccentBlue
                    else -> Color.White.copy(alpha = 0.4f)
                },
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Guide",
                color = when {
                    isFocused && guideActive -> Color.Black
                    isFocused -> Color.Black.copy(alpha = 0.4f)
                    guideActive -> Color.White
                    else -> Color.White.copy(alpha = 0.4f)
                },
                fontSize = 12.sp,
                fontWeight = if (guideActive) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

// =========================================================================
// SUBCOMPONENTS: Mode 1 - Scannable TV Channel Cards (TVChannelsList)
// =========================================================================

@Composable
private fun TvChannelsListView(
    channels: List<ChannelEntity>,
    epgDataMap: Map<String, ChannelEpgData>,
    isRecentlyWatched: Boolean,
    onClearRecentlyWatched: () -> Unit,
    onPlay: (ChannelEntity) -> Unit,
    onOpenMenu: (ChannelEntity) -> Unit,
    onFocusChannel: (ChannelEntity) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    firstItemFocusRequester: FocusRequester? = null,
    onFocusedRequester: ((FocusRequester) -> Unit)? = null,
    onMoveLeftToSidebar: (() -> Unit)? = null,
    onMoveUpToSwitcher: (() -> Unit)? = null
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp)
    ) {
        if (isRecentlyWatched) {
            item {
                TvClearRecentsButton(
                    onClear = onClearRecentlyWatched,
                    focusRequester = firstItemFocusRequester,
                    onMoveUp = onMoveUpToSwitcher,
                    onMoveLeft = onMoveLeftToSidebar
                )
            }
        }

        itemsIndexed(channels) { index, channel ->
            val epg = epgDataMap[channel.id]
            val isFirstItem = index == 0 && !isRecentlyWatched
            val cardRequester = remember { FocusRequester() }
            val effectiveRequester = if (isFirstItem && firstItemFocusRequester != null) firstItemFocusRequester else cardRequester
            TvChannelListCard(
                channel = channel,
                epg = epg,
                onPlay = { onPlay(channel) },
                onOpenMenu = { onOpenMenu(channel) },
                onFocused = {
                    onFocusChannel(channel)
                    onFocusedRequester?.invoke(effectiveRequester)
                },
                focusRequester = effectiveRequester,
                onMoveUp = if (isFirstItem) onMoveUpToSwitcher else null,
                onMoveLeft = onMoveLeftToSidebar
            )
        }
    }
}

@Composable
private fun TvClearRecentsButton(
    onClear: () -> Unit,
    focusRequester: FocusRequester? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveLeft: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.02f else 1.0f, label = "clearRecentsScale")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) Color(0xFFFF3B30) else Color.White.copy(alpha = 0.08f))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp)
            )
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when {
                        keyEvent.key == Key.DirectionUp && onMoveUp != null -> {
                            onMoveUp()
                            true
                        }
                        keyEvent.key == Key.DirectionLeft && onMoveLeft != null -> {
                            onMoveLeft()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClear)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Clear Recently Watched",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TvChannelListCard(
    channel: ChannelEntity,
    epg: ChannelEpgData?,
    onPlay: () -> Unit,
    onOpenMenu: () -> Unit,
    onFocused: () -> Unit = {},
    focusRequester: FocusRequester? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveLeft: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.025f else 1.0f, label = "chanCardScale")
    var isLongPress by remember { mutableStateOf(false) }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocused()
        }
    }

    val currentProgram = epg?.current
    val nextProgram = epg?.next
    val now = System.currentTimeMillis()
    val progress = if (currentProgram != null && currentProgram.endTime > currentProgram.startTime) {
        ((now - currentProgram.startTime).toFloat() / (currentProgram.endTime - currentProgram.startTime).toFloat()).coerceIn(0f, 1f)
    } else 0f

    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable(interactionSource = interactionSource)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isFocused) {
                    Brush.horizontalGradient(
                        listOf(Color(0xFF222B3D), Color(0xFF182030))
                    )
                } else {
                    Brush.horizontalGradient(
                        listOf(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.03f))
                    )
                }
            )
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.10f),
                shape = RoundedCornerShape(16.dp)
            )
            .onPreviewKeyEvent { keyEvent ->
                when (keyEvent.type) {
                    KeyEventType.KeyDown -> {
                        when {
                            keyEvent.key == Key.DirectionUp && onMoveUp != null -> {
                                onMoveUp()
                                true
                            }
                            keyEvent.key == Key.DirectionLeft && onMoveLeft != null -> {
                                onMoveLeft()
                                true
                            }
                            keyEvent.key in listOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter) -> {
                                if (keyEvent.nativeKeyEvent.repeatCount >= 1) {
                                    if (!isLongPress) {
                                        isLongPress = true
                                        onOpenMenu()
                                    }
                                    true
                                } else {
                                    isLongPress = false
                                    false
                                }
                            }
                            keyEvent.key in listOf(Key.Menu, Key.Help, Key.MediaPlayPause) -> {
                                onOpenMenu()
                                true
                            }
                            else -> false
                        }
                    }
                    KeyEventType.KeyUp -> {
                        if (keyEvent.key in listOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)) {
                            if (isLongPress) {
                                isLongPress = false
                                true
                            } else {
                                false
                            }
                        } else if (keyEvent.key in listOf(Key.Menu, Key.Help, Key.MediaPlayPause)) {
                            true
                        } else false
                    }
                    else -> false
                }
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Channel Logo / Number Box
        Box(
            modifier = Modifier
                .size(70.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF141720))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            if (channel.logoUrl.isNotBlank()) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = if (channel.channelNumber > 0) channel.channelNumber.toString() else "CH",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Center: Channel Name, Now Playing EPG, Progress bar & Next Up
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = channel.name,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (channel.isFavorite) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Favorite",
                        tint = Color(0xFFFF3B30),
                        modifier = Modifier.size(14.dp)
                    )
                }

                if (channel.catchupDays > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(LumeroidAccentBlue.copy(alpha = 0.25f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ARCHIVE ${channel.catchupDays}d",
                            color = LumeroidAccentBlue,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (currentProgram != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = currentProgram.title,
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${timeFmt.format(Date(currentProgram.startTime))} – ${timeFmt.format(Date(currentProgram.endTime))}",
                        color = LumeroidTextSecondary,
                        fontSize = 11.sp
                    )
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = LumeroidAccentBlue,
                    trackColor = Color.White.copy(alpha = 0.15f)
                )
            } else {
                Text(
                    text = "Live Stream Broadcast",
                    color = LumeroidTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            if (nextProgram != null) {
                Text(
                    text = "Next: ${nextProgram.title} (${timeFmt.format(Date(nextProgram.startTime))})",
                    color = Color.White.copy(alpha = 0.60f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Action Trigger Button / Options
        val menuInteractionSource = remember { MutableInteractionSource() }
        val isMenuFocused by menuInteractionSource.collectIsFocusedAsState()
        val menuScale by animateFloatAsState(targetValue = if (isMenuFocused) 1.15f else 1.0f, label = "menuBtnScale")

        Box(
            modifier = Modifier
                .scale(menuScale)
                .clip(CircleShape)
                .background(
                    when {
                        isMenuFocused -> Color.White
                        isFocused -> LumeroidAccentBlue
                        else -> Color.White.copy(alpha = 0.08f)
                    }
                )
                .border(
                    width = if (isMenuFocused) 2.dp else 0.dp,
                    color = if (isMenuFocused) LumeroidAccentBlue else Color.Transparent,
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = menuInteractionSource,
                    indication = null,
                    onClick = onOpenMenu
                )
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = if (isMenuFocused) Color.Black else Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// =========================================================================
// SUBCOMPONENTS: Live Video Hero Preview Pane (EPGPreviewPane / EPGHeroVideoFigure)
// =========================================================================

@Composable
private fun TvGuideHeroPreviewPane(
    focusedChannel: ChannelEntity?,
    focusedProgram: EpgListingEntity?,
    epgData: ChannelEpgData?,
    onPlay: (ChannelEntity) -> Unit
) {
    val context = LocalContext.current
    var activeChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    var isBuffering by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }

    // Settle debounce matching Apple TV GuidePreviewPolicy.settleDuration (800ms)
    LaunchedEffect(focusedChannel?.id) {
        isBuffering = true
        isError = false
        if (focusedChannel == null) {
            activeChannel = null
            return@LaunchedEffect
        }
        delay(800)
        activeChannel = focusedChannel
    }

    // Dedicated muted ExoPlayer instance for background Live preview
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            volume = 0f // MUST be muted for guide preview
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = (playbackState == Player.STATE_BUFFERING)
            }
            override fun onPlayerError(error: PlaybackException) {
                isError = true
                isBuffering = false
            }
            override fun onRenderedFirstFrame() {
                isBuffering = false
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    LaunchedEffect(activeChannel?.streamUrl) {
        val stream = activeChannel?.streamUrl
        if (!stream.isNullOrBlank()) {
            try {
                isError = false
                isBuffering = true
                exoPlayer.setMediaItem(MediaItem.fromUri(stream))
                exoPlayer.prepare()
                exoPlayer.play()
            } catch (_: Exception) {
                isError = true
            }
        } else {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Program Metadata & Progress Info (EPGHeroInfo.swift)
        TvHeroInfoBlock(
            channel = activeChannel ?: focusedChannel,
            program = focusedProgram ?: epgData?.current,
            nextProgram = epgData?.next,
            modifier = Modifier
                .weight(1f)
                .padding(end = 20.dp)
        )

        // Right: 16:9 Muted Live Video Preview Surface (EPGHeroVideoFigure.swift)
        TvHeroVideoFigure(
            channel = activeChannel,
            isBuffering = isBuffering,
            isError = isError,
            exoPlayer = exoPlayer,
            onClick = {
                val target = activeChannel ?: focusedChannel
                if (target != null) onPlay(target)
            }
        )
    }
}

@Composable
private fun TvHeroInfoBlock(
    channel: ChannelEntity?,
    program: EpgListingEntity?,
    nextProgram: EpgListingEntity?,
    modifier: Modifier = Modifier
) {
    val now = System.currentTimeMillis()
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.Center
    ) {
        // Header: Logo Tile + Channel Name + LIVE pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFF232730)),
                contentAlignment = Alignment.Center
            ) {
                if (!channel?.logoUrl.isNullOrBlank()) {
                    SubcomposeAsyncImage(
                        model = channel?.logoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(18.dp),
                        loading = {
                            Icon(
                                imageVector = Icons.Default.Podcasts,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.70f),
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        error = {
                            Icon(
                                imageVector = Icons.Default.Podcasts,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.70f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Podcasts,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.70f),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Text(
                text = channel?.name ?: "Live Channel",
                color = Color.White.copy(alpha = 0.90f),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFFFF3B30).copy(alpha = 0.14f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF3B30))
                    )
                    Text(
                        text = "LIVE",
                        color = Color(0xFFFF3B30),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Program Title / "No programme information" (EPGHeroInfo.swift)
        Text(
            text = program?.title ?: "No programme information",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Progress bar and Up next (EPGHeroInfo.swift)
        val progValid = program != null && program.endTime > program.startTime && program.endTime > now && program.startTime <= now
        if (progValid && program != null) {
            Spacer(modifier = Modifier.height(6.dp))
            val progress = ((now - program.startTime).toFloat() / (program.endTime - program.startTime).toFloat()).coerceIn(0f, 1f)
            val minutesLeft = ((program.endTime - now) / 60000L).coerceAtLeast(1L)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = progress)
                            .clip(CircleShape)
                            .background(Color(0xFF5AA9FF))
                    )
                }
                Text(
                    text = "$minutesLeft min left",
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false
                )
            }

            if (nextProgram != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Up next · ${timeFmt.format(Date(nextProgram.startTime))} ${nextProgram.title}",
                    color = Color.White.copy(alpha = 0.58f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TvHeroVideoFigure(
    channel: ChannelEntity?,
    isBuffering: Boolean,
    isError: Boolean,
    exoPlayer: ExoPlayer,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = 178.dp, height = 100.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black)
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (channel != null && !isError) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Subtle cinematic bottom gradient overlay matching Apple TV EPGHeroVideoFigure.swift
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.60f))
                    )
                )
        )

        if (isBuffering && channel != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.40f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 2.5.dp
                )
            }
        }
    }
}

@Composable
private fun TvHeroPlaceholderCard(channel: ChannelEntity?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF161C2A),
                        Color(0xFF0C0F17)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (channel != null && channel.logoUrl.isNotBlank()) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = channel.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = LumeroidAccentBlue.copy(alpha = 0.65f),
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = channel?.name ?: "Live Preview",
                    color = Color.White.copy(alpha = 0.50f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// =========================================================================
// SUBCOMPONENTS: Mode 2 - Extended EPG Timeline Guide (EPGGuideView / EPGGrid)
// =========================================================================

data class TvEpgCell(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val startTime: Long,
    val endTime: Long,
    val isGap: Boolean = false,
    val isLive: Boolean = false,
    val canReplay: Boolean = false,
    val originalListing: EpgListingEntity? = null
)

private fun sliceGapIntoHourCells(
    channel: ChannelEntity,
    gapStart: Long,
    gapEnd: Long,
    now: Long
): List<TvEpgCell> {
    if (gapEnd <= gapStart) return emptyList()
    val cells = mutableListOf<TvEpgCell>()
    val oneHourMs = 60 * 60 * 1000L

    var cur = gapStart
    while (cur < gapEnd) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = cur
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.HOUR_OF_DAY, 1)
        }
        var nextHour = cal.timeInMillis
        if (nextHour - cur < 15 * 60 * 1000L) {
            nextHour += oneHourMs
        }
        val slotEnd = minOf(nextHour, gapEnd)

        cells.add(
            TvEpgCell(
                id = "gap-${channel.id}-$cur-$slotEnd",
                title = "No Programme",
                subtitle = "",
                startTime = cur,
                endTime = slotEnd,
                isGap = true,
                isLive = (cur <= now && now < slotEnd),
                canReplay = false,
                originalListing = null
            )
        )
        cur = slotEnd
    }
    return cells
}

private fun formatEpgTimeSpan(startMs: Long, endMs: Long): String {
    val calStart = Calendar.getInstance().apply { timeInMillis = startMs }
    val calEnd = Calendar.getInstance().apply { timeInMillis = endMs }
    val sameAmPm = calStart.get(Calendar.AM_PM) == calEnd.get(Calendar.AM_PM)
    val sameDay = calStart.get(Calendar.DAY_OF_YEAR) == calEnd.get(Calendar.DAY_OF_YEAR)

    val fmtHourMin = SimpleDateFormat("h:mm", Locale.getDefault())
    val fmtFull = SimpleDateFormat("h:mm a", Locale.getDefault())

    val startStr = if (sameAmPm && sameDay) fmtHourMin.format(Date(startMs)) else fmtFull.format(Date(startMs))
    val endStr = fmtFull.format(Date(endMs))
    return "$startStr–$endStr"
}

private fun buildTimelineCells(
    channel: ChannelEntity,
    listings: List<EpgListingEntity>,
    timelineStart: Long,
    timelineEnd: Long,
    now: Long
): List<TvEpgCell> {
    val minDurationMs = 60 * 1000L
    val validListings = listings
        .filter { it.endTime > timelineStart && it.startTime < timelineEnd }
        .sortedBy { it.startTime }

    if (validListings.isEmpty()) {
        return sliceGapIntoHourCells(channel, timelineStart, timelineEnd, now)
    }

    val cells = mutableListOf<TvEpgCell>()
    var cursor = timelineStart

    for (listing in validListings) {
        val clampedEnd = minOf(listing.endTime, timelineEnd)
        if (clampedEnd <= cursor) continue

        val clampedStart = maxOf(maxOf(listing.startTime, timelineStart), cursor)
        if (clampedEnd - clampedStart < minDurationMs) continue

        if (clampedStart > cursor) {
            cells.addAll(sliceGapIntoHourCells(channel, cursor, clampedStart, now))
        }

        val isLive = clampedStart <= now && now < clampedEnd
        val isPast = clampedEnd <= now
        val canReplay = channel.catchupDays > 0 && isPast

        cells.add(
            TvEpgCell(
                id = "${channel.id}-${listing.id}",
                title = listing.title,
                subtitle = listing.description.ifBlank { "Broadcast" },
                startTime = clampedStart,
                endTime = clampedEnd,
                isGap = false,
                isLive = isLive,
                canReplay = canReplay,
                originalListing = listing
            )
        )
        cursor = clampedEnd
    }

    if (cursor < timelineEnd) {
        cells.addAll(sliceGapIntoHourCells(channel, cursor, timelineEnd, now))
    }

    return cells
}

@Composable
private fun TvEpgGuideView(
    channels: List<ChannelEntity>,
    epgDataMap: Map<String, ChannelEpgData>,
    onPlay: (ChannelEntity) -> Unit,
    onPlayCatchup: (ChannelEntity, EpgListingEntity) -> Unit,
    onOpenMenu: (ChannelEntity) -> Unit,
    onFocusChannel: (ChannelEntity) -> Unit,
    onFocusProgram: (EpgListingEntity?) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    firstItemFocusRequester: FocusRequester? = null,
    onFocusedRequester: ((FocusRequester) -> Unit)? = null,
    onMoveLeftToSidebar: (() -> Unit)? = null,
    onMoveUpToSwitcher: (() -> Unit)? = null
) {
    val timelineScrollState = rememberScrollState()
    val density = LocalDensity.current
    val now = remember { System.currentTimeMillis() }
    val pointsPerMinute = 4.5f

    // Timeline anchors: 2 hours in the past up to 24 hours into the future (26h total horizon)
    val (timelineStart, timelineEnd) = remember(now) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now - 2 * 3600 * 1000L
            val min = get(Calendar.MINUTE)
            set(Calendar.MINUTE, if (min < 30) 0 else 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        val end = start + 26 * 3600 * 1000L
        Pair(start, end)
    }

    val totalTimelineMinutes = ((timelineEnd - timelineStart) / 60000L).coerceAtLeast(1L)
    val totalTimelineWidth = (totalTimelineMinutes * pointsPerMinute).dp

    // Dynamic 30-minute ticks across the entire 26-hour timeline
    val timeTicks = remember(timelineStart, timelineEnd) {
        val ticks = mutableListOf<Long>()
        var cur = timelineStart
        while (cur <= timelineEnd) {
            ticks.add(cur)
            cur += 30 * 60 * 1000L
        }
        ticks
    }

    val timeTickFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val curTimeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }
    val nowOffsetDp = (((now - timelineStart) / 60000f) * pointsPerMinute).dp

    var hasAutoScrolled by remember { mutableStateOf(false) }
    LaunchedEffect(timelineStart, channels.isNotEmpty()) {
        if (!hasAutoScrolled && channels.isNotEmpty()) {
            snapshotFlow { timelineScrollState.maxValue }
                .filter { it > 0 && it != Int.MAX_VALUE }
                .first()
            val leadInMinutes = 10f
            val nowMinutes = ((now - timelineStart) / 60000f) - leadInMinutes
            val targetScrollPx = with(density) { (nowMinutes * pointsPerMinute).dp.toPx() }.coerceAtLeast(0f)
            timelineScrollState.scrollTo(targetScrollPx.toInt().coerceAtMost(timelineScrollState.maxValue))
            hasAutoScrolled = true
        }
    }

    val scrollOffsetDp = with(density) { timelineScrollState.value.toDp() }

    Column(modifier = Modifier.fillMaxSize()) {
        // Time Ruler Header (Pinned Corner Date + Horizontally Scrollable Timeline Ticks)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pinned corner above channel column: "Today · Thu, Oct 22" (Apple TV style)
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "Today · ${dateFmt.format(Date(now))}",
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Horizontally scrollable ruler ticks
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(timelineScrollState)
            ) {
                Row(
                    modifier = Modifier
                        .width(totalTimelineWidth)
                        .fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    timeTicks.forEach { tickTime ->
                        val tickStr = timeTickFmt.format(Date(tickTime))
                        val minutesDiff = (tickTime - now) / 60000f
                        val isCoveredByNowPill = minutesDiff > -24f && minutesDiff < 14f

                        Box(
                            modifier = Modifier
                                .width((30 * pointsPerMinute).dp)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (!isCoveredByNowPill) {
                                Text(
                                    text = tickStr,
                                    color = Color.White.copy(alpha = 0.62f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Apple TV Blue Capsule Badge for Current Time (LiveTVPalette.accent #6DA7F8)
                Box(
                    modifier = Modifier
                        .offset(x = (nowOffsetDp - 26.dp).coerceAtLeast(0.dp))
                        .clip(CircleShape)
                        .background(Color(0xFF6DA7F8))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = curTimeFmt.format(Date(now)),
                        color = Color(0xFF0C1725),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Channels Grid Area with Overlay Vertical Current Time Line
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(top = 2.dp, bottom = 6.dp)
            ) {
                itemsIndexed(channels, key = { _, c -> c.id }) { index, channel ->
                    val progs = epgDataMap[channel.id]?.programs ?: emptyList()
                    val cells = remember(progs, timelineStart, timelineEnd) {
                        buildTimelineCells(channel, progs, timelineStart, timelineEnd, now)
                    }
                    val badgeRequester = remember { FocusRequester() }
                    val effectiveRequester = if (index == 0 && firstItemFocusRequester != null) firstItemFocusRequester else badgeRequester

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pinned Channel Badge (Does NOT scroll horizontally)
                        TvGuideChannelBadge(
                            channel = channel,
                            modifier = Modifier.width(160.dp),
                            onPlay = { onPlay(channel) },
                            onOpenMenu = { onOpenMenu(channel) },
                            onFocused = {
                                onFocusChannel(channel)
                                onFocusProgram(null)
                                onFocusedRequester?.invoke(effectiveRequester)
                            },
                            focusRequester = effectiveRequester,
                            onMoveUp = if (index == 0) onMoveUpToSwitcher else null,
                            onMoveLeft = onMoveLeftToSidebar
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Horizontally scrollable program schedule cells
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(timelineScrollState),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            cells.forEach { cell ->
                                TvEpgCellBlock(
                                    cell = cell,
                                    pointsPerMinute = pointsPerMinute,
                                    timelineStart = timelineStart,
                                    scrollOffsetDp = scrollOffsetDp,
                                    onClick = {
                                        if (cell.canReplay && cell.originalListing != null) {
                                             onPlayCatchup(channel, cell.originalListing)
                                        } else {
                                            onPlay(channel)
                                        }
                                    },
                                    onOpenMenu = { onOpenMenu(channel) },
                                    onFocused = {
                                        onFocusChannel(channel)
                                        onFocusProgram(cell.originalListing)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Apple TV Vertical Blue Current Time Line descending across all grid rows
            val lineX = nowOffsetDp - scrollOffsetDp
            if (lineX >= 0.dp) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 166.dp)
                        .clipToBounds()
                ) {
                    Box(
                        modifier = Modifier
                            .offset(x = lineX)
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(Color(0xFF5AA9FF).copy(alpha = 0.85f))
                    )
                }
            }
        }

        // Apple TV Bottom Hint Bar: ⊙ Hold a channel to add it to Favorites or Multi-View, or to record it
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.RadioButtonChecked,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = "Hold a channel to add it to Favorites or Multi-View, or to record it",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@Composable
private fun TvGuideChannelBadge(
    channel: ChannelEntity,
    modifier: Modifier = Modifier,
    onPlay: () -> Unit,
    onOpenMenu: () -> Unit,
    onFocused: () -> Unit = {},
    focusRequester: FocusRequester? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveLeft: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.025f else 1.0f, label = "badgeScale")
    var isLongPress by remember { mutableStateOf(false) }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocused()
        }
    }

    Row(
        modifier = modifier
            .fillMaxHeight()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable(interactionSource = interactionSource)
            .scale(scale)
            .clip(RoundedCornerShape(9.dp))
            .background(if (isFocused) Color.White.copy(alpha = 0.96f) else Color.White.copy(alpha = 0.05f))
            .border(
                width = 1.dp,
                color = if (isFocused) Color.Transparent else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(9.dp)
            )
            .onPreviewKeyEvent { keyEvent ->
                when (keyEvent.type) {
                    KeyEventType.KeyDown -> {
                        when {
                            keyEvent.key == Key.DirectionUp && onMoveUp != null -> {
                                onMoveUp()
                                true
                            }
                            keyEvent.key == Key.DirectionLeft && onMoveLeft != null -> {
                                onMoveLeft()
                                true
                            }
                            keyEvent.key in listOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter) -> {
                                if (keyEvent.nativeKeyEvent.repeatCount >= 1) {
                                    if (!isLongPress) {
                                        isLongPress = true
                                        onOpenMenu()
                                    }
                                    true
                                } else {
                                    isLongPress = false
                                    false
                                }
                            }
                            keyEvent.key in listOf(Key.Menu, Key.Help, Key.MediaPlayPause) -> {
                                onOpenMenu()
                                true
                            }
                            else -> false
                        }
                    }
                    KeyEventType.KeyUp -> {
                        if (keyEvent.key in listOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)) {
                            if (isLongPress) {
                                isLongPress = false
                                true
                            } else {
                                false
                            }
                        } else if (keyEvent.key in listOf(Key.Menu, Key.Help, Key.MediaPlayPause)) {
                            true
                        } else false
                    }
                    else -> false
                }
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onPlay)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (isFocused) Color.Black.copy(alpha = 0.08f) else Color(0xFF282828)),
            contentAlignment = Alignment.Center
        ) {
            if (channel.logoUrl.isNotBlank()) {
                SubcomposeAsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(18.dp),
                    error = {
                        Icon(
                            imageVector = Icons.Default.Podcasts,
                            contentDescription = null,
                            tint = if (isFocused) Color.Black else Color.White.copy(alpha = 0.70f),
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    loading = {
                        Icon(
                            imageVector = Icons.Default.Podcasts,
                            contentDescription = null,
                            tint = if (isFocused) Color.Black else Color.White.copy(alpha = 0.70f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Podcasts,
                    contentDescription = null,
                    tint = if (isFocused) Color.Black else Color.White.copy(alpha = 0.70f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Text(
            text = channel.name,
            color = if (isFocused) Color(0xFF0B0D12) else Color.White,
            fontSize = 11.5.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TvEpgCellBlock(
    cell: TvEpgCell,
    pointsPerMinute: Float,
    timelineStart: Long,
    scrollOffsetDp: Dp,
    onClick: () -> Unit,
    onOpenMenu: () -> Unit,
    onFocused: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.025f else 1.0f, label = "cellScale")
    var isLongPress by remember { mutableStateOf(false) }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocused()
        }
    }

    val durationMin = ((cell.endTime - cell.startTime) / 60000L).coerceAtLeast(1L)
    val cellWidthDp = (durationMin * pointsPerMinute).dp.coerceAtLeast(80.dp)
    val timeSpan = remember(cell.startTime, cell.endTime) {
        formatEpgTimeSpan(cell.startTime, cell.endTime)
    }

    // Sticky Text shift calculation matching Apple TV EPGStickyText.swift
    val blockMinX = ((cell.startTime - timelineStart) / 60000f * pointsPerMinute).dp - scrollOffsetDp
    val textShiftX = if (blockMinX < 0.dp) {
        (-blockMinX).coerceAtMost((cellWidthDp - 28.dp).coerceAtLeast(0.dp))
    } else {
        0.dp
    }

    val isRealProgram = !cell.isGap
    val backgroundColor = when {
        isFocused && isRealProgram -> Color.White.copy(alpha = 0.96f)
        isFocused && cell.isGap -> Color.White.copy(alpha = 0.18f)
        isRealProgram -> Color.White.copy(alpha = 0.08f)
        else -> Color.White.copy(alpha = 0.04f)
    }
    val borderColor = when {
        isFocused && isRealProgram -> Color.Transparent
        isFocused && cell.isGap -> Color.White.copy(alpha = 0.20f)
        else -> Color.White.copy(alpha = 0.08f)
    }

    Box(
        modifier = Modifier
            .width(cellWidthDp)
            .fillMaxHeight()
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .onPreviewKeyEvent { keyEvent ->
                when (keyEvent.type) {
                    KeyEventType.KeyDown -> {
                        when {
                            keyEvent.key in listOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter) -> {
                                if (keyEvent.nativeKeyEvent.repeatCount >= 1) {
                                    if (!isLongPress) {
                                        isLongPress = true
                                        onOpenMenu()
                                    }
                                    true
                                } else {
                                    isLongPress = false
                                    false
                                }
                            }
                            keyEvent.key in listOf(Key.Menu, Key.Help, Key.MediaPlayPause) -> {
                                onOpenMenu()
                                true
                            }
                            else -> false
                        }
                    }
                    KeyEventType.KeyUp -> {
                        if (keyEvent.key in listOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)) {
                            if (isLongPress) {
                                isLongPress = false
                                true
                            } else {
                                false
                            }
                        } else if (keyEvent.key in listOf(Key.Menu, Key.Help, Key.MediaPlayPause)) {
                            true
                        } else false
                    }
                    else -> false
                }
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.CenterStart
    ) {
        if (cell.isGap) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = textShiftX)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = cell.title,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.50f),
                    fontSize = 12.sp,
                    fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = textShiftX)
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
            ) {
                Text(
                    text = cell.title,
                    color = if (isFocused) Color(0xFF0B0D12) else Color.White,
                    fontSize = 11.5.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (cell.canReplay) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Catchup Replay",
                            tint = if (isFocused) Color(0xFF3A404C) else Color(0xFF5AA9FF),
                            modifier = Modifier.size(9.dp)
                        )
                    }
                    Text(
                        text = timeSpan,
                        color = if (isFocused) Color(0xFF3A404C) else Color.White.copy(alpha = 0.60f),
                        fontSize = 9.5.sp,
                        lineHeight = 11.sp
                    )
                }
            }
        }
    }
}

// =========================================================================
// ACTION SHEET: Live Channel Menu (Favorite, Multi-View, Info)
// =========================================================================

@Composable
private fun TvLiveChannelActionSheet(
    channel: ChannelEntity,
    currentEpg: EpgListingEntity?,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onStartMultiView: () -> Unit,
    onDismiss: () -> Unit
) {
    val firstBtnFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        try {
            firstBtnFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF161A24))
                .border(1.5.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp))
                .padding(28.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with Logo
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0D1017))
                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (channel.logoUrl.isNotBlank()) {
                        AsyncImage(
                            model = channel.logoUrl,
                            contentDescription = channel.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = if (channel.channelNumber > 0) channel.channelNumber.toString() else "CH",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = channel.name,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                if (currentEpg != null) {
                    Text(
                        text = "Now Airing: ${currentEpg.title}",
                        color = LumeroidCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Action 1: Play Channel
                TvActionSheetButton(
                    title = "Play Channel",
                    icon = Icons.Default.PlayArrow,
                    focusRequester = firstBtnFocusRequester,
                    onClick = onPlay
                )

                // Action 2: Toggle Favorite
                TvActionSheetButton(
                    title = if (channel.isFavorite) "Remove from Favorites" else "Add to Favorites",
                    icon = Icons.Default.Favorite,
                    iconTint = if (channel.isFavorite) Color(0xFFFF3B30) else Color.White,
                    onClick = onToggleFavorite
                )

                // Action 3: Start Multi-View
                TvActionSheetButton(
                    title = "Start in Multi-View",
                    icon = Icons.Default.GridView,
                    onClick = onStartMultiView
                )

                // Action 4: Close
                TvActionSheetButton(
                    title = "Close",
                    icon = Icons.Default.Close,
                    onClick = onDismiss
                )
            }
        }
    }
}

@Composable
private fun TvActionSheetButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color = Color.White,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.03f else 1.0f, label = "actionBtnScale")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) Color.White else Color.White.copy(alpha = 0.08f))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) LumeroidAccentBlue else Color.White.copy(alpha = 0.10f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isFocused) Color.Black else iconTint,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            color = if (isFocused) Color.Black else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
