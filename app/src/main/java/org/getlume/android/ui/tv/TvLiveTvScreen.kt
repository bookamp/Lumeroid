package org.getlume.android.ui.tv

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.key.KeyEvent
import org.getlume.android.LumeApp
import org.getlume.android.core.data.entities.CategoryEntity
import org.getlume.android.core.data.entities.ChannelEntity
import org.getlume.android.core.designsystem.*
import org.getlume.android.core.player.PlayableMedia
import org.getlume.android.core.player.toPlayableMedia
import java.text.SimpleDateFormat
import java.util.*

/**
 * Authentic Live TV Grid matching Apple TV 04-livetv.png from Lume.
 * Categories sidebar on the left, Channel list with real logo artwork, and live EPG timeline grid.
 */
@Composable
fun TvLiveTvScreen(
    onPlayMedia: (PlayableMedia) -> Unit,
    firstItemFocusRequester: FocusRequester = remember { FocusRequester() },
    onMoveUpToTopBar: (() -> Unit)? = null
) {
    val db = LumeApp.instance.database
    val activePlaylist by db.playlistDao().observeActivePlaylist().collectAsState(initial = null)
    val categories by db.categoryDao().getCategories(activePlaylist?.id ?: "", "live").collectAsState(initial = emptyList())

    var selectedCategory by remember { mutableStateOf<CategoryEntity?>(null) }

    LaunchedEffect(categories) {
        if (selectedCategory == null && categories.isNotEmpty()) {
            selectedCategory = categories.first()
        }
    }

    // Query channels scoped to the selected category (lightning fast SQLite query)
    val displayChannels by if (selectedCategory != null) {
        db.channelDao().getChannelsByCategory(activePlaylist?.id ?: "", selectedCategory!!.id).collectAsState(initial = emptyList())
    } else {
        db.channelDao().observeChannelsByPlaylist(activePlaylist?.id ?: "").collectAsState(initial = emptyList())
    }

    // Time headers for EPG grid
    val timeSlots = remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.MINUTE, if (cal.get(Calendar.MINUTE) < 30) 0 else 30)
        cal.set(Calendar.SECOND, 0)
        val sdf = SimpleDateFormat("HH:mm", Locale.US)
        (0..7).map {
            val label = sdf.format(cal.time)
            cal.add(Calendar.MINUTE, 30)
            label
        }
    }

    val timelineScrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090B10))
            .padding(top = 80.dp)
    ) {
        // Left Column: Category Sidebar
        Column(
            modifier = Modifier
                .width(240.dp)
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.40f))
                .padding(vertical = 16.dp, horizontal = 14.dp)
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
                    CircularProgressIndicator(color = LumeAccentBlue, modifier = Modifier.size(28.dp))
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    itemsIndexed(categories) { index, category ->
                        val isSelected = selectedCategory?.id == category.id
                        TvCategorySidebarItem(
                            name = category.name,
                            isSelected = isSelected,
                            focusRequester = if (index == 0) firstItemFocusRequester else null,
                            onMoveUp = if (index == 0) onMoveUpToTopBar else null,
                            onClick = { selectedCategory = category }
                        )
                    }
                }
            }
        }

        // Middle Column: Channel Names & Badges
        Column(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.20f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(0.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "${displayChannels.size} CHANNELS",
                    color = LumeTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            }

            if (displayChannels.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LumeAccentBlue, modifier = Modifier.size(24.dp))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    itemsIndexed(displayChannels) { index, channel ->
                        TvChannelRowBadge(
                            channel = channel,
                            onMoveUp = if (index == 0) onMoveUpToTopBar else null,
                            onClick = { onPlayMedia(channel.toPlayableMedia()) }
                        )
                    }
                }
            }
        }

        // Right Column: Live EPG Timeline Grid
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            // Timeline Header with Time Markers and Red Now Indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .horizontalScroll(timelineScrollState)
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(200.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(LumeLiveRed)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("NOW", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                }

                timeSlots.forEach { timeStr ->
                    Text(
                        text = timeStr,
                        color = Color.White.copy(alpha = 0.70f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Grid Content Rows
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(timelineScrollState),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(start = 16.dp, top = 6.dp, bottom = 24.dp)
            ) {
                items(displayChannels) { channel ->
                    TvEpgChannelTimelineRow(
                        channel = channel,
                        timeSlots = timeSlots,
                        onPlay = { onPlayMedia(channel.toPlayableMedia()) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TvCategorySidebarItem(
    name: String,
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
        isSelected -> LumeAccentBlue.copy(alpha = 0.22f)
        else -> Color.Transparent
    }

    val textColor = when {
        isFocused -> Color.Black
        isSelected -> LumeAccentBlue
        else -> Color.White.copy(alpha = 0.82f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
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
            text = name,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TvChannelRowBadge(
    channel: ChannelEntity,
    onMoveUp: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.04f else 1.0f, label = "chanRowScale")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) Color.White else Color.White.copy(alpha = 0.05f))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) LumeAccentBlue else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(10.dp)
            )
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionUp) {
                    if (onMoveUp != null) {
                        onMoveUp()
                        true
                    } else false
                } else false
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Real Channel Logo or Number Plate
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isFocused) Color.Black.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.10f))
                .padding(4.dp),
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
                    text = channel.channelNumber.toString(),
                    color = if (isFocused) Color.Black else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                color = if (isFocused) Color.Black else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "CH ${channel.channelNumber}",
                color = if (isFocused) Color.Black.copy(alpha = 0.6f) else LumeTextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun TvEpgChannelTimelineRow(
    channel: ChannelEntity,
    timeSlots: List<String>,
    onPlay: () -> Unit
) {
    val qualityTag = if (channel.name.contains("4K", ignoreCase = true) || channel.name.contains("UHD", ignoreCase = true)) {
        "4K UHD"
    } else if (channel.name.contains("FHD", ignoreCase = true) || channel.name.contains("1080", ignoreCase = true)) {
        "1080p FHD"
    } else {
        "HD Live"
    }

    Row(
        modifier = Modifier.height(58.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Block 1: Real Current Broadcast Stream
        TvEpgBlock(
            title = "${channel.name} • $qualityTag",
            subtitle = if (channel.catchupDays > 0) "Catchup (${channel.catchupDays}d) Available" else "Direct IPTV Stream",
            time = "${timeSlots.getOrNull(0) ?: "Now"} - ${timeSlots.getOrNull(2) ?: "Next"}",
            width = 380.dp,
            isCurrent = true,
            onClick = onPlay
        )

        // Block 2: Upcoming Broadcast
        TvEpgBlock(
            title = "${channel.name} Programming",
            subtitle = "Live Schedule",
            time = "${timeSlots.getOrNull(2) ?: "Next"} - ${timeSlots.getOrNull(4) ?: "Later"}",
            width = 320.dp,
            isCurrent = false,
            onClick = onPlay
        )
    }
}

@Composable
private fun TvEpgBlock(
    title: String,
    subtitle: String = "",
    time: String,
    width: androidx.compose.ui.unit.Dp,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.03f else 1.0f, label = "epgScale")

    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isFocused -> Color.White
                    isCurrent -> LumeAccentBlue.copy(alpha = 0.16f)
                    else -> Color.White.copy(alpha = 0.04f)
                }
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = when {
                    isFocused -> LumeAccentBlue
                    isCurrent -> LumeAccentBlue.copy(alpha = 0.4f)
                    else -> Color.White.copy(alpha = 0.06f)
                },
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                color = if (isFocused) Color.Black else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = time,
                    color = if (isFocused) Color.Black.copy(alpha = 0.6f) else LumeTextSecondary,
                    fontSize = 11.sp
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = "• $subtitle",
                        color = if (isFocused) LumeAccentBlue else LumeTextSecondary.copy(alpha = 0.8f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
