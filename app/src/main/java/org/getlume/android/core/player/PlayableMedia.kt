package org.getlume.android.core.player

import org.getlume.android.core.data.entities.ChannelEntity
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
    val startPositionMs: Long = 0L
)

fun ChannelEntity.toPlayableMedia(): PlayableMedia {
    return PlayableMedia(
        id = id,
        title = name,
        subtitle = "Channel $channelNumber",
        streamUrl = streamUrl,
        posterUrl = logoUrl,
        isLive = true,
        channelNumber = channelNumber
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
        durationMs = (durationMinutes * 60 * 1000).toLong()
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
        durationMs = (durationMinutes * 60 * 1000).toLong()
    )
}

object ActivePlaybackStore {
    var currentMedia: PlayableMedia? = null
}
