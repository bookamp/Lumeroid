package org.getlumeroid.android.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.getlumeroid.android.LumeroidApp
import org.getlumeroid.android.core.data.entities.ChannelEntity
import org.getlumeroid.android.core.designsystem.*
import org.getlumeroid.android.core.network.ESPNClient
import org.getlumeroid.android.core.network.SportsFixture
import org.getlumeroid.android.core.player.PlayableMedia
import org.getlumeroid.android.core.player.toPlayableMedia
import org.getlumeroid.android.core.sports.SportsChannelResolver

@Composable
fun TvSportsScreen(
    onPlayMedia: (PlayableMedia) -> Unit,
    firstItemFocusRequester: FocusRequester = remember { FocusRequester() },
    isTopBarFocused: Boolean = false,
    onMoveUpToTopBar: (() -> Unit)? = null
) {
    val db = LumeroidApp.instance.database
    val activePlaylist by db.playlistDao().observeActivePlaylist().collectAsState(initial = null)
    val allChannels by db.channelDao().observeChannelsByPlaylist(activePlaylist?.id ?: "").collectAsState(initial = emptyList())

    val espnClient = remember { ESPNClient() }
    var selectedLeague by remember { mutableStateOf("soccer/eng.1") }
    var fixtures by remember { mutableStateOf<List<SportsFixture>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedFixtureForChannels by remember { mutableStateOf<SportsFixture?>(null) }
    var isInContentPane by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(firstItemFocusRequester) {
        TvFocusRescue.register(firstItemFocusRequester)
        onDispose {
            TvFocusRescue.unregister(firstItemFocusRequester)
        }
    }

    BackHandler(enabled = !isTopBarFocused) {
        if (selectedFixtureForChannels != null) {
            selectedFixtureForChannels = null
        } else if (isInContentPane) {
            isInContentPane = false
            for (i in 0..5) {
                try {
                    if (firstItemFocusRequester.requestFocus()) break
                } catch (_: Exception) {}
            }
        } else {
            onMoveUpToTopBar?.invoke()
        }
    }

    val leagues = remember {
        listOf(
            "soccer/eng.1" to "Premier League",
            "soccer/uefa.champions" to "Champions League",
            "soccer/esp.1" to "La Liga",
            "basketball/nba" to "NBA",
            "football/nfl" to "NFL",
            "baseball/mlb" to "MLB",
            "hockey/nhl" to "NHL",
            "tennis/atp" to "Tennis",
            "cricket" to "Cricket",
            "racing/f1" to "Formula 1"
        )
    }

    fun loadScores(league: String) {
        coroutineScope.launch {
            isLoading = true
            fixtures = espnClient.fetchScoreboard(league)
            isLoading = false
        }
    }

    LaunchedEffect(selectedLeague) {
        loadScores(selectedLeague)
    }

    // 3 distinct categories: Final, Live, Upcoming
    val liveFixtures = remember(fixtures) {
        fixtures.filter { it.isLive }
    }
    val finalFixtures = remember(fixtures) {
        fixtures.filter { fixture ->
            !fixture.isLive && (
                fixture.statusText.contains("Final", ignoreCase = true) ||
                fixture.statusText.contains("FT", ignoreCase = true) ||
                fixture.statusText.equals("F", ignoreCase = true) ||
                (fixture.homeTeamScore.isNotBlank() && fixture.homeTeamScore != "-")
            )
        }
    }
    val upcomingFixtures = remember(fixtures) {
        fixtures.filter { fixture ->
            !fixture.isLive && !finalFixtures.contains(fixture)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07090E))
            .padding(top = 65.dp, start = 40.dp, end = 40.dp, bottom = 16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // League Selector Pills (without redundant label)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.hasFocus) isInContentPane = false },
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                itemsIndexed(leagues) { _, (code, title) ->
                    val isSelected = selectedLeague == code
                    val interaction = remember { MutableInteractionSource() }
                    val isFocused by interaction.collectIsFocusedAsState()
                    val itemFocusRequester = if (isSelected) firstItemFocusRequester else null

                    Box(
                        modifier = Modifier
                            .then(if (itemFocusRequester != null) Modifier.focusRequester(itemFocusRequester) else Modifier)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                when {
                                    isFocused -> Color.White
                                    isSelected -> Color.White.copy(alpha = 0.22f)
                                    else -> Color.White.copy(alpha = 0.08f)
                                }
                            )
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionUp) {
                                    if (onMoveUpToTopBar != null) {
                                        onMoveUpToTopBar()
                                        true
                                    } else false
                                } else false
                            }
                            .clickable(interactionSource = interaction, indication = null) {
                                selectedLeague = code
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = title,
                            color = if (isFocused) Color.Black else Color.White,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            // Body: Loading / Empty / 3 Named Kanban Columns
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = LumeroidAccentBlue, modifier = Modifier.size(32.dp))
                }
            } else if (fixtures.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "No games scheduled for this competition",
                            color = LumeroidTextSecondary,
                            fontSize = 13.sp
                        )
                        TvSettingsActionButton(
                            title = "Refresh",
                            icon = Icons.Default.Refresh,
                            onClick = { loadScores(selectedLeague) }
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onFocusChanged { if (it.hasFocus) isInContentPane = true },
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Column 1: Final (Graphite / Slate shade)
                    SportsKanbanColumn(
                        title = "FINAL",
                        titleColor = Color(0xFF94A3B8),
                        fixtures = finalFixtures,
                        emptyText = "No completed games",
                        columnBg = Color(0xFF10141D).copy(alpha = 0.50f),
                        cardGradient = listOf(Color(0xFF161B24), Color(0xFF0F1218)),
                        cardBorder = Color.White.copy(alpha = 0.08f),
                        allChannels = allChannels,
                        onFixtureClick = { fixture, channels ->
                            if (channels.size == 1) {
                                onPlayMedia(channels[0].toPlayableMedia())
                            } else if (channels.size > 1) {
                                selectedFixtureForChannels = fixture
                            }
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )

                    // Column 2: Live (Crimson / Live Red shade)
                    SportsKanbanColumn(
                        title = "LIVE",
                        titleColor = LumeroidLiveRed,
                        isLive = true,
                        fixtures = liveFixtures,
                        emptyText = "No games live right now",
                        columnBg = Color(0xFF1B0F12).copy(alpha = 0.50f),
                        cardGradient = listOf(Color(0xFF261217), Color(0xFF160A0D)),
                        cardBorder = LumeroidLiveRed.copy(alpha = 0.22f),
                        allChannels = allChannels,
                        onFixtureClick = { fixture, channels ->
                            if (channels.size == 1) {
                                onPlayMedia(channels[0].toPlayableMedia())
                            } else if (channels.size > 1) {
                                selectedFixtureForChannels = fixture
                            }
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )

                    // Column 3: Upcoming (Deep Sapphire / Navy shade)
                    SportsKanbanColumn(
                        title = "UPCOMING",
                        titleColor = LumeroidAccentBlue,
                        fixtures = upcomingFixtures,
                        emptyText = "No upcoming games",
                        columnBg = Color(0xFF0D1522).copy(alpha = 0.50f),
                        cardGradient = listOf(Color(0xFF121B2B), Color(0xFF0A0F19)),
                        cardBorder = LumeroidAccentBlue.copy(alpha = 0.20f),
                        allChannels = allChannels,
                        onFixtureClick = { fixture, channels ->
                            if (channels.size == 1) {
                                onPlayMedia(channels[0].toPlayableMedia())
                            } else if (channels.size > 1) {
                                selectedFixtureForChannels = fixture
                            }
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
    }

    // Modal channel picker when multiple streams exist for a match
    selectedFixtureForChannels?.let { fixture ->
        val channels = remember(fixture, allChannels) {
            SportsChannelResolver.resolveChannelsForFixture(fixture, allChannels, limit = 8)
        }
        TvSportsChannelDialog(
            fixture = fixture,
            channels = channels,
            onSelect = { channel ->
                selectedFixtureForChannels = null
                onPlayMedia(channel.toPlayableMedia())
            },
            onDismiss = { selectedFixtureForChannels = null }
        )
    }
}

/**
 * Single named column in the 3-column Sports grid with its own color theme.
 */
@Composable
private fun SportsKanbanColumn(
    title: String,
    titleColor: Color,
    fixtures: List<SportsFixture>,
    emptyText: String,
    columnBg: Color,
    cardGradient: List<Color>,
    cardBorder: Color,
    allChannels: List<ChannelEntity>,
    onFixtureClick: (SportsFixture, List<ChannelEntity>) -> Unit,
    isLive: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(columnBg)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Column Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isLive) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(LumeroidLiveRed)
                        )
                    }
                    Text(
                        text = title,
                        color = titleColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                // Count badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${fixtures.size}",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Fixtures in this column
            if (fixtures.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emptyText,
                        color = Color.White.copy(alpha = 0.35f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(fixtures, key = { it.id }) { fixture ->
                        val channels = remember(fixture, allChannels) {
                            SportsChannelResolver.resolveChannelsForFixture(fixture, allChannels, limit = 6)
                        }
                        TvKanbanCard(
                            fixture = fixture,
                            channels = channels,
                            cardGradient = cardGradient,
                            cardBorder = cardBorder,
                            isLive = isLive,
                            onClick = { onFixtureClick(fixture, channels) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Card inside a Kanban column styled with specific column shades.
 */
@Composable
private fun TvKanbanCard(
    fixture: SportsFixture,
    channels: List<ChannelEntity>,
    cardGradient: List<Color>,
    cardBorder: Color,
    isLive: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1.0f,
        animationSpec = tween(130),
        label = "kanbanCardScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(138.dp)
            .zIndex(if (isFocused) 1f else 0f)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = if (isFocused) listOf(
                        Color(0xFF263244),
                        Color(0xFF18202E)
                    ) else cardGradient
                )
            )
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) Color.White else cardBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: League name + status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = fixture.leagueName.take(16).uppercase(),
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    maxLines = 1
                )

                if (isLive || fixture.isLive) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(LumeroidLiveRed)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "LIVE",
                            color = Color.White,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (fixture.statusText.isNotBlank()) {
                    Text(
                        text = fixture.statusText,
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Middle: Logos & Score / VS
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Home Logo
                if (fixture.homeTeamLogo.isNotBlank()) {
                    AsyncImage(
                        model = fixture.homeTeamLogo,
                        contentDescription = fixture.homeTeamName,
                        modifier = Modifier.size(34.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(fixture.homeTeamName.take(2).uppercase(), color = Color.White, fontSize = 9.sp)
                    }
                }

                // Score or VS
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (fixture.homeTeamScore.isNotBlank() && fixture.homeTeamScore != "-") {
                        Text(
                            text = "${fixture.homeTeamScore} - ${fixture.awayTeamScore}",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black
                        )
                    } else {
                        Text(
                            text = "VS",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Away Logo
                if (fixture.awayTeamLogo.isNotBlank()) {
                    AsyncImage(
                        model = fixture.awayTeamLogo,
                        contentDescription = fixture.awayTeamName,
                        modifier = Modifier.size(34.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(fixture.awayTeamName.take(2).uppercase(), color = Color.White, fontSize = 9.sp)
                    }
                }
            }

            // Team Names
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = fixture.homeTeamName,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = fixture.awayTeamName,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End
                )
            }

            // Bottom Row: Matched Playlist Channel Pill
            if (channels.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (isFocused) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.06f))
                        .padding(horizontal = 6.dp, vertical = 2.5.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = LumeroidAccentBlue,
                            modifier = Modifier.size(10.dp)
                        )
                        val channelLabel = if (channels.size == 1) {
                            channels[0].name
                        } else {
                            "${channels[0].name} (+${channels.size - 1})"
                        }
                        Text(
                            text = channelLabel,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
    }
}

/**
 * TV Channel Selection Dialog when a game is airing across multiple streams.
 */
@Composable
private fun TvSportsChannelDialog(
    fixture: SportsFixture,
    channels: List<ChannelEntity>,
    onSelect: (ChannelEntity) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(460.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF141923))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${fixture.homeTeamName} vs ${fixture.awayTeamName}",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Select a channel to watch live",
                        color = LumeroidTextSecondary,
                        fontSize = 11.5.sp
                    )
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 260.dp)
                ) {
                    items(channels) { channel ->
                        val interaction = remember { MutableInteractionSource() }
                        val isFocused by interaction.collectIsFocusedAsState()

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isFocused) Color.White else Color.White.copy(alpha = 0.08f))
                                .clickable(interactionSource = interaction, indication = null) {
                                    onSelect(channel)
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tv,
                                    contentDescription = null,
                                    tint = if (isFocused) Color.Black else LumeroidAccentBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = channel.name,
                                    color = if (isFocused) Color.Black else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
