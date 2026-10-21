package org.getlume.android.core.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.getlume.android.core.data.entities.ChannelEntity
import org.getlume.android.core.data.entities.MovieEntity

class LumePlayerManager(private val context: Context) {

    private val trackSelector = DefaultTrackSelector(context)

    val player: ExoPlayer by lazy {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                1000,  // minBufferMs (fast start)
                5000,  // maxBufferMs
                500,   // bufferForPlaybackMs
                1000   // bufferForPlaybackAfterRebufferMs
            )
            .build()

        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build().apply {
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        _isPlaying.value = playbackState == Player.STATE_READY && playWhenReady
                    }

                    override fun onTracksChanged(tracks: Tracks) {
                        extractTracks(tracks)
                    }
                })
            }
    }

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<String>>(emptyList())
    val audioTracks: StateFlow<List<String>> = _audioTracks.asStateFlow()

    private val _subtitleTracks = MutableStateFlow<List<String>>(emptyList())
    val subtitleTracks: StateFlow<List<String>> = _subtitleTracks.asStateFlow()

    fun playMedia(media: PlayableMedia, startPositionMs: Long = 0L) {
        val mediaItem = if (media.isLive) {
            MediaItem.Builder()
                .setUri(media.streamUrl)
                .setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder()
                        .setMaxPlaybackSpeed(1.02f)
                        .setMinPlaybackSpeed(0.98f)
                        .build()
                )
                .build()
        } else {
            MediaItem.fromUri(media.streamUrl)
        }

        player.setMediaItem(mediaItem, startPositionMs)
        player.prepare()
        player.play()
    }

    fun playChannel(channel: ChannelEntity) {
        playMedia(channel.toPlayableMedia())
    }

    fun playMovie(movie: MovieEntity, startPositionMs: Long = 0L) {
        playMedia(movie.toPlayableMedia(), startPositionMs)
    }

    fun seekRelative(deltaMs: Long) {
        val current = player.currentPosition
        val duration = player.duration
        val target = if (duration > 0) {
            (current + deltaMs).coerceIn(0L, duration)
        } else {
            (current + deltaMs).coerceAtLeast(0L)
        }
        player.seekTo(target)
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun playCatchup(channel: ChannelEntity, startEpochMs: Long, durationSeconds: Long) {
        val catchupUrl = resolveCatchupUrl(channel, startEpochMs, durationSeconds)
        player.setMediaItem(MediaItem.fromUri(catchupUrl))
        player.prepare()
        player.play()
    }

    private fun resolveCatchupUrl(channel: ChannelEntity, startEpochMs: Long, durationSeconds: Long): String {
        val startSec = startEpochMs / 1000
        return when (channel.catchupType) {
            "flussonic" -> {
                channel.streamUrl.replace(".m3u8", "-timeshift-$startSec-$durationSeconds.m3u8")
            }
            "xc" -> {
                // Xtream catchup stream format: /streaming/timeshift.php?username=...&password=...&stream=...&start=...&duration=...
                channel.streamUrl.replace("/live/", "/timeshift/")
            }
            else -> channel.streamUrl
        }
    }

    private fun extractTracks(tracks: Tracks) {
        val audioList = mutableListOf<String>()
        val subList = mutableListOf<String>()

        tracks.groups.forEach { group ->
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val label = format.label ?: format.language ?: "Track ${i + 1}"
                when (group.type) {
                    C.TRACK_TYPE_AUDIO -> audioList.add(label)
                    C.TRACK_TYPE_TEXT -> subList.add(label)
                }
            }
        }
        _audioTracks.value = audioList
        _subtitleTracks.value = subList
    }

    fun selectAudioTrack(trackIndex: Int) {
        // override track
    }

    fun selectSubtitleTrack(trackIndex: Int) {
        // override track
    }

    fun release() {
        player.release()
    }
}
