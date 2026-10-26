package org.getlume.android.core.player

import org.getlume.android.core.data.entities.ChannelEntity
import org.getlume.android.core.data.entities.EpgListingEntity
import org.getlume.android.core.data.entities.EpisodeEntity
import org.getlume.android.core.data.entities.MovieEntity

/**
 * Self-contained, value-type description of playable media matching Apple TV Lume's PlayableMedia.swift.
 */
data class PlayableMedia(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val streamUrl: String,
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val isLive: Boolean = true,
    val channelNumber: Int = 0,
    val durationMs: Long = 0L,
    val startPositionMs: Long = 0L,
    val contentType: String = "channel",
    val seriesId: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val channelLineup: List<ChannelEntity> = emptyList()
)

fun ChannelEntity.toPlayableMedia(
    lineup: List<ChannelEntity> = emptyList(),
    categoryName: String = ""
): PlayableMedia {
    return PlayableMedia(
        id = id,
        title = name,
        subtitle = if (categoryName.isNotBlank()) categoryName else "Channel $channelNumber",
        streamUrl = streamUrl,
        posterUrl = logoUrl,
        isLive = true,
        channelNumber = channelNumber,
        contentType = "channel",
        categoryId = categoryId,
        categoryName = categoryName,
        channelLineup = lineup
    )
}

fun ChannelEntity.toCatchupPlayableMedia(
    program: EpgListingEntity,
    lineup: List<ChannelEntity> = emptyList(),
    categoryName: String = ""
): PlayableMedia {
    val durationSec = ((program.endTime - program.startTime) / 1000L).coerceAtLeast(60L)
    val startSec = program.startTime / 1000L
    val catchupUrl = when (catchupType) {
        "flussonic" -> streamUrl.replace(".m3u8", "-timeshift-$startSec-$durationSec.m3u8")
        "xc" -> streamUrl.replace("/live/", "/timeshift/")
        else -> streamUrl
    }
    return PlayableMedia(
        id = "${id}-catchup-${program.id}",
        title = program.title,
        subtitle = "$name • Catchup Replay",
        streamUrl = catchupUrl,
        posterUrl = logoUrl,
        isLive = false,
        channelNumber = channelNumber,
        durationMs = (program.endTime - program.startTime).coerceAtLeast(60000L),
        contentType = "channel",
        categoryId = categoryId,
        categoryName = categoryName,
        channelLineup = lineup
    )
}

fun MovieEntity.toPlayableMedia(): PlayableMedia {
    return PlayableMedia(
        id = id,
        title = name,
        subtitle = if (releaseDate.isNotBlank()) releaseDate else if (durationMinutes > 0) "${durationMinutes}m" else "Movie",
        streamUrl = streamUrl,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        isLive = false,
        durationMs = (durationMinutes * 60 * 1000).toLong(),
        contentType = "movie"
    )
}

fun EpisodeEntity.toPlayableMedia(seriesTitle: String = ""): PlayableMedia {
    val sub = if (seriesTitle.isNotBlank()) "$seriesTitle • S${seasonNumber}E${episodeNumber}" else "S${seasonNumber}E${episodeNumber}"
    return PlayableMedia(
        id = id,
        title = title.ifBlank { "Episode $episodeNumber" },
        subtitle = sub,
        streamUrl = streamUrl,
        posterUrl = thumbnail,
        isLive = false,
        durationMs = (durationMinutes * 60 * 1000).toLong(),
        contentType = "episode",
        seriesId = seriesId
    )
}

object ActivePlaybackStore {
    var currentMedia: PlayableMedia? = null
}

object ActiveModalStore {
    val isModalOpen = androidx.compose.runtime.mutableStateOf(false)
}
