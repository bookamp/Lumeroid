package org.getlume.android.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.key.KeyEvent
import org.getlume.android.LumeApp
import org.getlume.android.core.data.entities.ChannelEntity
import org.getlume.android.core.designsystem.*
import org.getlume.android.core.network.ESPNClient
import org.getlume.android.core.network.SportsFixture
import org.getlume.android.core.player.PlayableMedia
import org.getlume.android.core.player.toPlayableMedia
import org.getlume.android.core.sports.SportsChannelResolver

/**
 * Authentic Apple TV Sports Hub Screen for Android TV.
 * Live scores with real-time automatic matching against the user's IPTV playlist channels.
 */
@Composable
fun TvSportsScreen(
    onPlayMedia: (PlayableMedia) -> Unit,
    firstItemFocusRequester: FocusRequester = remember { FocusRequester() },
    onMoveUpToTopBar: (() -> Unit)? = null
) {
    val db = LumeApp.instance.database
    val activePlaylist by db.playlistDao().observeActivePlaylist().collectAsState(initial = null)
    val allChannels by db.channelDao().observeChannelsByPlaylist(activePlaylist?.id ?: "").collectAsState(initial = emptyList())

    val espnClient = remember { ESPNClient() }
    var selectedLeague by remember { mutableStateOf("soccer/eng.1") }
    var fixtures by remember { mutableStateOf<List<SportsFixture>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isInFixturesPane by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Breadcrumb BackHandler: When in fixtures or matched channels list, Back retracts focus up to League pills
    BackHandler(enabled = isInFixturesPane) {
        try {
            firstItemFocusRequester.requestFocus()
        } catch (_: Exception) {}
        isInFixturesPane = false
    }

    // Breadcrumb BackHandler: When in League pills, Back retracts focus up to the TopBar
    BackHandler(enabled = !isInFixturesPane) {
        onMoveUpToTopBar?.invoke()
    }

    val leagues = remember {
        listOf(
            "soccer/eng.1" to "Premier League",
            "soccer/uefa.champions" to "Champions League",
            "soccer/esp.1" to "La Liga",
            "basketball/nba" to "NBA",
            "football/nfl" to "NFL",
            "baseball/mlb" to "MLB",
            "hockey/nhl" to "NHL"
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07090E))
            .padding(top = 80.dp, start = 48.dp, end = 48.dp, bottom = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header: Title & League selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Sports Hub", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                    Text("Live scores & auto-matched IPTV streams", color = LumeTextSecondary, fontSize = 14.sp)
                }

                // League Selector Pills
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.onFocusChanged { if (it.hasFocus) isInFixturesPane = false }
                ) {
                    itemsIndexed(leagues) { index, (code, title) ->
                        val isSelected = selectedLeague == code
                        val interaction = remember { MutableInteractionSource() }
                        val isFocused by interaction.collectIsFocusedAsState()
                        val itemFocusRequester = if (isSelected || index == 0) {
                            firstItemFocusRequester
                        } else null

                        Box(
                            modifier = Modifier
                                .then(if (itemFocusRequester != null) Modifier.focusRequester(itemFocusRequester) else Modifier)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected || isFocused) LumeAccentBlue else Color.White.copy(alpha = 0.10f))
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
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = title,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Main Content: Fixtures with Matched IPTV Channels
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onFocusChanged { if (it.hasFocus) isInFixturesPane = true },
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = LumeAccentBlue)
                }
            } else if (fixtures.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onFocusChanged { if (it.hasFocus) isInFixturesPane = true },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("No live matches right now for this competition", color = LumeTextSecondary, fontSize = 16.sp)
                        TvSettingsActionButton(
                            title = "Refresh Scores",
                            icon = Icons.Default.Refresh,
                            onClick = { loadScores(selectedLeague) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .onFocusChanged { if (it.hasFocus) isInFixturesPane = true },
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 36.dp)
                ) {
                    items(fixtures) { fixture ->
                        val matchedChannels = remember(fixture, allChannels) {
                            SportsChannelResolver.resolveChannelsForFixture(fixture, allChannels, limit = 4)
                        }

                        TvFixtureCard(
                            fixture = fixture,
                            matchedChannels = matchedChannels,
                            onPlayChannel = { channel ->
                                onPlayMedia(channel.toPlayableMedia())
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvFixtureCard(
    fixture: SportsFixture,
    matchedChannels: List<ChannelEntity>,
    onPlayChannel: (ChannelEntity) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.02f else 1.0f, label = "fixScale")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .background(if (isFocused) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) LumeAccentBlue else Color.White.copy(alpha = 0.09f),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Row 1: Matchup Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Home Team
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = fixture.homeTeamName,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    if (fixture.homeTeamLogo.isNotBlank()) {
                        AsyncImage(
                            model = fixture.homeTeamLogo,
                            contentDescription = fixture.homeTeamName,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Score / Status in Center
                Column(
                    modifier = Modifier.width(160.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${fixture.homeTeamScore} - ${fixture.awayTeamScore}",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = fixture.statusText,
                        color = if (fixture.isLive) LumeLiveRed else LumeTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Away Team
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    if (fixture.awayTeamLogo.isNotBlank()) {
                        AsyncImage(
                            model = fixture.awayTeamLogo,
                            contentDescription = fixture.awayTeamName,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = fixture.awayTeamName,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Row 2: Matched IPTV Channels (Click-to-Watch)
            if (matchedChannels.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "ON AIR ON:",
                        color = LumeTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    matchedChannels.forEach { channel ->
                        val chipInteraction = remember { MutableInteractionSource() }
                        val isChipFocused by chipInteraction.collectIsFocusedAsState()

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isChipFocused) Color.White else LumeAccentBlue.copy(alpha = 0.25f))
                                .border(
                                    width = 1.dp,
                                    color = if (isChipFocused) Color.White else LumeAccentBlue.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable(interactionSource = chipInteraction, indication = null) {
                                    onPlayChannel(channel)
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = if (isChipFocused) Color.Black else LumeAccentBlue,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = channel.name,
                                    color = if (isChipFocused) Color.Black else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
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
