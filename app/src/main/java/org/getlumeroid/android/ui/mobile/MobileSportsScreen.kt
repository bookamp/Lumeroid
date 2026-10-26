package org.getlumeroid.android.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun MobileSportsScreen(
    onPlayMedia: (PlayableMedia) -> Unit
) {
    val db = LumeroidApp.instance.database
    val activePlaylist by db.playlistDao().observeActivePlaylist().collectAsState(initial = null)
    val allChannels by db.channelDao().observeChannelsByPlaylist(activePlaylist?.id ?: "").collectAsState(initial = emptyList())

    val espnClient = remember { ESPNClient() }
    var selectedLeague by remember { mutableStateOf("soccer/eng.1") }
    var fixtures by remember { mutableStateOf<List<SportsFixture>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

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

    fun load(code: String) {
        coroutineScope.launch {
            isLoading = true
            fixtures = espnClient.fetchScoreboard(code)
            isLoading = false
        }
    }

    LaunchedEffect(selectedLeague) {
        load(selectedLeague)
    }

    val liveFixtures = remember(fixtures) { fixtures.filter { it.isLive } }
    val otherFixtures = remember(fixtures) { fixtures.filter { !it.isLive } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090B10))
    ) {
        // League selector pills
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            itemsIndexed(leagues) { _, (code, title) ->
                val isSelected = selectedLeague == code
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) Color.White else Color.White.copy(alpha = 0.08f))
                        .clickable { selectedLeague = code }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = title,
                        color = if (isSelected) Color.Black else Color.White,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LumeroidAccentBlue, modifier = Modifier.size(32.dp))
            }
        } else if (fixtures.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No fixtures scheduled right now", color = LumeroidTextSecondary, fontSize = 13.sp)
                    Button(
                        onClick = { load(selectedLeague) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f))
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Refresh", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (liveFixtures.isNotEmpty()) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(LumeroidLiveRed))
                            Text("LIVE MATCHES", color = LumeroidLiveRed, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        }
                    }
                    items(liveFixtures, key = { "live_${it.id}" }) { fixture ->
                        val matchedChannels = remember(fixture, allChannels) {
                            SportsChannelResolver.resolveChannelsForFixture(fixture, allChannels, limit = 2)
                        }
                        MobileFixtureCard(
                            fixture = fixture,
                            matchedChannel = matchedChannels.firstOrNull(),
                            onWatch = {
                                matchedChannels.firstOrNull()?.let { ch ->
                                    onPlayMedia(ch.toPlayableMedia())
                                }
                            }
                        )
                    }
                }

                if (otherFixtures.isNotEmpty()) {
                    item {
                        Text(
                            text = if (liveFixtures.isEmpty()) "FIXTURES" else "UPCOMING & COMPLETED",
                            color = LumeroidTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(otherFixtures, key = { "fix_${it.id}" }) { fixture ->
                        val matchedChannels = remember(fixture, allChannels) {
                            SportsChannelResolver.resolveChannelsForFixture(fixture, allChannels, limit = 2)
                        }
                        MobileFixtureCard(
                            fixture = fixture,
                            matchedChannel = matchedChannels.firstOrNull(),
                            onWatch = {
                                matchedChannels.firstOrNull()?.let { ch ->
                                    onPlayMedia(ch.toPlayableMedia())
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileFixtureCard(
    fixture: SportsFixture,
    matchedChannel: ChannelEntity?,
    onWatch: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141923))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(fixture.leagueName.uppercase(), color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                if (fixture.isLive) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(LumeroidLiveRed).padding(horizontal = 5.dp, vertical = 1.5.dp)) {
                        Text("LIVE", color = Color.White, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (fixture.statusText.isNotBlank()) {
                    Text(fixture.statusText, color = LumeroidTextSecondary, fontSize = 9.5.sp)
                }
            }

            // Teams and scores
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    if (fixture.homeTeamLogo.isNotBlank()) {
                        AsyncImage(model = fixture.homeTeamLogo, contentDescription = null, modifier = Modifier.size(24.dp), contentScale = ContentScale.Fit)
                    }
                    Text(fixture.homeTeamName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(if (fixture.homeTeamScore.isNotBlank()) fixture.homeTeamScore else "-", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    if (fixture.awayTeamLogo.isNotBlank()) {
                        AsyncImage(model = fixture.awayTeamLogo, contentDescription = null, modifier = Modifier.size(24.dp), contentScale = ContentScale.Fit)
                    }
                    Text(fixture.awayTeamName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(if (fixture.awayTeamScore.isNotBlank()) fixture.awayTeamScore else "-", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            }

            // Matched stream watch pill
            if (matchedChannel != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(onClick = onWatch)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = LumeroidAccentBlue, modifier = Modifier.size(12.dp))
                        Text(matchedChannel.name, color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
