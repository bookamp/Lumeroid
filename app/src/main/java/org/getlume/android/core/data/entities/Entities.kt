package org.getlume.android.core.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "playlists")
@Serializable
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String, // "xtream", "m3u", "stalker"
    val serverUrl: String,
    val username: String = "",
    val password: String = "",
    val epgUrl: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "categories",
    indices = [Index(value = ["playlistId", "type"])]
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val playlistId: String,
    val type: String, // "live", "movie", "series"
    val name: String,
    val sortOrder: Int = 0,
    val isHidden: Boolean = false
)

@Entity(
    tableName = "channels",
    indices = [
        Index(value = ["playlistId", "categoryId"]),
        Index(value = ["tvgId"])
    ]
)
data class ChannelEntity(
    @PrimaryKey val id: String,
    val playlistId: String,
    val categoryId: String,
    val streamId: String,
    val name: String,
    val logoUrl: String = "",
    val streamUrl: String,
    val tvgId: String = "",
    val channelNumber: Int = 0,
    val catchupType: String = "",
    val catchupDays: Int = 0,
    val isFavorite: Boolean = false,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "epg_listings",
    indices = [
        Index(value = ["channelTvgId", "startTime", "endTime"]),
        Index(value = ["startTime"])
    ]
)
data class EpgListingEntity(
    @PrimaryKey val id: String,
    val channelTvgId: String,
    val title: String,
    val description: String = "",
    val startTime: Long, // Epoch ms
    val endTime: Long,
    val posterUrl: String = "",
    val catchupSource: String = ""
)

@Entity(
    tableName = "movies",
    indices = [Index(value = ["playlistId", "categoryId"])]
)
data class MovieEntity(
    @PrimaryKey val id: String,
    val playlistId: String,
    val categoryId: String,
    val streamId: String,
    val name: String,
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val plot: String = "",
    val cast: String = "",
    val director: String = "",
    val releaseDate: String = "",
    val durationMinutes: Int = 0,
    val rating: Float = 0.0f,
    val streamUrl: String = "",
    val extension: String = "mp4",
    val isFavorite: Boolean = false
)

@Entity(
    tableName = "series",
    indices = [Index(value = ["playlistId", "categoryId"])]
)
data class SeriesEntity(
    @PrimaryKey val id: String,
    val playlistId: String,
    val categoryId: String,
    val seriesId: String,
    val name: String,
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val plot: String = "",
    val cast: String = "",
    val releaseDate: String = "",
    val rating: Float = 0.0f,
    val isFavorite: Boolean = false
)

@Entity(
    tableName = "episodes",
    indices = [Index(value = ["seriesId", "seasonNumber", "episodeNumber"])]
)
data class EpisodeEntity(
    @PrimaryKey val id: String,
    val seriesId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val plot: String = "",
    val thumbnail: String = "",
    val streamId: String,
    val streamUrl: String,
    val durationMinutes: Int = 0
)

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val avatarColor: Int,
    val isChild: Boolean = false,
    val pinHash: String = "",
    val isCurrent: Boolean = false
)

@Entity(
    tableName = "watch_progress",
    indices = [Index(value = ["profileId", "contentId"])]
)
data class WatchProgressEntity(
    @PrimaryKey val contentId: String,
    val profileId: String,
    val title: String,
    val posterUrl: String = "",
    val contentType: String, // "movie", "episode", "recording"
    val positionMs: Long,
    val durationMs: Long,
    val lastWatchedAt: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = false
)

@Entity(tableName = "sports_follows")
data class SportsFollowEntity(
    @PrimaryKey val id: String, // e.g. "team:arsenal" or "league:epl"
    val profileId: String,
    val type: String, // "team" or "league"
    val name: String,
    val logoUrl: String = "",
    val primaryColorHex: String = "#0A84FF"
)

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey val id: String,
    val serverId: String,
    val channelName: String,
    val programmeTitle: String,
    val status: String, // "scheduled", "recording", "completed", "failed"
    val startTime: Long,
    val durationMinutes: Int,
    val playbackUrl: String = "",
    val sizeBytes: Long = 0L
)
