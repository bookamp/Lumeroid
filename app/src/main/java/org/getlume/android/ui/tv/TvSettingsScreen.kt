package org.getlume.android.ui.tv

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import java.io.File
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.getlume.android.LumeApp
import org.getlume.android.core.data.entities.*
import org.getlume.android.core.designsystem.*
import org.getlume.android.core.network.LanSetupServer
import org.getlume.android.core.settings.*
import org.getlume.android.core.sync.PlaylistSyncManager
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import java.util.UUID

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
    data class EditProfile(val profile: UserProfileEntity?) : SettingsDrillIn()
    object ContentManagement : SettingsDrillIn()
    data class ChannelManagement(val categoryId: String, val categoryName: String) : SettingsDrillIn()
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
    object HomeContentLanguages : SettingsDrillIn()
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
    var isInContentPane by remember { mutableStateOf(false) }
    var returnFocusProfileId by remember { mutableStateOf<String?>(null) }

    val popDrillIn: () -> Unit = {
        if (drillInStack.isNotEmpty()) {
            val popped = drillInStack.removeAt(drillInStack.size - 1)
            if (popped is SettingsDrillIn.EditProfile && returnFocusProfileId == null) {
                returnFocusProfileId = popped.profile?.id ?: "add_profile"
            }
        }
    }

    // Intercept hardware remote Back button to pop drill-in before exiting screen
    BackHandler(enabled = drillInStack.isNotEmpty()) {
        popDrillIn()
    }

    // Breadcrumb BackHandler: When in right detail pane, Back retracts focus to the Left sidebar rail
    BackHandler(enabled = isInContentPane && drillInStack.isEmpty()) {
        try {
            firstItemFocusRequester.requestFocus()
        } catch (_: Exception) {}
        isInContentPane = false
    }

    // Breadcrumb BackHandler: When in Settings sidebar rail, Back retracts focus up to TopBar
    BackHandler(enabled = !isInContentPane && drillInStack.isEmpty()) {
        onMoveUpToTopBar?.invoke()
    }

    TvAmbientBackground {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 76.dp, start = 48.dp, end = 48.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(44.dp)
        ) {
            // LEFT COLUMN: TV SIDEBAR RAIL (270dp)
            Box(
                modifier = Modifier
                    .focusProperties { canFocus = drillInStack.isEmpty() }
                    .onFocusChanged { if (it.hasFocus) isInContentPane = false }
            ) {
                TvSidebarRail(
                    selectedCategory = selectedCategory,
                    firstItemFocusRequester = firstItemFocusRequester,
                    onMoveUpToTopBar = onMoveUpToTopBar,
                    onSelectCategory = { cat ->
                        if (drillInStack.isEmpty()) {
                            selectedCategory = cat
                        }
                    }
                )
            }

            // RIGHT COLUMN: DETAIL PANE CONTAINER (Max 860dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(top = 10.dp)
                    .onFocusChanged { if (it.hasFocus) isInContentPane = true }
            ) {
                // If drilled in, render the in-place drill-in sub-pane
                when (val drillIn = currentDrillIn) {
                    is SettingsDrillIn.PlaylistDetail -> {
                        TvPlaylistDetailPane(
                            playlist = drillIn.playlist,
                            onBack = popDrillIn
                        )
                    }
                    is SettingsDrillIn.EditProfile -> {
                        TvProfileEditorPane(
                            profile = drillIn.profile,
                            onBack = {
                                returnFocusProfileId = drillIn.profile?.id ?: "add_profile"
                                popDrillIn()
                            },
                            onProfileSaved = { savedId ->
                                returnFocusProfileId = savedId
                                popDrillIn()
                            },
                            onProfileDeleted = {
                                returnFocusProfileId = "add_profile"
                                popDrillIn()
                            }
                        )
                    }
                    is SettingsDrillIn.ContentManagement -> {
                        TvContentManagementPane(
                            onOpenChannelManagement = { catId, catName ->
                                drillInStack.add(SettingsDrillIn.ChannelManagement(catId, catName))
                            },
                            onOpenFavorites = {
                                drillInStack.add(SettingsDrillIn.FavoriteManagement)
                            },
                            onBack = popDrillIn
                        )
                    }
                    is SettingsDrillIn.ChannelManagement -> {
                        TvChannelManagementPane(
                            categoryId = drillIn.categoryId,
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
                    is SettingsDrillIn.HomeContentLanguages -> {
                        TvHomeContentLanguagesPane(
                            onBack = popDrillIn
                        )
                    }
                    null -> {
                        // Render Top-Level Category Pane
                        when (selectedCategory) {
                            SettingsCategory.PREMIUM -> TvPremiumSettingsPane()
                            SettingsCategory.PROFILES -> TvProfilesSettingsPane(
                                targetFocusProfileId = returnFocusProfileId,
                                onFocusConsumed = { returnFocusProfileId = null },
                                onOpenEditProfile = { profile ->
                                    returnFocusProfileId = profile?.id ?: "add_profile"
                                    drillInStack.add(SettingsDrillIn.EditProfile(profile))
                                }
                            )
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
                                },
                                onOpenContentLanguages = {
                                     drillInStack.add(SettingsDrillIn.HomeContentLanguages)
                                }
                            )
                            SettingsCategory.HOME -> TvHomeLayoutSettingsPane(
                                onOpenContentLanguages = {
                                    drillInStack.add(SettingsDrillIn.HomeContentLanguages)
                                }
                            )
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
            val isSelected = selectedCategory == category
            val itemFocusRequester = if (isSelected) firstItemFocusRequester else null
            TvCategoryRailItem(
                title = category.title,
                isSelected = isSelected,
                focusRequester = itemFocusRequester,
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
enum class PinDialogMode {
    SET, CHANGE, REMOVE, UNLOCK_GATE, UNLOCK_SWITCH
}

@Composable
private fun TvProfilesSettingsPane(
    targetFocusProfileId: String? = null,
    onFocusConsumed: () -> Unit = {},
    onOpenEditProfile: (UserProfileEntity?) -> Unit
) {
    val db = remember { LumeApp.instance.database }
    val settings = remember { AppSettings.get() }
    val scope = rememberCoroutineScope()

    val profiles by db.profileDao().getAllProfiles().collectAsState(initial = emptyList())
    val activeProfile by db.profileDao().observeActiveProfile().collectAsState(initial = null)

    var askOnStartup by remember { mutableStateOf(settings.askProfileOnStartup) }
    var parentalEnabled by remember { mutableStateOf(settings.parentalControlsEnabled) }
    var pinDialogMode by remember { mutableStateOf<PinDialogMode?>(null) }
    var targetSwitchProfile by remember { mutableStateOf<UserProfileEntity?>(null) }
    var isParentalUnlocked by remember { mutableStateOf(false) }

    val profileFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val addProfileFocusRequester = remember { FocusRequester() }

    LaunchedEffect(targetFocusProfileId, profiles) {
        val targetId = targetFocusProfileId ?: return@LaunchedEffect
        delay(40)
        try {
            if (targetId == "add_profile") {
                addProfileFocusRequester.requestFocus()
                onFocusConsumed()
            } else if (profiles.any { it.id == targetId }) {
                profileFocusRequesters[targetId]?.requestFocus()
                onFocusConsumed()
            } else {
                delay(80)
                if (profiles.any { it.id == targetId }) {
                    profileFocusRequesters[targetId]?.requestFocus()
                } else {
                    addProfileFocusRequester.requestFocus()
                }
                onFocusConsumed()
            }
        } catch (_: Exception) {}
    }

    // Seed default profile if empty
    LaunchedEffect(profiles) {
        if (profiles.isEmpty()) {
            withContext(Dispatchers.IO) {
                if (db.profileDao().getProfileCount() == 0) {
                    val defaultProfile = UserProfileEntity(
                        id = "default",
                        name = "Default",
                        avatarColor = 0xFF0A84FF.toInt(),
                        isChild = false,
                        isCurrent = true
                    )
                    db.profileDao().insertProfile(defaultProfile)
                    settings.activeProfileId = "default"
                    settings.activeProfileName = "Default"
                    settings.activeProfileIsChild = false
                }
            }
        }
    }

    val currentActiveId = activeProfile?.id ?: settings.activeProfileId
    val currentIsChild = activeProfile?.isChild ?: settings.activeProfileIsChild
    val requiresParentalGate = parentalEnabled && currentIsChild && !isParentalUnlocked

    Box(modifier = Modifier.fillMaxSize()) {
        if (requiresParentalGate) {
            // Parental Gate View matching Apple TV ParentalGateView
            TvParentalGateView(
                subtitle = "Enter your PIN to manage profiles.",
                onUnlock = { isParentalUnlocked = true }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(36.dp)
            ) {
                // Section: Profiles List
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvSettingsSectionLabel("Profiles")

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        profiles.forEach { profile ->
                            val isActive = profile.id == currentActiveId
                            val rowFocusRequester = profileFocusRequesters.getOrPut(profile.id) { FocusRequester() }
                            TvProfileSwitchRow(
                                profile = profile,
                                isActive = isActive,
                                focusRequester = rowFocusRequester,
                                onSwitch = {
                                    if (!isActive) {
                                        if (currentIsChild && parentalEnabled) {
                                            targetSwitchProfile = profile
                                            pinDialogMode = PinDialogMode.UNLOCK_SWITCH
                                        } else {
                                            scope.launch(Dispatchers.IO) {
                                                db.profileDao().setActiveProfile(profile.id)
                                                settings.activeProfileId = profile.id
                                                settings.activeProfileName = profile.name
                                                settings.activeProfileIsChild = profile.isChild
                                            }
                                        }
                                    }
                                },
                                onEdit = {
                                    onOpenEditProfile(profile)
                                }
                            )
                        }

                        // Add Profile Row
                        TvSettingsRow(
                            title = "Add Profile",
                            leadingIcon = Icons.Default.Add,
                            focusRequester = addProfileFocusRequester,
                            showsPremiumBadge = !settings.isPremium && profiles.isNotEmpty(),
                            onClick = {
                                onOpenEditProfile(null)
                            }
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

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (parentalEnabled) {
                            TvSettingsRow(
                                title = "Change PIN",
                                leadingIcon = Icons.Default.LockReset,
                                value = "••••",
                                onClick = { pinDialogMode = PinDialogMode.CHANGE }
                            )
                            TvSettingsRow(
                                title = "Turn Off PIN",
                                leadingIcon = Icons.Default.LockOpen,
                                isDestructive = true,
                                onClick = { pinDialogMode = PinDialogMode.REMOVE }
                            )
                        } else {
                            TvSettingsRow(
                                title = "Set a PIN",
                                leadingIcon = Icons.Default.Lock,
                                onClick = { pinDialogMode = PinDialogMode.SET }
                            )
                        }
                    }

                    TvSettingsFooter("A PIN is required to switch away from a child profile and to open Content Management.")
                }
            }
        }

        // Active PIN dialog overlay if requested
        pinDialogMode?.let { mode ->
            TvParentalPinDialog(
                mode = mode,
                targetProfile = targetSwitchProfile,
                onDismiss = {
                    pinDialogMode = null
                    targetSwitchProfile = null
                },
                onSuccess = { target ->
                    val finishedMode = pinDialogMode
                    pinDialogMode = null
                    parentalEnabled = settings.parentalControlsEnabled
                    if (finishedMode == PinDialogMode.UNLOCK_SWITCH && target != null) {
                        scope.launch(Dispatchers.IO) {
                            db.profileDao().setActiveProfile(target.id)
                            settings.activeProfileId = target.id
                            settings.activeProfileName = target.name
                            settings.activeProfileIsChild = target.isChild
                        }
                    }
                    targetSwitchProfile = null
                }
            )
        }
    }
}

@Composable
private fun TvProfileSwitchRow(
    profile: UserProfileEntity,
    isActive: Boolean,
    focusRequester: FocusRequester? = null,
    onSwitch: () -> Unit,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            TvSettingsRow(
                title = profile.name,
                focusRequester = focusRequester,
                value = if (profile.isChild) "Child" else if (isActive) "Active" else null,
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(profile.avatarColor)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                trailingCheckmark = isActive,
                onClick = onSwitch
            )
        }

        TvSettingsIconButton(
            icon = Icons.Default.Edit,
            contentDescription = "Edit ${profile.name}",
            onClick = onEdit
        )
    }
}

@Composable
private fun TvProfileEditorPane(
    profile: UserProfileEntity?,
    onBack: () -> Unit,
    onProfileSaved: (savedProfileId: String) -> Unit,
    onProfileDeleted: () -> Unit
) {
    val db = remember { LumeApp.instance.database }
    val settings = remember { AppSettings.get() }
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(profile?.name ?: "") }
    var selectedColor by remember { mutableStateOf(profile?.avatarColor ?: 0xFF0A84FF.toInt()) }
    var isChild by remember { mutableStateOf(profile?.isChild ?: false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var profileCount by remember { mutableStateOf(1) }

    val isEditing = profile != null
    val canDelete = isEditing && profileCount > 1
    val nameFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            nameFocusRequester.requestFocus()
        } catch (_: Exception) {}
        delay(40)
        try {
            nameFocusRequester.requestFocus()
        } catch (_: Exception) {}
        delay(100)
        try {
            nameFocusRequester.requestFocus()
        } catch (_: Exception) {}
        withContext(Dispatchers.IO) {
            profileCount = db.profileDao().getProfileCount()
        }
    }

    val profileColors = remember {
        listOf(
            0xFF0A84FF.toInt() to "Blue",
            0xFFBF5AF2.toInt() to "Purple",
            0xFFFF375F.toInt() to "Pink",
            0xFFFF453A.toInt() to "Red",
            0xFFFF9F0A.toInt() to "Orange",
            0xFFFFD60A.toInt() to "Yellow",
            0xFF30D158.toInt() to "Green",
            0xFF64D2FF.toInt() to "Teal",
            0xFF5E5CE6.toInt() to "Indigo",
            0xFFAC8E68.toInt() to "Brown"
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            TvDrillInHeader(
                backLabel = "Profiles",
                title = if (isEditing) "Edit Profile" else "New Profile",
                onBack = onBack
            )

            // Avatar Preview Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TVSettingsMetrics.rowHPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Color(selectedColor)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (name.isBlank()) "Profile Preview" else name,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isChild) "Child Profile" else "Standard Profile",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 14.sp
                    )
                }
            }

            // Section: Details
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TvSettingsSectionLabel("Details")
                TvSettingsTextInput(
                    label = "Name",
                    value = name,
                    placeholder = "Profile Name",
                    focusRequester = nameFocusRequester,
                    onValueChange = { name = it }
                )
            }

            // Section: Avatar Color
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TvSettingsSectionLabel("Avatar Color")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = TVSettingsMetrics.rowHPadding),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    profileColors.forEach { (colorInt, label) ->
                        val isSelected = selectedColor == colorInt
                        val interactionSource = remember { MutableInteractionSource() }
                        val isFocused by interactionSource.collectIsFocusedAsState()

                        Box(
                            modifier = Modifier
                                .size(if (isFocused) 46.dp else 40.dp)
                                .clip(CircleShape)
                                .background(Color(colorInt))
                                .border(
                                    width = if (isFocused) 3.dp else if (isSelected) 2.dp else 0.dp,
                                    color = if (isFocused || isSelected) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = { selectedColor = colorInt }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = label,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Section: Restrictions
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TvSettingsSectionLabel("Restrictions")
                TvOptionToggleRow(
                    title = "Child Profile",
                    isOn = isChild,
                    onToggle = { isChild = it }
                )
                TvSettingsFooter("Child profiles hide restricted categories from browsing and search. A parental-control PIN, if set, is required to switch away from a child profile.")
            }

            // Section: Actions
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = TVSettingsMetrics.rowHPadding, vertical = 8.dp)
            ) {
                TvSettingsActionButton(
                    title = "Save Profile",
                    prominent = true,
                    onClick = {
                        val trimmed = name.trim()
                        if (trimmed.isEmpty()) return@TvSettingsActionButton

                        scope.launch(Dispatchers.IO) {
                            val savedId = if (profile != null) {
                                val updated = profile.copy(
                                    name = trimmed,
                                    avatarColor = selectedColor,
                                    isChild = isChild
                                )
                                db.profileDao().updateProfile(updated)
                                if (updated.isCurrent) {
                                    settings.activeProfileName = updated.name
                                    settings.activeProfileIsChild = updated.isChild
                                }
                                updated.id
                            } else {
                                val newId = UUID.randomUUID().toString()
                                val newProfile = UserProfileEntity(
                                    id = newId,
                                    name = trimmed,
                                    avatarColor = selectedColor,
                                    isChild = isChild,
                                    isCurrent = false
                                )
                                db.profileDao().insertProfile(newProfile)
                                newId
                            }
                            withContext(Dispatchers.Main) {
                                onProfileSaved(savedId)
                            }
                        }
                    }
                )

                if (canDelete) {
                    TvSettingsActionButton(
                        title = "Delete Profile",
                        isDestructive = true,
                        onClick = { showDeleteConfirm = true }
                    )
                }
            }
        }

        // Delete Confirmation Dialog
        if (showDeleteConfirm && profile != null) {
            TvDeleteConfirmationDialog(
                title = "Delete Profile?",
                message = "This permanently removes this profile's watch history, progress and favorites. Your library is not affected.",
                onConfirm = {
                    showDeleteConfirm = false
                    scope.launch(Dispatchers.IO) {
                        if (profile.isCurrent) {
                            val remaining = db.profileDao().getProfilesList().filter { it.id != profile.id }
                            if (remaining.isNotEmpty()) {
                                val nextActive = remaining.first()
                                db.profileDao().setActiveProfile(nextActive.id)
                                settings.activeProfileId = nextActive.id
                                settings.activeProfileName = nextActive.name
                                settings.activeProfileIsChild = nextActive.isChild
                            }
                        }
                        db.profileDao().deleteProfile(profile)
                        db.watchProgressDao().clearAllProgressForProfile(profile.id)
                        withContext(Dispatchers.Main) {
                            onProfileDeleted()
                        }
                    }
                },
                onDismiss = { showDeleteConfirm = false }
            )
        }
    }
}

@Composable
private fun TvParentalGateView(
    subtitle: String,
    onUnlock: () -> Unit
) {
    val settings = remember { AppSettings.get() }
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(44.dp)
        )
        Text(
            text = "Parental Controls",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 16.sp
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until 4) {
                val isFilled = i < enteredPin.length
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (isFilled) Color.White else Color.White.copy(alpha = 0.2f))
                        .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                )
            }
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = Color(0xFFFF5252),
                fontSize = 14.sp
            )
        }

        TvNumericKeypad(
            onDigitClick = { digit ->
                if (enteredPin.length < 4) {
                    val next = enteredPin + digit
                    enteredPin = next
                    if (next.length == 4) {
                        if (next == settings.parentalPin) {
                            onUnlock()
                        } else {
                            errorMessage = "Incorrect PIN. Try again."
                            enteredPin = ""
                        }
                    }
                }
            },
            onClear = {
                enteredPin = ""
                errorMessage = null
            },
            onBackspace = {
                if (enteredPin.isNotEmpty()) {
                    enteredPin = enteredPin.dropLast(1)
                }
            }
        )
    }
}

@Composable
private fun TvParentalPinDialog(
    mode: PinDialogMode,
    targetProfile: UserProfileEntity? = null,
    onDismiss: () -> Unit,
    onSuccess: (target: UserProfileEntity?) -> Unit
) {
    val settings = remember { AppSettings.get() }
    var step by remember { mutableStateOf(1) }
    var enteredPin by remember { mutableStateOf("") }
    var tempNewPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val title = when (mode) {
        PinDialogMode.SET -> if (step == 1) "Set a PIN" else "Confirm PIN"
        PinDialogMode.CHANGE -> when (step) {
            1 -> "Current PIN"
            2 -> "New PIN"
            else -> "Confirm New PIN"
        }
        PinDialogMode.REMOVE -> "Turn Off PIN"
        PinDialogMode.UNLOCK_GATE -> "Parental Controls"
        PinDialogMode.UNLOCK_SWITCH -> "Enter PIN"
    }

    val subtitle = when (mode) {
        PinDialogMode.SET -> if (step == 1) "Enter a 4-digit parental control PIN." else "Re-enter your PIN to confirm."
        PinDialogMode.CHANGE -> when (step) {
            1 -> "Enter your current PIN."
            2 -> "Enter your new 4-digit PIN."
            else -> "Re-enter your new PIN to confirm."
        }
        PinDialogMode.REMOVE -> "Enter your current PIN to turn off parental controls."
        PinDialogMode.UNLOCK_GATE -> "Enter your PIN to manage profiles."
        PinDialogMode.UNLOCK_SWITCH -> "Enter your PIN to switch away from a child profile."
    }

    fun handlePinComplete(pin: String) {
        when (mode) {
            PinDialogMode.SET -> {
                if (step == 1) {
                    tempNewPin = pin
                    enteredPin = ""
                    errorMessage = null
                    step = 2
                } else {
                    if (pin == tempNewPin) {
                        settings.parentalPin = pin
                        settings.parentalControlsEnabled = true
                        onSuccess(null)
                    } else {
                        errorMessage = "PINs do not match. Try again."
                        enteredPin = ""
                        step = 1
                    }
                }
            }
            PinDialogMode.CHANGE -> {
                when (step) {
                    1 -> {
                        if (pin == settings.parentalPin) {
                            enteredPin = ""
                            errorMessage = null
                            step = 2
                        } else {
                            errorMessage = "Incorrect PIN. Try again."
                            enteredPin = ""
                        }
                    }
                    2 -> {
                        tempNewPin = pin
                        enteredPin = ""
                        errorMessage = null
                        step = 3
                    }
                    3 -> {
                        if (pin == tempNewPin) {
                            settings.parentalPin = pin
                            onSuccess(null)
                        } else {
                            errorMessage = "PINs do not match. Try again."
                            enteredPin = ""
                            step = 2
                        }
                    }
                }
            }
            PinDialogMode.REMOVE -> {
                if (pin == settings.parentalPin) {
                    settings.parentalControlsEnabled = false
                    onSuccess(null)
                } else {
                    errorMessage = "Incorrect PIN. Try again."
                    enteredPin = ""
                }
            }
            PinDialogMode.UNLOCK_GATE, PinDialogMode.UNLOCK_SWITCH -> {
                if (pin == settings.parentalPin) {
                    onSuccess(targetProfile)
                } else {
                    errorMessage = "Incorrect PIN. Try again."
                    enteredPin = ""
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    val digit = when (event.key) {
                        Key.Zero, Key.NumPad0 -> "0"
                        Key.One, Key.NumPad1 -> "1"
                        Key.Two, Key.NumPad2 -> "2"
                        Key.Three, Key.NumPad3 -> "3"
                        Key.Four, Key.NumPad4 -> "4"
                        Key.Five, Key.NumPad5 -> "5"
                        Key.Six, Key.NumPad6 -> "6"
                        Key.Seven, Key.NumPad7 -> "7"
                        Key.Eight, Key.NumPad8 -> "8"
                        Key.Nine, Key.NumPad9 -> "9"
                        Key.Backspace -> {
                            if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                            return@onKeyEvent true
                        }
                        Key.Escape, Key.Back -> {
                            onDismiss()
                            return@onKeyEvent true
                        }
                        else -> null
                    }
                    if (digit != null && enteredPin.length < 4) {
                        val next = enteredPin + digit
                        enteredPin = next
                        if (next.length == 4) {
                            handlePinComplete(next)
                        }
                        return@onKeyEvent true
                    }
                }
                false
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1E1E24))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                .padding(28.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )

                // 4 PIN Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 4) {
                        val isFilled = i < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(if (isFilled) Color.White else Color.White.copy(alpha = 0.2f))
                                .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                        )
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = Color(0xFFFF5252),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }

                // Numeric Keypad
                TvNumericKeypad(
                    onDigitClick = { digit ->
                        if (enteredPin.length < 4) {
                            val next = enteredPin + digit
                            enteredPin = next
                            if (next.length == 4) {
                                handlePinComplete(next)
                            }
                        }
                    },
                    onClear = {
                        enteredPin = ""
                        errorMessage = null
                    },
                    onBackspace = {
                        if (enteredPin.isNotEmpty()) {
                            enteredPin = enteredPin.dropLast(1)
                        }
                    }
                )

                TvSettingsActionButton(
                    title = "Cancel",
                    onClick = onDismiss
                )
            }
        }
    }
}

@Composable
private fun TvNumericKeypad(
    onDigitClick: (String) -> Unit,
    onClear: () -> Unit,
    onBackspace: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("C", "0", "⌫")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        rows.forEach { rowKeys ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                rowKeys.forEach { key ->
                    TvKeypadButton(
                        text = key,
                        onClick = {
                            when (key) {
                                "C" -> onClear()
                                "⌫" -> onBackspace()
                                else -> onDigitClick(key)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TvKeypadButton(
    text: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .width(76.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) Color.White else Color.White.copy(alpha = 0.08f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isFocused) Color.Black else Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TvDeleteConfirmationDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(440.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1E1E24))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                .padding(28.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = message,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    TvSettingsActionButton(
                        title = "Cancel",
                        onClick = onDismiss
                    )
                    TvSettingsActionButton(
                        title = "Delete",
                        isDestructive = true,
                        onClick = onConfirm
                    )
                }
            }
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
    var selectedFrequency by remember { mutableStateOf(settings.playlistSyncFrequency) }
    val isSyncing by PlaylistSyncManager.isSyncing.collectAsState()
    val syncProgressMessage by PlaylistSyncManager.syncProgressMessage.collectAsState()

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
                PlaylistSyncFrequency.values().forEach { freq ->
                    TvSettingsRow(
                        title = freq.title,
                        trailingCheckmark = selectedFrequency == freq,
                        onClick = {
                            selectedFrequency = freq
                            settings.playlistSyncFrequency = freq
                        }
                    )
                }
                TvSettingsRow(
                    title = if (isSyncing) "Syncing Playlists… ${syncProgressMessage?.let { "($it)" } ?: ""}" else "Sync All Playlists Now",
                    onClick = {
                        if (!isSyncing) {
                            scope.launch(Dispatchers.IO) {
                                PlaylistSyncManager.syncAllPlaylists()
                            }
                        }
                    }
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
    val livePlaylist by database.playlistDao().observePlaylistById(playlist.id).collectAsState(initial = playlist)
    val currentPlaylist = livePlaylist ?: playlist

    var isEditing by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(currentPlaylist.name) }
    var editUrl by remember { mutableStateOf(currentPlaylist.serverUrl) }
    var editUsername by remember { mutableStateOf(currentPlaylist.username) }
    var editPassword by remember { mutableStateOf(currentPlaylist.password) }
    var streamFormatIndex by remember { mutableStateOf(0) }
    val streamFormats = listOf("HLS (.m3u8)", "MPEG-TS (.ts)")

    val isSyncing by PlaylistSyncManager.isSyncing.collectAsState()
    val syncingPlaylistId by PlaylistSyncManager.syncingPlaylistId.collectAsState()
    val syncProgressMessage by PlaylistSyncManager.syncProgressMessage.collectAsState()
    val isThisPlaylistSyncing = isSyncing && syncingPlaylistId == currentPlaylist.id

    val backFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try { backFocusRequester.requestFocus() } catch (_: Exception) {}
        delay(60)
        try { backFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(36.dp)
    ) {
        TvDrillInHeader(
            backLabel = "Playlists",
            title = currentPlaylist.name,
            focusRequester = backFocusRequester,
            onBack = onBack
        )

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
                    TvSettingsValueRow("Name", currentPlaylist.name)
                    TvSettingsValueRow("Server URL", currentPlaylist.serverUrl)
                    if (!currentPlaylist.epgUrl.isNullOrEmpty()) {
                        TvSettingsValueRow("EPG URL", currentPlaylist.epgUrl!!)
                    }
                    if (currentPlaylist.username.isNotEmpty()) {
                        TvSettingsValueRow("Username", currentPlaylist.username)
                        TvSettingsValueRow("Password", "••••••••")
                    }
                    TvSettingsValueRow("Type", currentPlaylist.type)
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
                    isOn = currentPlaylist.syncEnabled,
                    onToggle = { isEnabled ->
                        scope.launch(Dispatchers.IO) {
                            database.playlistDao().updateSyncEnabled(currentPlaylist.id, isEnabled)
                        }
                    }
                )
                TvSettingsValueRow(
                    "Status",
                    if (isThisPlaylistSyncing) "Syncing…" else if (currentPlaylist.syncEnabled) "Active" else "Disabled",
                    valueColor = if (currentPlaylist.syncEnabled) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.55f)
                )
                TvSettingsValueRow(
                    "Last Synced",
                    PlaylistSyncManager.formatLastSync(currentPlaylist.lastSyncDate)
                )
                TvSettingsRow(
                    title = if (isThisPlaylistSyncing) "Syncing… ${syncProgressMessage?.let { "($it)" } ?: ""}" else "Sync Now",
                    onClick = {
                        if (!isSyncing) {
                            scope.launch(Dispatchers.IO) {
                                PlaylistSyncManager.syncPlaylist(currentPlaylist)
                            }
                        }
                    }
                )
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
    val settings = remember { AppSettings.get() }
    val scope = rememberCoroutineScope()
    val db = LumeApp.instance.database
    val activePlaylist by db.playlistDao().observeActivePlaylist().collectAsState(initial = null)
    val isSyncing by org.getlume.android.core.sync.EpgSyncManager.isSyncing.collectAsState()
    val syncMsg by org.getlume.android.core.sync.EpgSyncManager.syncMessage.collectAsState()
    var currentInterval by remember { mutableStateOf(settings.syncInterval) }
    var isAddingSource by remember { mutableStateOf(false) }
    var customSourceUrl by remember { mutableStateOf(settings.customEpgUrl) }

    val lastSyncText = if (settings.epgLastSyncDate > 0) {
        android.text.format.DateUtils.getRelativeTimeSpanString(settings.epgLastSyncDate).toString()
    } else {
        "Never"
    }

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
                TvSettingsValueRow(
                    title = activePlaylist?.let { "${it.name} Guide" } ?: "Primary XMLTV Guide",
                    value = if (activePlaylist?.epgUrl.isNullOrBlank()) "From playlist" else "Configured"
                )
                if (settings.customEpgUrl.isNotBlank()) {
                    TvSettingsRow(
                        title = "Custom XMLTV Feed",
                        value = "Remove",
                        isDestructive = true,
                        onClick = {
                            settings.customEpgUrl = ""
                            customSourceUrl = ""
                        }
                    )
                }
            }
            TvSettingsFooter(
                activePlaylist?.epgUrl?.takeIf { it.isNotBlank() }
                    ?: settings.customEpgUrl.takeIf { it.isNotBlank() }
                    ?: "No XMLTV guide URL attached to the active playlist. Add a custom source below."
            )
        }

        // Section: Add Custom Source
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Add Custom Source")

            if (isAddingSource) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvSettingsTextInput("XMLTV URL", customSourceUrl, "http://example.com/epg.xml") { customSourceUrl = it }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvSettingsActionButton(
                            title = "Save Source",
                            prominent = true,
                            onClick = {
                                settings.customEpgUrl = customSourceUrl.trim()
                                isAddingSource = false
                            }
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
                SyncInterval.values().forEach { interval ->
                    TvSettingsRow(
                        title = interval.title,
                        trailingCheckmark = currentInterval == interval,
                        onClick = {
                            currentInterval = interval
                            settings.syncInterval = interval
                        }
                    )
                }
                TvSettingsRow(
                    title = if (isSyncing) "Syncing Guide…" else "Sync Now",
                    value = if (isSyncing) (syncMsg ?: "Downloading") else "Last: $lastSyncText",
                    onClick = {
                        scope.launch {
                            org.getlume.android.core.sync.EpgSyncManager.syncNow(playlist = activePlaylist)
                        }
                    }
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
    onOpenContentManagement: () -> Unit,
    onOpenContentLanguages: () -> Unit = {}
) {
    val settings = remember { AppSettings.get() }
    val revision by settings.revision.collectAsState()
    val db = remember { LumeApp.instance.database }
    val activePlaylist by db.playlistDao().observeActivePlaylist().collectAsState(initial = null)

    var showMovies by remember(revision, activePlaylist?.id) {
        mutableStateOf(activePlaylist?.let { settings.isPlaylistTabVisible(it.id, "movies") } ?: settings.libraryShowMovies)
    }
    var showSeries by remember(revision, activePlaylist?.id) {
        mutableStateOf(activePlaylist?.let { settings.isPlaylistTabVisible(it.id, "series") } ?: settings.libraryShowSeries)
    }
    var showLiveTv by remember(revision, activePlaylist?.id) {
        mutableStateOf(activePlaylist?.let { settings.isPlaylistTabVisible(it.id, "livetv") } ?: settings.libraryShowLiveTV)
    }
    var showSports by remember(revision) { mutableStateOf(settings.libraryShowSports) }
    var searchAll by remember(revision) { mutableStateOf(settings.searchAllPlaylists) }

    val activePlaylistName = activePlaylist?.name
    val tabsFooter = if (!activePlaylistName.isNullOrBlank()) {
        "Movies, Series and Live TV apply to $activePlaylistName, your active playlist. Sports applies to every playlist."
    } else {
        "Movies, Series and Live TV apply to your active playlist. Sports applies to every playlist."
    }

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
                    activePlaylist?.let { pl -> settings.setPlaylistTabVisible(pl.id, "movies", it) }
                        ?: run { settings.libraryShowMovies = it }
                }
                TvOptionToggleRow("Series", showSeries) {
                    showSeries = it
                    activePlaylist?.let { pl -> settings.setPlaylistTabVisible(pl.id, "series", it) }
                        ?: run { settings.libraryShowSeries = it }
                }
                TvOptionToggleRow("Live TV", showLiveTv) {
                    showLiveTv = it
                    activePlaylist?.let { pl -> settings.setPlaylistTabVisible(pl.id, "livetv", it) }
                        ?: run { settings.libraryShowLiveTV = it }
                }
                TvOptionToggleRow("Sports", showSports) {
                    showSports = it
                    settings.libraryShowSports = it
                }
            }

            TvSettingsFooter(tabsFooter)
        }

        // Section: Content
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Content")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TvSettingsRow(
                    title = "Categories & Channels",
                    value = activePlaylistName ?: "No Active Playlist",
                    trailingChevron = true,
                    onClick = onOpenContentManagement
                )

                val selectedCount = settings.homeContentLanguages.size
                val langSummary = if (selectedCount == 0) {
                    "All Languages (Show Everything)"
                } else {
                    val names = settings.homeContentLanguages.mapNotNull { code ->
                        ContentLanguageCatalog.findByCode(code)?.displayName
                    }
                    if (names.size in 1..3) names.joinToString(", ") else "$selectedCount Selected"
                }

                TvSettingsRow(
                    title = "Content Languages",
                    value = langSummary,
                    leadingIcon = Icons.Default.Language,
                    trailingChevron = true,
                    onClick = onOpenContentLanguages
                )
            }

            TvSettingsFooter("Filter content across Home, Live TV, Movies, and Series by language, and manage categories and channels.")
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
    onOpenChannelManagement: (String, String) -> Unit,
    onOpenFavorites: () -> Unit,
    onBack: () -> Unit
) {
    val backFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try { backFocusRequester.requestFocus() } catch (_: Exception) {}
        delay(60)
        try { backFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    var selectedTab by remember { mutableStateOf("Live TV") }
    val tabs = listOf("Live TV", "Movies", "Series")
    val scope = rememberCoroutineScope()
    val db = remember { LumeApp.instance.database }
    val activePlaylist by db.playlistDao().observeActivePlaylist().collectAsState(initial = null)

    val categoryType = when (selectedTab) {
        "Live TV" -> "live"
        "Movies" -> "movie"
        "Series" -> "series"
        else -> "live"
    }

    val categories by db.categoryDao()
        .getAllCategoriesForManagement(activePlaylist?.id ?: "", categoryType)
        .collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        TvDrillInHeader(
            backLabel = "Library",
            title = "Content Management",
            focusRequester = backFocusRequester,
            onBack = onBack
        )

        // Favorites Entry (Matches Lume tvOS ContentManagementView Favorites link)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsRow(
                title = "Favorites",
                value = "Channels, Movies, Series",
                leadingIcon = Icons.Default.Favorite,
                trailingChevron = true,
                onClick = onOpenFavorites
            )
            TvSettingsFooter("Reorder and manage all your favorite channels, movies, and series in one unified list.")
        }

        // Type Picker (Live TV, Movies, Series)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            tabs.forEach { tab ->
                TvSettingsActionButton(
                    title = tab,
                    prominent = selectedTab == tab,
                    onClick = { selectedTab = tab }
                )
            }
        }

        // Bulk Actions
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvSettingsActionButton(
                title = "Show All",
                onClick = {
                    activePlaylist?.let { pl ->
                        scope.launch {
                            db.categoryDao().setAllCategoriesHidden(pl.id, categoryType, false)
                        }
                    }
                }
            )
            TvSettingsActionButton(
                title = "Hide All",
                onClick = {
                    activePlaylist?.let { pl ->
                        scope.launch {
                            db.categoryDao().setAllCategoriesHidden(pl.id, categoryType, true)
                        }
                    }
                }
            )
            TvSettingsActionButton(
                title = "Reset Order",
                onClick = {
                    activePlaylist?.let { pl ->
                        scope.launch {
                            db.categoryDao().resetCategorySortOrder(pl.id, categoryType)
                        }
                    }
                }
            )
        }

        // Categories List
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val headerTitle = if (activePlaylist != null) {
                "Categories (${categories.size}) — ${activePlaylist?.name}"
            } else {
                "Categories"
            }
            TvSettingsSectionLabel(headerTitle)

            if (activePlaylist == null) {
                TvSettingsValueRow("No Playlist", "Select an active playlist in Settings > Playlists first.")
            } else if (categories.isEmpty()) {
                TvSettingsValueRow("Categories", "No categories found for $selectedTab. Sync this playlist first.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    categories.forEachIndexed { index, cat ->
                        TvContentManagementRow(
                            title = cat.name,
                            isEnabled = !cat.isHidden,
                            isFirst = index == 0,
                            isLast = index == categories.size - 1,
                            showChannelsButton = selectedTab == "Live TV",
                            onToggle = {
                                scope.launch {
                                    db.categoryDao().updateCategoryHidden(cat.id, !cat.isHidden)
                                }
                            },
                            onMoveUp = {
                                if (index > 0) {
                                    scope.launch {
                                        val reordered = categories.toMutableList()
                                        val item = reordered.removeAt(index)
                                        reordered.add(index - 1, item)
                                        reordered.forEachIndexed { i, c ->
                                            db.categoryDao().updateCategorySortOrder(c.id, i)
                                        }
                                    }
                                }
                            },
                            onMoveDown = {
                                if (index < categories.size - 1) {
                                    scope.launch {
                                        val reordered = categories.toMutableList()
                                        val item = reordered.removeAt(index)
                                        reordered.add(index + 1, item)
                                        reordered.forEachIndexed { i, c ->
                                            db.categoryDao().updateCategorySortOrder(c.id, i)
                                        }
                                    }
                                }
                            },
                            onOpenChannels = { onOpenChannelManagement(cat.id, cat.name) }
                        )
                    }
                }
            }

            TvSettingsFooter(
                if (selectedTab == "Live TV") "Press Select on a category to show/hide. Use Channels to manage individual streams, or arrows to reorder."
                else "Press Select on a category to show/hide, or arrows to reorder."
            )
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
    val rowInteractionSource = remember { MutableInteractionSource() }
    val isRowFocused by rowInteractionSource.collectIsFocusedAsState()

    val upInteractionSource = remember { MutableInteractionSource() }
    val isUpFocused by upInteractionSource.collectIsFocusedAsState()

    val downInteractionSource = remember { MutableInteractionSource() }
    val isDownFocused by downInteractionSource.collectIsFocusedAsState()

    val isAnyFocused = isRowFocused || isUpFocused || isDownFocused

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TVSettingsMetrics.rowCornerRadius))
            .background(if (isAnyFocused) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.04f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left category card (clickable on DPAD_CENTER to toggle visibility)
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isRowFocused) Color.White.copy(alpha = 0.95f) else Color.Transparent)
                .clickable(
                    interactionSource = rowInteractionSource,
                    indication = null,
                    onClick = onToggle
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = if (isEnabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = null,
                    tint = if (isRowFocused) (if (isEnabled) Color(0xFF1565C0) else Color.Black.copy(alpha = 0.45f))
                           else (if (isEnabled) Color(0xFF64B5F6) else Color.White.copy(alpha = 0.35f)),
                    modifier = Modifier.size(20.dp)
                )

                Text(
                    text = title,
                    color = if (isRowFocused) Color.Black else if (isEnabled) Color.White else Color.White.copy(alpha = 0.45f),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium
                )

                if (!isEnabled) {
                    Text(
                        text = "(Hidden)",
                        color = if (isRowFocused) Color.Black.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.55f),
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Action buttons reachable via DPAD_RIGHT
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 8.dp)
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
                interactionSource = upInteractionSource,
                onClick = onMoveUp
            )
            TvMiniIconButton(
                icon = Icons.Default.KeyboardArrowDown,
                enabled = !isLast,
                interactionSource = downInteractionSource,
                onClick = onMoveDown
            )
        }
    }
}

// ==========================================
// DRILL-IN: CHANNEL MANAGEMENT (ChannelManagementView.swift)
// ==========================================
@Composable
private fun TvChannelManagementPane(
    categoryId: String,
    categoryName: String,
    onBack: () -> Unit
) {
    val backFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try { backFocusRequester.requestFocus() } catch (_: Exception) {}
        delay(60)
        try { backFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    val scope = rememberCoroutineScope()
    val db = remember { LumeApp.instance.database }
    val activePlaylist by db.playlistDao().observeActivePlaylist().collectAsState(initial = null)
    val channels by db.channelDao()
        .getChannelsForManagement(activePlaylist?.id ?: "", categoryId)
        .collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        TvDrillInHeader(
            backLabel = "Categories",
            title = categoryName,
            focusRequester = backFocusRequester,
            onBack = onBack
        )

        // Bulk Actions
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvSettingsActionButton(
                title = "Show All",
                onClick = {
                    activePlaylist?.let { pl ->
                        scope.launch {
                            db.channelDao().setAllChannelsHidden(pl.id, categoryId, false)
                        }
                    }
                }
            )
            TvSettingsActionButton(
                title = "Hide All",
                onClick = {
                    activePlaylist?.let { pl ->
                        scope.launch {
                            db.channelDao().setAllChannelsHidden(pl.id, categoryId, true)
                        }
                    }
                }
            )
            TvSettingsActionButton(
                title = "Reset Order",
                onClick = {
                    activePlaylist?.let { pl ->
                        scope.launch {
                            db.channelDao().resetChannelSortOrder(pl.id, categoryId)
                        }
                    }
                }
            )
        }

        // Channels List
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Channels (${channels.size})")

            if (channels.isEmpty()) {
                TvSettingsValueRow("Channels", "No channels found in this category.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    channels.forEachIndexed { index, channel ->
                        TvSettingsReorderRow(
                            title = channel.name,
                            isEnabled = !channel.isHidden,
                            isFirst = index == 0,
                            isLast = index == channels.size - 1,
                            onToggle = {
                                scope.launch {
                                    db.channelDao().updateChannelHidden(channel.id, !channel.isHidden)
                                }
                            },
                            onMoveUp = {
                                if (index > 0) {
                                    scope.launch {
                                        val reordered = channels.toMutableList()
                                        val item = reordered.removeAt(index)
                                        reordered.add(index - 1, item)
                                        reordered.forEachIndexed { i, c ->
                                            db.channelDao().updateChannelSortOrder(c.id, i)
                                        }
                                    }
                                }
                            },
                            onMoveDown = {
                                if (index < channels.size - 1) {
                                    scope.launch {
                                        val reordered = channels.toMutableList()
                                        val item = reordered.removeAt(index)
                                        reordered.add(index + 1, item)
                                        reordered.forEachIndexed { i, c ->
                                            db.channelDao().updateChannelSortOrder(c.id, i)
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }

            TvSettingsFooter("Hide channels to remove them from this category in Live TV, or reorder to customize channel lineup.")
        }
    }
}

// ==========================================
// DRILL-IN: FAVORITES MANAGEMENT (FavoriteManagementView.swift)
// ==========================================
data class TvFavoriteItem(
    val id: String,
    val title: String,
    val type: String, // "Live TV", "Movie", "Series"
    val originalOrder: Int
)

@Composable
private fun TvFavoriteManagementPane(
    onBack: () -> Unit
) {
    val backFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try { backFocusRequester.requestFocus() } catch (_: Exception) {}
        delay(60)
        try { backFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    val scope = rememberCoroutineScope()
    val db = remember { LumeApp.instance.database }
    val activePlaylist by db.playlistDao().observeActivePlaylist().collectAsState(initial = null)

    val favChannels by db.channelDao().getFavoriteChannels(activePlaylist?.id ?: "").collectAsState(initial = emptyList())
    val favMovies by db.vodDao().getFavoriteMovies(activePlaylist?.id ?: "").collectAsState(initial = emptyList())
    val favSeries by db.vodDao().getFavoriteSeries(activePlaylist?.id ?: "").collectAsState(initial = emptyList())

    val allFavorites = remember(favChannels, favMovies, favSeries) {
        val list = mutableListOf<TvFavoriteItem>()
        favChannels.forEach { list.add(TvFavoriteItem(it.id, it.name, "Live TV", it.sortOrder)) }
        favMovies.forEach { list.add(TvFavoriteItem(it.id, it.name, "Movie", 0)) }
        favSeries.forEach { list.add(TvFavoriteItem(it.id, it.name, "Series", 0)) }
        list
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        TvDrillInHeader(
            backLabel = "Content Management",
            title = "Favorites",
            focusRequester = backFocusRequester,
            onBack = onBack
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("All Favorites (${allFavorites.size})")

            if (allFavorites.isEmpty()) {
                TvSettingsValueRow("Favorites", "No favorites yet. Mark channels, movies, or series as favorites to manage them here.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    allFavorites.forEach { fav ->
                        TvFavoriteItemRow(
                            title = fav.title,
                            type = fav.type,
                            onRemove = {
                                scope.launch {
                                    when (fav.type) {
                                        "Live TV" -> db.channelDao().updateFavorite(fav.id, false)
                                        "Movie" -> db.vodDao().updateMovieFavorite(fav.id, false)
                                        "Series" -> db.vodDao().updateSeriesFavorite(fav.id, false)
                                    }
                                }
                            }
                        )
                    }
                }
            }

            TvSettingsFooter("All your favorited channels, movies, and series. Press Select on an item to remove it from Favorites.")
        }
    }
}

@Composable
private fun TvFavoriteItemRow(
    title: String,
    type: String,
    onRemove: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TVSettingsMetrics.rowCornerRadius))
            .background(if (isFocused) Color.White.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.05f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onRemove
            )
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
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(22.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        color = if (isFocused) Color.Black else Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = type,
                        color = if (isFocused) Color.Black.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp
                    )
                }
            }
            Text(
                text = "Remove",
                color = if (isFocused) Color.Black.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.45f),
                fontSize = 14.sp
            )
        }
    }
}

// ==========================================
// PANE 6: HOME (SettingsView+TVHome.swift)
// ==========================================
data class TvHomeSectionItem(val key: String, val title: String, val isPro: Boolean = false)

@Composable
private fun TvHomeLayoutSettingsPane(
    onOpenContentLanguages: () -> Unit
) {
    val settings = remember { AppSettings.get() }

    val canonicalSections = remember {
        listOf(
            TvHomeSectionItem("recentlyWatched", "Recently Watched"),
            TvHomeSectionItem("favorites", "Favorites"),
            TvHomeSectionItem("forYou", "For You", isPro = true),
            TvHomeSectionItem("trendingMovies", "Trending Movies"),
            TvHomeSectionItem("trendingSeries", "Trending Series"),
            TvHomeSectionItem("traktWatchlist", "Trakt Watchlist"),
            TvHomeSectionItem("simklWatchlist", "Simkl Watchlist"),
            TvHomeSectionItem("sports", "Sports", isPro = true)
        )
    }

    val orderedSections = remember(settings.homeSectionOrder) {
        val orderKeys = settings.homeSectionOrder.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val map = canonicalSections.associateBy { it.key }
        val result = mutableListOf<TvHomeSectionItem>()
        val seen = mutableSetOf<String>()
        orderKeys.forEach { key ->
            map[key]?.let {
                result.add(it)
                seen.add(key)
            }
        }
        canonicalSections.forEach { if (!seen.contains(it.key)) result.add(it) }
        result
    }

    var sections by remember { mutableStateOf(orderedSections) }
    var disabledKeys by remember { mutableStateOf(settings.homeDisabledSections) }
    var includeLiveTv by remember { mutableStateOf(settings.homeRecentlyWatchedIncludesLiveTV) }
    var androidTvChannels by remember { mutableStateOf(settings.androidTvChannelsEnabled) }
    val homeStyles = listOf("Rows", "Carousel")
    var homeStyleIndex by remember { mutableStateOf(if (settings.homeLayoutStyle == "Carousel") 1 else 0) }
    val itemCounts = listOf(5, 10, 15, 20)
    var itemsPerSectionIndex by remember {
        mutableStateOf(itemCounts.indexOf(settings.homeItemsPerSection).coerceAtLeast(1))
    }

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
                    val isEnabled = !disabledKeys.contains(sec.key)
                    TvSettingsReorderRow(
                        title = sec.title,
                        isEnabled = isEnabled,
                        isFirst = index == 0,
                        isLast = index == sections.size - 1,
                        showsPremiumBadge = sec.isPro && !settings.isPremium,
                        onToggle = {
                            val newEnabled = !isEnabled
                            settings.setHomeSectionEnabled(sec.key, newEnabled)
                            disabledKeys = settings.homeDisabledSections
                        },
                        onMoveUp = {
                            if (index > 0) {
                                val list = sections.toMutableList()
                                val temp = list[index]
                                list[index] = list[index - 1]
                                list[index - 1] = temp
                                sections = list
                                settings.homeSectionOrder = list.joinToString(",") { it.key }
                            }
                        },
                        onMoveDown = {
                            if (index < sections.size - 1) {
                                val list = sections.toMutableList()
                                val temp = list[index]
                                list[index] = list[index + 1]
                                list[index + 1] = temp
                                sections = list
                                settings.homeSectionOrder = list.joinToString(",") { it.key }
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

        // Section: Content Languages
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Content Languages")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val selectedCount = settings.homeContentLanguages.size
                val summary = if (selectedCount == 0) {
                    "All Languages (Show Everything)"
                } else {
                    val names = settings.homeContentLanguages.mapNotNull { code ->
                        ContentLanguageCatalog.findByCode(code)?.displayName
                    }
                    if (names.size in 1..3) names.joinToString(", ") else "$selectedCount Selected"
                }

                TvSettingsRow(
                    title = "Filter Home Content",
                    value = summary,
                    leadingIcon = Icons.Default.Language,
                    trailingChevron = true,
                    onClick = onOpenContentLanguages
                )
            }

            TvSettingsFooter("Choose languages to show on Home. If nothing is selected, all content will be shown.")
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
                        onAdvance = {
                            homeStyleIndex = (homeStyleIndex + 1) % homeStyles.size
                            settings.homeLayoutStyle = homeStyles[homeStyleIndex]
                        }
                    )
                    TvOptionCycleRow(
                        title = "Items per Section",
                        value = itemCounts[itemsPerSectionIndex].toString(),
                        onAdvance = {
                            itemsPerSectionIndex = (itemsPerSectionIndex + 1) % itemCounts.size
                            settings.homeItemsPerSection = itemCounts[itemsPerSectionIndex]
                        }
                    )
                }
            }

            TvSettingsFooter("What Lume shows on your Android TV and Google TV home screen channels. A section appears only when it has content to show.")
        }
    }
}

// ==========================================
// DRILL-IN: HOME CONTENT LANGUAGES
// ==========================================
@Composable
private fun TvHomeContentLanguagesPane(
    onBack: () -> Unit
) {
    val settings = remember { AppSettings.get() }
    var activeLangs by remember { mutableStateOf(settings.homeContentLanguages) }

    fun setLanguages(newSet: Set<String>) {
        activeLangs = newSet
        settings.homeContentLanguages = newSet
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        TvDrillInHeader(backLabel = "Settings", title = "Content Languages", onBack = onBack)

        // Status & Quick Actions
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("Filter Status")

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvSettingsActionButton(
                    title = if (activeLangs.isEmpty()) "Showing Everything (No Filter)" else "Deselect All (Show Everything)",
                    prominent = activeLangs.isEmpty(),
                    onClick = {
                        setLanguages(emptySet())
                    }
                )
            }

            val statusDescription = if (activeLangs.isEmpty()) {
                "Showing all content across Home, Live TV, Movies, and Series. Select any languages below to filter."
            } else {
                "${activeLangs.size} language(s) selected. Only matching content and categories will appear across Home, Live TV, Movies, and Series. Deselect all to show everything."
            }
            TvSettingsFooter(statusDescription)
        }

        // Section: All Languages (Multiple Selection)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvSettingsSectionLabel("All Languages")

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ContentLanguageCatalog.allLanguages.forEach { lang ->
                    val isChecked = settings.isHomeContentLanguageEnabled(lang.code)
                    TvSettingsRow(
                        title = "${lang.displayName} (${lang.hint})",
                        trailingCheckmark = isChecked,
                        onClick = {
                            val nextChecked = !isChecked
                            settings.setHomeContentLanguageEnabled(lang.code, nextChecked)
                            activeLangs = settings.homeContentLanguages
                        }
                    )
                }
            }

            TvSettingsFooter("Check any languages you want to appear across Home, Live TV, Movies, and Series. If nothing is selected, all content will be shown. Categories with no matching items will be hidden automatically.")
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
                val lastRefreshText = if (settings.lastSportsRefreshDate > 0) {
                    android.text.format.DateUtils.getRelativeTimeSpanString(settings.lastSportsRefreshDate).toString()
                } else {
                    "Never"
                }

                TvSettingsRow(
                    title = if (isSyncing) "Refreshing Sports Data…" else "Refresh Now",
                    onClick = {
                        scope.launch {
                            isSyncing = true
                            try {
                                val espn = org.getlume.android.core.network.ESPNClient()
                                espn.fetchScoreboard("soccer/eng.1")
                                settings.lastSportsRefreshDate = System.currentTimeMillis()
                            } catch (_: Exception) {}
                            isSyncing = false
                        }
                    }
                )
                TvSettingsValueRow("Last Refreshed", lastRefreshText)
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
    val channelSurfingModes = listOf("Remote Order", "List Order")
    var channelSurfingIndex by remember {
        mutableStateOf(settings.liveSurfMode.ordinal.coerceIn(0, channelSurfingModes.size - 1))
    }
    val streamInfoModes = listOf("Off", "Minimal", "Detailed (Bitrate, Codec, FPS, Buffer)")
    var streamInfoIndex by remember {
        mutableStateOf(settings.streamInfoLevel.ordinal.coerceIn(0, streamInfoModes.size - 1))
    }
    val externalPlayers = listOf("Off", "VLC for Android", "Just Player", "Kodi")
    var externalPlayerIndex by remember {
        mutableStateOf(settings.externalPlayer.ordinal.coerceIn(0, externalPlayers.size - 1))
    }
    var externalPlayerScopeIndex by remember { mutableStateOf(settings.externalPlayerScope.ordinal) }
    val externalPlayerScopes = listOf("All Streams", "Live TV Only", "VOD Only")
    var passthrough by remember { mutableStateOf(settings.audioPassthrough) }
    var tunneledHardware by remember { mutableStateOf(settings.tunneledVideoPlayback) }
    val buffers = listOf("Low Latency (2s)", "Default (10s)", "Large (30s)")
    var bufferIndex by remember {
        mutableStateOf(settings.bufferSize.ordinal.coerceIn(0, buffers.size - 1))
    }

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
                    onAdvance = {
                        channelSurfingIndex = (channelSurfingIndex + 1) % channelSurfingModes.size
                        settings.liveSurfMode = LiveSurfMode.values()[channelSurfingIndex]
                    }
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
                        onClick = {
                            streamInfoIndex = index
                            settings.streamInfoLevel = StreamInfoLevel.values()[index]
                        }
                    )
                }
            }

            TvSettingsFooter(
                if (streamInfoIndex == 0) "Stream technical readout is disabled."
                else if (streamInfoIndex == 1) "Shows the playlist and what's on now."
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
                    onAdvance = {
                        externalPlayerIndex = (externalPlayerIndex + 1) % externalPlayers.size
                        settings.externalPlayer = ExternalPlayerType.values()[externalPlayerIndex]
                    }
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
                    onAdvance = {
                        bufferIndex = (bufferIndex + 1) % buffers.size
                        settings.bufferSize = BufferSizeType.values()[bufferIndex]
                    }
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
    val settings = remember { AppSettings.get() }
    val sizes = listOf("Small", "Medium", "Large", "Extra Large")
    val sizeValues = listOf(16, 20, 24, 28)
    var sizeIndex by remember {
        mutableStateOf(sizeValues.indexOf(settings.subtitleSizeSp).let { if (it >= 0) it else 1 })
    }
    val fonts = listOf("System", "Monospaced", "Serif", "Rounded")
    var fontIndex by remember {
        mutableStateOf(fonts.indexOf(settings.subtitleFont).let { if (it >= 0) it else 0 })
    }
    var isBold by remember { mutableStateOf(settings.subtitleBold) }
    val colors = listOf("White", "Yellow", "Cyan", "Green")
    val colorHexes = listOf("#FFFFFF", "#FFD54F", "#4DD0E1", "#81C784")
    var colorIndex by remember {
        mutableStateOf(colorHexes.indexOf(settings.subtitleColorHex).let { if (it >= 0) it else 0 })
    }
    val boxes = listOf("None", "Translucent", "Solid")
    var boxIndex by remember {
        mutableStateOf(if (!settings.subtitleBackground) 0 else 1)
    }
    val positions = listOf("Bottom", "Middle", "Top")
    var positionIndex by remember {
        mutableStateOf(positions.indexOf(settings.subtitlePosition).let { if (it >= 0) it else 0 })
    }

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
            contentAlignment = when (positionIndex) {
                1 -> Alignment.Center
                2 -> Alignment.TopCenter
                else -> Alignment.BottomCenter
            }
        ) {
            Box(
                modifier = Modifier
                    .padding(
                        top = if (positionIndex == 2) 24.dp else 0.dp,
                        bottom = if (positionIndex == 0) 24.dp else 0.dp
                    )
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
                    fontFamily = when (fontIndex) {
                        1 -> FontFamily.Monospace
                        2 -> FontFamily.Serif
                        else -> FontFamily.Default
                    },
                    fontSize = sizeValues[sizeIndex].sp,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium
                )
            }
        }

        // 2-column Grid of Options
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    TvOptionCycleRow("Size", sizes[sizeIndex]) {
                        sizeIndex = (sizeIndex + 1) % sizes.size
                        settings.subtitleSizeSp = sizeValues[sizeIndex]
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    TvOptionCycleRow("Font", fonts[fontIndex]) {
                        fontIndex = (fontIndex + 1) % fonts.size
                        settings.subtitleFont = fonts[fontIndex]
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    TvOptionToggleRow("Bold", isBold) {
                        isBold = it
                        settings.subtitleBold = it
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    TvOptionCycleRow("Text Color", colors[colorIndex]) {
                        colorIndex = (colorIndex + 1) % colors.size
                        settings.subtitleColorHex = colorHexes[colorIndex]
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    TvOptionCycleRow("Box", boxes[boxIndex]) {
                        boxIndex = (boxIndex + 1) % boxes.size
                        settings.subtitleBackground = (boxIndex != 0)
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    TvOptionCycleRow("Position", positions[positionIndex]) {
                        positionIndex = (positionIndex + 1) % positions.size
                        settings.subtitlePosition = positions[positionIndex]
                    }
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
                    settings.subtitleSizeSp = 20
                    settings.subtitleFont = "System"
                    settings.subtitleBold = false
                    settings.subtitleColorHex = "#FFFFFF"
                    settings.subtitleBackground = true
                    settings.subtitlePosition = "Bottom"
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
    val settings = remember { AppSettings.get() }
    val languageCodeMap = remember {
        mapOf(
            "en" to Pair("English", "ENG"),
            "es" to Pair("Spanish", "SPA"),
            "fr" to Pair("French", "FRA"),
            "de" to Pair("German", "DEU"),
            "it" to Pair("Italian", "ITA"),
            "pt" to Pair("Portuguese", "POR"),
            "ja" to Pair("Japanese", "JPN"),
            "ko" to Pair("Korean", "KOR"),
            "zh" to Pair("Chinese", "ZHO"),
            "ar" to Pair("Arabic", "ARA"),
            "hi" to Pair("Hindi", "HIN"),
            "ru" to Pair("Russian", "RUS"),
            "pl" to Pair("Polish", "POL"),
            "nl" to Pair("Dutch", "NLD"),
            "tr" to Pair("Turkish", "TUR"),
            "sv" to Pair("Swedish", "SWE")
        )
    }

    var languages by remember {
        val parsed = settings.preferredAudioLanguages.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .map { code ->
                languageCodeMap[code] ?: Pair(code.uppercase(), code.uppercase())
            }
        mutableStateOf(
            if (parsed.isNotEmpty()) parsed else listOf(
                Pair("English", "ENG"),
                Pair("Spanish", "SPA"),
                Pair("French", "FRA"),
                Pair("German", "DEU")
            )
        )
    }

    fun persistLanguages(list: List<Pair<String, String>>) {
        languages = list
        settings.preferredAudioLanguages = list.joinToString(",") { it.second.lowercase() }
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
                                persistLanguages(list)
                            }
                        },
                        onMoveDown = {
                            if (index < languages.size - 1) {
                                val list = languages.toMutableList()
                                val temp = list[index]
                                list[index] = list[index + 1]
                                list[index + 1] = temp
                                persistLanguages(list)
                            }
                        },
                        onRemove = {
                            val list = languages.toMutableList()
                            list.removeAt(index)
                            persistLanguages(list)
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
    val settings = remember { AppSettings.get() }
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

    fun addLanguage(lang: Pair<String, String>) {
        val existing = settings.preferredAudioLanguages.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toMutableList()
        val code = lang.second.lowercase()
        if (!existing.contains(code)) {
            existing.add(code)
            settings.preferredAudioLanguages = existing.joinToString(",")
        }
        onBack()
    }

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
                        onClick = { addLanguage(lang) }
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
                        onClick = { addLanguage(lang) }
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
    val settings = remember { AppSettings.get() }
    val engineDefs = remember {
        mapOf(
            "exoplayer_hw" to Triple("ExoPlayer (Hardware Accelerated)", "High performance hardware decoding for modern codecs", true),
            "exoplayer_tunneled" to Triple("ExoPlayer (Tunneled Video)", "Low latency synchronized A/V pipeline for Shield TV", true),
            "vlc_sw" to Triple("LibVLC (Software Fallback)", "Maximum format compatibility for legacy TS and codecs", true)
        )
    }

    var engineKeys by remember {
        val keys = settings.enginePriority.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        mutableStateOf(if (keys.isNotEmpty()) keys else listOf("exoplayer_hw", "exoplayer_tunneled", "vlc_sw"))
    }

    fun persistPriority(newKeys: List<String>) {
        engineKeys = newKeys
        settings.enginePriority = newKeys.joinToString(",")
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
                engineKeys.forEachIndexed { index, key ->
                    val def = engineDefs[key] ?: Triple(key, "", true)
                    TvSettingsReorderRow(
                        title = def.first,
                        isFirst = index == 0,
                        isLast = index == engineKeys.size - 1,
                        onMoveUp = {
                            if (index > 0) {
                                val list = engineKeys.toMutableList()
                                val temp = list[index]
                                list[index] = list[index - 1]
                                list[index - 1] = temp
                                persistPriority(list)
                            }
                        },
                        onMoveDown = {
                            if (index < engineKeys.size - 1) {
                                val list = engineKeys.toMutableList()
                                val temp = list[index]
                                list[index] = list[index + 1]
                                list[index + 1] = temp
                                persistPriority(list)
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
                    persistPriority(listOf("exoplayer_hw", "exoplayer_tunneled", "vlc_sw"))
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
    val liveBuffers = listOf("1 Second", "2 Seconds", "5 Seconds (Recommended)", "10 Seconds")
    val liveBufferMs = listOf(1000, 2000, 5000, 10000)
    var liveBufferIndex by remember {
        mutableStateOf(liveBufferMs.indexOf(settings.exoPlayerLiveBufferMs).let { if (it >= 0) it else 2 })
    }
    val vodBuffers = listOf("5 Seconds", "15 Seconds (Recommended)", "30 Seconds", "60 Seconds")
    val vodBufferMs = listOf(5000, 15000, 30000, 60000)
    var vodBufferIndex by remember {
        mutableStateOf(vodBufferMs.indexOf(settings.exoPlayerVodBufferMs).let { if (it >= 0) it else 1 })
    }
    val probeSizes = listOf("Auto (Provider Default)", "1 Megabyte", "5 Megabytes", "10 Megabytes")
    val probeMb = listOf(0, 1, 5, 10)
    var probeIndex by remember {
        mutableStateOf(probeMb.indexOf(settings.exoPlayerProbeSizeMb).let { if (it >= 0) it else 0 })
    }

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
                    onAdvance = {
                        liveBufferIndex = (liveBufferIndex + 1) % liveBuffers.size
                        settings.exoPlayerLiveBufferMs = liveBufferMs[liveBufferIndex]
                    }
                )
                TvOptionCycleRow(
                    title = "VOD Pre-Buffer",
                    value = vodBuffers[vodBufferIndex],
                    onAdvance = {
                        vodBufferIndex = (vodBufferIndex + 1) % vodBuffers.size
                        settings.exoPlayerVodBufferMs = vodBufferMs[vodBufferIndex]
                    }
                )
                TvOptionCycleRow(
                    title = "Stream Probe Size",
                    value = probeSizes[probeIndex],
                    onAdvance = {
                        probeIndex = (probeIndex + 1) % probeSizes.size
                        settings.exoPlayerProbeSizeMb = probeMb[probeIndex]
                    }
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
                        liveBufferIndex = 2
                        vodBufferIndex = 1
                        probeIndex = 0
                        settings.tunneledVideoPlayback = true
                        settings.audioPassthrough = false
                        settings.exoPlayerHttpReconnect = true
                        settings.exoPlayerLiveBufferMs = 5000
                        settings.exoPlayerVodBufferMs = 15000
                        settings.exoPlayerProbeSizeMb = 0
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
    val hwModes = listOf("Auto (Recommended)", "Hardware Enabled", "Software Only")
    val hwValues = listOf("Auto", "Hardware Enabled", "Software Only")
    var hwDecodingIndex by remember {
        mutableStateOf(hwValues.indexOf(settings.vlcHardwareDecoding).let { if (it >= 0) it else 0 })
    }
    val deinterlaceModes = listOf("Off", "Auto", "Blend", "Bob", "Yadif")
    var deinterlaceIndex by remember {
        mutableStateOf(deinterlaceModes.indexOf(settings.vlcDeinterlaceMode).let { if (it >= 0) it else 1 })
    }
    val netCachings = listOf("500 ms (Low Latency)", "1500 ms (Default)", "3000 ms (High Stability)", "5000 ms")
    val netCachingMs = listOf(500, 1500, 3000, 5000)
    var netCachingIndex by remember {
        mutableStateOf(netCachingMs.indexOf(settings.vlcNetworkCachingMs).let { if (it >= 0) it else 1 })
    }
    var skipFrames by remember { mutableStateOf(settings.vlcSkipFrames) }
    var dropLate by remember { mutableStateOf(settings.vlcDropLateFrames) }
    val audioOutputs = listOf("AudioTrack (Android Standard)", "OpenSL ES (Low Latency Native)")
    val audioOutputValues = listOf("AudioTrack", "OpenSL ES")
    var audioOutIndex by remember {
        mutableStateOf(audioOutputValues.indexOf(settings.vlcAudioOutput).let { if (it >= 0) it else 0 })
    }

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
                    onAdvance = {
                        hwDecodingIndex = (hwDecodingIndex + 1) % hwModes.size
                        settings.vlcHardwareDecoding = hwValues[hwDecodingIndex]
                    }
                )
                TvOptionCycleRow(
                    title = "Deinterlace Mode",
                    value = deinterlaceModes[deinterlaceIndex],
                    onAdvance = {
                        deinterlaceIndex = (deinterlaceIndex + 1) % deinterlaceModes.size
                        settings.vlcDeinterlaceMode = deinterlaceModes[deinterlaceIndex]
                    }
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
                    onAdvance = {
                        netCachingIndex = (netCachingIndex + 1) % netCachings.size
                        settings.vlcNetworkCachingMs = netCachingMs[netCachingIndex]
                    }
                )
                TvOptionCycleRow(
                    title = "Audio Output Backend",
                    value = audioOutputs[audioOutIndex],
                    onAdvance = {
                        audioOutIndex = (audioOutIndex + 1) % audioOutputs.size
                        settings.vlcAudioOutput = audioOutputValues[audioOutIndex]
                    }
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
                        settings.vlcHardwareDecoding = "Auto"
                        settings.vlcDeinterlaceMode = "Auto"
                        settings.vlcNetworkCachingMs = 1500
                        settings.vlcSkipFrames = false
                        settings.vlcDropLateFrames = true
                        settings.vlcAudioOutput = "AudioTrack"
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
    val context = LocalContext.current
    var isClearing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val db = LumeApp.instance.database
    val activePlaylist by db.playlistDao().observeActivePlaylist().collectAsState(initial = null)
    val channels by db.channelDao().observeChannelsByPlaylist(activePlaylist?.id ?: "").collectAsState(initial = emptyList())
    var movieCount by remember { mutableStateOf(0) }
    var seriesCount by remember { mutableStateOf(0) }
    val isSyncing by org.getlume.android.core.sync.ContentSyncManager.isSyncing.collectAsState()
    val syncProgress by org.getlume.android.core.sync.ContentSyncManager.syncProgress.collectAsState()

    var imageCacheSizeMb by remember { mutableStateOf(0.0) }
    var metadataCacheSizeMb by remember { mutableStateOf(0.0) }
    var dbSizeMb by remember { mutableStateOf(0.0) }

    fun computeSizes() {
        scope.launch(Dispatchers.IO) {
            fun getFolderSize(file: File): Long {
                if (!file.exists()) return 0L
                var total = 0L
                if (file.isDirectory) {
                    file.listFiles()?.forEach { total += getFolderSize(it) }
                } else {
                    total += file.length()
                }
                return total
            }

            val imageCacheDir = File(context.cacheDir, "image_cache")
            val imgBytes = getFolderSize(imageCacheDir)
            val totalCacheBytes = getFolderSize(context.cacheDir)
            val metaBytes = (totalCacheBytes - imgBytes).coerceAtLeast(0L)

            val dbFile = context.getDatabasePath("lume_database")
            val dbBytes = if (dbFile.exists()) dbFile.length() else 0L

            withContext(Dispatchers.Main) {
                imageCacheSizeMb = imgBytes / (1024.0 * 1024.0)
                metadataCacheSizeMb = metaBytes / (1024.0 * 1024.0)
                dbSizeMb = dbBytes / (1024.0 * 1024.0)
            }
        }
    }

    LaunchedEffect(activePlaylist, isSyncing) {
        activePlaylist?.let { pl ->
            movieCount = db.vodDao().getMovieCount(pl.id)
            seriesCount = db.vodDao().getSeriesCount(pl.id)
        }
        computeSizes()
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
                TvSettingsValueRow(
                    "Library Data",
                    if (dbSizeMb > 0.0) String.format("%.1f MB", dbSizeMb)
                    else "${(channels.size * 0.005 + movieCount * 0.005 + 5.0).toInt()} MB"
                )
                TvSettingsValueRow("Image Cache", String.format("%.1f MB", imageCacheSizeMb))
                TvSettingsValueRow("Metadata Cache", String.format("%.1f MB", metadataCacheSizeMb))
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
                                computeSizes()
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
                    title = if (isClearing) "Clearing Image Cache..." else "Clear Image Cache",
                    value = String.format("%.1f MB", imageCacheSizeMb),
                    isDestructive = true,
                    onClick = {
                        scope.launch {
                            isClearing = true
                            withContext(Dispatchers.IO) {
                                try {
                                    coil.Coil.imageLoader(context).diskCache?.clear()
                                    coil.Coil.imageLoader(context).memoryCache?.clear()
                                    val imgDir = File(context.cacheDir, "image_cache")
                                    if (imgDir.exists()) imgDir.deleteRecursively()
                                } catch (_: Exception) {}
                            }
                            computeSizes()
                            isClearing = false
                        }
                    }
                )
                TvSettingsRow(
                    title = if (isClearing) "Clearing Metadata Cache..." else "Clear Metadata Cache",
                    value = String.format("%.1f MB", metadataCacheSizeMb),
                    isDestructive = true,
                    onClick = {
                        scope.launch {
                            isClearing = true
                            withContext(Dispatchers.IO) {
                                try {
                                    context.cacheDir.listFiles()?.forEach { file ->
                                        if (file.name != "image_cache") {
                                            file.deleteRecursively()
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                            computeSizes()
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
                    title = if (isClearing) "Clearing Watch History..." else "Clear Watch History",
                    isDestructive = true,
                    onClick = {
                        scope.launch {
                            isClearing = true
                            withContext(Dispatchers.IO) {
                                db.watchProgressDao().clearAllWatchProgress()
                            }
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
    val context = LocalContext.current
    val settings = remember { AppSettings.get() }
    val db = LumeApp.instance.database
    val activePlaylist by db.playlistDao().observeActivePlaylist().collectAsState(initial = null)
    var channelCount by remember { mutableStateOf(0) }
    var movieCount by remember { mutableStateOf(0) }
    var seriesCount by remember { mutableStateOf(0) }
    var detailedLogging by remember { mutableStateOf(settings.detailedLogging) }

    LaunchedEffect(activePlaylist) {
        activePlaylist?.let { pl ->
            channelCount = db.channelDao().getChannelCount(pl.id)
            movieCount = db.vodDao().getMovieCount(pl.id)
            seriesCount = db.vodDao().getSeriesCount(pl.id)
        }
    }

    val reportText = remember(channelCount, movieCount, seriesCount, detailedLogging, activePlaylist) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val display = windowManager?.defaultDisplay
        val resStr = if (display != null) {
            val mode = display.mode
            "${mode.physicalWidth}x${mode.physicalHeight} @ ${String.format("%.2f", display.refreshRate)}Hz"
        } else {
            "1920x1080 @ 60Hz"
        }

        val versionStr = try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${pInfo.versionName} (Build ${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pInfo.longVersionCode else @Suppress("DEPRECATION") pInfo.versionCode})"
        } catch (_: Exception) {
            "1.0.0 (Build 1)"
        }

        val playlistStr = if (activePlaylist != null) {
            "${activePlaylist!!.name} (${channelCount} channels, ${movieCount} movies, ${seriesCount} series)"
        } else {
            "None Connected"
        }

        """
        Lume Android TV $versionStr
        Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})
        OS: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
        Display: $resStr
        Primary Engine: ${settings.playerEngine.title}
        Engine Priority: ${settings.enginePriority}
        Audio Passthrough: ${if (settings.audioPassthrough) "Enabled" else "Disabled"}
        Active Playlist: $playlistStr
        EPG Sync: ${settings.syncInterval.title}
        Buffer Mode: ${settings.bufferSize.title}
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
    val rowInteractionSource = remember { MutableInteractionSource() }
    val isRowFocused by rowInteractionSource.collectIsFocusedAsState()

    val upInteractionSource = remember { MutableInteractionSource() }
    val isUpFocused by upInteractionSource.collectIsFocusedAsState()

    val downInteractionSource = remember { MutableInteractionSource() }
    val isDownFocused by downInteractionSource.collectIsFocusedAsState()

    val removeInteractionSource = remember { MutableInteractionSource() }
    val isRemoveFocused by removeInteractionSource.collectIsFocusedAsState()

    val isAnyFocused = isRowFocused || isUpFocused || isDownFocused || isRemoveFocused

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TVSettingsMetrics.rowCornerRadius))
            .background(if (isAnyFocused) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.04f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left item clickable box
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isRowFocused) Color.White.copy(alpha = 0.95f) else Color.Transparent)
                .clickable(
                    interactionSource = rowInteractionSource,
                    indication = null,
                    onClick = { onToggle?.invoke() }
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (onToggle != null) {
                    Icon(
                        imageVector = if (isEnabled) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isEnabled) {
                            if (isRowFocused) Color(0xFF1565C0) else Color(0xFF64B5F6)
                        } else {
                            if (isRowFocused) Color.Black.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.35f)
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = title,
                    color = if (isRowFocused) Color.Black else if (isEnabled) Color.White else Color.White.copy(alpha = 0.45f),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium
                )
                if (showsPremiumBadge) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isRowFocused) Color.Black.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "PRO",
                            color = if (isRowFocused) Color.Black else Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Action buttons reachable via DPAD_RIGHT
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 8.dp)
        ) {
            TvMiniIconButton(
                icon = Icons.Default.KeyboardArrowUp,
                enabled = !isFirst,
                interactionSource = upInteractionSource,
                onClick = onMoveUp
            )
            TvMiniIconButton(
                icon = Icons.Default.KeyboardArrowDown,
                enabled = !isLast,
                interactionSource = downInteractionSource,
                onClick = onMoveDown
            )
            if (onRemove != null) {
                TvMiniIconButton(
                    icon = Icons.Default.Remove,
                    enabled = true,
                    interactionSource = removeInteractionSource,
                    onClick = onRemove
                )
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
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: () -> Unit
) {
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
            .size(34.dp)
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
    leadingIcon: ImageVector? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingChevron: Boolean = false,
    trailingCheckmark: Boolean = false,
    showsPremiumBadge: Boolean = false,
    isDestructive: Boolean = false,
    focusRequester: FocusRequester? = null,
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

    val focusModifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(focusModifier)
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
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                if (leadingContent != null) {
                    leadingContent()
                } else if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = foregroundColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

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
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bgModifier = when {
        isFocused && isDestructive -> Modifier.background(Color(0xFFE53935))
        isFocused -> Modifier.background(Color.White.copy(alpha = 0.95f))
        isDestructive -> Modifier.background(Color(0xFFE53935).copy(alpha = 0.15f))
        prominent -> Modifier.background(Color.White.copy(alpha = 0.16f))
        else -> Modifier.background(Color.White.copy(alpha = 0.06f))
    }

    val foregroundColor = when {
        isFocused && isDestructive -> Color.White
        isFocused -> Color.Black
        isDestructive -> Color(0xFFFF5252)
        else -> Color.White
    }

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

@Composable
fun TvSettingsIconButton(
    icon: ImageVector,
    contentDescription: String? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(TVSettingsMetrics.rowCornerRadius))
            .background(if (isFocused) Color.White.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.08f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isFocused) Color.Black else Color.White,
            modifier = Modifier.size(20.dp)
        )
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
    focusRequester: FocusRequester? = null,
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
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
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
    focusRequester: FocusRequester? = null,
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
                .background(if (isFocused) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.08f))
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
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(Color.White),
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .onFocusChanged { isFocused = it.isFocused }
            )
        }
    }
}
