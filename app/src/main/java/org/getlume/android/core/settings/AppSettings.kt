package org.getlume.android.core.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.getlume.android.LumeApp

enum class PlayerEngineType(val title: String, val subtitle: String) {
    EXOPLAYER_HARDWARE("ExoPlayer (Hardware)", "Default low-latency hardware decoder with 4K HDR support"),
    EXOPLAYER_TUNNELED("ExoPlayer (Tunneled)", "Direct A/V hardware tunneling for ultra-low audio latency"),
    VLC_SOFTWARE("VLC Engine (Software)", "High compatibility software fallback for legacy MPEG-2/TS streams")
}

enum class StreamInfoLevel(val title: String) {
    OFF("Off"),
    MINIMAL("Minimal"),
    DETAILED("Detailed (Bitrate, Codec, FPS, Buffer)")
}

enum class SyncInterval(val title: String) {
    HOURS_6("Every 6 Hours"),
    HOURS_12("Every 12 Hours"),
    HOURS_24("Every 24 Hours"),
    MANUAL("Manual Only")
}

enum class LiveSurfMode(val title: String, val subtitle: String) {
    BY_NUMBER("Channel Number Order", "Up/Down advances by channel number (like a traditional cable remote)"),
    LIST_ORDER("List Order", "Up/Down moves in the order of the category channel list")
}

enum class LiveTVLayoutMode(val title: String) {
    GUIDE("EPG Timeline Guide"),
    LIST("Category Channel List")
}

enum class GuidePreviewMode(val title: String, val subtitle: String) {
    SMALL("Small Picture-in-Picture", "Plays focused channel muted in preview pane"),
    LARGE("Large Hero Preview", "Plays focused channel at top of guide"),
    INFO_ONLY("Info Only (No Video)", "Shows show metadata without opening video stream"),
    OFF("Off", "Full height guide with maximum channels visible")
}

enum class BufferSizeType(val title: String, val subtitle: String) {
    LOW_LATENCY("Low Latency (2s)", "Fastest channel zapping, requires stable network"),
    DEFAULT("Default (10s)", "Balanced buffer for reliable IPTV playback"),
    LARGE("Large (30s)", "Maximum protection against stream buffering and jitter")
}

enum class ExternalPlayerType(val title: String) {
    OFF("Off (Built-in ExoPlayer)"),
    VLC("VLC for Android"),
    JUST_PLAYER("Just Player"),
    KODI("Kodi")
}

enum class ExternalPlayerScope(val title: String) {
    ALL("All Streams"),
    LIVE_ONLY("Live TV Only"),
    VOD_ONLY("Movies & Series (VOD) Only")
}

class AppSettings private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("lume_settings", Context.MODE_PRIVATE)

    // Player Settings
    var playerEngine: PlayerEngineType
        get() = try {
            PlayerEngineType.valueOf(prefs.getString("player_engine", PlayerEngineType.EXOPLAYER_HARDWARE.name)!!)
        } catch (e: Exception) { PlayerEngineType.EXOPLAYER_HARDWARE }
        set(value) = prefs.edit().putString("player_engine", value.name).apply()

    var audioPassthrough: Boolean
        get() = prefs.getBoolean("audio_passthrough", true)
        set(value) = prefs.edit().putBoolean("audio_passthrough", value).apply()

    var tunneledVideoPlayback: Boolean
        get() = prefs.getBoolean("tunneled_video_playback", true)
        set(value) = prefs.edit().putBoolean("tunneled_video_playback", value).apply()

    var autoPlayNext: Boolean
        get() = prefs.getBoolean("auto_play_next", true)
        set(value) = prefs.edit().putBoolean("auto_play_next", value).apply()

    var showSkipIntro: Boolean
        get() = prefs.getBoolean("show_skip_intro", true)
        set(value) = prefs.edit().putBoolean("show_skip_intro", value).apply()

    var showNextEpisodeButton: Boolean
        get() = prefs.getBoolean("show_next_episode_btn", true)
        set(value) = prefs.edit().putBoolean("show_next_episode_btn", value).apply()

    var streamInfoLevel: StreamInfoLevel
        get() = try {
            StreamInfoLevel.valueOf(prefs.getString("stream_info_level", StreamInfoLevel.MINIMAL.name)!!)
        } catch (e: Exception) { StreamInfoLevel.MINIMAL }
        set(value) = prefs.edit().putString("stream_info_level", value.name).apply()

    var liveSurfMode: LiveSurfMode
        get() = try {
            LiveSurfMode.valueOf(prefs.getString("live_surf_mode", LiveSurfMode.BY_NUMBER.name)!!)
        } catch (e: Exception) { LiveSurfMode.BY_NUMBER }
        set(value) = prefs.edit().putString("live_surf_mode", value.name).apply()

    var remoteSwipeGestures: Boolean
        get() = prefs.getBoolean("remote_swipes", true)
        set(value) = prefs.edit().putBoolean("remote_swipes", value).apply()

    var bufferSize: BufferSizeType
        get() = try {
            BufferSizeType.valueOf(prefs.getString("buffer_size", BufferSizeType.DEFAULT.name)!!)
        } catch (e: Exception) { BufferSizeType.DEFAULT }
        set(value) = prefs.edit().putString("buffer_size", value.name).apply()

    var externalPlayer: ExternalPlayerType
        get() = try {
            ExternalPlayerType.valueOf(prefs.getString("ext_player", ExternalPlayerType.OFF.name)!!)
        } catch (e: Exception) { ExternalPlayerType.OFF }
        set(value) = prefs.edit().putString("ext_player", value.name).apply()

    var externalPlayerScope: ExternalPlayerScope
        get() = try {
            ExternalPlayerScope.valueOf(prefs.getString("ext_player_scope", ExternalPlayerScope.ALL.name)!!)
        } catch (e: Exception) { ExternalPlayerScope.ALL }
        set(value) = prefs.edit().putString("ext_player_scope", value.name).apply()

    var preferredAudioLanguage: String
        get() = prefs.getString("pref_audio_lang", "English") ?: "English"
        set(value) = prefs.edit().putString("pref_audio_lang", value).apply()

    var preferredSubtitleLanguage: String
        get() = prefs.getString("pref_sub_lang", "English") ?: "English"
        set(value) = prefs.edit().putString("pref_sub_lang", value).apply()

    // Subtitle Style Settings
    var subtitleSizeSp: Int
        get() = prefs.getInt("sub_size", 24)
        set(value) = prefs.edit().putInt("sub_size", value).apply()

    var subtitleColorHex: String
        get() = prefs.getString("sub_color", "#FFFFFF") ?: "#FFFFFF"
        set(value) = prefs.edit().putString("sub_color", value).apply()

    var subtitleBackground: Boolean
        get() = prefs.getBoolean("sub_bg", true)
        set(value) = prefs.edit().putBoolean("sub_bg", value).apply()

    var subtitleBold: Boolean
        get() = prefs.getBoolean("sub_bold", true)
        set(value) = prefs.edit().putBoolean("sub_bold", value).apply()

    var subtitleFont: String
        get() = prefs.getString("sub_font", "System") ?: "System"
        set(value) = prefs.edit().putString("sub_font", value).apply()

    var subtitlePosition: String
        get() = prefs.getString("sub_position", "Bottom") ?: "Bottom"
        set(value) = prefs.edit().putString("sub_position", value).apply()

    // EPG & Sync Settings
    var epgTimeShiftHours: Int
        get() = prefs.getInt("epg_time_shift", 0)
        set(value) = prefs.edit().putInt("epg_time_shift", value).apply()

    var syncInterval: SyncInterval
        get() = try {
            SyncInterval.valueOf(prefs.getString("sync_interval", SyncInterval.HOURS_12.name)!!)
        } catch (e: Exception) { SyncInterval.HOURS_12 }
        set(value) = prefs.edit().putString("sync_interval", value.name).apply()

    var customEpgUrl: String
        get() = prefs.getString("custom_epg_url", "") ?: ""
        set(value) = prefs.edit().putString("custom_epg_url", value).apply()

    // Library Settings
    var libraryShowMovies: Boolean
        get() = prefs.getBoolean("lib_show_movies", true)
        set(value) = prefs.edit().putBoolean("lib_show_movies", value).apply()

    var libraryShowSeries: Boolean
        get() = prefs.getBoolean("lib_show_series", true)
        set(value) = prefs.edit().putBoolean("lib_show_series", value).apply()

    var libraryShowLiveTV: Boolean
        get() = prefs.getBoolean("lib_show_livetv", true)
        set(value) = prefs.edit().putBoolean("lib_show_livetv", value).apply()

    var libraryShowSports: Boolean
        get() = prefs.getBoolean("lib_show_sports", true)
        set(value) = prefs.edit().putBoolean("lib_show_sports", value).apply()

    var searchAllPlaylists: Boolean
        get() = prefs.getBoolean("search_all_playlists", false)
        set(value) = prefs.edit().putBoolean("search_all_playlists", value).apply()

    // Home Layout Settings
    var homeShowContinueWatching: Boolean
        get() = prefs.getBoolean("home_show_cw", true)
        set(value) = prefs.edit().putBoolean("home_show_cw", value).apply()

    var homeShowRecentlyAdded: Boolean
        get() = prefs.getBoolean("home_show_ra", true)
        set(value) = prefs.edit().putBoolean("home_show_ra", value).apply()

    var homeShowForYou: Boolean
        get() = prefs.getBoolean("home_show_foryou", true)
        set(value) = prefs.edit().putBoolean("home_show_foryou", value).apply()

    var homeShowLiveTV: Boolean
        get() = prefs.getBoolean("home_show_livetv", true)
        set(value) = prefs.edit().putBoolean("home_show_livetv", value).apply()

    var homeShowSports: Boolean
        get() = prefs.getBoolean("home_show_sports", true)
        set(value) = prefs.edit().putBoolean("home_show_sports", value).apply()

    var homeRecentlyWatchedIncludesLiveTV: Boolean
        get() = prefs.getBoolean("home_rw_livetv", false)
        set(value) = prefs.edit().putBoolean("home_rw_livetv", value).apply()

    // Live TV Settings
    var liveTVLayoutMode: LiveTVLayoutMode
        get() = try {
            LiveTVLayoutMode.valueOf(prefs.getString("livetv_layout_mode", LiveTVLayoutMode.GUIDE.name)!!)
        } catch (e: Exception) { LiveTVLayoutMode.GUIDE }
        set(value) = prefs.edit().putString("livetv_layout_mode", value.name).apply()

    var guidePreviewMode: GuidePreviewMode
        get() = try {
            GuidePreviewMode.valueOf(prefs.getString("guide_preview_mode", GuidePreviewMode.SMALL.name)!!)
        } catch (e: Exception) { GuidePreviewMode.SMALL }
        set(value) = prefs.edit().putString("guide_preview_mode", value.name).apply()

    var showsFavoritesInLiveTV: Boolean
        get() = prefs.getBoolean("livetv_shows_favs", true)
        set(value) = prefs.edit().putBoolean("livetv_shows_favs", value).apply()

    var showsRecentlyWatchedInLiveTV: Boolean
        get() = prefs.getBoolean("livetv_shows_recent", true)
        set(value) = prefs.edit().putBoolean("livetv_shows_recent", value).apply()

    var showsRecordingsInLiveTVRail: Boolean
        get() = prefs.getBoolean("livetv_shows_recordings", true)
        set(value) = prefs.edit().putBoolean("livetv_shows_recordings", value).apply()

    // Profiles Settings
    var askProfileOnStartup: Boolean
        get() = prefs.getBoolean("ask_profile_startup", false)
        set(value) = prefs.edit().putBoolean("ask_profile_startup", value).apply()

    var activeProfileName: String
        get() = prefs.getString("active_profile_name", "Main Profile") ?: "Main Profile"
        set(value) = prefs.edit().putString("active_profile_name", value).apply()

    // Trakt Integration
    var isTraktConnected: Boolean
        get() = prefs.getBoolean("trakt_connected", false)
        set(value) = prefs.edit().putBoolean("trakt_connected", value).apply()

    var traktUsername: String
        get() = prefs.getString("trakt_username", "") ?: ""
        set(value) = prefs.edit().putString("trakt_username", value).apply()

    var traktScrobble: Boolean
        get() = prefs.getBoolean("trakt_scrobble", true)
        set(value) = prefs.edit().putBoolean("trakt_scrobble", value).apply()

    // Simkl Integration
    var isSimklConnected: Boolean
        get() = prefs.getBoolean("simkl_connected", false)
        set(value) = prefs.edit().putBoolean("simkl_connected", value).apply()

    var simklUsername: String
        get() = prefs.getString("simkl_username", "") ?: ""
        set(value) = prefs.edit().putString("simkl_username", value).apply()

    var simklScrobble: Boolean
        get() = prefs.getBoolean("simkl_scrobble", true)
        set(value) = prefs.edit().putBoolean("simkl_scrobble", value).apply()

    // OpenSubtitles Integration
    var isOpenSubtitlesConnected: Boolean
        get() = prefs.getBoolean("opensub_connected", false)
        set(value) = prefs.edit().putBoolean("opensub_connected", value).apply()

    var openSubtitlesUsername: String
        get() = prefs.getString("opensub_username", "") ?: ""
        set(value) = prefs.edit().putString("opensub_username", value).apply()

    // Recording Server (DVR)
    var isRecordingServerPaired: Boolean
        get() = prefs.getBoolean("recorder_paired", false)
        set(value) = prefs.edit().putBoolean("recorder_paired", value).apply()

    var recordingServerUrl: String
        get() = prefs.getString("recorder_url", "http://192.168.1.185:8090") ?: "http://192.168.1.185:8090"
        set(value) = prefs.edit().putString("recorder_url", value).apply()

    // Parental Controls
    var parentalControlsEnabled: Boolean
        get() = prefs.getBoolean("parental_enabled", false)
        set(value) = prefs.edit().putBoolean("parental_enabled", value).apply()

    var parentalPin: String
        get() = prefs.getString("parental_pin", "0000") ?: "0000"
        set(value) = prefs.edit().putString("parental_pin", value).apply()

    // Sports Hub
    var sportsHubEnabled: Boolean
        get() = prefs.getBoolean("sports_enabled", true)
        set(value) = prefs.edit().putBoolean("sports_enabled", value).apply()

    var hideScores: Boolean
        get() = prefs.getBoolean("sports_hide_scores", false)
        set(value) = prefs.edit().putBoolean("sports_hide_scores", value).apply()

    var isPremium: Boolean
        get() = prefs.getBoolean("is_premium", true)
        set(value) = prefs.edit().putBoolean("is_premium", value).apply()

    var cloudSyncEnabled: Boolean
        get() = prefs.getBoolean("cloud_sync_enabled", true)
        set(value) = prefs.edit().putBoolean("cloud_sync_enabled", value).apply()

    var cloudSyncMethod: String
        get() = prefs.getString("cloud_sync_method", "Local Network (LAN Sync)") ?: "Local Network (LAN Sync)"
        set(value) = prefs.edit().putString("cloud_sync_method", value).apply()

    var chooseSourceBeforePlaying: Boolean
        get() = prefs.getBoolean("choose_source_before_playing", false)
        set(value) = prefs.edit().putBoolean("choose_source_before_playing", value).apply()

    var showLiveQualitySwitch: Boolean
        get() = prefs.getBoolean("show_live_quality_switch", true)
        set(value) = prefs.edit().putBoolean("show_live_quality_switch", value).apply()

    var androidTvChannelsEnabled: Boolean
        get() = prefs.getBoolean("android_tv_channels_enabled", true)
        set(value) = prefs.edit().putBoolean("android_tv_channels_enabled", value).apply()

    var homeSectionOrder: String
        get() = prefs.getString("home_section_order", "recentlyWatched,favorites,forYou,trendingMovies,trendingSeries,traktWatchlist,simklWatchlist,sports")
            ?: "recentlyWatched,favorites,forYou,trendingMovies,trendingSeries,traktWatchlist,simklWatchlist,sports"
        set(value) = prefs.edit().putString("home_section_order", value).apply()

    var enginePriority: String
        get() = prefs.getString("engine_priority", "exoplayer_hw,exoplayer_tunneled,vlc_sw") ?: "exoplayer_hw,exoplayer_tunneled,vlc_sw"
        set(value) = prefs.edit().putString("engine_priority", value).apply()

    var favoriteLeagues: Set<String>
        get() = prefs.getStringSet("fav_leagues", setOf("Premier League", "NBA", "Champions League", "NFL")) ?: emptySet()
        set(value) = prefs.edit().putStringSet("fav_leagues", value).apply()

    var detailedLogging: Boolean
        get() = prefs.getBoolean("detailed_logging", false)
        set(value) = prefs.edit().putBoolean("detailed_logging", value).apply()

    var preferredAudioLanguages: String
        get() = prefs.getString("preferred_audio_languages", "en,es,fr,de") ?: "en,es,fr,de"
        set(value) = prefs.edit().putString("preferred_audio_languages", value).apply()

    var exoPlayerLiveBufferMs: Int
        get() = prefs.getInt("exoplayer_live_buffer", 5000)
        set(value) = prefs.edit().putInt("exoplayer_live_buffer", value).apply()

    var exoPlayerVodBufferMs: Int
        get() = prefs.getInt("exoplayer_vod_buffer", 15000)
        set(value) = prefs.edit().putInt("exoplayer_vod_buffer", value).apply()

    var exoPlayerHttpReconnect: Boolean
        get() = prefs.getBoolean("exoplayer_http_reconnect", true)
        set(value) = prefs.edit().putBoolean("exoplayer_http_reconnect", value).apply()

    var vlcHardwareDecoding: String
        get() = prefs.getString("vlc_hw_decoding", "Auto") ?: "Auto"
        set(value) = prefs.edit().putString("vlc_hw_decoding", value).apply()

    var vlcDeinterlaceMode: String
        get() = prefs.getString("vlc_deinterlace_mode", "Auto") ?: "Auto"
        set(value) = prefs.edit().putString("vlc_deinterlace_mode", value).apply()

    var vlcNetworkCachingMs: Int
        get() = prefs.getInt("vlc_net_caching", 1500)
        set(value) = prefs.edit().putInt("vlc_net_caching", value).apply()

    var vlcSkipFrames: Boolean
        get() = prefs.getBoolean("vlc_skip_frames", false)
        set(value) = prefs.edit().putBoolean("vlc_skip_frames", value).apply()

    var vlcDropLateFrames: Boolean
        get() = prefs.getBoolean("vlc_drop_late", true)
        set(value) = prefs.edit().putBoolean("vlc_drop_late", value).apply()

    var vlcAudioOutput: String
        get() = prefs.getString("vlc_audio_out", "AudioTrack") ?: "AudioTrack"
        set(value) = prefs.edit().putString("vlc_audio_out", value).apply()

    companion object {
        @Volatile
        private var INSTANCE: AppSettings? = null

        fun get(): AppSettings {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppSettings(LumeApp.instance.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
