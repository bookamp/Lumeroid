package org.getlumeroid.android.ui.tv

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.getlumeroid.android.LumeroidApp
import org.getlumeroid.android.core.data.entities.ChannelEntity
import org.getlumeroid.android.core.data.entities.EpgListingEntity
import org.getlumeroid.android.core.data.entities.WatchProgressEntity
import org.getlumeroid.android.core.designsystem.*
import org.getlumeroid.android.core.player.*
import org.getlumeroid.android.core.settings.AppSettings
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TvPlayerScreen(
    media: PlayableMedia,
    channelLineup: List<ChannelEntity> = emptyList(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val db = LumeroidApp.instance.database
    val coroutineScope = rememberCoroutineScope()
    val playerManager = remember { LumeroidPlayerManager(context) }

    var currentMedia by remember { mutableStateOf(media) }
    var currentLineup by remember { mutableStateOf(if (media.channelLineup.isNotEmpty()) media.channelLineup else channelLineup) }

    var showHud by remember { mutableStateOf(true) }
    val isPlayerPlaying by playerManager.isPlaying.collectAsState()
    var isPlaying by remember { mutableStateOf(true) }

    LaunchedEffect(isPlayerPlaying) {
        isPlaying = isPlayerPlaying
    }

    var currentPositionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(media.durationMs) }
    var boundaryNotice by remember { mutableStateOf<String?>(null) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

    var showAudioTrackDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    val audioTracks by playerManager.audioTracks.collectAsState()
    val subtitleTracks by playerManager.subtitleTracks.collectAsState()
    var selectedAudioIndex by remember { mutableStateOf(0) }
    var selectedSubtitleIndex by remember { mutableStateOf(-1) }

    val playerFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }

    fun resetHudTimer() {
        lastInteractionTime = System.currentTimeMillis()
        showHud = true
    }

    fun handleTogglePlay() {
        val target = playerManager.togglePlayPause()
        isPlaying = target
        resetHudTimer()
    }

    // Auto-fetch category channels if launched from outside Live TV and lineup is empty
    LaunchedEffect(currentMedia.id, currentMedia.categoryId) {
        if (currentMedia.isLive && currentLineup.isEmpty() && currentMedia.categoryId.isNotBlank()) {
            withContext(Dispatchers.IO) {
                val activePl = db.playlistDao().getActivePlaylist()
                val plId = activePl?.id ?: ""
                val catList = db.channelDao().getAllChannels(plId).filter { it.categoryId == currentMedia.categoryId }
                if (catList.isNotEmpty()) {
                    currentLineup = catList
                }
            }
        }
    }

    // Active channel lookup within current lineup
    val currentChan = remember(currentLineup, currentMedia) {
        currentLineup.find { it.id == currentMedia.id || it.streamUrl == currentMedia.streamUrl }
    }
    val currentIndex = remember(currentChan, currentLineup) {
        currentChan?.let { c -> currentLineup.indexOfFirst { it.id == c.id } } ?: -1
    }
    val canChannelUp = remember(currentChan, currentLineup) {
        currentChan?.let { LiveChannelNavigator.canChannelUp(it, currentLineup) } ?: false
    }
    val canChannelDown = remember(currentChan, currentLineup) {
        currentChan?.let { LiveChannelNavigator.canChannelDown(it, currentLineup) } ?: false
    }
    val prevChannel = remember(currentChan, currentLineup) {
        currentChan?.let { LiveChannelNavigator.nextChannel(it, currentLineup, RemoteDirectionGate.Direction.UP) }
    }
    val nextChannel = remember(currentChan, currentLineup) {
        currentChan?.let { LiveChannelNavigator.nextChannel(it, currentLineup, RemoteDirectionGate.Direction.DOWN) }
    }

    // Live EPG program resolution
    var currentEpg by remember { mutableStateOf<EpgListingEntity?>(null) }
    LaunchedEffect(currentMedia.id, currentChan?.tvgId) {
        if (currentMedia.isLive) {
            withContext(Dispatchers.IO) {
                val tvgId = currentChan?.tvgId?.ifBlank { currentChan.streamId } ?: ""
                if (tvgId.isNotBlank()) {
                    currentEpg = db.epgDao().getCurrentProgram(tvgId, System.currentTimeMillis())
                }
            }
        }
    }

    // Live Favorite state
    var isFavorite by remember { mutableStateOf(currentChan?.isFavorite ?: false) }
    LaunchedEffect(currentChan?.id, currentChan?.isFavorite) {
        isFavorite = currentChan?.isFavorite ?: false
    }

    // Screen lifecycle: release ExoPlayer only on exit of the player screen
    DisposableEffect(Unit) {
        onDispose {
            val finalPos = try { playerManager.player.currentPosition } catch (_: Exception) { currentPositionMs }
            val finalDur = try { playerManager.player.duration } catch (_: Exception) { durationMs }
            if (!currentMedia.isLive && finalPos > 3000L) {
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    val progressContentId = if (currentMedia.contentType == "episode" && currentMedia.seriesId.isNotBlank()) {
                        currentMedia.seriesId
                    } else {
                        currentMedia.id
                    }
                    val progress = WatchProgressEntity(
                        contentId = progressContentId,
                        profileId = AppSettings.get().activeProfileId,
                        title = currentMedia.title,
                        posterUrl = currentMedia.posterUrl,
                        contentType = currentMedia.contentType.ifBlank { "movie" },
                        positionMs = finalPos,
                        durationMs = finalDur.coerceAtLeast(1L),
                        isCompleted = finalDur > 0 && finalPos > (finalDur * 0.90)
                    )
                    db.watchProgressDao().saveProgress(progress)
                    org.getlumeroid.android.core.data.BrowseCache.refreshRecentlyWatched()
                }
            }
            playerManager.release()
        }
    }

    // Direct playback when currentMedia changes
    LaunchedEffect(currentMedia) {
        playerManager.playMedia(currentMedia)
        isPlaying = true
        if (currentMedia.isLive) {
            coroutineScope.launch(Dispatchers.IO) {
                val progress = WatchProgressEntity(
                    contentId = currentMedia.id,
                    profileId = AppSettings.get().activeProfileId,
                    title = currentMedia.title,
                    posterUrl = currentMedia.posterUrl,
                    contentType = "channel",
                    positionMs = 0L,
                    durationMs = 0L,
                    lastWatchedAt = System.currentTimeMillis(),
                    isCompleted = false
                )
                db.watchProgressDao().saveProgress(progress)
            }
        }
    }

    // Ticker for playback progress and periodic auto-save
    LaunchedEffect(currentMedia) {
        var tickCount = 0
        while (true) {
            delay(1000)
            if (!currentMedia.isLive) {
                currentPositionMs = playerManager.player.currentPosition
                if (playerManager.player.duration > 0) {
                    durationMs = playerManager.player.duration
                }
                tickCount++
                if (tickCount % 5 == 0 && currentPositionMs > 3000L) {
                    withContext(Dispatchers.IO) {
                        val progressContentId = if (currentMedia.contentType == "episode" && currentMedia.seriesId.isNotBlank()) {
                            currentMedia.seriesId
                        } else {
                            currentMedia.id
                        }
                        val progress = WatchProgressEntity(
                            contentId = progressContentId,
                            profileId = AppSettings.get().activeProfileId,
                            title = currentMedia.title,
                            posterUrl = currentMedia.posterUrl,
                            contentType = currentMedia.contentType.ifBlank { "movie" },
                            positionMs = currentPositionMs,
                            durationMs = durationMs.coerceAtLeast(1L),
                            isCompleted = durationMs > 0 && currentPositionMs > (durationMs * 0.90)
                        )
                        db.watchProgressDao().saveProgress(progress)
                        org.getlumeroid.android.core.data.BrowseCache.refreshRecentlyWatched()
                    }
                }
            }
        }
    }

    // Boundary Notice auto-clear
    LaunchedEffect(boundaryNotice) {
        if (boundaryNotice != null) {
            delay(2800)
            boundaryNotice = null
        }
    }

    // Auto-hide HUD after 5s of inactivity while playing
    LaunchedEffect(showHud, lastInteractionTime, isPlaying, showAudioTrackDialog, showSubtitleDialog) {
        if (showHud && isPlaying && !showAudioTrackDialog && !showSubtitleDialog) {
            delay(5000)
            showHud = false
        }
    }

    // Focus switching when HUD visibility toggles
    LaunchedEffect(showHud) {
        delay(50)
        try {
            if (showHud) {
                playPauseFocusRequester.requestFocus()
            } else {
                playerFocusRequester.requestFocus()
            }
        } catch (_: Exception) {}
    }

    // Unified Back Button Handling
    BackHandler {
        when {
            showAudioTrackDialog -> showAudioTrackDialog = false
            showSubtitleDialog -> showSubtitleDialog = false
            showHud -> showHud = false
            else -> onBack()
        }
    }

    fun triggerChannelUp() {
        if (currentMedia.isLive && currentLineup.isNotEmpty()) {
            val chan = currentChan ?: currentLineup.first()
            val nextChan = LiveChannelNavigator.nextChannel(
                current = chan,
                lineup = currentLineup,
                direction = RemoteDirectionGate.Direction.UP
            )
            if (nextChan != null) {
                currentMedia = nextChan.toPlayableMedia(lineup = currentLineup, categoryName = currentMedia.categoryName)
                boundaryNotice = null
                resetHudTimer()
            } else {
                boundaryNotice = "Start of ${currentMedia.categoryName.ifBlank { "category" }} (Channel Up disabled)"
                resetHudTimer()
            }
        }
    }

    fun triggerChannelDown() {
        if (currentMedia.isLive && currentLineup.isNotEmpty()) {
            val chan = currentChan ?: currentLineup.first()
            val nextChan = LiveChannelNavigator.nextChannel(
                current = chan,
                lineup = currentLineup,
                direction = RemoteDirectionGate.Direction.DOWN
            )
            if (nextChan != null) {
                currentMedia = nextChan.toPlayableMedia(lineup = currentLineup, categoryName = currentMedia.categoryName)
                boundaryNotice = null
                resetHudTimer()
            } else {
                boundaryNotice = "End of ${currentMedia.categoryName.ifBlank { "category" }} (Channel Down disabled)"
                resetHudTimer()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(playerFocusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            handleTogglePlay()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            playerManager.player.playWhenReady = true
                            isPlaying = true
                            resetHudTimer()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            playerManager.player.playWhenReady = false
                            isPlaying = false
                            resetHudTimer()
                            true
                        }
                        KeyEvent.KEYCODE_CHANNEL_UP -> {
                            if (currentMedia.isLive) {
                                triggerChannelUp()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                            if (currentMedia.isLive) {
                                triggerChannelDown()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (!showHud) {
                                resetHudTimer()
                                true
                            } else if (currentMedia.isLive) {
                                triggerChannelUp()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (!showHud) {
                                resetHudTimer()
                                true
                            } else if (currentMedia.isLive) {
                                triggerChannelDown()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (!currentMedia.isLive) {
                                playerManager.seekRelative(-10000L)
                                currentPositionMs = playerManager.player.currentPosition
                                resetHudTimer()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (!currentMedia.isLive) {
                                playerManager.seekRelative(10000L)
                                currentPositionMs = playerManager.player.currentPosition
                                resetHudTimer()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            if (!showHud) {
                                resetHudTimer()
                                true
                            } else {
                                // Default transport action when HUD is visible: toggle play/pause
                                handleTogglePlay()
                                true
                            }
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // Media3 Video Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = playerManager.player
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Apple TV HUD Overlay (Scaled to Android TV Viewport)
        AnimatedVisibility(
            visible = showHud,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            TvPlayerControlsOverlay(
                media = currentMedia,
                isPlaying = isPlaying,
                currentChan = currentChan,
                currentEpg = currentEpg,
                channelIndex = currentIndex,
                channelCount = currentLineup.size,
                prevChannel = prevChannel,
                nextChannel = nextChannel,
                isFavorite = isFavorite,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                boundaryNotice = boundaryNotice,
                playPauseFocusRequester = playPauseFocusRequester,
                onResetTimer = { resetHudTimer() },
                onTogglePlay = { handleTogglePlay() },
                onSeekRelative = { delta ->
                    playerManager.seekRelative(delta)
                    currentPositionMs = playerManager.player.currentPosition
                    resetHudTimer()
                },
                onToggleFavorite = {
                    coroutineScope.launch {
                        val nextFav = !isFavorite
                        db.channelDao().updateFavorite(currentMedia.id, nextFav)
                        isFavorite = nextFav
                        resetHudTimer()
                    }
                },
                onOpenAudioTracks = {
                    showAudioTrackDialog = true
                    resetHudTimer()
                },
                onOpenSubtitles = {
                    showSubtitleDialog = true
                    resetHudTimer()
                },
                onBack = onBack
            )
        }

        // Audio Track Selection Dialog
        if (showAudioTrackDialog) {
            TvTrackPickerDialog(
                title = "Audio Tracks",
                tracks = audioTracks.ifEmpty { listOf("Default Audio") },
                selectedIndex = selectedAudioIndex,
                onSelectTrack = { index ->
                    selectedAudioIndex = index
                    playerManager.selectAudioTrack(index)
                    showAudioTrackDialog = false
                    resetHudTimer()
                },
                onDismiss = {
                    showAudioTrackDialog = false
                    resetHudTimer()
                }
            )
        }

        // Subtitle Selection Dialog
        if (showSubtitleDialog) {
            val subOptions = listOf("Off") + subtitleTracks
            TvTrackPickerDialog(
                title = "Subtitles",
                tracks = subOptions,
                selectedIndex = selectedSubtitleIndex + 1,
                onSelectTrack = { index ->
                    val trackIdx = index - 1
                    selectedSubtitleIndex = trackIdx
                    playerManager.selectSubtitleTrack(trackIdx)
                    showSubtitleDialog = false
                    resetHudTimer()
                },
                onDismiss = {
                    showSubtitleDialog = false
                    resetHudTimer()
                }
            )
        }
    }
}

@Composable
private fun TvPlayerControlsOverlay(
    media: PlayableMedia,
    isPlaying: Boolean,
    currentChan: ChannelEntity?,
    currentEpg: EpgListingEntity?,
    channelIndex: Int,
    channelCount: Int,
    prevChannel: ChannelEntity? = null,
    nextChannel: ChannelEntity? = null,
    isFavorite: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    boundaryNotice: String?,
    playPauseFocusRequester: FocusRequester,
    onResetTimer: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekRelative: (Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenAudioTracks: () -> Unit,
    onOpenSubtitles: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.45f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.55f),
                        Color.Black.copy(alpha = 0.90f)
                    )
                )
            )
            .padding(horizontal = 40.dp, vertical = 24.dp)
    ) {
        // Top Bar: Back Button, Metadata, Badges
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TVPlayerCircleButton(
                icon = Icons.Default.ArrowBack,
                contentDescription = "Back",
                diameter = 32.dp,
                iconSize = 16.dp,
                onClick = onBack,
                onFocused = onResetTimer
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                val caption = if (media.isLive) {
                    if (media.categoryName.isNotBlank()) "${media.categoryName} • Channel ${media.channelNumber}"
                    else "Channel ${media.channelNumber}"
                } else {
                    media.subtitle
                }
                if (caption.isNotBlank()) {
                    Text(
                        text = caption,
                        color = LumeroidTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = media.title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (media.isLive) {
                if (channelCount > 0 && channelIndex >= 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${channelIndex + 1} of $channelCount",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(LumeroidLiveRed)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("LIVE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(LumeroidAccentBlue.copy(alpha = 0.75f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("VOD", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }

        // Bottom Region: Scrubber, Timeline, Transport Buttons & Action Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Category Boundary Notice Toast
            AnimatedVisibility(
                visible = boundaryNotice != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.88f))
                        .border(1.dp, LumeroidLiveRed.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 18.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = boundaryNotice ?: "",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Scrubber / EPG Timeline Row
            if (media.isLive) {
                if (currentEpg != null) {
                    val startMs = currentEpg.startTime
                    val endMs = currentEpg.endTime
                    val nowMs = System.currentTimeMillis()
                    val totalDuration = (endMs - startMs).coerceAtLeast(1L)
                    val elapsed = (nowMs - startMs).coerceAtLeast(0L)
                    val progressFraction = (elapsed.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "NOW: ${currentEpg.title}",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${formatClockTime(startMs)} – ${formatClockTime(endMs)}",
                                color = LumeroidTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(1.5.dp)),
                            color = LumeroidLiveRed,
                            trackColor = Color.White.copy(alpha = 0.2f)
                        )
                    }
                }
            } else if (durationMs > 0) {
                val progressFraction = (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(currentPositionMs),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "-" + formatTime((durationMs - currentPositionMs).coerceAtLeast(0L)),
                            color = LumeroidTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp)),
                        color = LumeroidAccentBlue,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                }
            }

            // Apple TV 1:1 Parity Control Row (ZStack: Centered Transport, Side Groups)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                // Centered Transport Buttons (78pt scaled to 42dp for Android TV)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!media.isLive) {
                        // VOD Skip Backward 10s
                        TVPlayerCircleButton(
                            icon = Icons.Default.FastRewind,
                            contentDescription = "Rewind 10 Seconds",
                            diameter = 32.dp,
                            iconSize = 16.dp,
                            onClick = { onSeekRelative(-10000L) },
                            onFocused = onResetTimer
                        )
                    }

                    // Central Play/Pause Button (Apple TV 1:1 proportional scale: 42dp)
                    TVPlayerCircleButton(
                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        diameter = 42.dp,
                        iconSize = 20.dp,
                        isPrimary = true,
                        focusRequester = playPauseFocusRequester,
                        onClick = onTogglePlay,
                        onFocused = onResetTimer
                    )

                    if (!media.isLive) {
                        // VOD Skip Forward 10s
                        TVPlayerCircleButton(
                            icon = Icons.Default.FastForward,
                            contentDescription = "Forward 10 Seconds",
                            diameter = 32.dp,
                            iconSize = 16.dp,
                            onClick = { onSeekRelative(10000L) },
                            onFocused = onResetTimer
                        )
                    }
                }

                // Left Hint: Channel surfing indicators (prev/next channel)
                Box(
                    modifier = Modifier.align(Alignment.CenterStart),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (media.isLive) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            if (prevChannel != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "▲",
                                        color = LumeroidAccentBlue,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = prevChannel.name,
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 240.dp)
                                    )
                                }
                            }
                            if (nextChannel != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "▼",
                                        color = LumeroidAccentBlue,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = nextChannel.name,
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 240.dp)
                                    )
                                }
                            }
                            if (prevChannel == null && nextChannel == null) {
                                Text(
                                    text = "▲/▼ Channel Surfing",
                                    color = LumeroidTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "◀/▶ Skip 10s",
                            color = LumeroidTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                // Right Trailing Controls: Favorite, Audio, Subtitles
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (media.isLive) {
                        TVPlayerCircleButton(
                            icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isFavorite) "Remove Favorite" else "Add Favorite",
                            diameter = 30.dp,
                            iconSize = 15.dp,
                            iconTint = if (isFavorite) LumeroidLiveRed else null,
                            onClick = onToggleFavorite,
                            onFocused = onResetTimer
                        )
                    }

                    TVPlayerCircleButton(
                        icon = Icons.Default.VolumeUp,
                        contentDescription = "Audio Tracks",
                        diameter = 30.dp,
                        iconSize = 15.dp,
                        onClick = onOpenAudioTracks,
                        onFocused = onResetTimer
                    )

                    TVPlayerCircleButton(
                        icon = Icons.Default.ClosedCaption,
                        contentDescription = "Subtitles",
                        diameter = 30.dp,
                        iconSize = 15.dp,
                        onClick = onOpenSubtitles,
                        onFocused = onResetTimer
                    )
                }
            }
        }
    }
}

@Composable
fun TVPlayerCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 32.dp,
    iconSize: Dp = 16.dp,
    isEnabled: Boolean = true,
    isPrimary: Boolean = false,
    iconTint: Color? = null,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.12f else 1.0f,
        label = "btnScale"
    )

    val bgColor = when {
        isFocused -> Color.White
        isPrimary -> Color.White.copy(alpha = 0.25f)
        isEnabled -> Color.White.copy(alpha = 0.15f)
        else -> Color.White.copy(alpha = 0.05f)
    }

    val contentTint = when {
        isFocused -> Color.Black
        iconTint != null -> iconTint
        isEnabled -> Color.White
        else -> Color.White.copy(alpha = 0.35f)
    }

    val borderStroke = when {
        isFocused -> BorderStroke(2.dp, Color.White)
        isPrimary -> BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
        isEnabled -> BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
        else -> BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    }

    Box(
        modifier = modifier
            .size(diameter)
            .scale(scale)
            .clip(CircleShape)
            .background(bgColor)
            .border(borderStroke, CircleShape)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    onFocused?.invoke()
                }
            }
            .focusable(enabled = isEnabled)
            .onKeyEvent { keyEvent ->
                if (isEnabled && keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                     keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
                     keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                    onClick()
                    true
                } else false
            }
            .clickable(
                enabled = isEnabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentTint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun TvTrackPickerDialog(
    title: String,
    tracks: List<String>,
    selectedIndex: Int,
    onSelectTrack: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val firstItemRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        try { firstItemRequester.requestFocus() } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(360.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1E1E22))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                .padding(20.dp)
                .clickable(enabled = false) {}
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.heightIn(max = 260.dp)
                ) {
                    itemsIndexed(tracks) { index, trackName ->
                        var isItemFocused by remember { mutableStateOf(false) }
                        val isSelected = index == selectedIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    when {
                                        isItemFocused -> Color.White
                                        isSelected -> Color.White.copy(alpha = 0.15f)
                                        else -> Color.Transparent
                                    }
                                )
                                .then(if (index == 0) Modifier.focusRequester(firstItemRequester) else Modifier)
                                .onFocusChanged { isItemFocused = it.isFocused }
                                .focusable()
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown &&
                                        (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                         keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)) {
                                        onSelectTrack(index)
                                        true
                                    } else false
                                }
                                .clickable { onSelectTrack(index) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = trackName,
                                color = if (isItemFocused) Color.Black else Color.White,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = if (isItemFocused) Color.Black else LumeroidAccentBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Overload for backwards compatibility
@Composable
fun TvPlayerScreen(
    initialChannel: ChannelEntity,
    channelLineup: List<ChannelEntity>,
    onBack: () -> Unit
) {
    TvPlayerScreen(
        media = initialChannel.toPlayableMedia(lineup = channelLineup),
        channelLineup = channelLineup,
        onBack = onBack
    )
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private fun formatClockTime(epochMs: Long): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.US)
    return sdf.format(Date(epochMs))
}
