package org.getlume.android.ui.mobile

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
import org.getlume.android.core.data.entities.ChannelEntity
import org.getlume.android.core.designsystem.*
import org.getlume.android.core.player.LumePlayerManager

@Composable
fun MobilePlayerScreen(
    initialChannel: ChannelEntity,
    channelLineup: List<ChannelEntity>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val playerManager = remember { LumePlayerManager(context) }
    var currentChannel by remember { mutableStateOf(initialChannel) }

    var showHud by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }

    DisposableEffect(currentChannel) {
        playerManager.playChannel(currentChannel)
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
        // ExoPlayer Surface
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
                            text = currentChannel.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Channel ${currentChannel.channelNumber}",
                            color = LumeTextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(LumeLiveRed)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("LIVE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Center Play/Pause Control
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
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
                }

                // Bottom Quick Prev/Next Channel Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .align(Alignment.BottomCenter)
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val currentIndex = channelLineup.indexOfFirst { it.id == currentChannel.id }
                            if (currentIndex > 0) {
                                currentChannel = channelLineup[currentIndex - 1]
                            } else if (channelLineup.isNotEmpty()) {
                                currentChannel = channelLineup.last()
                            }
                        }
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous Channel", tint = Color.White, modifier = Modifier.size(32.dp))
                    }

                    Text(
                        "Swipe / Buttons to switch channel",
                        color = LumeTextSecondary,
                        fontSize = 12.sp
                    )

                    IconButton(
                        onClick = {
                            val currentIndex = channelLineup.indexOfFirst { it.id == currentChannel.id }
                            if (currentIndex in 0 until channelLineup.lastIndex) {
                                currentChannel = channelLineup[currentIndex + 1]
                            } else if (channelLineup.isNotEmpty()) {
                                currentChannel = channelLineup.first()
                            }
                        }
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next Channel", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}
