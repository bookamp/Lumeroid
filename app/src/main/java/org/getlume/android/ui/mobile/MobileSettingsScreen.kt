package org.getlume.android.ui.mobile

import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.getlume.android.LumeApp
import org.getlume.android.core.data.entities.PlaylistEntity
import org.getlume.android.core.designsystem.*
import org.getlume.android.core.settings.*
import org.getlume.android.core.sync.EpgSyncManager
import org.getlume.android.core.sync.PlaylistSyncManager
import java.io.File
import java.util.UUID

enum class MobileSettingsPage(val title: String) {
    ROOT("Settings"),
    PLAYLISTS("Playlists"),
    EPG("TV Guide"),
    LIBRARY("Library & Languages"),
    HOME("Home Layout"),
    LIVE_TV("Live TV"),
    SPORTS("Sports"),
    PLAYER("Player & Audio"),
    SERVICES("Connected Services"),
    STORAGE("Storage & Cache")
}

@Composable
fun MobileSettingsScreen(
    onDismiss: (() -> Unit)? = null
) {
    var currentPage by remember { mutableStateOf(MobileSettingsPage.ROOT) }

    BackHandler {
        if (currentPage != MobileSettingsPage.ROOT) {
            currentPage = MobileSettingsPage.ROOT
        } else {
            onDismiss?.invoke()
        }
    }

    when (currentPage) {
        MobileSettingsPage.ROOT -> {
            MobileSettingsRootScreen(
                onNavigate = { currentPage = it },
                onDismiss = onDismiss
            )
        }
        MobileSettingsPage.PLAYLISTS -> {
            MobilePlaylistsSubScreen(onBack = { currentPage = MobileSettingsPage.ROOT })
        }
        MobileSettingsPage.EPG -> {
            MobileEpgSubScreen(onBack = { currentPage = MobileSettingsPage.ROOT })
        }
        MobileSettingsPage.LIBRARY -> {
            MobileLibraryLanguagesSubScreen(onBack = { currentPage = MobileSettingsPage.ROOT })
        }
        MobileSettingsPage.HOME -> {
            MobileHomeLayoutSubScreen(onBack = { currentPage = MobileSettingsPage.ROOT })
        }
        MobileSettingsPage.LIVE_TV -> {
            MobileLiveTvSubScreen(onBack = { currentPage = MobileSettingsPage.ROOT })
        }
        MobileSettingsPage.SPORTS -> {
            MobileSportsSubScreen(onBack = { currentPage = MobileSettingsPage.ROOT })
        }
        MobileSettingsPage.PLAYER -> {
            MobilePlayerSubScreen(onBack = { currentPage = MobileSettingsPage.ROOT })
        }
        MobileSettingsPage.SERVICES -> {
            MobileConnectedServicesSubScreen(onBack = { currentPage = MobileSettingsPage.ROOT })
        }
        MobileSettingsPage.STORAGE -> {
            MobileStorageSubScreen(onBack = { currentPage = MobileSettingsPage.ROOT })
        }
    }
}

// ==========================================
// ROOT SETTINGS (Matching iOS SettingsView.swift)
// ==========================================
@Composable
private fun MobileSettingsRootScreen(
    onNavigate: (MobileSettingsPage) -> Unit,
    onDismiss: (() -> Unit)?
) {
    val db = remember { LumeApp.instance.database }
    val activePlaylist by db.playlistDao().observeActivePlaylist().collectAsState(initial = null)
    val settings = remember { AppSettings.get() }

    Scaffold(
        containerColor = Color(0xFF090B10),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0E121A))
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settings",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                if (onDismiss != null) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Group: ACCOUNT
            item {
                IosSettingsGroup {
                    IosSettingsRow(
                        title = "Profiles",
                        icon = Icons.Default.Person,
                        trailingValue = settings.activeProfileName
                    )
                }
            }

            // Group: YOUR CONTENT
            item {
                IosGroupHeader("YOUR CONTENT")
                IosSettingsGroup {
                    IosSettingsRow(
                        title = "Playlists",
                        icon = Icons.Default.Tv,
                        trailingValue = activePlaylist?.name ?: "None",
                        onClick = { onNavigate(MobileSettingsPage.PLAYLISTS) }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                    IosSettingsRow(
                        title = "TV Guide",
                        icon = Icons.Default.CalendarToday,
                        trailingValue = settings.syncInterval.title,
                        onClick = { onNavigate(MobileSettingsPage.EPG) }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                    IosSettingsRow(
                        title = "Library & Languages",
                        icon = Icons.Default.Tune,
                        trailingValue = if (settings.homeContentLanguages.isEmpty()) "All Languages" else "${settings.homeContentLanguages.size} Selected",
                        onClick = { onNavigate(MobileSettingsPage.LIBRARY) }
                    )
                }
            }

            // Group: EXPERIENCE
            item {
                IosGroupHeader("EXPERIENCE")
                IosSettingsGroup {
                    IosSettingsRow(
                        title = "Home Layout",
                        icon = Icons.Default.Home,
                        onClick = { onNavigate(MobileSettingsPage.HOME) }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                    IosSettingsRow(
                        title = "Live TV",
                        icon = Icons.Default.LiveTv,
                        trailingValue = settings.liveSurfMode.title,
                        onClick = { onNavigate(MobileSettingsPage.LIVE_TV) }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                    IosSettingsRow(
                        title = "Sports",
                        icon = Icons.Default.SportsSoccer,
                        trailingValue = if (settings.hideScores) "Scores Hidden" else "Visible",
                        onClick = { onNavigate(MobileSettingsPage.SPORTS) }
                    )
                }
            }

            // Group: PLAYBACK
            item {
                IosGroupHeader("PLAYBACK")
                IosSettingsGroup {
                    IosSettingsRow(
                        title = "Player & Audio",
                        icon = Icons.Default.PlayCircle,
                        trailingValue = settings.playerEngine.title.substringBefore(" ("),
                        onClick = { onNavigate(MobileSettingsPage.PLAYER) }
                    )
                }
            }

            // Group: ACCOUNTS & SYNC
            item {
                IosGroupHeader("ACCOUNTS & SYNC")
                IosSettingsGroup {
                    IosSettingsRow(
                        title = "Connected Services",
                        icon = Icons.Default.Sync,
                        trailingValue = if (settings.isTraktConnected) "Trakt Active" else "None",
                        onClick = { onNavigate(MobileSettingsPage.SERVICES) }
                    )
                }
            }

            // Group: SYSTEM
            item {
                IosGroupHeader("SYSTEM")
                IosSettingsGroup {
                    IosSettingsRow(
                        title = "Storage & Cache",
                        icon = Icons.Default.Storage,
                        onClick = { onNavigate(MobileSettingsPage.STORAGE) }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                    IosSettingsRow(
                        title = "Help & Feedback",
                        icon = Icons.Default.HelpOutline
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                    IosSettingsRow(
                        title = "About Lume",
                        icon = Icons.Default.Info,
                        trailingValue = "v1.0.0"
                    )
                }
            }
        }
    }
}

// ==========================================
// SUB-SCREEN: PLAYLISTS
// ==========================================
@Composable
private fun MobilePlaylistsSubScreen(onBack: () -> Unit) {
    val db = remember { LumeApp.instance.database }
    val scope = rememberCoroutineScope()
    val allPlaylists by db.playlistDao().getAllPlaylists().collectAsState(initial = emptyList())
    val isSyncing by PlaylistSyncManager.isSyncing.collectAsState()
    val syncMsg by PlaylistSyncManager.syncProgressMessage.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var playlistToDelete by remember { mutableStateOf<PlaylistEntity?>(null) }

    SubScreenScaffold(title = "Playlists", onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Quick connect button if empty
            if (allPlaylists.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141923))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("No Playlists Configured", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Add your IPTV provider using Xtream Codes credentials or an M3U playlist URL.", color = LumeTextSecondary, fontSize = 12.sp)
                        Button(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val pl = PlaylistEntity(
                                        id = "82f6e8d7-96c3-48c2-a1f1-2313fc8e7834",
                                        name = "My IPTV",
                                        type = "xtream",
                                        serverUrl = "http://192.168.1.235:8082",
                                        username = "appa",
                                        password = "amma",
                                        epgUrl = "",
                                        isActive = true
                                    )
                                    db.playlistDao().insertPlaylist(pl)
                                    db.playlistDao().setActivePlaylist(pl.id)
                                    ContentFilterCache.rebuildAsync(pl.id)
                                    PlaylistSyncManager.syncPlaylist(pl)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = LumeAccentBlue),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Auto-Connect Local Provider (My IPTV)", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            IosSettingsGroup {
                allPlaylists.forEachIndexed { index, pl ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    db.playlistDao().setActivePlaylist(pl.id)
                                    ContentFilterCache.rebuildAsync(pl.id)
                                }
                            }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            RadioButton(
                                selected = pl.isActive,
                                onClick = {
                                    scope.launch {
                                        db.playlistDao().setActivePlaylist(pl.id)
                                        ContentFilterCache.rebuildAsync(pl.id)
                                    }
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = LumeAccentBlue)
                            )
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(pl.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    if (pl.isActive) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(LumeAccentBlue.copy(alpha = 0.2f))
                                                .padding(horizontal = 6.dp, vertical = 1.dp)
                                        ) {
                                            Text("ACTIVE", color = LumeAccentBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Text(pl.serverUrl, color = LumeTextSecondary, fontSize = 11.5.sp, maxLines = 1)
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { PlaylistSyncManager.syncPlaylistAsync(pl) },
                                enabled = !isSyncing
                            ) {
                                if (isSyncing && pl.isActive) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = LumeAccentBlue, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Sync, contentDescription = "Sync", tint = LumeAccentBlue, modifier = Modifier.size(20.dp))
                                }
                            }
                            IconButton(onClick = { playlistToDelete = pl }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    if (index < allPlaylists.size - 1) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                    }
                }
            }

            if (isSyncing) {
                Text(syncMsg ?: "Syncing catalog...", color = LumeAccentBlue, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
            }

            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = LumeAccentBlue),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add IPTV Playlist", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showAddDialog) {
        MobileAddPlaylistDialog(
            onDismiss = { showAddDialog = false },
            onPlaylistAdded = { showAddDialog = false }
        )
    }

    if (playlistToDelete != null) {
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            containerColor = Color(0xFF141923),
            title = { Text("Delete Playlist", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Remove '${playlistToDelete?.name}' from this device?", color = LumeTextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        val pl = playlistToDelete
                        playlistToDelete = null
                        if (pl != null) scope.launch { db.playlistDao().deletePlaylist(pl) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LumeLiveRed)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) { Text("Cancel", color = Color.White) }
            }
        )
    }
}

// ==========================================
// SUB-SCREEN: TV GUIDE (EPG)
// ==========================================
@Composable
private fun MobileEpgSubScreen(onBack: () -> Unit) {
    val settings = remember { AppSettings.get() }
    val scope = rememberCoroutineScope()
    val db = remember { LumeApp.instance.database }
    val activePlaylist by db.playlistDao().observeActivePlaylist().collectAsState(initial = null)

    var syncInterval by remember { mutableStateOf(settings.syncInterval) }
    var timeShift by remember { mutableStateOf(settings.epgTimeShiftHours) }
    var customUrl by remember { mutableStateOf(settings.customEpgUrl) }
    var isSyncing by remember { mutableStateOf(false) }
    var syncStatus by remember { mutableStateOf("EPG database ready") }

    SubScreenScaffold(title = "TV Guide (EPG)", onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            IosSettingsGroup {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Database Sync", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(syncStatus, color = LumeTextSecondary, fontSize = 11.5.sp)
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    isSyncing = true
                                    syncStatus = "Downloading guide..."
                                    val res = EpgSyncManager.syncNow(playlist = activePlaylist)
                                    syncStatus = res.fold(
                                        onSuccess = { "Synced $it programmes" },
                                        onFailure = { it.message ?: "Sync error" }
                                    )
                                    isSyncing = false
                                }
                            },
                            enabled = !isSyncing,
                            colors = ButtonDefaults.buttonColors(containerColor = LumeAccentBlue),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text("Sync Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            IosGroupHeader("SYNC FREQUENCY")
            IosSettingsGroup {
                SyncInterval.values().forEachIndexed { index, interval ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                syncInterval = interval
                                settings.syncInterval = interval
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(interval.title, color = Color.White, fontSize = 13.5.sp)
                        if (syncInterval == interval) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = LumeAccentBlue, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (index < SyncInterval.values().size - 1) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                    }
                }
            }

            IosGroupHeader("TIME SHIFT (HOURS)")
            IosSettingsGroup {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Timezone Offset", color = Color.White, fontSize = 13.5.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconButton(
                            onClick = {
                                timeShift -= 1
                                settings.epgTimeShiftHours = timeShift
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("-", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("${timeShift}h", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        IconButton(
                            onClick = {
                                timeShift += 1
                                settings.epgTimeShiftHours = timeShift
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("+", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            IosGroupHeader("CUSTOM XMLTV URL")
            IosSettingsGroup {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = {
                            customUrl = it
                            settings.customEpgUrl = it.trim()
                        },
                        placeholder = { Text("Default (from playlist provider)", color = Color.White.copy(alpha = 0.3f), fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = LumeAccentBlue,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                        ),
                        singleLine = true
                    )
                }
            }
        }
    }
}

// ==========================================
// SUB-SCREEN: LIBRARY & CONTENT LANGUAGES FILTER
// ==========================================
@Composable
private fun MobileLibraryLanguagesSubScreen(onBack: () -> Unit) {
    val settings = remember { AppSettings.get() }
    var activeLanguages by remember { mutableStateOf(settings.homeContentLanguages) }
    var searchQuery by remember { mutableStateOf("") }

    val allCatalog = remember { ContentLanguageCatalog.allLanguages }
    val filteredLanguages = remember(searchQuery) {
        if (searchQuery.isBlank()) allCatalog else allCatalog.filter {
            it.displayName.contains(searchQuery, ignoreCase = true) || it.code.contains(searchQuery, ignoreCase = true)
        }
    }

    SubScreenScaffold(title = "Content Languages", onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Description & Quick Action Chips
            Text(
                "Filter your catalog by language. When none are selected, all available content is displayed.",
                color = LumeTextSecondary,
                fontSize = 12.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val newSet = allCatalog.map { it.code }.toSet()
                        activeLanguages = newSet
                        settings.homeContentLanguages = newSet
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Select All", color = Color.White, fontSize = 11.5.sp)
                }
                Button(
                    onClick = {
                        activeLanguages = emptySet()
                        settings.homeContentLanguages = emptySet()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Clear All", color = Color.White, fontSize = 11.5.sp)
                }
            }

            // Search Filter
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search languages...", color = Color.White.copy(alpha = 0.35f), fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = LumeTextSecondary, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = LumeAccentBlue,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                )
            )

            // Language Checklist
            IosSettingsGroup {
                filteredLanguages.forEachIndexed { index, lang ->
                    val isChecked = activeLanguages.contains(lang.code) || activeLanguages.contains(lang.displayName)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val current = activeLanguages.toMutableSet()
                                if (isChecked) {
                                    current.remove(lang.code)
                                    current.remove(lang.displayName)
                                } else {
                                    current.add(lang.code)
                                }
                                activeLanguages = current
                                settings.homeContentLanguages = current
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(lang.displayName, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                            Text(lang.code.uppercase(), color = LumeTextSecondary, fontSize = 10.5.sp)
                        }
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                val current = activeLanguages.toMutableSet()
                                if (checked) current.add(lang.code) else current.remove(lang.code)
                                activeLanguages = current
                                settings.homeContentLanguages = current
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = LumeAccentBlue,
                                uncheckedColor = Color.White.copy(alpha = 0.4f)
                            )
                        )
                    }
                    if (index < filteredLanguages.size - 1) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                    }
                }
            }
        }
    }
}

// ==========================================
// SUB-SCREEN: HOME LAYOUT
// ==========================================
@Composable
private fun MobileHomeLayoutSubScreen(onBack: () -> Unit) {
    val settings = remember { AppSettings.get() }
    var cw by remember { mutableStateOf(settings.homeShowContinueWatching) }
    var sports by remember { mutableStateOf(settings.homeShowSports) }
    var movies by remember { mutableStateOf(settings.homeShowRecentlyAdded) }
    var series by remember { mutableStateOf(settings.homeShowForYou) }
    var live by remember { mutableStateOf(settings.homeShowLiveTV) }

    SubScreenScaffold(title = "Home Layout", onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            IosGroupHeader("RAILS VISIBILITY")
            IosSettingsGroup {
                ToggleRow("Continue Watching", "Unfinished movies and episodes", cw) {
                    cw = it
                    settings.homeShowContinueWatching = it
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                ToggleRow("Live Sports Rail", "Live matches and schedules", sports) {
                    sports = it
                    settings.homeShowSports = it
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                ToggleRow("Trending Movies Rail", "Popular VOD catalog titles", movies) {
                    movies = it
                    settings.homeShowRecentlyAdded = it
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                ToggleRow("Popular TV Series Rail", "Top serialized shows", series) {
                    series = it
                    settings.homeShowForYou = it
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                ToggleRow("Top Live TV Channels", "Featured live broadcasts", live) {
                    live = it
                    settings.homeShowLiveTV = it
                }
            }
        }
    }
}

// ==========================================
// SUB-SCREEN: LIVE TV
// ==========================================
@Composable
private fun MobileLiveTvSubScreen(onBack: () -> Unit) {
    val settings = remember { AppSettings.get() }
    var surfMode by remember { mutableStateOf(settings.liveSurfMode) }
    var favs by remember { mutableStateOf(settings.showsFavoritesInLiveTV) }
    var recents by remember { mutableStateOf(settings.showsRecentlyWatchedInLiveTV) }

    SubScreenScaffold(title = "Live TV", onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            IosGroupHeader("CHANNEL SURF ORDER")
            IosSettingsGroup {
                LiveSurfMode.values().forEachIndexed { index, mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                surfMode = mode
                                settings.liveSurfMode = mode
                            }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(mode.title, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                            Text(mode.subtitle, color = LumeTextSecondary, fontSize = 11.sp)
                        }
                        if (surfMode == mode) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = LumeAccentBlue, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (index < LiveSurfMode.values().size - 1) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                    }
                }
            }

            IosGroupHeader("RAILS")
            IosSettingsGroup {
                ToggleRow("Show Favorites Rail", "Place starred channels at top", favs) {
                    favs = it
                    settings.showsFavoritesInLiveTV = it
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                ToggleRow("Show Recently Watched", "Recent channel zapping history", recents) {
                    recents = it
                    settings.showsRecentlyWatchedInLiveTV = it
                }
            }
        }
    }
}

// ==========================================
// SUB-SCREEN: SPORTS
// ==========================================
@Composable
private fun MobileSportsSubScreen(onBack: () -> Unit) {
    val settings = remember { AppSettings.get() }
    var sportsEnabled by remember { mutableStateOf(settings.sportsHubEnabled) }
    var hideScores by remember { mutableStateOf(settings.hideScores) }

    SubScreenScaffold(title = "Sports", onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            IosSettingsGroup {
                ToggleRow("Enable Sports Hub", "Access live schedules and scores", sportsEnabled) {
                    sportsEnabled = it
                    settings.sportsHubEnabled = it
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                ToggleRow("Hide Scores (Spoiler Protection)", "Conceals match scores and goal counts", hideScores) {
                    hideScores = it
                    settings.hideScores = it
                }
            }
        }
    }
}

// ==========================================
// SUB-SCREEN: PLAYER & AUDIO
// ==========================================
@Composable
private fun MobilePlayerSubScreen(onBack: () -> Unit) {
    val settings = remember { AppSettings.get() }
    var engine by remember { mutableStateOf(settings.playerEngine) }
    var buffer by remember { mutableStateOf(settings.bufferSize) }
    var tunneled by remember { mutableStateOf(settings.tunneledVideoPlayback) }
    var passthrough by remember { mutableStateOf(settings.audioPassthrough) }
    var autoNext by remember { mutableStateOf(settings.autoPlayNext) }
    var skipIntro by remember { mutableStateOf(settings.showSkipIntro) }

    SubScreenScaffold(title = "Player & Audio", onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            IosGroupHeader("VIDEO DECODER ENGINE")
            IosSettingsGroup {
                PlayerEngineType.values().forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                engine = item
                                settings.playerEngine = item
                            }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                            Text(item.subtitle, color = LumeTextSecondary, fontSize = 11.sp)
                        }
                        if (engine == item) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = LumeAccentBlue, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (index < PlayerEngineType.values().size - 1) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                    }
                }
            }

            IosGroupHeader("BUFFER LATENCY PROFILE")
            IosSettingsGroup {
                BufferSizeType.values().forEachIndexed { index, b ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                buffer = b
                                settings.bufferSize = b
                            }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(b.title, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                            Text(b.subtitle, color = LumeTextSecondary, fontSize = 11.sp)
                        }
                        if (buffer == b) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = LumeAccentBlue, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (index < BufferSizeType.values().size - 1) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                    }
                }
            }

            IosGroupHeader("ADVANCED PLAYBACK")
            IosSettingsGroup {
                ToggleRow("Tunneled Video Playback", "Direct A/V hardware tunneling", tunneled) {
                    tunneled = it
                    settings.tunneledVideoPlayback = it
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                ToggleRow("Audio Passthrough", "Bitstream Dolby Digital / DTS", passthrough) {
                    passthrough = it
                    settings.audioPassthrough = it
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                ToggleRow("Auto-Play Next Episode", "Load next episode automatically", autoNext) {
                    autoNext = it
                    settings.autoPlayNext = it
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
                ToggleRow("Show Skip Intro Button", "Displays skip prompt during intros", skipIntro) {
                    skipIntro = it
                    settings.showSkipIntro = it
                }
            }
        }
    }
}

// ==========================================
// SUB-SCREEN: CONNECTED SERVICES
// ==========================================
@Composable
private fun MobileConnectedServicesSubScreen(onBack: () -> Unit) {
    val settings = remember { AppSettings.get() }
    var traktScrobble by remember { mutableStateOf(settings.traktScrobble) }
    var simklScrobble by remember { mutableStateOf(settings.simklScrobble) }

    SubScreenScaffold(title = "Connected Services", onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            IosGroupHeader("TRAKT.TV")
            IosSettingsGroup {
                ToggleRow("Trakt Scrobbling", "Automatically track watched movies and series", traktScrobble) {
                    traktScrobble = it
                    settings.traktScrobble = it
                }
            }

            IosGroupHeader("SIMKL")
            IosSettingsGroup {
                ToggleRow("Simkl Scrobbling", "Sync watchlist and history with Simkl", simklScrobble) {
                    simklScrobble = it
                    settings.simklScrobble = it
                }
            }
        }
    }
}

// ==========================================
// SUB-SCREEN: STORAGE & CACHE
// ==========================================
@Composable
private fun MobileStorageSubScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cacheSizeMb by remember { mutableStateOf(0.0) }
    var isClearing by remember { mutableStateOf(false) }

    fun refreshSizes() {
        scope.launch(Dispatchers.IO) {
            fun getFolderSize(file: File): Long {
                if (!file.exists()) return 0L
                var total = 0L
                if (file.isDirectory) file.listFiles()?.forEach { total += getFolderSize(it) } else total += file.length()
                return total
            }
            val totalCacheBytes = getFolderSize(context.cacheDir)
            withContext(Dispatchers.Main) {
                cacheSizeMb = totalCacheBytes / (1024.0 * 1024.0)
            }
        }
    }

    LaunchedEffect(Unit) { refreshSizes() }

    SubScreenScaffold(title = "Storage & Cache", onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            IosSettingsGroup {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Temporary Media Cache", color = Color.White, fontSize = 13.5.sp)
                    Text(String.format("%.1f MB", cacheSizeMb), color = LumeTextSecondary, fontSize = 13.sp)
                }
            }

            Button(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        isClearing = true
                        try {
                            context.cacheDir.deleteRecursively()
                            context.cacheDir.mkdirs()
                        } catch (_: Exception) {}
                        withContext(Dispatchers.Main) {
                            isClearing = false
                            refreshSizes()
                        }
                    }
                },
                enabled = !isClearing,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isClearing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Clear Image & Stream Cache", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}

// ==========================================
// REUSABLE UI HELPERS (Matching iOS Design System)
// ==========================================
@Composable
private fun SubScreenScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Scaffold(
        containerColor = Color(0xFF090B10),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0E121A))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp)
        ) {
            item { content() }
        }
    }
}

@Composable
private fun IosGroupHeader(title: String) {
    Text(
        text = title,
        color = Color.White.copy(alpha = 0.45f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
    )
}

@Composable
private fun IosSettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141923)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Column(content = content)
    }
}

@Composable
private fun IosSettingsRow(
    title: String,
    icon: ImageVector,
    trailingValue: String? = null,
    tint: Color = LumeAccentBlue,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            }
            Text(title, color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (trailingValue != null) {
                Text(trailingValue, color = LumeTextSecondary, fontSize = 12.5.sp)
            }
            if (onClick != null) {
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, color = LumeTextSecondary, fontSize = 10.5.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = LumeAccentBlue,
                uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
            )
        )
    }
}

@Composable
private fun MobileAddPlaylistDialog(
    onDismiss: () -> Unit,
    onPlaylistAdded: () -> Unit
) {
    val db = remember { LumeApp.instance.database }
    val scope = rememberCoroutineScope()

    var isXtream by remember { mutableStateOf(true) }
    var playlistName by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var m3uUrl by remember { mutableStateOf("") }
    var epgUrl by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = { if (!isLoading) onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161C27)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Add IPTV Playlist",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .padding(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isXtream) LumeAccentBlue else Color.Transparent)
                            .clickable { isXtream = true }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Xtream Codes", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (!isXtream) LumeAccentBlue else Color.Transparent)
                            .clickable { isXtream = false }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("M3U URL", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text("Playlist Name") },
                    placeholder = { Text("My IPTV") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isXtream) {
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text("Server URL") },
                        placeholder = { Text("http://provider.tv:8080") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = m3uUrl,
                        onValueChange = { m3uUrl = it },
                        label = { Text("M3U Playlist URL") },
                        placeholder = { Text("http://example.com/playlist.m3u") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = epgUrl,
                        onValueChange = { epgUrl = it },
                        label = { Text("EPG URL (Optional)") },
                        placeholder = { Text("http://example.com/guide.xml.gz") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (errorMessage != null) {
                    Text(errorMessage ?: "", color = LumeLiveRed, fontSize = 12.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, enabled = !isLoading) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val targetUrl = if (isXtream) serverUrl.trim() else m3uUrl.trim()
                            if (targetUrl.isBlank()) {
                                errorMessage = "Please enter a valid URL"
                                return@Button
                            }
                            isLoading = true
                            errorMessage = null

                            scope.launch(Dispatchers.IO) {
                                try {
                                    val playlistId = UUID.randomUUID().toString()
                                    val playlist = PlaylistEntity(
                                        id = playlistId,
                                        name = playlistName.trim().ifBlank { if (isXtream) "Xtream IPTV" else "M3U Playlist" },
                                        type = if (isXtream) "xtream" else "m3u",
                                        serverUrl = targetUrl,
                                        username = username.trim(),
                                        password = password.trim(),
                                        epgUrl = epgUrl.trim(),
                                        isActive = true
                                    )
                                    db.playlistDao().insertPlaylist(playlist)
                                    db.playlistDao().setActivePlaylist(playlistId)
                                    ContentFilterCache.rebuildAsync(playlistId)
                                    PlaylistSyncManager.syncPlaylist(playlist)

                                    withContext(Dispatchers.Main) {
                                        isLoading = false
                                        onPlaylistAdded()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isLoading = false
                                        errorMessage = e.localizedMessage ?: "Failed to save playlist"
                                    }
                                }
                            }
                        },
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = LumeAccentBlue)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Connect & Sync", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
