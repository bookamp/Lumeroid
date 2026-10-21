package org.getlume.android.ui.tv

import android.view.KeyEvent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import org.getlume.android.LumeApp
import org.getlume.android.core.data.entities.ChannelEntity
import org.getlume.android.core.designsystem.*
import org.getlume.android.core.player.MultiViewLayout
import org.getlume.android.core.player.MultiViewSession
import org.getlume.android.core.player.MultiViewSlot

@Composable
fun TvMultiViewScreen(
    initialChannel: ChannelEntity? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val db = LumeApp.instance.database
    val session = remember { MultiViewSession(context) }

    val layout by session.layout.collectAsState()
    val slots by session.slots.collectAsState()

    val activePlaylist by db.playlistDao().observeActivePlaylist().collectAsState(initial = null)
    val allChannels by db.channelDao().observeChannelsByPlaylist(activePlaylist?.id ?: "").collectAsState(initial = emptyList())

    var focusedSlotIndex by remember { mutableStateOf(0) }
    var channelPickerSlotIndex by remember { mutableStateOf<Int?>(null) }
    var showControlsOverlay by remember { mutableStateOf(true) }

    // Assign initial channel to slot 0 if passed
    LaunchedEffect(initialChannel, allChannels) {
        if (initialChannel != null && slots[0].channel == null) {
            session.assignChannel(0, initialChannel)
        } else if (slots[0].channel == null && allChannels.isNotEmpty()) {
            session.assignChannel(0, allChannels[0])
            if (allChannels.size > 1) session.assignChannel(1, allChannels[1])
            if (allChannels.size > 2) session.assignChannel(2, allChannels[2])
            if (allChannels.size > 3) session.assignChannel(3, allChannels[3])
        }
    }

    DisposableEffect(Unit) {
        onDispose { session.release() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LumeBackground)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_BACK -> {
                            if (channelPickerSlotIndex != null) {
                                channelPickerSlotIndex = null
                                true
                            } else {
                                onBack()
                                true
                            }
                        }
                        KeyEvent.KEYCODE_MENU -> {
                            showControlsOverlay = !showControlsOverlay
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // Multi-View Video Grid
        when (layout) {
            MultiViewLayout.TWO -> {
                Row(modifier = Modifier.fillMaxSize()) {
                    SlotTile(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        session = session,
                        slot = slots[0],
                        isCurrentlyFocused = focusedSlotIndex == 0,
                        onFocus = { focusedSlotIndex = 0 },
                        onClick = {
                            session.setActiveAudioSlot(0)
                        },
                        onLongClick = { channelPickerSlotIndex = 0 }
                    )
                    SlotTile(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        session = session,
                        slot = slots[1],
                        isCurrentlyFocused = focusedSlotIndex == 1,
                        onFocus = { focusedSlotIndex = 1 },
                        onClick = {
                            session.setActiveAudioSlot(1)
                        },
                        onLongClick = { channelPickerSlotIndex = 1 }
                    )
                }
            }
            MultiViewLayout.THREE -> {
                Row(modifier = Modifier.fillMaxSize()) {
                    SlotTile(
                        modifier = Modifier.weight(1.2f).fillMaxHeight(),
                        session = session,
                        slot = slots[0],
                        isCurrentlyFocused = focusedSlotIndex == 0,
                        onFocus = { focusedSlotIndex = 0 },
                        onClick = { session.setActiveAudioSlot(0) },
                        onLongClick = { channelPickerSlotIndex = 0 }
                    )
                    Column(modifier = Modifier.weight(0.8f).fillMaxHeight()) {
                        SlotTile(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            session = session,
                            slot = slots[1],
                            isCurrentlyFocused = focusedSlotIndex == 1,
                            onFocus = { focusedSlotIndex = 1 },
                            onClick = { session.setActiveAudioSlot(1) },
                            onLongClick = { channelPickerSlotIndex = 1 }
                        )
                        SlotTile(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            session = session,
                            slot = slots[2],
                            isCurrentlyFocused = focusedSlotIndex == 2,
                            onFocus = { focusedSlotIndex = 2 },
                            onClick = { session.setActiveAudioSlot(2) },
                            onLongClick = { channelPickerSlotIndex = 2 }
                        )
                    }
                }
            }
            MultiViewLayout.FOUR -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        SlotTile(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            session = session,
                            slot = slots[0],
                            isCurrentlyFocused = focusedSlotIndex == 0,
                            onFocus = { focusedSlotIndex = 0 },
                            onClick = { session.setActiveAudioSlot(0) },
                            onLongClick = { channelPickerSlotIndex = 0 }
                        )
                        SlotTile(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            session = session,
                            slot = slots[1],
                            isCurrentlyFocused = focusedSlotIndex == 1,
                            onFocus = { focusedSlotIndex = 1 },
                            onClick = { session.setActiveAudioSlot(1) },
                            onLongClick = { channelPickerSlotIndex = 1 }
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        SlotTile(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            session = session,
                            slot = slots[2],
                            isCurrentlyFocused = focusedSlotIndex == 2,
                            onFocus = { focusedSlotIndex = 2 },
                            onClick = { session.setActiveAudioSlot(2) },
                            onLongClick = { channelPickerSlotIndex = 2 }
                        )
                        SlotTile(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            session = session,
                            slot = slots[3],
                            isCurrentlyFocused = focusedSlotIndex == 3,
                            onFocus = { focusedSlotIndex = 3 },
                            onClick = { session.setActiveAudioSlot(3) },
                            onLongClick = { channelPickerSlotIndex = 3 }
                        )
                    }
                }
            }
            MultiViewLayout.SPOTLIGHT -> {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Big Stage Tile
                    SlotTile(
                        modifier = Modifier.weight(2f).fillMaxHeight(),
                        session = session,
                        slot = slots[0],
                        isCurrentlyFocused = focusedSlotIndex == 0,
                        onFocus = { focusedSlotIndex = 0 },
                        onClick = { session.setActiveAudioSlot(0) },
                        onLongClick = { channelPickerSlotIndex = 0 }
                    )
                    // 3 Stacked Side Tiles
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        for (i in 1..3) {
                            SlotTile(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                session = session,
                                slot = slots[i],
                                isCurrentlyFocused = focusedSlotIndex == i,
                                onFocus = { focusedSlotIndex = i },
                                onClick = { session.setActiveAudioSlot(i) },
                                onLongClick = { channelPickerSlotIndex = i }
                            )
                        }
                    }
                }
            }
        }

        // Top Action Bar & Layout Switcher Overlay
        AnimatedVisibility(
            visible = showControlsOverlay,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(LumeSurface.copy(alpha = 0.92f))
                    .border(1.dp, LumeSurfaceBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LumeCard(
                        modifier = Modifier.size(40.dp),
                        onClick = onBack
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    }
                    Text(
                        "Multi-View 4-Stream",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                // Layout picker pills
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LayoutModeButton("2 Streams", layout == MultiViewLayout.TWO) {
                        session.setLayout(MultiViewLayout.TWO)
                    }
                    LayoutModeButton("3 Streams", layout == MultiViewLayout.THREE) {
                        session.setLayout(MultiViewLayout.THREE)
                    }
                    LayoutModeButton("4 Grid", layout == MultiViewLayout.FOUR) {
                        session.setLayout(MultiViewLayout.FOUR)
                    }
                    LayoutModeButton("Spotlight", layout == MultiViewLayout.SPOTLIGHT) {
                        session.setLayout(MultiViewLayout.SPOTLIGHT)
                    }
                }

                Text(
                    "Click: Audio | Long-Press: Change Channel",
                    color = LumeTextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        // Channel Selection Drawer Modal
        if (channelPickerSlotIndex != null) {
            val slotIdx = channelPickerSlotIndex!!
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.CenterEnd
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(420.dp)
                        .background(LumeSurfaceElevated)
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Select Channel for Tile #${slotIdx + 1}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        LumeCard(
                            modifier = Modifier.size(36.dp),
                            onClick = { channelPickerSlotIndex = null }
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(allChannels) { channel ->
                            LumeCard(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    session.assignChannel(slotIdx, channel)
                                    channelPickerSlotIndex = null
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(LumeSurfaceHighlight),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            channel.channelNumber.toString(),
                                            color = LumeTextSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        channel.name,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
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

@Composable
private fun SlotTile(
    modifier: Modifier = Modifier,
    session: MultiViewSession,
    slot: MultiViewSlot,
    isCurrentlyFocused: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val player = remember(slot.index) { session.getOrCreatePlayer(slot.index) }
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .border(
                width = if (slot.isAudioActive) 3.dp else if (isCurrentlyFocused) 2.dp else 1.dp,
                color = if (slot.isAudioActive) LumeCyan else if (isCurrentlyFocused) Color.White.copy(alpha = 0.6f) else LumeSurfaceBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .focusRequester(focusRequester)
            .onFocusChanged {
                if (it.isFocused) onFocus()
            }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            onClick()
                            true
                        }
                        KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            onLongClick()
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        if (slot.channel != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add Channel",
                        tint = LumeTextSecondary,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Slot #${slot.index + 1} Empty", color = LumeTextSecondary, fontSize = 13.sp)
                }
            }
        }

        // Slot Header Info Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                slot.channel?.name ?: "Slot #${slot.index + 1}",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            if (slot.isAudioActive) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(LumeCyan)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        Icons.Default.VolumeUp,
                        contentDescription = "Active Audio",
                        tint = Color.Black,
                        modifier = Modifier.size(12.dp)
                    )
                    Text("AUDIO", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            } else {
                Icon(
                    Icons.Default.VolumeMute,
                    contentDescription = "Muted",
                    tint = LumeTextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun LayoutModeButton(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    LumeCard(
        modifier = Modifier,
        onClick = onClick,
        containerColor = if (isSelected) LumePrimary else LumeSurfaceHighlight
    ) {
        Text(
            text = title,
            color = if (isSelected) Color.White else LumeTextSecondary,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
