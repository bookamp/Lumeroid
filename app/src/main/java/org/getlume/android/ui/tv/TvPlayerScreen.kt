package org.getlume.android.ui.tv

import android.view.KeyEvent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.getlume.android.LumeApp
import org.getlume.android.core.data.entities.ChannelEntity
import org.getlume.android.core.data.entities.WatchProgressEntity
import org.getlume.android.core.designsystem.*
import org.getlume.android.core.settings.AppSettings
import org.getlume.android.core.player.*
import java.util.Locale

@Composable
fun TvPlayerScreen(
    media: PlayableMedia,
    channelLineup: List<ChannelEntity> = emptyList(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val playerManager = remember { LumePlayerManager(context) }
    var currentMedia by remember { mutableStateOf(media) }

    var showHud by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(media.durationMs) }

    DisposableEffect(currentMedia) {
        playerManager.playMedia(currentMedia)
        if (currentMedia.isLive) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
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
                LumeApp.instance.database.watchProgressDao().saveProgress(progress)
            }
        }
        onDispose {
            // Save watch progress if VOD
            if (!currentMedia.isLive && currentPositionMs > 5000L) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    val progress = WatchProgressEntity(
                        contentId = currentMedia.id,
                        profileId = AppSettings.get().activeProfileId,
                        title = currentMedia.title,
                        posterUrl = currentMedia.posterUrl,
                        contentType = "movie",
                        positionMs = currentPositionMs,
                        durationMs = durationMs.coerceAtLeast(1L),
                        isCompleted = durationMs > 0 && currentPositionMs > (durationMs * 0.90)
                    )
                    LumeApp.instance.database.watchProgressDao().saveProgress(progress)
                }
            }
            playerManager.release()
        }
    }

    // Ticker for playback progress and HUD auto-hide
    LaunchedEffect(currentMedia) {
        while (true) {
            delay(1000)
            if (!currentMedia.isLive) {
                currentPositionMs = playerManager.player.currentPosition
                if (playerManager.player.duration > 0) {
                    durationMs = playerManager.player.duration
                }
            }
            isPlaying = playerManager.player.isPlaying
        }
    }

    // Auto-hide HUD after 5 seconds of inactivity
    LaunchedEffect(showHud, currentMedia) {
        if (showHud) {
            delay(5000)
            showHud = false
        }
    }

    // Atomic back handling for Android TV remote
    androidx.activity.compose.BackHandler {
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (currentMedia.isLive && channelLineup.isNotEmpty()) {
                                val currentChan = channelLineup.find { it.streamUrl == currentMedia.streamUrl } ?: channelLineup.first()
                                val nextChan = LiveChannelNavigator.nextChannel(
                                    current = currentChan,
                                    lineup = channelLineup,
                                    direction = RemoteDirectionGate.Direction.UP
                                )
                                currentMedia = nextChan.toPlayableMedia()
                                showHud = true
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (currentMedia.isLive && channelLineup.isNotEmpty()) {
                                val currentChan = channelLineup.find { it.streamUrl == currentMedia.streamUrl } ?: channelLineup.first()
                                val nextChan = LiveChannelNavigator.nextChannel(
                                    current = currentChan,
                                    lineup = channelLineup,
                                    direction = RemoteDirectionGate.Direction.DOWN
                                )
                                currentMedia = nextChan.toPlayableMedia()
                                showHud = true
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (!currentMedia.isLive) {
                                playerManager.seekRelative(-10000L)
                                currentPositionMs = playerManager.player.currentPosition
                                showHud = true
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (!currentMedia.isLive) {
                                playerManager.seekRelative(10000L)
                                currentPositionMs = playerManager.player.currentPosition
                                showHud = true
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            if (!currentMedia.isLive) {
                                playerManager.togglePlayPause()
                                isPlaying = playerManager.player.isPlaying
                            }
                            showHud = !showHud
                            true
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

        // Top & Bottom Apple TV HUD Overlay
        AnimatedVisibility(
            visible = showHud,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.85f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.88f)
                            )
                        )
                    )
            ) {
                // Top Bar: Back Button, Title, Subtitle, Live / VOD badge
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 36.dp, vertical = 28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LumeCard(
                        modifier = Modifier.size(46.dp),
                        onClick = onBack
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    Column {
                        Text(
                            text = currentMedia.title,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (currentMedia.subtitle.isNotBlank()) {
                            Text(
                                text = currentMedia.subtitle,
                                color = LumeTextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (currentMedia.isLive) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(LumeLiveRed)
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text("LIVE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(LumeAccentBlue.copy(alpha = 0.7f))
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text("VOD", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                // Bottom Transport Bar (for VOD seekbar, elapsed time, controls hints)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 36.dp, vertical = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!currentMedia.isLive && durationMs > 0) {
                        val progressFraction = (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatTime(currentPositionMs),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = formatTime(durationMs),
                                color = LumeTextSecondary,
                                fontSize = 13.sp
                            )
                        }

                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = LumeAccentBlue,
                            trackColor = Color.White.copy(alpha = 0.2f)
                        )
                    }

                    // Navigation Hints
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        if (currentMedia.isLive) {
                            Text("▲/▼ Change Channel", color = LumeTextSecondary, fontSize = 13.sp)
                            Text("● Show/Hide HUD", color = LumeTextSecondary, fontSize = 13.sp)
                        } else {
                            Text("◀/▶ Skip 10s", color = LumeTextSecondary, fontSize = 13.sp)
                            Text("● Play/Pause", color = LumeTextSecondary, fontSize = 13.sp)
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
        media = initialChannel.toPlayableMedia(),
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
