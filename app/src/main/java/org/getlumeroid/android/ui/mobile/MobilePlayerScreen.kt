package org.getlumeroid.android.ui.mobile

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import org.getlumeroid.android.core.data.entities.ChannelEntity
import org.getlumeroid.android.core.designsystem.*
import org.getlumeroid.android.core.player.LumeroidPlayerManager
import org.getlumeroid.android.core.player.PlayableMedia
import org.getlumeroid.android.core.player.toPlayableMedia

@Composable
fun MobilePlayerScreen(
    initialChannel: ChannelEntity,
    channelLineup: List<ChannelEntity> = emptyList(),
    onBack: () -> Unit
) {
    MobilePlayerScreen(
        media = initialChannel.toPlayableMedia(lineup = channelLineup),
        channelLineup = channelLineup,
        onBack = onBack
    )
}

@Composable
fun MobilePlayerScreen(
    media: PlayableMedia,
    channelLineup: List<ChannelEntity> = emptyList(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val playerManager = remember { LumeroidPlayerManager(context) }
    var currentMedia by remember { mutableStateOf(media) }

    var showHud by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(media.durationMs.coerceAtLeast(1L)) }

    DisposableEffect(currentMedia) {
        playerManager.playMedia(currentMedia)
        onDispose {
            playerManager.release()
        }
    }

    // Auto-hide HUD after 4 seconds
    LaunchedEffect(showHud) {
        if (showHud) {
            delay(4000)
            showHud = false
        }
    }

    // Position updater for non-live content
    LaunchedEffect(currentMedia) {
        if (!currentMedia.isLive) {
            while (true) {
                currentPositionMs = playerManager.player.currentPosition
                val d = playerManager.player.duration
                if (d > 0) durationMs = d
                delay(1000)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                showHud = !showHud
            }
    ) {
        // Video Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = playerManager.player
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay HUD
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
                            listOf(
                                Color.Black.copy(alpha = 0.8f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = currentMedia.title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (currentMedia.subtitle.isNotBlank()) {
                            Text(
                                text = currentMedia.subtitle,
                                color = LumeroidTextSecondary,
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    if (currentMedia.isLive) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(LumeroidLiveRed)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("LIVE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Center Play/Pause Control
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(32.dp)
                    ) {
                        if (!currentMedia.isLive) {
                            IconButton(
                                onClick = { playerManager.player.seekTo((playerManager.player.currentPosition - 10000).coerceAtLeast(0)) },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                        }

                        IconButton(
                            onClick = {
                                if (isPlaying) {
                                    playerManager.player.pause()
                                    isPlaying = false
                                } else {
                                    playerManager.player.play()
                                    isPlaying = true
                                }
                            },
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(32.dp))
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        if (!currentMedia.isLive) {
                            IconButton(
                                onClick = { playerManager.player.seekTo((playerManager.player.currentPosition + 10000).coerceAtMost(durationMs)) },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }

                // Bottom Progress / Controls (for VOD)
                if (!currentMedia.isLive && durationMs > 0) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Slider(
                            value = (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f),
                            onValueChange = { fraction ->
                                val target = (fraction * durationMs).toLong()
                                playerManager.player.seekTo(target)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = LumeroidAccentBlue,
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(formatTime(currentPositionMs), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                            Text(formatTime(durationMs), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes % 60, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
