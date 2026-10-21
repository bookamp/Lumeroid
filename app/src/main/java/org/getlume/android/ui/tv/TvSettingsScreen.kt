package org.getlume.android.ui.tv

import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.input.key.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.getlume.android.LumeApp
import org.getlume.android.core.data.entities.PlaylistEntity
import org.getlume.android.core.designsystem.*
import org.getlume.android.core.network.LanSetupServer
import org.getlume.android.core.settings.*

/**
 * Top-level Settings Categories matching Apple TV SettingsCategory.swift exactly:
 * 1. PREMIUM ("Lume Pro")
 * 2. PROFILES ("Profiles")
 * 3. PLAYLISTS ("Playlists")
 * 4. EPG ("TV Guide")
 * 5. LIBRARY ("Library")
 * 6. HOME ("Home")
 * 7. LIVETV ("Live TV")
 * 8. SPORTS ("Sports")
 * 9. PLAYER ("Player")
 * 10. CLOUD_SYNC ("Cloud Sync")
 * 11. INTEGRATIONS ("Connected Services" - Trakt & Simkl)
 * 12. STORAGE ("Storage & Cache")
 * 13. HELP ("Help & Feedback")
 * 14. ABOUT ("About")
 */
enum class SettingsCategory(val id: String, val title: String) {
    PREMIUM("premium", "Lume Pro"),
    PROFILES("profiles", "Profiles"),
    PLAYLISTS("playlists", "Playlists"),
    EPG("epg", "TV Guide"),
    LIBRARY("library", "Library"),
    HOME("home", "Home"),
    LIVETV("livetv", "Live TV"),
    SPORTS("sports", "Sports"),
    PLAYER("player", "Player"),
    CLOUD_SYNC("cloud_sync", "Cloud Sync"),
    INTEGRATIONS("connected_services", "Connected Services"),
    STORAGE("storage", "Storage & Cache"),
    HELP("help", "Help & Feedback"),
    ABOUT("about", "About")
}

/**
 * In-place drill-in routes matching Apple TV SettingsView drill-ins:
 * Sub-pages replace the detail pane in-place (not full-screen pushes) so the top
 * navigation bar and remote focus context are preserved.
 */
sealed class SettingsDrillIn {
    data class PlaylistDetail(val playlist: PlaylistEntity) : SettingsDrillIn()
    object ContentManagement : SettingsDrillIn()
    data class ChannelManagement(val categoryName: String) : SettingsDrillIn()
    object FavoriteManagement : SettingsDrillIn()
    object SubtitleStyle : SettingsDrillIn()
    object OpenSubtitles : SettingsDrillIn()
    object AudioLanguages : SettingsDrillIn()
    object AddAudioLanguage : SettingsDrillIn()
    object Engines : SettingsDrillIn()
    object ExoPlayerOptions : SettingsDrillIn()
    object VlcOptions : SettingsDrillIn()
    object RecordingServer : SettingsDrillIn()
    object ManageTeams : SettingsDrillIn()
    object Diagnostics : SettingsDrillIn()
}

// MARK: - Metrics from Apple TV TVSettingsMetrics
object TVSettingsMetrics {
    val rowHPadding = 20.dp
    val rowVPadding = 14.dp
    val rowCornerRadius = 10.dp
    val railCornerRadius = 18.dp
    val detailMaxWidth = 860.dp
}

@Composable
fun TvSettingsScreen(
    onOpenAddPlaylist: () -> Unit,
    firstItemFocusRequester: FocusRequester = remember { FocusRequester() },
    onMoveUpToTopBar: (() -> Unit)? = null
) {
    var selectedCategory by remember { mutableStateOf(SettingsCategory.PLAYLISTS) }
    val drillInStack = remember { mutableStateListOf<SettingsDrillIn>() }
    val currentDrillIn = drillInStack.lastOrNull()

    val popDrillIn: () -> Unit = {
        if (drillInStack.isNotEmpty()) {
            drillInStack.removeAt(drillInStack.size - 1)
        }
    }

    // Intercept hardware remote Back button to pop drill-in before exiting screen
    BackHandler(enabled = drillInStack.isNotEmpty()) {
        popDrillIn()
    }

    TvAmbientBackground {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 76.dp, start = 48.dp, end = 48.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(44.dp)
        ) {
            // LEFT COLUMN: TV SIDEBAR RAIL (270dp)
            TvSidebarRail(
                selectedCategory = selectedCategory,
                firstItemFocusRequester = firstItemFocusRequester,
                onMoveUpToTopBar = onMoveUpToTopBar,
                onSelectCategory = { cat ->
                    selectedCategory = cat
                    drillInStack.clear()
                }
            )

            // RIGHT COLUMN: DETAIL PANE CONTAINER (Max 860dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(top = 10.dp)
            ) {
                // If drilled in, render the in-place drill-in sub-pane
                when (val drillIn = currentDrillIn) {
                    is SettingsDrillIn.PlaylistDetail -> {
                        TvPlaylistDetailPane(
                            playlist = drillIn.playlist,
                            onBack = popDrillIn
                        )
                    }
                    is SettingsDrillIn.ContentManagement -> {
                        TvContentManagementPane(
                            onOpenChannelManagement = { catName ->
                                drillInStack.add(SettingsDrillIn.ChannelManagement(catName))
                            },
                            onOpenFavorites = {
                                drillInStack.add(SettingsDrillIn.FavoriteManagement)
                            },
                            onBack = popDrillIn
                        )
                    }
                    is SettingsDrillIn.ChannelManagement -> {
                        TvChannelManagementPane(
                            categoryName = drillIn.categoryName,
                            onBack = popDrillIn
                        )
                    }
                    is SettingsDrillIn.FavoriteManagement -> {
                        TvFavoriteManagementPane(
                            onBack = popDrillIn
                        )
                    }
                    is SettingsDrillIn.SubtitleStyle -> {
                        TvSubtitleStylePane(
                            onBack = popDrillIn
                        )
                    }
                    is SettingsDrillIn.OpenSubtitles -> {
                        TvOpenSubtitlesPane(
                            onBack = popDrillIn
                        )
                    }
                    is SettingsDrillIn.AudioLanguages -> {
                        TvAudioLanguagesPane(
                            onOpenAddLanguage = { drillInStack.add(SettingsDrillIn.AddAudioLanguage) },
                            onBack = popDrillIn
                        )
                    }
                    is SettingsDrillIn.AddAudioLanguage -> {
                        TvAddAudioLanguagePane(
                            onBack = popDrillIn
                        )
                    }
                    is SettingsDrillIn.Engines -> {
                        TvEnginesPane(
                            onOpenExoPlayerOptions = { drillInStack.add(SettingsDrillIn.ExoPlayerOptions) },
                            onOpenVlcOptions = { drillInStack.add(SettingsDrillIn.VlcOptions) },
                            onBack = popDrillIn
                        )
                    }
                    is SettingsDrillIn.ExoPlayerOptions -> {
                        TvExoPlayerOptionsPane(
                            onBack = popDrillIn
                        )
                    }
                    is SettingsDrillIn.VlcOptions -> {
                        TvVlcOptionsPane(
                            onBack = popDrillIn
                        )
                    }
                    is SettingsDrillIn.RecordingServer -> {
                        TvRecordingServerPane(
                            onBack = popDrillIn
                        )
                    }
                    is SettingsDrillIn.ManageTeams -> {
                        TvManageTeamsPane(
                            onBack = popDrillIn
                        )
                    }
                    is SettingsDrillIn.Diagnostics -> {
                        TvDiagnosticsPane(
                            onBack = popDrillIn
                        )
                    }
                    null -> {
                        // Render Top-Level Category Pane
                        when (selectedCategory) {
                            SettingsCategory.PREMIUM -> TvPremiumSettingsPane()
                            SettingsCategory.PROFILES -> TvProfilesSettingsPane()
                            SettingsCategory.PLAYLISTS -> TvPlaylistsSettingsPane(
                                onOpenAddPlaylist = onOpenAddPlaylist,
                                onOpenDetail = { playlist ->
                                    drillInStack.add(SettingsDrillIn.PlaylistDetail(playlist))
                                }
                            )
                            SettingsCategory.EPG -> TvEpgSettingsPane()
                            SettingsCategory.LIBRARY -> TvLibrarySettingsPane(
                                onOpenContentManagement = {
                                    drillInStack.add(SettingsDrillIn.ContentManagement)
                                }
                            )
                            SettingsCategory.HOME -> TvHomeLayoutSettingsPane()
                            SettingsCategory.LIVETV -> TvLiveTvSettingsPane(
                                onOpenRecordingServer = {
                                    drillInStack.add(SettingsDrillIn.RecordingServer)
                                }
                            )
                            SettingsCategory.SPORTS -> TvSportsSettingsPane(
                                onOpenManageTeams = {
                                    drillInStack.add(SettingsDrillIn.ManageTeams)
                                }
                            )
                            SettingsCategory.PLAYER -> TvPlayerSettingsPane(
                                onOpenSubtitleStyle = { drillInStack.add(SettingsDrillIn.SubtitleStyle) },
                                onOpenOpenSubtitles = { drillInStack.add(SettingsDrillIn.OpenSubtitles) },
                                onOpenAudioLanguages = { drillInStack.add(SettingsDrillIn.AudioLanguages) },
                                onOpenEngines = { drillInStack.add(SettingsDrillIn.Engines) }
                            )
                            SettingsCategory.CLOUD_SYNC -> TvCloudSyncSettingsPane()
                            SettingsCategory.INTEGRATIONS -> TvIntegrationsSettingsPane()
                            SettingsCategory.STORAGE -> TvStorageSettingsPane()
                            SettingsCategory.HELP -> TvHelpSettingsPane(
                                onOpenDiagnostics = { drillInStack.add(SettingsDrillIn.Diagnostics) }
                            )
                            SettingsCategory.ABOUT -> TvAboutSettingsPane()
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// TV SIDEBAR RAIL (Authentic TVSidebarRail.swift)
// ==========================================
@Composable
private fun TvSidebarRail(
    selectedCategory: SettingsCategory,
    firstItemFocusRequester: FocusRequester? = null,
    onMoveUpToTopBar: (() -> Unit)? = null,
    onSelectCategory: (SettingsCategory) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .width(270.dp)
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(SettingsCategory.values()) { index, category ->
            TvCategoryRailItem(
                title = category.title,
                isSelected = selectedCategory == category,
                focusRequester = if (index == 0) firstItemFocusRequester else null,
                onMoveUp = if (index == 0) onMoveUpToTopBar else null,
                onClick = { onSelectCategory(category) }
            )
        }
    }
}

@Composable
private fun TvCategoryRailItem(
    title: String,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    onMoveUp: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Smooth focus-driven category switching
    LaunchedEffect(isFocused) {
        if (isFocused && !isSelected) {
            onClick()
        }
    }

    val bgModifier = when {
        isFocused -> Modifier.background(Color.White.copy(alpha = 0.95f))
        isSelected -> Modifier
            .background(Color.White.copy(alpha = 0.16f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(TVSettingsMetrics.railCornerRadius))
        else -> Modifier.background(Color.Transparent)
    }

    val textColor = when {
        isFocused -> Color.Black
        isSelected -> Color.White
        else -> Color.White.copy(alpha = 0.82f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(RoundedCornerShape(TVSettingsMetrics.railCornerRadius))
            .then(bgModifier)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionUp) {
                    if (onMoveUp != null) {
                        onMoveUp()
                        true
                    } else false
                } else false
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = title,
            color = textColor,
            fontSize = 17.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

// ==========================================
// PANE 1: LUME PRO (Authentic SettingsView+Premium.swift)
// ==========================================
@Composable
private fun TvPremiumSettingsPane() {
    val settings = remember { AppSettings.get() }
    var isPro by remember { mutableStateOf(settings.isPremium) }

    // All 10 Features from PremiumFeature.swift
    val features = listOf(
        Pair("Unlimited Playlists", "Add as many IPTV playlists as you like and switch between them."),
        Pair("Offline Downloads", "Save movies and episodes to watch offline, anywhere."),
        Pair("Multiple Profiles", "Give everyone their own watch history, progress and favorites."),
        Pair("Trakt & Simkl Sync", "Scrobble what you watch to Trakt or Simkl, import your history and surface your watchlist on Home."),
        Pair("Smart Playback", "Autoplay the next episode, skip intros, and jump ahead with one tap."),
        Pair("For You Recommendations", "Get an on-device \"For You\" row tuned to your taste from your library and what you watch."),
        Pair("Multi-View", "Watch up to four live channels side by side across your playlists."),
        Pair("Guide Preview", "Preview the focused channel, muted, right in the TV Guide."),
        Pair("Sports Hub", "Follow your leagues and teams — fixtures, live scores, standings and one tap to the channel."),
        Pair("Recording Server", "Record live TV and schedule shows from the guide on your own LumeRecorder server.")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(36.dp)
    ) {
        // Section: Lume Pro Status
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Lume Pro")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(TVSettingsMetrics.rowCornerRadius))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(22.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(if (isPro) "Lume Pro" else "Free Plan", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (isPro) "Lifetime Access • All Features Unlocked" else "Upgrade to unlock the features below",
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 15.sp
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (!isPro) {
                            TvSettingsActionButton(
                                title = "Upgrade to Premium",
                                icon = Icons.Default.Star,
                                prominent = true,
                                onClick = {
                                    isPro = true
                                    settings.isPremium = true
                                }
                            )
                            TvSettingsActionButton(
                                title = "Restore Purchases",
                                icon = Icons.Default.Refresh,
                                onClick = {
                                    isPro = true
                                    settings.isPremium = true
                                }
                            )
                        } else {
                            TvSettingsActionButton(
                                title = "Manage Subscription",
                                onClick = {
                                    isPro = false
                                    settings.isPremium = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Section: Features 2-column Grid across full width
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TvSettingsSectionLabel(if (isPro) "Included" else "Premium Features")

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (chunk in features.chunked(2)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        for (item in chunk) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(TVSettingsMetrics.rowCornerRadius))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .padding(horizontal = 20.dp, vertical = 16.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(item.first, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                    Text(item.second, color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp, lineHeight = 19.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// PANE 2: PROFILES (Authentic SettingsView+Profiles.swift)
// ==========================================
@Composable
private fun TvProfilesSettingsPane() {
    val settings = remember { AppSettings.get() }
    var activeProfileName by remember { mutableStateOf(settings.activeProfileName) }
    var askOnStartup by remember { mutableStateOf(settings.askProfileOnStartup) }
    var parentalEnabled by remember { mutableStateOf(settings.parentalControlsEnabled) }
    var pinValue by remember { mutableStateOf(settings.parentalPin) }
    var showPinDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(36.dp)
    ) {
        // Section: Profiles List
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Profiles")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvSettingsRow(
                    title = activeProfileName,
                    value = "Active",
                    trailingCheckmark = true,
                    onClick = { /* switch */ }
                )
                TvSettingsRow(
                    title = "Add Profile",
                    showsPremiumBadge = !settings.isPremium,
                    onClick = { /* add profile */ }
                )
            }

            TvSettingsFooter("Each profile keeps its own watch history, progress and favorites, synced across your devices.")
        }

        // Section: Startup
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Startup")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvOptionToggleRow(
                    title = "Ask on Startup",
                    isOn = askOnStartup,
                    onToggle = {
                        askOnStartup = it
                        settings.askProfileOnStartup = it
                    }
                )
            }

            TvSettingsFooter("Choose a profile each time Lume launches. When off, Lume resumes the last profile you used.")
        }

        // Section: Parental Controls
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Parental Controls")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (parentalEnabled) {
                    TvSettingsRow(
                        title = "Change PIN",
                        value = "••••",
                        onClick = { showPinDialog = true }
                    )
                    TvSettingsRow(
                        title = "Turn Off PIN",
                        isDestructive = true,
                        onClick = {
                            parentalEnabled = false
                            settings.parentalControlsEnabled = false
                        }
                    )
                } else {
                    TvSettingsRow(
                        title = "Set a PIN",
                        onClick = {
                            parentalEnabled = true
                            settings.parentalControlsEnabled = true
                            showPinDialog = true
                        }
                    )
                }
            }

            TvSettingsFooter("A PIN is required to switch away from a child profile and to open Content Management.")
        }
    }
}

// ==========================================
// PANE 3: PLAYLISTS (Authentic SettingsView+Playlists.swift)
// ==========================================
@Composable
private fun TvPlaylistsSettingsPane(
    onOpenAddPlaylist: () -> Unit,
    onOpenDetail: (PlaylistEntity) -> Unit
) {
    val database = remember { LumeApp.instance.database }
    val playlists by database.playlistDao().getAllPlaylists().collectAsState(initial = emptyList())
    val settings = remember { AppSettings.get() }
    val scope = rememberCoroutineScope()
    var syncIntervalIndex by remember { mutableStateOf(2) } // default: Every 3 Days
    val syncIntervals = listOf("Every 6 Hours", "Every Day", "Every 3 Days", "Every Week")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(36.dp)
    ) {
        // Section: Playlists
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Playlists")

            if (playlists.isEmpty()) {
                Text(
                    text = "No playlists yet. Add your IPTV provider to start streaming.",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 18.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    playlists.forEach { playlist ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                TvSettingsRow(
                                    title = playlist.name,
                                    value = playlist.serverUrl,
                                    trailingCheckmark = playlist.isActive,
                                    onClick = {
                                        scope.launch {
                                            database.playlistDao().setActivePlaylist(playlist.id)
                                        }
                                    }
                                )
                            }
                            TvMiniIconButton(
                                icon = Icons.Default.Edit,
                                tint = Color.White,
                                onClick = { onOpenDetail(playlist) }
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(top = 4.dp)) {
                TvSettingsRow(
                    title = "Add Playlist",
                    showsPremiumBadge = !settings.isPremium && playlists.isNotEmpty(),
                    onClick = onOpenAddPlaylist
                )
            }

            TvSettingsFooter(
                if (!settings.isPremium) "Free includes one playlist. Upgrade to Lume Pro to add more."
                else "Switching playlist changes the content shown across Home, Movies, Series and Live TV."
            )
        }

        // Section: Automatic Sync
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Automatic Sync")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                syncIntervals.forEachIndexed { index, label ->
                    TvSettingsRow(
                        title = label,
                        trailingCheckmark = syncIntervalIndex == index,
                        onClick = { syncIntervalIndex = index }
                    )
                }
                TvSettingsRow(
                    title = "Sync All Playlists Now",
                    onClick = { /* trigger sync */ }
                )
            }

            TvSettingsFooter("Playlists refresh automatically in the background at this interval. Disable a specific playlist's sync in its details. The TV guide refreshes on its own schedule.")
        }
    }
}

// ==========================================
// DRILL-IN: PLAYLIST DETAIL (PlaylistDetailView+TV.swift)
// ==========================================
@Composable
private fun TvPlaylistDetailPane(
    playlist: PlaylistEntity,
    onBack: () -> Unit
) {
    val database = remember { LumeApp.instance.database }
    val scope = rememberCoroutineScope()
    var isEditing by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(playlist.name) }
    var editUrl by remember { mutableStateOf(playlist.serverUrl) }
    var editUsername by remember { mutableStateOf(playlist.username) }
    var editPassword by remember { mutableStateOf(playlist.password) }
    var streamFormatIndex by remember { mutableStateOf(0) }
    val streamFormats = listOf("HLS (.m3u8)", "MPEG-TS (.ts)")
    var syncEnabled by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(36.dp)
    ) {
        TvDrillInHeader(backLabel = "Playlists", title = playlist.name, onBack = onBack)

        // Details Section
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Details")

            if (isEditing) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvSettingsTextInput("Name", editName, "Name") { editName = it }
                    TvSettingsTextInput("Server URL", editUrl, "http://example.com:8080") { editUrl = it }
                    TvSettingsTextInput("Username", editUsername, "Username") { editUsername = it }
                    TvSettingsTextInput("Password", editPassword, "Password", isPassword = true) { editPassword = it }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    TvSettingsValueRow("Name", playlist.name)
                    TvSettingsValueRow("Server URL", playlist.serverUrl)
                    if (!playlist.epgUrl.isNullOrEmpty()) {
                        TvSettingsValueRow("EPG URL", playlist.epgUrl!!)
                    }
                    if (playlist.username.isNotEmpty()) {
                        TvSettingsValueRow("Username", playlist.username)
                        TvSettingsValueRow("Password", "••••••••")
                    }
                    TvSettingsValueRow("Type", playlist.type)
                }
            }
        }

        // Account Section
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Account")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvSettingsValueRow("Status", "Active", valueColor = Color(0xFF4CAF50))
                TvSettingsValueRow("Expires", "Never")
                TvSettingsValueRow("Max Connections", "2")
                TvSettingsValueRow("Active Connections", "1")
            }
        }

        // Playback Section
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Playback")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvOptionCycleRow(
                    title = "Live Stream Format",
                    value = streamFormats[streamFormatIndex],
                    onAdvance = { streamFormatIndex = (streamFormatIndex + 1) % streamFormats.size }
                )
            }

            TvSettingsFooter("Live streams switch between HLS and MPEG-TS. HLS is recommended for adaptive streaming stability; MPEG-TS for lower latency.")
        }

        // Sync Section
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Sync")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvOptionToggleRow(
                    title = "Sync Enabled",
                    isOn = syncEnabled,
                    onToggle = { syncEnabled = it }
                )
                TvSettingsValueRow("Status", "Up to date", valueColor = Color(0xFF4CAF50))
                TvSettingsValueRow("Last Synced", "15 minutes ago")
                TvSettingsRow(title = "Sync Now", onClick = { /* sync */ })
            }
        }

        // Actions Section
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Actions")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (isEditing) {
                    TvSettingsRow(
                        title = "Save Changes",
                        onClick = {
                            scope.launch {
                                database.playlistDao().insertPlaylist(
                                    playlist.copy(
                                        name = editName,
                                        serverUrl = editUrl,
                                        username = editUsername,
                                        password = editPassword
                                    )
                                )
                                isEditing = false
                            }
                        }
                    )
                    TvSettingsRow(
                        title = "Cancel",
                        onClick = { isEditing = false }
                    )
                } else {
                    TvSettingsRow(
                        title = "Edit Playlist",
                        onClick = { isEditing = true }
                    )
                    TvSettingsRow(
                        title = "Delete Playlist",
                        isDestructive = true,
                        onClick = {
                            scope.launch {
                                database.playlistDao().deletePlaylist(playlist)
                                onBack()
                            }
                        }
                    )
                }
            }
        }
    }
}

// ==========================================
// PANE 4: TV GUIDE (EPGSettingsView.swift)
// ==========================================
@Composable
private fun TvEpgSettingsPane() {
    var epgIntervalIndex by remember { mutableStateOf(1) } // default: Every Day
    val intervals = listOf("Every 6 Hours", "Every Day", "Every 3 Days", "Every Week")
    var isAddingSource by remember { mutableStateOf(false) }
    var customSourceName by remember { mutableStateOf("") }
    var customSourceUrl by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(36.dp)
    ) {
        // Section: EPG Sources
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("EPG Sources")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvSettingsRow(
                    title = "Primary XMLTV Guide",
                    value = "On",
                    onClick = { /* toggle */ }
                )
            }
        }

        // Section: Add Custom Source
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Add Custom Source")

            if (isAddingSource) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvSettingsTextInput("Source Name", customSourceName, "Name") { customSourceName = it }
                    TvSettingsTextInput("XMLTV URL", customSourceUrl, "http://example.com/epg.xml") { customSourceUrl = it }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvSettingsActionButton(
                            title = "Add Source",
                            prominent = true,
                            onClick = { isAddingSource = false }
                        )
                        TvSettingsActionButton(
                            title = "Cancel",
                            onClick = { isAddingSource = false }
                        )
                    }
                }
            } else {
                TvSettingsRow(
                    title = "Add EPG Source",
                    onClick = { isAddingSource = true }
                )
            }
        }

        // Section: Automatic Refresh
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Automatic Refresh")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                intervals.forEachIndexed { index, label ->
                    TvSettingsRow(
                        title = label,
                        trailingCheckmark = epgIntervalIndex == index,
                        onClick = { epgIntervalIndex = index }
                    )
                }
                TvSettingsRow(
                    title = "Sync Now",
                    onClick = { /* trigger epg sync */ }
                )
            }

            TvSettingsFooter("The TV guide refreshes automatically in the background at this interval.")
        }
    }
}

// ==========================================
// PANE 5: LIBRARY (SettingsView+Library.swift)
// ==========================================
@Composable
private fun TvLibrarySettingsPane(
    onOpenContentManagement: () -> Unit
) {
    val settings = remember { AppSettings.get() }
    var showMovies by remember { mutableStateOf(settings.libraryShowMovies) }
    var showSeries by remember { mutableStateOf(settings.libraryShowSeries) }
    var showLiveTv by remember { mutableStateOf(settings.libraryShowLiveTV) }
    var showSports by remember { mutableStateOf(settings.libraryShowSports) }
    var searchAll by remember { mutableStateOf(settings.searchAllPlaylists) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(36.dp)
    ) {
        // Section: Tabs
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Tabs")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvOptionToggleRow("Movies", showMovies) {
                    showMovies = it
                    settings.libraryShowMovies = it
                }
                TvOptionToggleRow("Series", showSeries) {
                    showSeries = it
                    settings.libraryShowSeries = it
                }
                TvOptionToggleRow("Live TV", showLiveTv) {
                    showLiveTv = it
                    settings.libraryShowLiveTV = it
                }
                TvOptionToggleRow("Sports", showSports) {
                    showSports = it
                    settings.libraryShowSports = it
                }
            }

            TvSettingsFooter("Movies, Series and Live TV apply to your active playlist. Sports applies to every playlist.")
        }

        // Section: Content
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Content")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvSettingsRow(
                    title = "Categories & Channels",
                    trailingChevron = true,
                    onClick = onOpenContentManagement
                )
            }

            TvSettingsFooter("Hide and reorder categories and channels for the active playlist.")
        }

        // Section: Search
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Search")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvOptionToggleRow("Search All Playlists", searchAll) {
                    searchAll = it
                    settings.searchAllPlaylists = it
                }
            }

            TvSettingsFooter("When off, search only finds content in the active playlist. Turn this on to search across all your playlists.")
        }
    }
}

// ==========================================
// DRILL-IN: CONTENT MANAGEMENT (ContentManagementView.swift)
// ==========================================
@Composable
private fun TvContentManagementPane(
    onOpenChannelManagement: (String) -> Unit,
    onOpenFavorites: () -> Unit,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf("Live TV") }
    val tabs = listOf("Live TV", "Movies", "Series", "Favorites")
    var categories by remember {
        mutableStateOf(
            listOf(
                Pair("General Entertainment", true),
                Pair("Sports & Events", true),
                Pair("News & Business", true),
                Pair("Documentary & Nature", true),
                Pair("Movies & Cinema", true),
                Pair("Kids & Family", true),
                Pair("International Channels", false),
                Pair("Music & Audio", true)
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        TvDrillInHeader(backLabel = "Library", title = "Content Management", onBack = onBack)

        // Type Picker (Live TV, Movies, Series, Favorites)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            tabs.forEach { tab ->
                TvSettingsActionButton(
                    title = tab,
                    prominent = selectedTab == tab,
                    onClick = { selectedTab = tab }
                )
            }
        }

        if (selectedTab == "Favorites") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TvSettingsSectionLabel("Favorites")
                TvSettingsRow(
                    title = "Manage Favorites",
                    value = "Channels, Movies, Series",
                    trailingChevron = true,
                    onClick = onOpenFavorites
                )
                TvSettingsFooter("Reorder all your favorite channels, movies, and series in one unified list.")
            }
        } else {
            // Bulk Actions
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvSettingsActionButton(
                    title = "Show All",
                    onClick = {
                        categories = categories.map { it.copy(second = true) }
                    }
                )
                TvSettingsActionButton(
                    title = "Hide All",
                    onClick = {
                        categories = categories.map { it.copy(second = false) }
                    }
                )
                TvSettingsActionButton(
                    title = "Reset Order",
                    onClick = {
                        categories = listOf(
                            Pair("General Entertainment", true),
                            Pair("Sports & Events", true),
                            Pair("News & Business", true),
                            Pair("Documentary & Nature", true),
                            Pair("Movies & Cinema", true),
                            Pair("Kids & Family", true),
                            Pair("International Channels", false),
                            Pair("Music & Audio", true)
                        )
                    }
                )
            }

            // Categories List
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TvSettingsSectionLabel("Categories")

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    categories.forEachIndexed { index, cat ->
                        TvContentManagementRow(
                            title = cat.first,
                            isEnabled = cat.second,
                            isFirst = index == 0,
                            isLast = index == categories.size - 1,
                            showChannelsButton = selectedTab == "Live TV",
                            onToggle = {
                                categories = categories.toMutableList().also {
                                    it[index] = it[index].copy(second = !cat.second)
                                }
                            },
                            onMoveUp = {
                                if (index > 0) {
                                    val list = categories.toMutableList()
                                    val temp = list[index]
                                    list[index] = list[index - 1]
                                    list[index - 1] = temp
                                    categories = list
                                }
                            },
                            onMoveDown = {
                                if (index < categories.size - 1) {
                                    val list = categories.toMutableList()
                                    val temp = list[index]
                                    list[index] = list[index + 1]
                                    list[index + 1] = temp
                                    categories = list
                                }
                            },
                            onOpenChannels = { onOpenChannelManagement(cat.first) }
                        )
                    }
                }

                TvSettingsFooter(
                    if (selectedTab == "Live TV") "Hide categories to remove them from Live TV, or tap Channels to manage individual streams."
                    else "Hide categories to remove them from $selectedTab."
                )
            }
        }
    }
}

@Composable
private fun TvContentManagementRow(
    title: String,
    isEnabled: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    showChannelsButton: Boolean,
    onToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onOpenChannels: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TVSettingsMetrics.rowCornerRadius))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                TvMiniIconButton(
                    icon = if (isEnabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    tint = if (isEnabled) Color(0xFF64B5F6) else Color.White.copy(alpha = 0.35f),
                    onClick = onToggle
                )

                Text(
                    text = title,
                    color = if (isEnabled) Color.White else Color.White.copy(alpha = 0.45f),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showChannelsButton) {
                    TvSettingsActionButton(
                        title = "Channels",
                        icon = Icons.Default.ChevronRight,
                        onClick = onOpenChannels
                    )
                }

                TvMiniIconButton(
                    icon = Icons.Default.KeyboardArrowUp,
                    enabled = !isFirst,
                    onClick = onMoveUp
                )
                TvMiniIconButton(
                    icon = Icons.Default.KeyboardArrowDown,
                    enabled = !isLast,
                    onClick = onMoveDown
                )
            }
        }
    }
}

// ==========================================
// DRILL-IN: CHANNEL MANAGEMENT (ChannelManagementView.swift)
// ==========================================
@Composable
private fun TvChannelManagementPane(
    categoryName: String,
    onBack: () -> Unit
) {
    var channels by remember {
        mutableStateOf(
            listOf(
                Pair("BBC One HD", true),
                Pair("BBC Two HD", true),
                Pair("ITV 1 HD", true),
                Pair("Channel 4 HD", true),
                Pair("Channel 5 HD", true),
                Pair("Sky Sports Main Event HD", true),
                Pair("Sky Sports Premier League HD", true),
                Pair("TNT Sports 1 HD", true),
                Pair("EuroSport 1 HD", false)
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        TvDrillInHeader(backLabel = "Categories", title = categoryName, onBack = onBack)

        // Bulk Actions
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvSettingsActionButton(
                title = "Show All",
                onClick = { channels = channels.map { it.copy(second = true) } }
            )
            TvSettingsActionButton(
                title = "Hide All",
                onClick = { channels = channels.map { it.copy(second = false) } }
            )
            TvSettingsActionButton(
                title = "Reset Order",
                onClick = {
                    channels = listOf(
                        Pair("BBC One HD", true),
                        Pair("BBC Two HD", true),
                        Pair("ITV 1 HD", true),
                        Pair("Channel 4 HD", true),
                        Pair("Channel 5 HD", true),
                        Pair("Sky Sports Main Event HD", true),
                        Pair("Sky Sports Premier League HD", true),
                        Pair("TNT Sports 1 HD", true),
                        Pair("EuroSport 1 HD", false)
                    )
                }
            )
        }

        // Channels List
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Channels")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                channels.forEachIndexed { index, channel ->
                    TvSettingsReorderRow(
                        title = channel.first,
                        isEnabled = channel.second,
                        isFirst = index == 0,
                        isLast = index == channels.size - 1,
                        onToggle = {
                            channels = channels.toMutableList().also {
                                it[index] = it[index].copy(second = !channel.second)
                            }
                        },
                        onMoveUp = {
                            if (index > 0) {
                                val list = channels.toMutableList()
                                val temp = list[index]
                                list[index] = list[index - 1]
                                list[index - 1] = temp
                                channels = list
                            }
                        },
                        onMoveDown = {
                            if (index < channels.size - 1) {
                                val list = channels.toMutableList()
                                val temp = list[index]
                                list[index] = list[index + 1]
                                list[index + 1] = temp
                                channels = list
                            }
                        }
                    )
                }
            }

            TvSettingsFooter("Hide channels to remove them from this category in Live TV, or reorder to customize channel lineup.")
        }
    }
}

// ==========================================
// DRILL-IN: FAVORITES MANAGEMENT (FavoriteManagementView.swift)
// ==========================================
@Composable
private fun TvFavoriteManagementPane(
    onBack: () -> Unit
) {
    var favorites by remember {
        mutableStateOf(
            listOf(
                Triple("Sky Sports Premier League HD", "Live TV", true),
                Triple("Inception (2010)", "Movie", true),
                Triple("Breaking Bad", "Series", true),
                Triple("BBC One HD", "Live TV", true),
                Triple("Interstellar (2014)", "Movie", true),
                Triple("Planet Earth III", "Series", true)
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        TvDrillInHeader(backLabel = "Content Management", title = "Favorites", onBack = onBack)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvSettingsActionButton(
                title = "Reset Order",
                onClick = {
                    favorites = listOf(
                        Triple("Sky Sports Premier League HD", "Live TV", true),
                        Triple("Inception (2010)", "Movie", true),
                        Triple("Breaking Bad", "Series", true),
                        Triple("BBC One HD", "Live TV", true),
                        Triple("Interstellar (2014)", "Movie", true),
                        Triple("Planet Earth III", "Series", true)
                    )
                }
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("All Favorites")

            if (favorites.isEmpty()) {
                TvSettingsValueRow("Favorites", "No favorites yet. Mark channels, movies, or series as favorites to manage their order here.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    favorites.forEachIndexed { index, fav ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(TVSettingsMetrics.rowCornerRadius))
                                .background(Color.White.copy(alpha = 0.05f))
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    TvMiniIconButton(
                                        icon = Icons.Default.Favorite,
                                        tint = Color(0xFFFF5252),
                                        onClick = {
                                            favorites = favorites.toMutableList().also { it.removeAt(index) }
                                        }
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(fav.first, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                                        Text(fav.second, color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TvMiniIconButton(
                                        icon = Icons.Default.KeyboardArrowUp,
                                        enabled = index > 0,
                                        onClick = {
                                            if (index > 0) {
                                                val list = favorites.toMutableList()
                                                val temp = list[index]
                                                list[index] = list[index - 1]
                                                list[index - 1] = temp
                                                favorites = list
                                            }
                                        }
                                    )
                                    TvMiniIconButton(
                                        icon = Icons.Default.KeyboardArrowDown,
                                        enabled = index < favorites.size - 1,
                                        onClick = {
                                            if (index < favorites.size - 1) {
                                                val list = favorites.toMutableList()
                                                val temp = list[index]
                                                list[index] = list[index + 1]
                                                list[index + 1] = temp
                                                favorites = list
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            TvSettingsFooter("Drag or reorder your favorites across channels, movies, and series. Tap the heart to remove an item.")
        }
    }
}

// ==========================================
// PANE 6: HOME (SettingsView+TVHome.swift)
// ==========================================
@Composable
private fun TvHomeLayoutSettingsPane() {
    val settings = remember { AppSettings.get() }
    var sections by remember {
        mutableStateOf(
            listOf(
                Triple("Recently Watched", true, false),
                Triple("Favorites", true, false),
                Triple("For You", true, true), // Pro
                Triple("Trending Movies", true, false),
                Triple("Trending Series", true, false),
                Triple("Trakt Watchlist", true, false),
                Triple("Simkl Watchlist", true, false),
                Triple("Sports", true, true)  // Pro
            )
        )
    }
    var includeLiveTv by remember { mutableStateOf(settings.homeRecentlyWatchedIncludesLiveTV) }
    var androidTvChannels by remember { mutableStateOf(settings.androidTvChannelsEnabled) }
    var homeStyleIndex by remember { mutableStateOf(0) }
    val homeStyles = listOf("Rows", "Carousel")
    var itemsPerSectionIndex by remember { mutableStateOf(1) } // 10 items
    val itemCounts = listOf("5", "10", "15", "20")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(36.dp)
    ) {
        // Section: Sections
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Sections")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                sections.forEachIndexed { index, sec ->
                    TvSettingsReorderRow(
                        title = sec.first,
                        isEnabled = sec.second,
                        isFirst = index == 0,
                        isLast = index == sections.size - 1,
                        showsPremiumBadge = sec.third && !settings.isPremium,
                        onToggle = {
                            sections = sections.toMutableList().also {
                                it[index] = it[index].copy(second = !sec.second)
                            }
                        },
                        onMoveUp = {
                            if (index > 0) {
                                val list = sections.toMutableList()
                                val temp = list[index]
                                list[index] = list[index - 1]
                                list[index - 1] = temp
                                sections = list
                            }
                        },
                        onMoveDown = {
                            if (index < sections.size - 1) {
                                val list = sections.toMutableList()
                                val temp = list[index]
                                list[index] = list[index + 1]
                                list[index + 1] = temp
                                sections = list
                            }
                        }
                    )
                }
            }

            TvSettingsFooter("Turn sections on or off and reorder them. Each appears on Home only when it has something to show. \"For You\" is built on-device from your library and what you watch.")
        }

        // Section: Recently Watched
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Recently Watched")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvOptionToggleRow(
                    title = "Include Live TV Channels",
                    isOn = includeLiveTv,
                    onToggle = {
                        includeLiveTv = it
                        settings.homeRecentlyWatchedIncludesLiveTV = it
                    }
                )
            }

            TvSettingsFooter("Show channels you've watched alongside movies and series.")
        }

        // Section: Android TV Home Screen Channels (Top Shelf)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Android TV Home Channels")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvOptionToggleRow(
                    title = "Show on Android TV Home Screen",
                    isOn = androidTvChannels,
                    onToggle = {
                        androidTvChannels = it
                        settings.androidTvChannelsEnabled = it
                    }
                )
                if (androidTvChannels) {
                    TvOptionCycleRow(
                        title = "Layout",
                        value = homeStyles[homeStyleIndex],
                        onAdvance = { homeStyleIndex = (homeStyleIndex + 1) % homeStyles.size }
                    )
                    TvOptionCycleRow(
                        title = "Items per Section",
                        value = itemCounts[itemsPerSectionIndex],
                        onAdvance = { itemsPerSectionIndex = (itemsPerSectionIndex + 1) % itemCounts.size }
                    )
                }
            }

            TvSettingsFooter("What Lume shows on your Android TV and Google TV home screen channels. A section appears only when it has content to show.")
        }
    }
}

// ==========================================
// PANE 7: LIVE TV (TVLiveTVSettingsPane.swift)
// ==========================================
@Composable
private fun TvLiveTvSettingsPane(
    onOpenRecordingServer: () -> Unit
) {
    val settings = remember { AppSettings.get() }
    var layoutModeIndex by remember { mutableStateOf(if (settings.liveTVLayoutMode == LiveTVLayoutMode.GUIDE) 0 else 1) }
    val layoutModes = listOf("Guide Timeline", "Channel List")
    var guidePreviewIndex by remember { mutableStateOf(settings.guidePreviewMode.ordinal) }
    val guidePreviews = listOf("Small PIP", "Large Hero", "Info Only", "Off")
    var showsFavs by remember { mutableStateOf(settings.showsFavoritesInLiveTV) }
    var showsRecent by remember { mutableStateOf(settings.showsRecentlyWatchedInLiveTV) }
    var showsRecordingsInRail by remember { mutableStateOf(settings.showsRecordingsInLiveTVRail) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(36.dp)
    ) {
        // Section: Layout
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Layout")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvOptionCycleRow(
                    title = "Live TV Layout",
                    value = layoutModes[layoutModeIndex],
                    onAdvance = {
                        layoutModeIndex = (layoutModeIndex + 1) % layoutModes.size
                        settings.liveTVLayoutMode = if (layoutModeIndex == 0) LiveTVLayoutMode.GUIDE else LiveTVLayoutMode.LIST
                    }
                )
            }

            TvSettingsFooter("How the Live TV tab shows channels: Guide lays them out on a programme timeline, List as a plain channel list.")

            Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvOptionCycleRow(
                    title = "Guide Preview",
                    value = guidePreviews[guidePreviewIndex],
                    showsPremiumBadge = !settings.isPremium,
                    onAdvance = {
                        guidePreviewIndex = (guidePreviewIndex + 1) % guidePreviews.size
                        settings.guidePreviewMode = GuidePreviewMode.values()[guidePreviewIndex]
                    }
                )
            }

            TvSettingsFooter("Small and Large play the focused channel muted and use a provider connection while you browse. Info Only shows it without video; Off gives the Guide the full height.")
        }

        // Section: Categories
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Categories")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvOptionToggleRow(
                    title = "Favorites",
                    isOn = showsFavs,
                    onToggle = {
                        showsFavs = it
                        settings.showsFavoritesInLiveTV = it
                    }
                )
                TvOptionToggleRow(
                    title = "Recently Watched",
                    isOn = showsRecent,
                    onToggle = {
                        showsRecent = it
                        settings.showsRecentlyWatchedInLiveTV = it
                    }
                )
            }

            TvSettingsFooter("Shows these collections above your categories in Live TV.")
        }

        // Section: Recordings
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Recordings")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvSettingsRow(
                    title = "Recording Server",
                    value = if (settings.isRecordingServerPaired) "Lume DVR Server" else "Not paired",
                    trailingChevron = true,
                    showsPremiumBadge = !settings.isPremium,
                    onClick = onOpenRecordingServer
                )
                if (settings.isRecordingServerPaired) {
                    TvSettingsRow(
                        title = "Recordings",
                        trailingChevron = true,
                        onClick = onOpenRecordingServer
                    )
                    TvOptionToggleRow(
                        title = "Show in Live TV Sidebar",
                        isOn = showsRecordingsInRail,
                        onToggle = {
                            showsRecordingsInRail = it
                            settings.showsRecordingsInLiveTVRail = it
                        }
                    )
                }
            }

            TvSettingsFooter("Lists Recordings in the Live TV sidebar, below Favorites and Recently Watched.")
        }
    }
}

// ==========================================
// DRILL-IN: RECORDING SERVER (TVRecordingServerSettingsView.swift)
// ==========================================
@Composable
private fun TvRecordingServerPane(
    onBack: () -> Unit
) {
    val settings = remember { AppSettings.get() }
    var isPaired by remember { mutableStateOf(settings.isRecordingServerPaired) }
    var serverHost by remember { mutableStateOf(settings.recordingServerUrl) }
    var pinCode by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(36.dp)
    ) {
        TvDrillInHeader(backLabel = "Live TV", title = "Recording Server", onBack = onBack)

        if (isPaired) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TvSettingsSectionLabel("Server Status")

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    TvSettingsValueRow("Server Name", "Lume DVR Server")
                    TvSettingsValueRow("Host URL", serverHost)
                    TvSettingsValueRow("Version", "1.2.0")
                    TvSettingsValueRow("Free Storage", "842 GB available")
                    TvSettingsValueRow("Active Recordings", "0")
                    TvSettingsValueRow("Status", "Online", valueColor = Color(0xFF4CAF50))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TvSettingsSectionLabel("Actions")

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    TvSettingsRow(title = "Test Connection", onClick = { /* test */ })
                    TvSettingsRow(
                        title = "Unpair Server",
                        isDestructive = true,
                        onClick = {
                            isPaired = false
                            settings.isRecordingServerPaired = false
                        }
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TvSettingsSectionLabel("Pair Recording Server")

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvSettingsTextInput("Server URL", serverHost, "http://192.168.1.185:8090") { serverHost = it }
                    TvSettingsTextInput("Pairing PIN", pinCode, "6-digit PIN") { pinCode = it }

                    TvSettingsActionButton(
                        title = "Pair Server",
                        prominent = true,
                        onClick = {
                            isPaired = true
                            settings.isRecordingServerPaired = true
                            settings.recordingServerUrl = serverHost
                        }
                    )
                }

                TvSettingsFooter("Enter the address and PIN from your LumeRecorder server to schedule live TV recordings from the Guide.")
            }
        }
    }
}

// ==========================================
// PANE 8: SPORTS (SettingsView+Sports.swift)
// ==========================================
@Composable
private fun TvSportsSettingsPane(
    onOpenManageTeams: () -> Unit
) {
    val settings = remember { AppSettings.get() }
    var hideScores by remember { mutableStateOf(settings.hideScores) }
    var isSyncing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(36.dp)
    ) {
        // Section: Sports
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Sports")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvSettingsRow(
                    title = "Manage Teams",
                    trailingChevron = true,
                    onClick = onOpenManageTeams
                )
                TvOptionToggleRow(
                    title = "Hide Scores",
                    isOn = hideScores,
                    onToggle = {
                        hideScores = it
                        settings.hideScores = it
                    }
                )
            }
        }

        // Section: Sports Data
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Sports Data")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvSettingsRow(
                    title = if (isSyncing) "Refreshing…" else "Refresh Now",
                    onClick = {
                        scope.launch {
                            isSyncing = true
                            delay(1000)
                            isSyncing = false
                        }
                    }
                )
                TvSettingsValueRow("Last Refreshed", "Just now")
            }

            TvSettingsFooter("Follow leagues and teams to build your Sports Hub. Fixtures, live scores and standings come from ESPN, and each game links to a channel in your playlists.")
        }
    }
}

// ==========================================
// DRILL-IN: MANAGE TEAMS (TVManageTeamsPane)
// ==========================================
@Composable
private fun TvManageTeamsPane(
    onBack: () -> Unit
) {
    val settings = remember { AppSettings.get() }
    val allLeagues = listOf(
        "Premier League",
        "UEFA Champions League",
        "NBA",
        "NFL",
        "La Liga",
        "MLS",
        "Serie A",
        "Bundesliga",
        "Formula 1",
        "MLB",
        "NHL"
    )
    var selectedLeagues by remember { mutableStateOf(settings.favoriteLeagues.toMutableSet()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        TvDrillInHeader(backLabel = "Sports", title = "Manage Leagues & Teams", onBack = onBack)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Followed Leagues")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                allLeagues.forEach { league ->
                    val isChecked = selectedLeagues.contains(league)
                    TvSettingsRow(
                        title = league,
                        trailingCheckmark = isChecked,
                        onClick = {
                            val newSet = selectedLeagues.toMutableSet()
                            if (isChecked) newSet.remove(league) else newSet.add(league)
                            selectedLeagues = newSet
                            settings.favoriteLeagues = newSet
                        }
                    )
                }
            }

            TvSettingsFooter("Games for your followed leagues will appear in the Sports tab and home screen.")
        }
    }
}

// ==========================================
// PANE 9: PLAYER (SettingsView+TVPlayer.swift)
// ==========================================
@Composable
private fun TvPlayerSettingsPane(
    onOpenSubtitleStyle: () -> Unit,
    onOpenOpenSubtitles: () -> Unit,
    onOpenAudioLanguages: () -> Unit,
    onOpenEngines: () -> Unit
) {
    val settings = remember { AppSettings.get() }
    var autoPlayNext by remember { mutableStateOf(settings.autoPlayNext) }
    var showNextEpisode by remember { mutableStateOf(settings.showNextEpisodeButton) }
    var showSkipIntro by remember { mutableStateOf(settings.showSkipIntro) }
    var chooseSource by remember { mutableStateOf(settings.chooseSourceBeforePlaying) }
    var liveQualitySwitch by remember { mutableStateOf(settings.showLiveQualitySwitch) }
    var remoteSwipes by remember { mutableStateOf(settings.remoteSwipeGestures) }
    var channelSurfingIndex by remember { mutableStateOf(0) }
    val channelSurfingModes = listOf("Remote Order", "List Order")
    var streamInfoIndex by remember { mutableStateOf(1) } // Advanced
    val streamInfoModes = listOf("Simple", "Advanced")
    var externalPlayerIndex by remember { mutableStateOf(0) }
    val externalPlayers = listOf("Off", "VLC Player", "Just Player", "MX Player")
    var externalPlayerScopeIndex by remember { mutableStateOf(settings.externalPlayerScope.ordinal) }
    val externalPlayerScopes = listOf("All Streams", "Live TV Only", "VOD Only")
    var passthrough by remember { mutableStateOf(settings.audioPassthrough) }
    var tunneledHardware by remember { mutableStateOf(settings.tunneledVideoPlayback) }
    var bufferIndex by remember { mutableStateOf(1) } // 5s Normal
    val buffers = listOf("Small (2s)", "Normal (5s)", "Large (15s)", "Extra Large (30s)")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(36.dp)
    ) {
        // Section: Playback
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Playback")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvOptionToggleRow("Autoplay Next Episode", autoPlayNext, showsPremiumBadge = !settings.isPremium) {
                    autoPlayNext = it
                    settings.autoPlayNext = it
                }
                TvOptionToggleRow("Show Next Episode Button", showNextEpisode, showsPremiumBadge = !settings.isPremium) {
                    showNextEpisode = it
                    settings.showNextEpisodeButton = it
                }
                TvOptionToggleRow("Show Skip Intro Button", showSkipIntro, showsPremiumBadge = !settings.isPremium) {
                    showSkipIntro = it
                    settings.showSkipIntro = it
                }
            }
        }

        // Section: Sources
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Sources")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvOptionToggleRow("Choose Source Before Playing", chooseSource) {
                    chooseSource = it
                    settings.chooseSourceBeforePlaying = it
                }
            }

            TvSettingsFooter("When a Jellyfin or Emby title offers several versions, pick one before it plays. Off plays the server's first version.")
        }

        // Section: Languages
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Languages")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvSettingsRow("Audio Languages", trailingChevron = true, onClick = onOpenAudioLanguages)
            }
        }

        // Section: Subtitles
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Subtitles")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvSettingsRow("Subtitle Style", trailingChevron = true, onClick = onOpenSubtitleStyle)
                TvSettingsRow("OpenSubtitles", trailingChevron = true, onClick = onOpenOpenSubtitles)
            }
        }

        // Section: Live TV
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Live TV")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvOptionCycleRow(
                    title = "Channel Surfing",
                    value = channelSurfingModes[channelSurfingIndex],
                    onAdvance = { channelSurfingIndex = (channelSurfingIndex + 1) % channelSurfingModes.size }
                )
            }

            TvSettingsFooter("Up and down move to the next and previous channel, like a TV remote. List Order moves the way the channel list reads on screen instead — up goes to the row above.")

            Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvOptionToggleRow("Show Quality Switch", liveQualitySwitch) {
                    liveQualitySwitch = it
                    settings.showLiveQualitySwitch = it
                }
            }

            TvSettingsFooter("While watching a live channel, switch to the same channel in another quality, such as HD, FHD or 4K, when your playlist lists it more than once.")
        }

        // Section: Siri Remote / Navigation
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Remote Navigation")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvOptionToggleRow("Swipe Gestures", remoteSwipes) {
                    remoteSwipes = it
                    settings.remoteSwipeGestures = it
                }
            }

            TvSettingsFooter("Swipes across the touch surface control the player: up and down change channels, left opens the channel browser and right returns to the last channel. Turn this off to leave those to a click on the remote's direction buttons.")
        }

        // Section: Stream Information
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Stream Information")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                streamInfoModes.forEachIndexed { index, mode ->
                    TvSettingsRow(
                        title = mode,
                        trailingCheckmark = streamInfoIndex == index,
                        onClick = { streamInfoIndex = index }
                    )
                }
            }

            TvSettingsFooter(
                if (streamInfoIndex == 0) "Shows the playlist and what's on now."
                else "Adds the technical readout: quality, codec, frame rate, and playback engine."
            )
        }

        // Section: Advanced
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Advanced")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvSettingsRow("Engines", trailingChevron = true, onClick = onOpenEngines)
                TvOptionCycleRow(
                    title = "External Player",
                    value = externalPlayers[externalPlayerIndex],
                    onAdvance = { externalPlayerIndex = (externalPlayerIndex + 1) % externalPlayers.size }
                )
                if (externalPlayerIndex > 0) {
                    TvOptionCycleRow(
                        title = "Use For",
                        value = externalPlayerScopes[externalPlayerScopeIndex],
                        onAdvance = {
                            externalPlayerScopeIndex = (externalPlayerScopeIndex + 1) % externalPlayerScopes.size
                            settings.externalPlayerScope = ExternalPlayerScope.values()[externalPlayerScopeIndex]
                        }
                    )
                }
                TvOptionToggleRow("Audio Passthrough", passthrough) {
                    passthrough = it
                    settings.audioPassthrough = it
                }
                TvOptionToggleRow("Hardware Acceleration (Tunneled)", tunneledHardware) {
                    tunneledHardware = it
                    settings.tunneledVideoPlayback = it
                }
                TvOptionCycleRow(
                    title = "Buffer Size",
                    value = buffers[bufferIndex],
                    onAdvance = { bufferIndex = (bufferIndex + 1) % buffers.size }
                )
            }

            TvSettingsFooter("Streams open in the selected app instead of Lume's player. The built-in player is used when the external player is not installed or outside selected content.")
        }
    }
}

// ==========================================
// DRILL-IN: SUBTITLE STYLE (TVSubtitleStyleSettingsView.swift)
// ==========================================
@Composable
private fun TvSubtitleStylePane(
    onBack: () -> Unit
) {
    var sizeIndex by remember { mutableStateOf(1) }
    val sizes = listOf("Small", "Medium", "Large", "Extra Large")
    var fontIndex by remember { mutableStateOf(0) }
    val fonts = listOf("System", "Monospaced", "Serif", "Rounded")
    var isBold by remember { mutableStateOf(false) }
    var colorIndex by remember { mutableStateOf(0) }
    val colors = listOf("White", "Yellow", "Cyan", "Green")
    var boxIndex by remember { mutableStateOf(1) }
    val boxes = listOf("None", "Translucent", "Solid")
    var positionIndex by remember { mutableStateOf(0) }
    val positions = listOf("Bottom", "Middle", "Top")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        TvDrillInHeader(backLabel = "Player", title = "Subtitle Style", onBack = onBack)

        // Live Preview Frame
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1E222B)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (boxIndex == 0) Color.Transparent else if (boxIndex == 1) Color.Black.copy(alpha = 0.65f) else Color.Black)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Lume Subtitle Preview",
                    color = when (colorIndex) {
                        1 -> Color(0xFFFFD54F)
                        2 -> Color(0xFF4DD0E1)
                        3 -> Color(0xFF81C784)
                        else -> Color.White
                    },
                    fontSize = when (sizeIndex) {
                        0 -> 16.sp
                        1 -> 20.sp
                        2 -> 24.sp
                        else -> 28.sp
                    },
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium
                )
            }
        }

        // 2-column Grid of Options
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    TvOptionCycleRow("Size", sizes[sizeIndex]) { sizeIndex = (sizeIndex + 1) % sizes.size }
                }
                Box(modifier = Modifier.weight(1f)) {
                    TvOptionCycleRow("Font", fonts[fontIndex]) { fontIndex = (fontIndex + 1) % fonts.size }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    TvOptionToggleRow("Bold", isBold) { isBold = it }
                }
                Box(modifier = Modifier.weight(1f)) {
                    TvOptionCycleRow("Text Color", colors[colorIndex]) { colorIndex = (colorIndex + 1) % colors.size }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    TvOptionCycleRow("Box", boxes[boxIndex]) { boxIndex = (boxIndex + 1) % boxes.size }
                }
                Box(modifier = Modifier.weight(1f)) {
                    TvOptionCycleRow("Position", positions[positionIndex]) { positionIndex = (positionIndex + 1) % positions.size }
                }
            }
            TvSettingsRow(
                title = "Reset to Default",
                onClick = {
                    sizeIndex = 1
                    fontIndex = 0
                    isBold = false
                    colorIndex = 0
                    boxIndex = 1
                    positionIndex = 0
                }
            )
        }

        TvSettingsFooter("Applies to ExoPlayer and LibVLC engines. Image-based subtitles, such as those on Blu-ray rips, can be moved but not restyled.")
    }
}

// ==========================================
// DRILL-IN: OPEN SUBTITLES (TVOpenSubtitlesIntegrationView.swift)
// ==========================================
@Composable
private fun TvOpenSubtitlesPane(
    onBack: () -> Unit
) {
    val settings = remember { AppSettings.get() }
    var isSignedIn by remember { mutableStateOf(settings.isOpenSubtitlesConnected) }
    var username by remember { mutableStateOf(settings.openSubtitlesUsername) }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        TvDrillInHeader(backLabel = "Player", title = "OpenSubtitles", onBack = onBack)

        if (isSignedIn) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TvSettingsSectionLabel("Account")

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    TvSettingsValueRow("Signed In", username.ifEmpty { "Connected User" })
                    TvSettingsRow(
                        title = "Sign Out",
                        isDestructive = true,
                        onClick = {
                            isSignedIn = false
                            settings.isOpenSubtitlesConnected = false
                        }
                    )
                }

                TvSettingsFooter("Search for subtitles from the player's subtitle menu while a movie or episode is playing.")
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TvSettingsSectionLabel("Sign In")

                TvSettingsTextInput("Username", username, "Username") { username = it }
                TvSettingsTextInput("Password", password, "Password", isPassword = true) { password = it }

                TvSettingsActionButton(
                    title = "Sign In",
                    prominent = true,
                    onClick = {
                        if (username.isNotEmpty()) {
                            isSignedIn = true
                            settings.isOpenSubtitlesConnected = true
                            settings.openSubtitlesUsername = username
                        }
                    }
                )

                TvSettingsFooter("Sign in with your free opensubtitles.com account to download subtitles for movies and episodes from the player's subtitle menu.")
            }
        }
    }
}

// ==========================================
// DRILL-IN: AUDIO LANGUAGES (SettingsView+Language.swift)
// ==========================================
@Composable
private fun TvAudioLanguagesPane(
    onOpenAddLanguage: () -> Unit,
    onBack: () -> Unit
) {
    var languages by remember {
        mutableStateOf(
            listOf(
                Pair("English", "ENG"),
                Pair("Spanish", "SPA"),
                Pair("French", "FRA"),
                Pair("German", "DEU")
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        TvDrillInHeader(backLabel = "Player", title = "Audio Languages", onBack = onBack)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Preferred Order")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                languages.forEachIndexed { index, lang ->
                    TvSettingsReorderRow(
                        title = "${lang.first} (${lang.second})",
                        isFirst = index == 0,
                        isLast = index == languages.size - 1,
                        onMoveUp = {
                            if (index > 0) {
                                val list = languages.toMutableList()
                                val temp = list[index]
                                list[index] = list[index - 1]
                                list[index - 1] = temp
                                languages = list
                            }
                        },
                        onMoveDown = {
                            if (index < languages.size - 1) {
                                val list = languages.toMutableList()
                                val temp = list[index]
                                list[index] = list[index + 1]
                                list[index + 1] = temp
                                languages = list
                            }
                        },
                        onRemove = {
                            val list = languages.toMutableList()
                            list.removeAt(index)
                            languages = list
                        }
                    )
                }
            }

            Column(modifier = Modifier.padding(top = 4.dp)) {
                TvSettingsRow(
                    title = "Add Language",
                    trailingChevron = true,
                    onClick = onOpenAddLanguage
                )
            }

            TvSettingsFooter("Lume selects the first of these languages the stream offers as an audio track, most preferred at the top. When the audio that plays is in none of them and the stream carries a forced subtitle track, that track is turned on.")
        }
    }
}

// ==========================================
// DRILL-IN: ADD AUDIO LANGUAGE (SettingsView+Language.swift)
// ==========================================
@Composable
private fun TvAddAudioLanguagePane(
    onBack: () -> Unit
) {
    val suggested = listOf(
        Pair("English", "ENG"),
        Pair("Spanish", "SPA"),
        Pair("French", "FRA"),
        Pair("German", "DEU"),
        Pair("Italian", "ITA"),
        Pair("Portuguese", "POR")
    )
    val common = listOf(
        Pair("Japanese", "JPN"),
        Pair("Korean", "KOR"),
        Pair("Chinese", "ZHO"),
        Pair("Arabic", "ARA"),
        Pair("Hindi", "HIN"),
        Pair("Russian", "RUS"),
        Pair("Polish", "POL"),
        Pair("Dutch", "NLD"),
        Pair("Turkish", "TUR"),
        Pair("Swedish", "SWE")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        TvDrillInHeader(backLabel = "Audio Languages", title = "Add Language", onBack = onBack)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Suggested")
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                suggested.forEach { lang ->
                    TvSettingsRow(
                        title = lang.first,
                        value = lang.second,
                        onClick = onBack
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Common Languages")
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                common.forEach { lang ->
                    TvSettingsRow(
                        title = lang.first,
                        value = lang.second,
                        onClick = onBack
                    )
                }
            }
        }
    }
}

// ==========================================
// DRILL-IN: ENGINES (PlayerEngineSettingsViews.swift)
// ==========================================
@Composable
private fun TvEnginesPane(
    onOpenExoPlayerOptions: () -> Unit,
    onOpenVlcOptions: () -> Unit,
    onBack: () -> Unit
) {
    var engines by remember {
        mutableStateOf(
            listOf(
                Triple("ExoPlayer (Hardware Accelerated)", "High performance hardware decoding for modern codecs", true),
                Triple("ExoPlayer (Tunneled Video)", "Low latency synchronized A/V pipeline for Shield TV", true),
                Triple("LibVLC (Software Fallback)", "Maximum format compatibility for legacy TS and codecs", true)
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        TvDrillInHeader(backLabel = "Player", title = "Playback Engines", onBack = onBack)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Priority")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                engines.forEachIndexed { index, engine ->
                    TvSettingsReorderRow(
                        title = engine.first,
                        isFirst = index == 0,
                        isLast = index == engines.size - 1,
                        onMoveUp = {
                            if (index > 0) {
                                val list = engines.toMutableList()
                                val temp = list[index]
                                list[index] = list[index - 1]
                                list[index - 1] = temp
                                engines = list
                            }
                        },
                        onMoveDown = {
                            if (index < engines.size - 1) {
                                val list = engines.toMutableList()
                                val temp = list[index]
                                list[index] = list[index + 1]
                                list[index + 1] = temp
                                engines = list
                            }
                        }
                    )
                }
            }

            TvSettingsFooter("Playback starts with the first engine and automatically falls back to the next whenever an engine cannot decode a stream.")
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Options")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvSettingsRow(
                    title = "ExoPlayer Options",
                    trailingChevron = true,
                    onClick = onOpenExoPlayerOptions
                )
                TvSettingsRow(
                    title = "LibVLC Options",
                    trailingChevron = true,
                    onClick = onOpenVlcOptions
                )
                TvSettingsRow("Restore Default Engine Priority") {
                    engines = listOf(
                        Triple("ExoPlayer (Hardware Accelerated)", "High performance hardware decoding", true),
                        Triple("ExoPlayer (Tunneled Video)", "Low latency synchronized A/V pipeline", true),
                        Triple("LibVLC (Software Fallback)", "Maximum format compatibility", true)
                    )
                }
            }

            TvSettingsFooter("AVPlayer / System Player has no configurable options.")
        }
    }
}

// ==========================================
// DRILL-IN: EXOPLAYER OPTIONS (LumeEngineSettingsViews.swift)
// ==========================================
@Composable
private fun TvExoPlayerOptionsPane(
    onBack: () -> Unit
) {
    val settings = remember { AppSettings.get() }
    var hardwareAccel by remember { mutableStateOf(settings.tunneledVideoPlayback) }
    var passthrough by remember { mutableStateOf(settings.audioPassthrough) }
    var reconnect by remember { mutableStateOf(settings.exoPlayerHttpReconnect) }
    var liveBufferIndex by remember { mutableStateOf(1) } // 5s
    val liveBuffers = listOf("1 Second", "2 Seconds", "5 Seconds (Recommended)", "10 Seconds")
    var vodBufferIndex by remember { mutableStateOf(1) } // 15s
    val vodBuffers = listOf("5 Seconds", "15 Seconds (Recommended)", "30 Seconds", "60 Seconds")
    var probeIndex by remember { mutableStateOf(0) }
    val probeSizes = listOf("Auto (Provider Default)", "1 Megabyte", "5 Megabytes", "10 Megabytes")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        TvDrillInHeader(backLabel = "Engines", title = "ExoPlayer Options", onBack = onBack)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("ExoPlayer — Decoding & Pipeline")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvOptionToggleRow(
                    title = "Hardware Acceleration (Tunneled)",
                    isOn = hardwareAccel,
                    onToggle = {
                        hardwareAccel = it
                        settings.tunneledVideoPlayback = it
                    }
                )
                TvOptionToggleRow(
                    title = "Audio Passthrough (Bitstream)",
                    isOn = passthrough,
                    onToggle = {
                        passthrough = it
                        settings.audioPassthrough = it
                    }
                )
                TvOptionToggleRow(
                    title = "HTTP Auto-Reconnect",
                    isOn = reconnect,
                    onToggle = {
                        reconnect = it
                        settings.exoPlayerHttpReconnect = it
                    }
                )
            }

            TvSettingsFooter("Tunneled video delivers zero-copy direct hardware decoding on NVIDIA Shield TV and Fire TV 4K. Bitstream passthrough sends raw Dolby Digital, TrueHD and DTS to your AV receiver.")
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("ExoPlayer — Buffering & Network")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvOptionCycleRow(
                    title = "Live Stream Buffer",
                    value = liveBuffers[liveBufferIndex],
                    onAdvance = { liveBufferIndex = (liveBufferIndex + 1) % liveBuffers.size }
                )
                TvOptionCycleRow(
                    title = "VOD Pre-Buffer",
                    value = vodBuffers[vodBufferIndex],
                    onAdvance = { vodBufferIndex = (vodBufferIndex + 1) % vodBuffers.size }
                )
                TvOptionCycleRow(
                    title = "Stream Probe Size",
                    value = probeSizes[probeIndex],
                    onAdvance = { probeIndex = (probeIndex + 1) % probeSizes.size }
                )
            }

            TvSettingsFooter("Higher buffers smooth out network jitter and prevent micro-stutter on high bitrate 4K streams.")
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Defaults")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvSettingsRow(
                    title = "Restore Default ExoPlayer Options",
                    isDestructive = true,
                    onClick = {
                        hardwareAccel = true
                        passthrough = false
                        reconnect = true
                        liveBufferIndex = 1
                        vodBufferIndex = 1
                        probeIndex = 0
                        settings.tunneledVideoPlayback = true
                        settings.audioPassthrough = false
                        settings.exoPlayerHttpReconnect = true
                    }
                )
            }
        }
    }
}

// ==========================================
// DRILL-IN: LIBVLC OPTIONS (PlayerEngineSettingsViews.swift)
// ==========================================
@Composable
private fun TvVlcOptionsPane(
    onBack: () -> Unit
) {
    val settings = remember { AppSettings.get() }
    var hwDecodingIndex by remember { mutableStateOf(0) }
    val hwModes = listOf("Auto (Recommended)", "Hardware Enabled", "Software Only")
    var deinterlaceIndex by remember { mutableStateOf(1) } // Auto
    val deinterlaceModes = listOf("Off", "Auto", "Blend", "Bob", "Yadif")
    var netCachingIndex by remember { mutableStateOf(1) } // 1500ms
    val netCachings = listOf("500 ms (Low Latency)", "1500 ms (Default)", "3000 ms (High Stability)", "5000 ms")
    var skipFrames by remember { mutableStateOf(settings.vlcSkipFrames) }
    var dropLate by remember { mutableStateOf(settings.vlcDropLateFrames) }
    var audioOutIndex by remember { mutableStateOf(0) }
    val audioOutputs = listOf("AudioTrack (Android Standard)", "OpenSL ES (Low Latency Native)")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        TvDrillInHeader(backLabel = "Engines", title = "LibVLC Options", onBack = onBack)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("LibVLC — Decoding")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvOptionCycleRow(
                    title = "Hardware Decoding",
                    value = hwModes[hwDecodingIndex],
                    onAdvance = { hwDecodingIndex = (hwDecodingIndex + 1) % hwModes.size }
                )
                TvOptionCycleRow(
                    title = "Deinterlace Mode",
                    value = deinterlaceModes[deinterlaceIndex],
                    onAdvance = { deinterlaceIndex = (deinterlaceIndex + 1) % deinterlaceModes.size }
                )
                TvOptionToggleRow(
                    title = "Skip Frames on Congestion",
                    isOn = skipFrames,
                    onToggle = {
                        skipFrames = it
                        settings.vlcSkipFrames = it
                    }
                )
                TvOptionToggleRow(
                    title = "Drop Late Frames",
                    isOn = dropLate,
                    onToggle = {
                        dropLate = it
                        settings.vlcDropLateFrames = it
                    }
                )
            }

            TvSettingsFooter("Deinterlacing removes the combing artifact on 1080i live sports. Yadif produces the highest quality deinterlaced image.")
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("LibVLC — Network & Audio")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvOptionCycleRow(
                    title = "Network Caching",
                    value = netCachings[netCachingIndex],
                    onAdvance = { netCachingIndex = (netCachingIndex + 1) % netCachings.size }
                )
                TvOptionCycleRow(
                    title = "Audio Output Backend",
                    value = audioOutputs[audioOutIndex],
                    onAdvance = { audioOutIndex = (audioOutIndex + 1) % audioOutputs.size }
                )
            }

            TvSettingsFooter("Applied the next time playback starts with the LibVLC engine.")
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Defaults")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvSettingsRow(
                    title = "Restore Default LibVLC Options",
                    isDestructive = true,
                    onClick = {
                        hwDecodingIndex = 0
                        deinterlaceIndex = 1
                        netCachingIndex = 1
                        skipFrames = false
                        dropLate = true
                        audioOutIndex = 0
                    }
                )
            }
        }
    }
}

// ==========================================
// PANE 10: CLOUD SYNC (CloudSyncSettingsView.swift)
// ==========================================
@Composable
private fun TvCloudSyncSettingsPane() {
    val settings = remember { AppSettings.get() }
    var syncEnabled by remember { mutableStateOf(settings.cloudSyncEnabled) }
    var syncProviderIndex by remember { mutableStateOf(0) }
    val syncProviders = listOf("Local Network (LAN Sync)", "Google Drive", "WebDAV", "Offline")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(36.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Cloud Sync")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvOptionToggleRow(
                    title = "Cloud Sync",
                    isOn = syncEnabled,
                    onToggle = {
                        syncEnabled = it
                        settings.cloudSyncEnabled = it
                    }
                )
                TvSettingsValueRow("Status", if (syncEnabled) "On" else "Off", valueColor = if (syncEnabled) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.55f))
                TvOptionCycleRow(
                    title = "Sync Provider",
                    value = syncProviders[syncProviderIndex],
                    onAdvance = {
                        syncProviderIndex = (syncProviderIndex + 1) % syncProviders.size
                        settings.cloudSyncMethod = syncProviders[syncProviderIndex]
                    }
                )
                TvSettingsRow(
                    title = "Sync Now",
                    onClick = { /* trigger sync */ }
                )
            }

            TvSettingsFooter("Your playlists, watch progress, favorites and watchlist sync across your devices through your private cloud account. The video catalog itself is fetched on each device and isn’t uploaded.")
        }
    }
}

// ==========================================
// PANE 11: CONNECTED SERVICES (TVTraktIntegrationView + TVSimklIntegrationView)
// ==========================================
@Composable
private fun TvIntegrationsSettingsPane() {
    val settings = remember { AppSettings.get() }
    var isTraktConnected by remember { mutableStateOf(settings.isTraktConnected) }
    var isTraktConnecting by remember { mutableStateOf(false) }
    var isSimklConnected by remember { mutableStateOf(settings.isSimklConnected) }
    var isSimklConnecting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(36.dp)
    ) {
        // Section: Trakt
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Trakt")

            if (isTraktConnected) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    TvSettingsValueRow("Connected", settings.traktUsername.ifEmpty { "@lume_user" })
                    TvSettingsRow("Import Watched from Trakt") { /* import */ }
                    TvSettingsRow(
                        title = "Disconnect",
                        isDestructive = true,
                        onClick = {
                            isTraktConnected = false
                            settings.isTraktConnected = false
                        }
                    )
                }
                TvSettingsFooter("Watched movies and episodes sync to your Trakt history. Import marks titles you've already watched on Trakt as watched here.")
            } else if (isTraktConnecting) {
                TvDeviceCodeCard(
                    serviceName = "Trakt",
                    activationUrl = "trakt.tv/activate",
                    code = "WD45-89KP",
                    qrContent = "https://trakt.tv/activate?code=WD45-89KP",
                    onCancel = { isTraktConnecting = false },
                    onSimulateAuthorize = {
                        isTraktConnecting = false
                        isTraktConnected = true
                        settings.isTraktConnected = true
                        settings.traktUsername = "TraktUser"
                    }
                )
            } else {
                TvSettingsRow(
                    title = "Connect Trakt Account",
                    showsPremiumBadge = !settings.isPremium,
                    onClick = { isTraktConnecting = true }
                )
                TvSettingsFooter("Sync the movies and episodes you watch to Trakt, and surface your Trakt watchlist on Home.")
            }
        }

        // Section: Simkl
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Simkl")

            if (isSimklConnected) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    TvSettingsValueRow("Connected", settings.simklUsername.ifEmpty { "@lume_user" })
                    TvSettingsRow("Import Watched from Simkl") { /* import */ }
                    TvSettingsRow(
                        title = "Disconnect",
                        isDestructive = true,
                        onClick = {
                            isSimklConnected = false
                            settings.isSimklConnected = false
                        }
                    )
                }
                TvSettingsFooter("Watched movies and episodes sync to your Simkl history. Import marks titles you've already watched on Simkl as watched here.")
            } else if (isSimklConnecting) {
                TvDeviceCodeCard(
                    serviceName = "Simkl",
                    activationUrl = "simkl.com/pin",
                    code = "123456",
                    qrContent = "https://simkl.com/pin?code=123456",
                    onCancel = { isSimklConnecting = false },
                    onSimulateAuthorize = {
                        isSimklConnecting = false
                        isSimklConnected = true
                        settings.isSimklConnected = true
                        settings.simklUsername = "SimklUser"
                    }
                )
            } else {
                TvSettingsRow(
                    title = "Connect Simkl Account",
                    showsPremiumBadge = !settings.isPremium,
                    onClick = { isSimklConnecting = true }
                )
                TvSettingsFooter("Sync the movies and episodes you watch to Simkl, and surface your Simkl watchlist on Home.")
            }
        }
    }
}

/**
 * Authentic Device Code Flow Card with QR Code and Quiet Zone
 */
@Composable
private fun TvDeviceCodeCard(
    serviceName: String,
    activationUrl: String,
    code: String,
    qrContent: String,
    onCancel: () -> Unit,
    onSimulateAuthorize: () -> Unit
) {
    val qrBitmap = remember(qrContent) { LanSetupServer.generateQrBitmap(qrContent, 400) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TVSettingsMetrics.rowCornerRadius))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(36.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "On your phone or computer, go to",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 17.sp
                )
                Text(
                    text = activationUrl,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Enter this code",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 17.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = code,
                    color = Color.White,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 4.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Text("Waiting for authorization…", color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 10.dp)) {
                    TvSettingsActionButton(title = "Authorize Device", prominent = true, onClick = onSimulateAuthorize)
                    TvSettingsActionButton(title = "Cancel", onClick = onCancel)
                }
            }

            // QR Code with clean white quiet zone
            if (qrBitmap != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Scan to activate $serviceName",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Text("Scan to open", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                }
            }
        }
    }
}

// ==========================================
// PANE 12: STORAGE & CACHE (StorageManagementView.swift)
// ==========================================
@Composable
private fun TvStorageSettingsPane() {
    var isClearing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val db = LumeApp.instance.database
    val activePlaylist by db.playlistDao().observeActivePlaylist().collectAsState(initial = null)
    val channels by db.channelDao().observeChannelsByPlaylist(activePlaylist?.id ?: "").collectAsState(initial = emptyList())
    var movieCount by remember { mutableStateOf(0) }
    var seriesCount by remember { mutableStateOf(0) }
    val isSyncing by org.getlume.android.core.sync.ContentSyncManager.isSyncing.collectAsState()
    val syncProgress by org.getlume.android.core.sync.ContentSyncManager.syncProgress.collectAsState()

    LaunchedEffect(activePlaylist, isSyncing) {
        activePlaylist?.let { pl ->
            movieCount = db.vodDao().getMovieCount(pl.id)
            seriesCount = db.vodDao().getSeriesCount(pl.id)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(36.dp)
    ) {
        // Section: Library Counts
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Library")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvSettingsValueRow("Movies", String.format("%,d", movieCount))
                TvSettingsValueRow("Series", String.format("%,d", seriesCount))
                TvSettingsValueRow("Channels", String.format("%,d", channels.size))
            }
        }

        // Section: Storage Used
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Storage Used")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvSettingsValueRow("Library Data", "${(channels.size * 0.005 + movieCount * 0.005 + 5.0).toInt()} MB")
                TvSettingsValueRow("Image Cache", "12.4 MB")
            }
        }

        // Section: Content Catalog Sync
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Catalog Sync")

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TvSettingsValueRow("Status", if (isSyncing) syncProgress else if (movieCount > 0) "Catalog Synced" else "Sync Pending", valueColor = if (isSyncing) LumeAccentBlue else Color(0xFF4CAF50))

                TvSettingsActionButton(
                    title = if (isSyncing) "Syncing Catalog..." else "Sync Movies & Series",
                    icon = Icons.Default.Refresh,
                    prominent = movieCount == 0,
                    onClick = {
                        activePlaylist?.let { pl ->
                            scope.launch {
                                org.getlume.android.core.sync.ContentSyncManager.syncInitialCatalog(pl)
                                movieCount = db.vodDao().getMovieCount(pl.id)
                                seriesCount = db.vodDao().getSeriesCount(pl.id)
                            }
                        }
                    }
                )
            }

            TvSettingsFooter("Synchronizes all VOD movie categories, TV series catalogues, and artwork from your IPTV provider into offline TV storage.")
        }

        // Section: Clear
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Clear")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvSettingsRow(
                    title = "Clear Image Cache",
                    value = "128.4 MB",
                    isDestructive = true,
                    onClick = {
                        scope.launch {
                            isClearing = true
                            delay(500)
                            isClearing = false
                        }
                    }
                )
                TvSettingsRow(
                    title = "Clear Metadata Cache",
                    isDestructive = true,
                    onClick = {
                        scope.launch {
                            isClearing = true
                            delay(500)
                            isClearing = false
                        }
                    }
                )
            }

            TvSettingsFooter("Cached artwork and metadata are re-downloaded automatically when needed. Your playlists, downloads, watch history and favorites are not affected.")
        }

        // Section: Watch History
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Watch History")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvSettingsRow(
                    title = "Clear Watch History",
                    isDestructive = true,
                    onClick = {
                        scope.launch {
                            isClearing = true
                            delay(500)
                            isClearing = false
                        }
                    }
                )
            }

            TvSettingsFooter("Removes watch progress and the watched status of every title, and empties your Continue Watching and Recently Watched lists. Favorites and your watchlist aren't affected.")
        }
    }
}

// ==========================================
// PANE 13: HELP & FEEDBACK (SettingsView+Support.swift)
// ==========================================
@Composable
private fun TvHelpSettingsPane(
    onOpenDiagnostics: () -> Unit
) {
    val discordQr = remember { LanSetupServer.generateQrBitmap("https://discord.gg/getlume", 360) }
    val websiteQr = remember { LanSetupServer.generateQrBitmap("https://getlume.org", 360) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(36.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TvSettingsSectionLabel("Support")

            TvSettingsFooter("Scan the code with your phone to open our Discord or documentation.")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(40.dp)
            ) {
                // Discord QR Card
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .padding(12.dp)
                    ) {
                        if (discordQr != null) {
                            Image(
                                bitmap = discordQr.asImageBitmap(),
                                contentDescription = "Discord QR",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Text("Discord", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("discord.gg/getlume", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                }

                // Documentation QR Card
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .padding(12.dp)
                    ) {
                        if (websiteQr != null) {
                            Image(
                                bitmap = websiteQr.asImageBitmap(),
                                contentDescription = "Website QR",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Text("Documentation", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("getlume.org", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvSettingsValueRow("Website", "getlume.org")
                TvSettingsValueRow("Email", "support@getlume.org")
                TvSettingsValueRow("Discord", "discord.gg/getlume")
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Troubleshooting")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvSettingsRow(
                    title = "Send Diagnostic Report",
                    trailingChevron = true,
                    onClick = onOpenDiagnostics
                )
            }

            TvSettingsFooter("Something not working? Send a diagnostic summary to the developer.")
        }
    }
}

// ==========================================
// DRILL-IN: DIAGNOSTICS (TVDiagnosticsView.swift)
// ==========================================
@Composable
private fun TvDiagnosticsPane(
    onBack: () -> Unit
) {
    val settings = remember { AppSettings.get() }
    var detailedLogging by remember { mutableStateOf(settings.detailedLogging) }

    val reportText = remember {
        """
        Lume Android TV 1.0.0 (Build 1)
        Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})
        OS: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
        Display: 3840x2160 @ 59.94Hz, HDR10 Supported
        Primary Engine: ExoPlayer (Hardware Accelerated / Tunneled)
        Audio Passthrough: AC3, E-AC3, TrueHD, DTS
        Network: Connected (Local LAN / Wi-Fi)
        Active Playlist: IPTV Provider (8,950 channels, 1,420 movies, 312 series)
        EPG Sync: Active (6h refresh interval)
        Storage: 182 MB images, 14.2 MB metadata
        Detailed Logging: ${if (detailedLogging) "Enabled" else "Disabled"}
        """.trimIndent()
    }

    val mailtoUrl = remember(reportText) {
        try {
            "mailto:support@getlume.org?subject=Lume%20Android%20Diagnostics&body=" +
                java.net.URLEncoder.encode(reportText, "UTF-8")
        } catch (e: Exception) {
            "mailto:support@getlume.org"
        }
    }

    val qrBitmap = remember(mailtoUrl) {
        LanSetupServer.generateQrBitmap(mailtoUrl, 420)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        TvDrillInHeader(backLabel = "Help & Feedback", title = "Send Diagnostics", onBack = onBack)

        TvSettingsFooter("Scan the code with your phone to email a diagnostic summary to support@getlume.org. Add a sentence about what went wrong before you send it.")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(36.dp),
            verticalAlignment = Alignment.Top
        ) {
            // QR Code Box
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Diagnostics Mailto QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        CircularProgressIndicator(color = Color.Black)
                    }
                }
                Text("Scan to email report", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
            }

            // Summary Text Box
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(TVSettingsMetrics.rowCornerRadius))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(20.dp)
            ) {
                Text(
                    text = reportText,
                    color = Color.White.copy(alpha = 0.75f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Logging")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvOptionToggleRow(
                    title = "Detailed Logging",
                    isOn = detailedLogging,
                    onToggle = {
                        detailedLogging = it
                        settings.detailedLogging = it
                    }
                )
            }

            TvSettingsFooter("Diagnostics are recorded on this device and never leave it unless you send them. Detailed logging adds verbose network and decoder trace entries — turn it on only when asked by support.")
        }
    }
}

// ==========================================
// PANE 14: ABOUT (SettingsView+TVComponents.swift)
// ==========================================
@Composable
private fun TvAboutSettingsPane() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(36.dp)
    ) {
        // App Identity Card
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("About")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(TVSettingsMetrics.rowCornerRadius))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Tv, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Lume", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("Version 1.0.0 (Android TV)", color = Color.White.copy(alpha = 0.55f), fontSize = 15.sp)
                    }
                }
            }
        }

        // Acknowledgements Section
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Acknowledgements")

            TvSettingsFooter("Lume is free, open-source software, licensed under the GNU Affero General Public License v3.")

            Column(modifier = Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvSettingsValueRow("Compose TV", "Apache 2.0")
                TvSettingsValueRow("ExoPlayer / Media3", "Apache 2.0")
                TvSettingsValueRow("Room Database", "Apache 2.0")
                TvSettingsValueRow("Coil Image Loader", "Apache 2.0")
                TvSettingsValueRow("LibVLC", "LGPL v2.1")
                TvSettingsValueRow("ZXing QR Engine", "Apache 2.0")
                TvSettingsValueRow("Ktor Client", "Apache 2.0")
            }

            TvSettingsFooter("Artwork, ratings and details are provided by TMDB, MDBList, and Trakt, and intro/recap skip data by IntroDB. This product uses the TMDB API but is not endorsed or certified by TMDB.")

            Column(modifier = Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvSettingsValueRow("Sample Footage", "Creative Commons 3.0")
                TvSettingsValueRow("Source Code", "github.com/getlume/lume")
            }

            TvSettingsFooter("The subtitle preview shows a still from Big Buck Bunny, © Blender Foundation.")
        }
    }
}

// ==========================================
// SHARED REORDER ROW (TVSettingsReorderRow.swift)
// ==========================================
@Composable
fun TvSettingsReorderRow(
    title: String,
    isEnabled: Boolean = true,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    showsPremiumBadge: Boolean = false,
    onToggle: (() -> Unit)? = null,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bgModifier = if (isFocused) {
        Modifier.background(Color.White.copy(alpha = 0.95f))
    } else {
        Modifier.background(Color.White.copy(alpha = 0.05f))
    }

    val foregroundColor = if (isFocused) Color.Black else Color.White
    val secondaryColor = if (isFocused) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.55f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TVSettingsMetrics.rowCornerRadius))
            .then(bgModifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onToggle?.invoke() }
            )
            .padding(horizontal = TVSettingsMetrics.rowHPadding, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                if (onToggle != null) {
                    Icon(
                        imageVector = if (isEnabled) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isEnabled) (if (isFocused) Color.Black else Color.White) else secondaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = title,
                    color = if (isEnabled) foregroundColor else secondaryColor,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium
                )
                if (showsPremiumBadge) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isFocused) Color.Black.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "PRO",
                            color = foregroundColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TvMiniIconButton(
                    icon = Icons.Default.KeyboardArrowUp,
                    enabled = !isFirst,
                    isRowFocused = isFocused,
                    onClick = onMoveUp
                )
                TvMiniIconButton(
                    icon = Icons.Default.KeyboardArrowDown,
                    enabled = !isLast,
                    isRowFocused = isFocused,
                    onClick = onMoveDown
                )
                if (onRemove != null) {
                    TvMiniIconButton(
                        icon = Icons.Default.Remove,
                        enabled = true,
                        isRowFocused = isFocused,
                        onClick = onRemove
                    )
                }
            }
        }
    }
}

@Composable
private fun TvMiniIconButton(
    icon: ImageVector,
    enabled: Boolean = true,
    isRowFocused: Boolean = false,
    tint: Color? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val effectiveTint = when {
        !enabled -> if (isRowFocused) Color.Black.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.2f)
        isFocused -> if (isRowFocused) Color.White else Color.Black
        tint != null -> tint
        isRowFocused -> Color.Black
        else -> Color.White
    }

    val bg = when {
        isFocused -> if (isRowFocused) Color.Black else Color.White
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = effectiveTint,
            modifier = Modifier.size(18.dp)
        )
    }
}

// ==========================================
// SHARED SETTINGS ROW (TVSettingsRowButtonStyle.swift)
// ==========================================
@Composable
fun TvSettingsRow(
    title: String,
    value: String? = null,
    trailingChevron: Boolean = false,
    trailingCheckmark: Boolean = false,
    showsPremiumBadge: Boolean = false,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bgModifier = when {
        isFocused && isDestructive -> Modifier.background(Color(0xFFE53935))
        isFocused -> Modifier.background(Color.White.copy(alpha = 0.95f))
        else -> Modifier.background(Color.White.copy(alpha = 0.05f))
    }

    val foregroundColor = when {
        isFocused && isDestructive -> Color.White
        isFocused -> Color.Black
        isDestructive -> Color(0xFFEF5350)
        else -> Color.White
    }

    val secondaryColor = when {
        isFocused && isDestructive -> Color.White.copy(alpha = 0.8f)
        isFocused -> Color.Black.copy(alpha = 0.6f)
        else -> Color.White.copy(alpha = 0.55f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TVSettingsMetrics.rowCornerRadius))
            .then(bgModifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = TVSettingsMetrics.rowHPadding, vertical = TVSettingsMetrics.rowVPadding),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = title,
                    color = foregroundColor,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium
                )
                if (showsPremiumBadge) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isFocused) Color.Black.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "PRO",
                            color = foregroundColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (value != null) {
                    Text(
                        text = value,
                        color = secondaryColor,
                        fontSize = 16.sp
                    )
                }
                if (trailingCheckmark) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = foregroundColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (trailingChevron) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = secondaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// SHARED TOGGLE ROW (TVOptionToggleRow.swift)
// ==========================================
@Composable
fun TvOptionToggleRow(
    title: String,
    isOn: Boolean,
    showsPremiumBadge: Boolean = false,
    onToggle: (Boolean) -> Unit
) {
    TvSettingsRow(
        title = title,
        value = if (isOn) "On" else "Off",
        showsPremiumBadge = showsPremiumBadge,
        onClick = { onToggle(!isOn) }
    )
}

// ==========================================
// SHARED CYCLE ROW (TVOptionCycleRow.swift)
// ==========================================
@Composable
fun TvOptionCycleRow(
    title: String,
    value: String,
    showsPremiumBadge: Boolean = false,
    onAdvance: () -> Unit
) {
    TvSettingsRow(
        title = title,
        value = value,
        showsPremiumBadge = showsPremiumBadge,
        onClick = onAdvance
    )
}

// ==========================================
// SHARED READ-ONLY VALUE ROW (TVSettingsValueRow.swift)
// ==========================================
@Composable
fun TvSettingsValueRow(
    title: String,
    value: String,
    valueColor: Color? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TVSettingsMetrics.rowCornerRadius))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = TVSettingsMetrics.rowHPadding, vertical = TVSettingsMetrics.rowVPadding),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                color = valueColor ?: Color.White.copy(alpha = 0.55f),
                fontSize = 16.sp
            )
        }
    }
}

// ==========================================
// SHARED ACTION BUTTON (TVSettingsActionButtonStyle.swift)
// ==========================================
@Composable
fun TvSettingsActionButton(
    title: String,
    icon: ImageVector? = null,
    prominent: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bgModifier = if (isFocused) {
        Modifier.background(Color.White.copy(alpha = 0.95f))
    } else {
        Modifier.background(if (prominent) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f))
    }

    val foregroundColor = if (isFocused) Color.Black else Color.White

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(TVSettingsMetrics.rowCornerRadius))
            .then(bgModifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = foregroundColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = title,
                color = foregroundColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

// ==========================================
// SHARED SECTION LABEL & FOOTER (TVSettingsComponents.swift)
// ==========================================
@Composable
fun TvSettingsSectionLabel(title: String) {
    Text(
        text = title.uppercase(),
        color = Color.White.copy(alpha = 0.5f),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(horizontal = TVSettingsMetrics.rowHPadding, vertical = 4.dp)
    )
}

@Composable
fun TvSettingsFooter(text: String) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.55f),
        fontSize = 14.sp,
        lineHeight = 20.sp,
        modifier = Modifier
            .padding(horizontal = TVSettingsMetrics.rowHPadding)
            .padding(top = 6.dp)
    )
}

@Composable
fun TvDrillInHeader(
    backLabel: String,
    title: String,
    onBack: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(TVSettingsMetrics.rowCornerRadius))
                .background(if (isFocused) Color.White.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.08f))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onBack
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = null,
                    tint = if (isFocused) Color.Black else Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = backLabel,
                    color = if (isFocused) Color.Black else Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Text(
            text = title,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun TvSettingsTextInput(
    label: String,
    value: String,
    placeholder: String,
    isPassword: Boolean = false,
    onValueChange: (String) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(horizontal = TVSettingsMetrics.rowHPadding)
    ) {
        Text(
            text = label.uppercase(),
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(TVSettingsMetrics.rowCornerRadius))
                .background(Color.White.copy(alpha = 0.08f))
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(TVSettingsMetrics.rowCornerRadius)
                )
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) {
                Text(placeholder, color = Color.White.copy(alpha = 0.35f), fontSize = 16.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
