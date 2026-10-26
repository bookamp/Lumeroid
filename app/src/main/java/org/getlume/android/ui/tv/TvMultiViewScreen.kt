package org.getlume.android.ui.tv

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.getlume.android.LumeApp
import org.getlume.android.core.data.entities.CategoryEntity
import org.getlume.android.core.data.entities.ChannelEntity
import org.getlume.android.core.designsystem.*
import org.getlume.android.core.player.MultiViewLayout
import org.getlume.android.core.player.MultiViewSession
import org.getlume.android.core.player.MultiViewSlot
import org.getlume.android.core.settings.AppSettings

@Composable
fun TvMultiViewScreen(
    initialChannel: ChannelEntity? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val db = LumeApp.instance.database
    val session = remember { MultiViewSession(context) }
    val scope = rememberCoroutineScope()

    val layout by session.layout.collectAsState()
    val slots by session.slots.collectAsState()

    val activePlaylist by db.playlistDao().observeActivePlaylist().collectAsState(initial = null)
    val playlistId = activePlaylist?.id ?: ""
    val categories by db.categoryDao().getCategories(playlistId, "live").collectAsState(initial = emptyList())
    val favoriteChannels by db.channelDao().getFavoriteChannels(playlistId).collectAsState(initial = emptyList())
    val recentChannels by db.channelDao().getRecentlyWatchedChannels(playlistId, AppSettings.get().activeProfileId).collectAsState(initial = emptyList())

    var focusedSlotIndex by remember { mutableStateOf(0) }
    var channelPickerSlotIndex by remember { mutableStateOf<Int?>(null) }
    var slotForActionMenu by remember { mutableStateOf<MultiViewSlot?>(null) }
    var showControlsOverlay by remember { mutableStateOf(true) }

    val tileFocusRequesters = remember { List(4) { FocusRequester() } }
    val topBarFocusRequester = remember { FocusRequester() }

    // Seed ONLY slot 0 with initialChannel. If none provided, open channel picker for Slot 0!
    LaunchedEffect(initialChannel) {
        if (initialChannel != null && slots[0].channel == null) {
            session.assignChannel(0, initialChannel)
        } else if (initialChannel == null && slots[0].channel == null) {
            channelPickerSlotIndex = 0
        }
    }

    DisposableEffect(Unit) {
        onDispose { session.release() }
    }

    var showActiveHighlight by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Auto-hide controls overlay after 5 seconds of inactivity
    LaunchedEffect(showControlsOverlay) {
        if (showControlsOverlay) {
            delay(5000)
            showControlsOverlay = false
        }
    }

    // Auto-hide active tile highlight after 3 seconds of inactivity
    LaunchedEffect(showActiveHighlight, lastInteractionTime) {
        if (showActiveHighlight) {
            delay(3000)
            showActiveHighlight = false
        }
    }

    // Initial focus on Slot 0 & ensure Slot 0 has exclusive audio
    LaunchedEffect(Unit) {
        delay(100)
        try {
            session.setActiveAudioSlot(0)
            tileFocusRequesters[0].requestFocus()
        } catch (_: Exception) {}
    }

    // Unified Back Button Handling
    BackHandler {
        when {
            channelPickerSlotIndex != null -> channelPickerSlotIndex = null
            slotForActionMenu != null -> slotForActionMenu = null
            showControlsOverlay -> showControlsOverlay = false
            else -> onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    showActiveHighlight = true
                    lastInteractionTime = System.currentTimeMillis()
                    if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MENU) {
                        showControlsOverlay = !showControlsOverlay
                        return@onPreviewKeyEvent true
                    }
                }
                false
            }
    ) {
        // Multi-View Video Grid
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = if (showControlsOverlay) 56.dp else 12.dp, bottom = 12.dp, start = 16.dp, end = 16.dp)
        ) {
            when (layout) {
                MultiViewLayout.TWO -> {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (i in 0..1) {
                            MultiViewTile(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                session = session,
                                slot = slots[i],
                                isCurrentlyFocused = focusedSlotIndex == i,
                                showHighlight = showActiveHighlight,
                                focusRequester = tileFocusRequesters[i],
                                onFocus = {
                                    focusedSlotIndex = i
                                    session.setActiveAudioSlot(i)
                                },
                                onPickChannel = { channelPickerSlotIndex = i },
                                onOpenActionMenu = { slotForActionMenu = slots[i] },
                                onNavigateUp = {
                                    showControlsOverlay = true
                                    scope.launch {
                                        delay(50)
                                        try { topBarFocusRequester.requestFocus() } catch (_: Exception) {}
                                    }
                                }
                            )
                        }
                    }
                }
                MultiViewLayout.THREE -> {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MultiViewTile(
                            modifier = Modifier.weight(1.3f).fillMaxHeight(),
                            session = session,
                            slot = slots[0],
                            isCurrentlyFocused = focusedSlotIndex == 0,
                            showHighlight = showActiveHighlight,
                            focusRequester = tileFocusRequesters[0],
                            onFocus = {
                                focusedSlotIndex = 0
                                session.setActiveAudioSlot(0)
                            },
                            onPickChannel = { channelPickerSlotIndex = 0 },
                            onOpenActionMenu = { slotForActionMenu = slots[0] },
                            onNavigateUp = {
                                showControlsOverlay = true
                                scope.launch {
                                    delay(50)
                                    try { topBarFocusRequester.requestFocus() } catch (_: Exception) {}
                                }
                            }
                        )
                        Column(
                            modifier = Modifier.weight(0.7f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MultiViewTile(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                session = session,
                                slot = slots[1],
                                isCurrentlyFocused = focusedSlotIndex == 1,
                                showHighlight = showActiveHighlight,
                                focusRequester = tileFocusRequesters[1],
                                onFocus = {
                                    focusedSlotIndex = 1
                                    session.setActiveAudioSlot(1)
                                },
                                onPickChannel = { channelPickerSlotIndex = 1 },
                                onOpenActionMenu = { slotForActionMenu = slots[1] },
                                onNavigateUp = {
                                    showControlsOverlay = true
                                    scope.launch {
                                        delay(50)
                                        try { topBarFocusRequester.requestFocus() } catch (_: Exception) {}
                                    }
                                }
                            )
                            MultiViewTile(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                session = session,
                                slot = slots[2],
                                isCurrentlyFocused = focusedSlotIndex == 2,
                                showHighlight = showActiveHighlight,
                                focusRequester = tileFocusRequesters[2],
                                onFocus = {
                                    focusedSlotIndex = 2
                                    session.setActiveAudioSlot(2)
                                },
                                onPickChannel = { channelPickerSlotIndex = 2 },
                                onOpenActionMenu = { slotForActionMenu = slots[2] },
                                onNavigateUp = {
                                    try { tileFocusRequesters[1].requestFocus() } catch (_: Exception) {}
                                }
                            )
                        }
                    }
                }
                MultiViewLayout.FOUR -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            for (i in 0..1) {
                                MultiViewTile(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    session = session,
                                    slot = slots[i],
                                    isCurrentlyFocused = focusedSlotIndex == i,
                                    showHighlight = showActiveHighlight,
                                    focusRequester = tileFocusRequesters[i],
                                    onFocus = {
                                        focusedSlotIndex = i
                                        session.setActiveAudioSlot(i)
                                    },
                                    onPickChannel = { channelPickerSlotIndex = i },
                                    onOpenActionMenu = { slotForActionMenu = slots[i] },
                                    onNavigateUp = {
                                        showControlsOverlay = true
                                        scope.launch {
                                            delay(50)
                                            try { topBarFocusRequester.requestFocus() } catch (_: Exception) {}
                                        }
                                    }
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            for (i in 2..3) {
                                MultiViewTile(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    session = session,
                                    slot = slots[i],
                                    isCurrentlyFocused = focusedSlotIndex == i,
                                    showHighlight = showActiveHighlight,
                                    focusRequester = tileFocusRequesters[i],
                                    onFocus = {
                                        focusedSlotIndex = i
                                        session.setActiveAudioSlot(i)
                                    },
                                    onPickChannel = { channelPickerSlotIndex = i },
                                    onOpenActionMenu = { slotForActionMenu = slots[i] },
                                    onNavigateUp = {
                                        try { tileFocusRequesters[i - 2].requestFocus() } catch (_: Exception) {}
                                    }
                                )
                            }
                        }
                    }
                }
                MultiViewLayout.SPOTLIGHT -> {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Big Main Stage (Slot 0)
                        MultiViewTile(
                            modifier = Modifier.weight(2.3f).fillMaxHeight(),
                            session = session,
                            slot = slots[0],
                            isCurrentlyFocused = focusedSlotIndex == 0,
                            showHighlight = showActiveHighlight,
                            focusRequester = tileFocusRequesters[0],
                            onFocus = {
                                focusedSlotIndex = 0
                                session.setActiveAudioSlot(0)
                            },
                            onPickChannel = { channelPickerSlotIndex = 0 },
                            onOpenActionMenu = { slotForActionMenu = slots[0] },
                            onNavigateUp = {
                                showControlsOverlay = true
                                scope.launch {
                                    delay(50)
                                    try { topBarFocusRequester.requestFocus() } catch (_: Exception) {}
                                }
                            }
                        )
                        // Side Rail (Slots 1, 2, 3)
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            for (i in 1..3) {
                                MultiViewTile(
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                    session = session,
                                    slot = slots[i],
                                    isCurrentlyFocused = focusedSlotIndex == i,
                                    showHighlight = showActiveHighlight,
                                    focusRequester = tileFocusRequesters[i],
                                    onFocus = {
                                        focusedSlotIndex = i
                                        session.setActiveAudioSlot(i)
                                    },
                                    onPickChannel = { channelPickerSlotIndex = i },
                                    onOpenActionMenu = { slotForActionMenu = slots[i] },
                                    onNavigateUp = {
                                        if (i == 1) {
                                            showControlsOverlay = true
                                            scope.launch {
                                                delay(50)
                                                try { topBarFocusRequester.requestFocus() } catch (_: Exception) {}
                                            }
                                        } else {
                                            try { tileFocusRequesters[i - 1].requestFocus() } catch (_: Exception) {}
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                MultiViewLayout.PIP -> {
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Big Main Stage / Fullscreen (Slot 0)
                        MultiViewTile(
                            modifier = Modifier.fillMaxSize(),
                            session = session,
                            slot = slots[0],
                            isCurrentlyFocused = focusedSlotIndex == 0,
                            showHighlight = showActiveHighlight,
                            focusRequester = tileFocusRequesters[0],
                            onFocus = {
                                focusedSlotIndex = 0
                                session.setActiveAudioSlot(0)
                            },
                            onPickChannel = { channelPickerSlotIndex = 0 },
                            onOpenActionMenu = { slotForActionMenu = slots[0] },
                            onNavigateUp = {
                                showControlsOverlay = true
                                scope.launch {
                                    delay(50)
                                    try { topBarFocusRequester.requestFocus() } catch (_: Exception) {}
                                }
                            },
                            onNavigateRight = {
                                try { tileFocusRequesters[1].requestFocus() } catch (_: Exception) {}
                            }
                        )

                        // Floating Picture-in-Picture Overlay (Slot 1) in the Top-Right
                        val pipTopPadding by animateDpAsState(
                            targetValue = if (showControlsOverlay) 72.dp else 16.dp,
                            label = "pipTopPadding"
                        )
                        MultiViewTile(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = pipTopPadding, end = 16.dp)
                                .width(288.dp)
                                .aspectRatio(16f / 9f)
                                .shadow(24.dp, RoundedCornerShape(12.dp)),
                            session = session,
                            slot = slots[1],
                            isCurrentlyFocused = focusedSlotIndex == 1,
                            showHighlight = showActiveHighlight,
                            focusRequester = tileFocusRequesters[1],
                            onFocus = {
                                focusedSlotIndex = 1
                                session.setActiveAudioSlot(1)
                            },
                            onPickChannel = { channelPickerSlotIndex = 1 },
                            onOpenActionMenu = { slotForActionMenu = slots[1] },
                            onNavigateUp = {
                                showControlsOverlay = true
                                scope.launch {
                                    delay(50)
                                    try { topBarFocusRequester.requestFocus() } catch (_: Exception) {}
                                }
                            },
                            onNavigateLeft = {
                                try { tileFocusRequesters[0].requestFocus() } catch (_: Exception) {}
                            },
                            onNavigateDown = {
                                try { tileFocusRequesters[0].requestFocus() } catch (_: Exception) {}
                            }
                        )
                    }
                }
            }
        }

        // Top Action Bar & Layout Switcher Overlay (Apple TV Parity)
        AnimatedVisibility(
            visible = showControlsOverlay,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.85f),
                                Color.Black.copy(alpha = 0.40f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(horizontal = 36.dp, vertical = 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Close Button
                    MultiViewCircleButton(
                        icon = Icons.Default.Close,
                        contentDescription = "Close Multi-View",
                        focusRequester = topBarFocusRequester,
                        onClick = onBack,
                        onNavigateDown = {
                            showControlsOverlay = false
                            scope.launch {
                                delay(50)
                                try { tileFocusRequesters[focusedSlotIndex].requestFocus() } catch (_: Exception) {}
                            }
                        }
                    )

                    // Layout Mode Selector: Glass Segmented Control
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MultiViewLayoutButton(
                            icon = Icons.Default.ViewAgenda,
                            contentDescription = "2 Streams",
                            isActive = layout == MultiViewLayout.TWO,
                            onClick = { session.setLayout(MultiViewLayout.TWO) },
                            onNavigateDown = {
                                showControlsOverlay = false
                                try { tileFocusRequesters[focusedSlotIndex].requestFocus() } catch (_: Exception) {}
                            }
                        )
                        MultiViewLayoutButton(
                            icon = Icons.Default.Dashboard,
                            contentDescription = "3 Streams",
                            isActive = layout == MultiViewLayout.THREE,
                            onClick = { session.setLayout(MultiViewLayout.THREE) },
                            onNavigateDown = {
                                showControlsOverlay = false
                                try { tileFocusRequesters[focusedSlotIndex].requestFocus() } catch (_: Exception) {}
                            }
                        )
                        MultiViewLayoutButton(
                            icon = Icons.Default.GridView,
                            contentDescription = "4 Grid",
                            isActive = layout == MultiViewLayout.FOUR,
                            onClick = { session.setLayout(MultiViewLayout.FOUR) },
                            onNavigateDown = {
                                showControlsOverlay = false
                                try { tileFocusRequesters[focusedSlotIndex].requestFocus() } catch (_: Exception) {}
                            }
                        )
                        MultiViewLayoutButton(
                            contentDescription = "Spotlight",
                            isActive = layout == MultiViewLayout.SPOTLIGHT,
                            onClick = { session.setLayout(MultiViewLayout.SPOTLIGHT) },
                            onNavigateDown = {
                                showControlsOverlay = false
                                try { tileFocusRequesters[focusedSlotIndex].requestFocus() } catch (_: Exception) {}
                            },
                            customIcon = { tint ->
                                SpotlightLayoutIcon(tint = tint)
                            }
                        )
                        MultiViewLayoutButton(
                            contentDescription = "Picture-in-Picture",
                            isActive = layout == MultiViewLayout.PIP,
                            onClick = { session.setLayout(MultiViewLayout.PIP) },
                            onNavigateDown = {
                                showControlsOverlay = false
                                try { tileFocusRequesters[focusedSlotIndex].requestFocus() } catch (_: Exception) {}
                            },
                            customIcon = { tint ->
                                PipLayoutIcon(tint = tint)
                            }
                        )
                    }
                }
            }
        }

        // Context Action Sheet Modal for Occupied Tile (Long-press / Menu)
        slotForActionMenu?.let { slot ->
            val hasAudio = slot.isAudioActive
            MultiViewTileActionSheet(
                slot = slot,
                hasAudio = hasAudio,
                onPlayAudio = {
                    session.setActiveAudioSlot(slot.index)
                    slotForActionMenu = null
                },
                onChangeChannel = {
                    val idx = slot.index
                    slotForActionMenu = null
                    channelPickerSlotIndex = idx
                },
                onRemove = {
                    session.assignChannel(slot.index, null)
                    slotForActionMenu = null
                },
                onDismiss = { slotForActionMenu = null }
            )
        }

        // TV 2-Column Channel Picker Drawer (Apple TV Parity)
        channelPickerSlotIndex?.let { slotIdx ->
            MultiViewChannelPickerTV(
                slotIndex = slotIdx,
                playlistId = playlistId,
                categories = categories,
                favorites = favoriteChannels,
                recents = recentChannels,
                usedChannelIds = slots.mapNotNull { it.channel?.id }.toSet(),
                onPickChannel = { channel ->
                    session.assignChannel(slotIdx, channel)
                    session.setActiveAudioSlot(slotIdx)
                    channelPickerSlotIndex = null
                },
                onDismiss = { channelPickerSlotIndex = null }
            )
        }
    }
}

@Composable
private fun MultiViewTile(
    modifier: Modifier = Modifier,
    session: MultiViewSession,
    slot: MultiViewSlot,
    isCurrentlyFocused: Boolean,
    showHighlight: Boolean = true,
    focusRequester: FocusRequester,
    onFocus: () -> Unit,
    onPickChannel: () -> Unit,
    onOpenActionMenu: () -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateRight: (() -> Unit)? = null,
    onNavigateLeft: (() -> Unit)? = null,
    onNavigateDown: (() -> Unit)? = null
) {
    val player = remember(slot.index) { session.getOrCreatePlayer(slot.index) }
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused && showHighlight) 1.015f else 1.0f,
        label = "tileScale"
    )

    val hasChannel = slot.channel != null
    val hasAudio = slot.isAudioActive

    val targetBorderColor = when {
        hasChannel && !showHighlight -> Color.Transparent
        isFocused -> Color.White
        hasAudio -> LumeCyan
        else -> Color.White.copy(alpha = 0.12f)
    }

    val targetBorderWidth = when {
        hasChannel && !showHighlight -> 0.dp
        isFocused -> 3.dp
        hasAudio -> 2.dp
        else -> 1.dp
    }

    val borderColor by animateColorAsState(targetValue = targetBorderColor, label = "tileBorderColor")
    val borderWidth by animateDpAsState(targetValue = targetBorderWidth, label = "tileBorderWidth")

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (hasChannel) Modifier.background(Color.Black)
                else Modifier.background(Brush.verticalGradient(listOf(Color(0xFF161A24), Color(0xFF0F1118))))
            )
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .focusRequester(focusRequester)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            if (keyEvent.nativeKeyEvent.repeatCount >= 1) {
                                // Long press detected -> open action menu
                                if (hasChannel) onOpenActionMenu()
                            } else {
                                // Short press -> if empty pick channel, if occupied set audio
                                if (!hasChannel) {
                                    onPickChannel()
                                } else {
                                    if ((session.layout.value == MultiViewLayout.SPOTLIGHT || session.layout.value == MultiViewLayout.PIP) && slot.index != 0) {
                                        session.promoteToStage(slot.index)
                                    } else {
                                        session.setActiveAudioSlot(slot.index)
                                    }
                                }
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            onNavigateUp()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (onNavigateRight != null) {
                                onNavigateRight()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (onNavigateLeft != null) {
                                onNavigateLeft()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (onNavigateDown != null) {
                                onNavigateDown()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_MENU -> {
                            if (hasChannel) onOpenActionMenu()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!hasChannel) {
                    onPickChannel()
                } else {
                    if ((session.layout.value == MultiViewLayout.SPOTLIGHT || session.layout.value == MultiViewLayout.PIP) && slot.index != 0) {
                        session.promoteToStage(slot.index)
                    } else {
                        session.setActiveAudioSlot(slot.index)
                    }
                }
            }
    ) {
        if (slot.channel != null) {
            // Live Video Surface
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Modern Apple TV Glass Capsule Audio & Channel Caption
            AnimatedVisibility(
                visible = showHighlight,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.72f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (hasAudio) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeMute,
                            contentDescription = if (hasAudio) "Audio Active" else "Muted",
                            tint = if (hasAudio) LumeCyan else Color.White.copy(alpha = 0.40f),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = slot.channel.name,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (hasAudio) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(LumeCyan.copy(alpha = 0.22f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "AUDIO",
                                    color = LumeCyan,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Empty Slot Tile: Modern Apple TV [+] Add Channel Card
            val iconBadgeScale by animateFloatAsState(
                targetValue = if (isFocused) 1.15f else 1.0f,
                label = "addBadgeScale"
            )
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .scale(iconBadgeScale)
                        .clip(CircleShape)
                        .background(if (isFocused) Color.White else Color.White.copy(alpha = 0.08f))
                        .border(
                            1.dp,
                            if (isFocused) Color.White else Color.White.copy(alpha = 0.20f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Channel",
                        tint = if (isFocused) Color.Black else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Add Channel",
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.80f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Press Select to browse",
                    color = LumeTextSecondary,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun MultiViewCircleButton(
    icon: ImageVector,
    contentDescription: String,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
    onNavigateDown: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1.0f,
        label = "circleBtnScale"
    )

    Box(
        modifier = Modifier
            .size(34.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(if (isFocused) Color.White else Color.White.copy(alpha = 0.12f))
            .border(
                if (isFocused) 2.dp else 1.dp,
                if (isFocused) Color.White else Color.White.copy(alpha = 0.20f),
                CircleShape
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            onClick()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            onNavigateDown()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isFocused) Color.Black else Color.White,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun SpotlightLayoutIcon(tint: Color, modifier: Modifier = Modifier.size(17.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
        val smallRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())

        // 1 large main stage on the left (occupies ~58% width, full height)
        val stageWidth = w * 0.58f
        drawRoundRect(
            color = tint,
            topLeft = Offset(0f, 0f),
            size = Size(stageWidth, h),
            cornerRadius = cornerRadius
        )

        // 3 stacked small tiles on the right
        val railX = w * 0.68f
        val railWidth = w * 0.32f
        val gap = 1.5.dp.toPx()
        val tileHeight = (h - (gap * 2)) / 3f

        for (i in 0..2) {
            val tileY = i * (tileHeight + gap)
            drawRoundRect(
                color = tint,
                topLeft = Offset(railX, tileY),
                size = Size(railWidth, tileHeight),
                cornerRadius = smallRadius
            )
        }
    }
}

@Composable
private fun PipLayoutIcon(tint: Color, modifier: Modifier = Modifier.size(17.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
        val pipRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())

        // Main fullscreen container background and border
        drawRoundRect(
            color = tint.copy(alpha = 0.30f),
            topLeft = Offset(0f, 0f),
            size = Size(w, h),
            cornerRadius = cornerRadius
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()),
            size = Size(w - 1.dp.toPx(), h - 1.dp.toPx()),
            cornerRadius = cornerRadius,
            style = Stroke(width = 1.2.dp.toPx())
        )

        // Floating PiP overlay in the top-right corner
        val pipWidth = w * 0.36f
        val pipHeight = h * 0.36f
        val pipX = w - pipWidth - 1.2.dp.toPx()
        val pipY = 1.2.dp.toPx()

        drawRoundRect(
            color = tint,
            topLeft = Offset(pipX, pipY),
            size = Size(pipWidth, pipHeight),
            cornerRadius = pipRadius
        )
    }
}

@Composable
private fun MultiViewLayoutButton(
    icon: ImageVector? = null,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit,
    onNavigateDown: () -> Unit,
    customIcon: (@Composable (tint: Color) -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.10f else 1.0f,
        label = "layoutBtnScale"
    )

    val bgColor = when {
        isFocused -> Color.White
        isActive -> Color.White.copy(alpha = 0.40f)
        else -> Color.Transparent
    }

    val iconTint = when {
        isFocused -> Color.Black
        isActive -> Color.Black
        else -> Color.White.copy(alpha = 0.65f)
    }

    val borderStroke = when {
        isFocused -> BorderStroke(2.dp, Color.White)
        isActive -> BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
        else -> BorderStroke(0.dp, Color.Transparent)
    }

    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 30.dp)
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(borderStroke, RoundedCornerShape(8.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            onClick()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            onNavigateDown()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (customIcon != null) {
            customIcon(iconTint)
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun MultiViewTileActionSheet(
    slot: MultiViewSlot,
    hasAudio: Boolean,
    onPlayAudio: () -> Unit,
    onChangeChannel: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        try { firstFocus.requestFocus() } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(340.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF141720))
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
                .padding(20.dp)
                .clickable(enabled = false) {}
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = slot.channel?.name ?: "Tile Options",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (!hasAudio) {
                    ActionSheetButton(
                        title = "Play Audio Here",
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        focusRequester = firstFocus,
                        onClick = onPlayAudio
                    )
                }

                ActionSheetButton(
                    title = "Change Channel",
                    icon = Icons.Default.SwapHoriz,
                    focusRequester = if (hasAudio) firstFocus else null,
                    onClick = onChangeChannel
                )

                ActionSheetButton(
                    title = "Remove Channel",
                    icon = Icons.Default.Delete,
                    isDestructive = true,
                    onClick = onRemove
                )

                ActionSheetButton(
                    title = "Cancel",
                    icon = Icons.Default.Close,
                    onClick = onDismiss
                )
            }
        }
    }
}

@Composable
private fun ActionSheetButton(
    title: String,
    icon: ImageVector,
    isDestructive: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val bgColor = when {
        isFocused && isDestructive -> LumeLiveRed
        isFocused -> Color.White
        else -> Color.Transparent
    }

    val contentColor = when {
        isFocused && isDestructive -> Color.White
        isFocused -> Color.Black
        isDestructive -> LumeLiveRed
        else -> Color.White
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                     keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)) {
                    onClick()
                    true
                } else false
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = contentColor,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            color = contentColor,
            fontSize = 14.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium
        )
    }
}

private enum class PickerColumn { CATEGORIES, CHANNELS }

@Composable
private fun MultiViewChannelPickerTV(
    slotIndex: Int,
    playlistId: String,
    categories: List<CategoryEntity>,
    favorites: List<ChannelEntity>,
    recents: List<ChannelEntity>,
    usedChannelIds: Set<String>,
    onPickChannel: (ChannelEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val db = LumeApp.instance.database
    val scope = rememberCoroutineScope()

    val firstCategoryRequester = remember { FocusRequester() }
    val categoryFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val channelFirstFocusRequester = remember { FocusRequester() }
    val channelListState = rememberLazyListState()

    var selectedSectionId by remember {
        mutableStateOf(
            when {
                favorites.isNotEmpty() -> "fav"
                recents.isNotEmpty() -> "rec"
                categories.isNotEmpty() -> categories.first().id
                else -> null
            }
        )
    }
    var currentColumn by remember { mutableStateOf(PickerColumn.CATEGORIES) }
    var shouldFocusChannels by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        TvFocusRescue.register(firstCategoryRequester)
        onDispose {
            TvFocusRescue.unregister(firstCategoryRequester)
        }
    }

    // Synchronize initial selection once sections/categories are available
    LaunchedEffect(categories, favorites, recents) {
        if (selectedSectionId == null) {
            selectedSectionId = when {
                favorites.isNotEmpty() -> "fav"
                recents.isNotEmpty() -> "rec"
                categories.isNotEmpty() -> categories.first().id
                else -> null
            }
        }
    }

    // Land initial focus on first category reliably
    LaunchedEffect(Unit) {
        for (i in 0..12) {
            delay(35)
            try {
                firstCategoryRequester.requestFocus()
                break
            } catch (_: Exception) {}
        }
    }

    // Reset scroll to top whenever selected section changes
    LaunchedEffect(selectedSectionId) {
        try {
            channelListState.scrollToItem(0)
        } catch (_: Exception) {}
    }

    val enterChannelsColumn: () -> Unit = {
        currentColumn = PickerColumn.CHANNELS
        shouldFocusChannels = true
        scope.launch {
            for (attempt in 0..20) {
                try {
                    channelFirstFocusRequester.requestFocus()
                    shouldFocusChannels = false
                    break
                } catch (_: Exception) {}
                delay(25)
            }
        }
    }

    val restoreCategoryFocus: () -> Unit = {
        currentColumn = PickerColumn.CATEGORIES
        shouldFocusChannels = false
        scope.launch {
            for (attempt in 0..6) {
                try {
                    val req = categoryFocusRequesters[selectedSectionId] ?: firstCategoryRequester
                    req.requestFocus()
                    break
                } catch (_: Exception) {}
                delay(20)
            }
        }
    }

    // Load channels asynchronously per selected category to prevent CursorWindow overflow
    val displayChannels by produceState<List<ChannelEntity>>(
        initialValue = when {
            favorites.isNotEmpty() -> favorites
            recents.isNotEmpty() -> recents
            else -> emptyList()
        },
        selectedSectionId,
        playlistId
    ) {
        val secId = selectedSectionId
        when {
            secId == "fav" -> this.value = favorites
            secId == "rec" -> this.value = recents
            secId != null && playlistId.isNotBlank() -> {
                db.channelDao().getChannelsByCategory(playlistId, secId).collect {
                    this@produceState.value = it
                }
            }
            else -> this.value = emptyList()
        }
    }

    // Trigger focus transfer to channels column as soon as channels are populated
    LaunchedEffect(shouldFocusChannels, displayChannels) {
        if (shouldFocusChannels && displayChannels.isNotEmpty()) {
            for (attempt in 0..15) {
                try {
                    channelFirstFocusRequester.requestFocus()
                    shouldFocusChannels = false
                    break
                } catch (_: Exception) {}
                delay(25)
            }
        }
    }

    // Back handling: If in channels list, Back goes to categories. If in categories, Back closes picker.
    BackHandler {
        if (currentColumn == PickerColumn.CHANNELS) {
            restoreCategoryFocus()
        } else {
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.90f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Column 1: Categories
            Column(
                modifier = Modifier
                    .width(260.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF141720))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(
                    "Categories",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (favorites.isNotEmpty()) {
                        item(key = "fav") {
                            CategoryRow(
                                title = "Favorites (${favorites.size})",
                                isSelected = selectedSectionId == "fav",
                                focusRequester = firstCategoryRequester,
                                onSelect = {
                                    selectedSectionId = "fav"
                                },
                                onRequestMoveToChannels = {
                                    selectedSectionId = "fav"
                                    enterChannelsColumn()
                                }
                            )
                        }
                    }
                    if (recents.isNotEmpty()) {
                        item(key = "rec") {
                            val requester = if (favorites.isEmpty()) firstCategoryRequester else categoryFocusRequesters.getOrPut("rec") { FocusRequester() }
                            CategoryRow(
                                title = "Recently Watched",
                                isSelected = selectedSectionId == "rec",
                                focusRequester = requester,
                                onSelect = {
                                    selectedSectionId = "rec"
                                },
                                onRequestMoveToChannels = {
                                    selectedSectionId = "rec"
                                    enterChannelsColumn()
                                }
                            )
                        }
                    }
                    itemsIndexed(categories) { index, cat ->
                        val isFirst = favorites.isEmpty() && recents.isEmpty() && index == 0
                        val requester = if (isFirst) firstCategoryRequester else categoryFocusRequesters.getOrPut(cat.id) { FocusRequester() }
                        CategoryRow(
                            title = cat.name,
                            isSelected = selectedSectionId == cat.id,
                            focusRequester = requester,
                            onSelect = {
                                selectedSectionId = cat.id
                            },
                            onRequestMoveToChannels = {
                                selectedSectionId = cat.id
                                enterChannelsColumn()
                            }
                        )
                    }
                }
            }

            // Column 2: Channels
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF141720))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Tile #${slotIndex + 1} Channels",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${displayChannels.size} channels",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp
                    )
                }

                if (displayChannels.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(channelFirstFocusRequester)
                            .focusable()
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionLeft) {
                                    restoreCategoryFocus()
                                    true
                                } else false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No channels in this category",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 15.sp
                        )
                    }
                } else {
                    LazyColumn(
                        state = channelListState,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(displayChannels) { idx, channel ->
                            val isUsed = usedChannelIds.contains(channel.id)
                            val itemRequester = remember { FocusRequester() }
                            val effectiveRequester = if (idx == 0) channelFirstFocusRequester else itemRequester
                            ChannelRow(
                                channel = channel,
                                isUsed = isUsed,
                                focusRequester = effectiveRequester,
                                onSelect = {
                                    if (!isUsed) onPickChannel(channel)
                                },
                                onRequestMoveToCategories = restoreCategoryFocus
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(
    title: String,
    isSelected: Boolean,
    focusRequester: FocusRequester,
    onSelect: () -> Unit,
    onRequestMoveToChannels: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.02f else 1.0f, label = "catScale")

    val bgColor = when {
        isFocused -> Color.White
        isSelected -> Color.White.copy(alpha = 0.16f)
        else -> Color.Transparent
    }

    val textColor = when {
        isFocused -> Color(0xFF0B0D12)
        isSelected -> Color.White
        else -> Color.White.copy(alpha = 0.78f)
    }

    val borderStroke = when {
        isSelected && !isFocused -> BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
        else -> BorderStroke(0.dp, Color.Transparent)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(borderStroke, RoundedCornerShape(8.dp))
            .focusRequester(focusRequester)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onSelect()
            }
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_RIGHT,
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            onRequestMoveToChannels()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(
            text = title,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ChannelRow(
    channel: ChannelEntity,
    isUsed: Boolean,
    focusRequester: FocusRequester? = null,
    onSelect: () -> Unit,
    onRequestMoveToCategories: () -> Unit
) {
    val context = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.02f else 1.0f, label = "chanScale")

    val bgColor = when {
        isFocused -> Color.White
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(enabled = true)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            onRequestMoveToCategories()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            if (isUsed) {
                                android.widget.Toast.makeText(context, "Channel already playing in Multi-View", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                onSelect()
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            // Channel Number Badge
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (isFocused) Color.Black.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = channel.channelNumber.toString(),
                    color = if (isFocused) Color(0xFF0B0D12) else Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Channel Logo Thumbnail if available
            if (!channel.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    modifier = Modifier
                        .size(width = 36.dp, height = 24.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Fit
                )
            }

            // Channel Name
            Text(
                text = channel.name,
                color = when {
                    isFocused -> Color(0xFF0B0D12)
                    isUsed -> Color.White.copy(alpha = 0.45f)
                    else -> Color.White
                },
                fontSize = 14.sp,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isUsed) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isFocused) Color.Black.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "Playing",
                    color = if (isFocused) Color(0xFF0B0D12) else LumeAccentBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
