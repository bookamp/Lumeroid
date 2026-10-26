package org.getlumeroid.android.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
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
import org.getlumeroid.android.LumeroidApp
import org.getlumeroid.android.core.designsystem.*
import org.getlumeroid.android.core.player.PlayableMedia
import org.getlumeroid.android.core.player.toPlayableMedia

@Composable
fun MobileSearchScreen(
    onPlayMedia: (PlayableMedia) -> Unit,
    onClose: () -> Unit
) {
    val db = LumeroidApp.instance.database
    val activePlaylist by db.playlistDao().observeActivePlaylist().collectAsState(initial = null)
    val playlistId = activePlaylist?.id ?: ""

    val channels by db.channelDao().observeChannelsByPlaylist(playlistId).collectAsState(initial = emptyList())
    val movies by db.vodDao().getMovies(playlistId).collectAsState(initial = emptyList())
    val series by db.vodDao().getSeries(playlistId).collectAsState(initial = emptyList())

    var query by remember { mutableStateOf("") }
    var selectedScope by remember { mutableStateOf("All") }

    val scopes = listOf("All", "Channels", "Movies", "Series")

    val filteredChannels = remember(channels, query, selectedScope) {
        if (query.isBlank() || (selectedScope != "All" && selectedScope != "Channels")) emptyList()
        else channels.filter { it.name.contains(query, ignoreCase = true) }
    }

    val filteredMovies = remember(movies, query, selectedScope) {
        if (query.isBlank() || (selectedScope != "All" && selectedScope != "Movies")) emptyList()
        else movies.filter { it.name.contains(query, ignoreCase = true) }
    }

    val filteredSeries = remember(series, query, selectedScope) {
        if (query.isBlank() || (selectedScope != "All" && selectedScope != "Series")) emptyList()
        else series.filter { it.name.contains(query, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090B10))
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Search", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search titles, channels...", color = LumeroidTextSecondary, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = LumeroidTextSecondary) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = LumeroidTextSecondary)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF141923),
                unfocusedContainerColor = Color(0xFF141923),
                focusedBorderColor = LumeroidAccentBlue,
                unfocusedBorderColor = Color.White.copy(alpha = 0.10f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            items(scopes) { scope ->
                val isSelected = selectedScope == scope
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) Color.White else Color.White.copy(alpha = 0.08f))
                        .clickable { selectedScope = scope }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(scope, color = if (isSelected) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (query.isBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Type above to search channels, movies & series", color = LumeroidTextSecondary, fontSize = 13.sp)
            }
        } else if (filteredChannels.isEmpty() && filteredMovies.isEmpty() && filteredSeries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No results found for \"$query\"", color = LumeroidTextSecondary, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                if (filteredChannels.isNotEmpty()) {
                    item {
                        Text("CHANNELS (${filteredChannels.size})", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    }
                    items(filteredChannels, key = { "ch_${it.id}" }) { channel ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF141923))
                                .clickable { onPlayMedia(channel.toPlayableMedia(lineup = channels)) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                                if (channel.logoUrl.isNotBlank()) {
                                    AsyncImage(model = channel.logoUrl, contentDescription = null, modifier = Modifier.size(30.dp), contentScale = ContentScale.Fit)
                                } else {
                                    Icon(Icons.Default.Tv, contentDescription = null, tint = Color.White.copy(0.4f), modifier = Modifier.size(20.dp))
                                }
                                Text(channel.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = LumeroidAccentBlue, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                if (filteredMovies.isNotEmpty()) {
                    item {
                        Text("MOVIES (${filteredMovies.size})", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    }
                    items(filteredMovies, key = { "mv_${it.id}" }) { movie ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF141923))
                                .clickable { onPlayMedia(movie.toPlayableMedia()) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                                if (movie.posterUrl.isNotBlank()) {
                                    AsyncImage(model = movie.posterUrl, contentDescription = null, modifier = Modifier.size(30.dp, 44.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(movie.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (movie.releaseDate.isNotBlank()) {
                                        Text(movie.releaseDate.take(4), color = LumeroidTextSecondary, fontSize = 10.5.sp)
                                    }
                                }
                            }
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = LumeroidAccentBlue, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                if (filteredSeries.isNotEmpty()) {
                    item {
                        Text("SERIES (${filteredSeries.size})", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    }
                    items(filteredSeries, key = { "se_${it.id}" }) { s ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF141923))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                                if (s.posterUrl.isNotBlank()) {
                                    AsyncImage(model = s.posterUrl, contentDescription = null, modifier = Modifier.size(30.dp, 44.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
                                }
                                Text(s.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
