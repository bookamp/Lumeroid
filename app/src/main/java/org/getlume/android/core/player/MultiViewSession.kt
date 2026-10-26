package org.getlume.android.core.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.getlume.android.core.data.entities.ChannelEntity

enum class MultiViewLayout(val slotCount: Int) {
    TWO(2),
    THREE(3),
    FOUR(4),
    SPOTLIGHT(4), // 1 stage tile + 3 rail tiles
    PIP(2)        // Fullscreen main video + floating top-right overlay
}

data class MultiViewSlot(
    val index: Int,
    val channel: ChannelEntity? = null,
    val isAudioActive: Boolean = false,
    val isFocused: Boolean = false
)

class MultiViewSession(private val context: Context) {

    private val _layout = MutableStateFlow(MultiViewLayout.FOUR)
    val layout: StateFlow<MultiViewLayout> = _layout.asStateFlow()

    private val _activeAudioSlotIndex = MutableStateFlow(0)
    val activeAudioSlotIndex: StateFlow<Int> = _activeAudioSlotIndex.asStateFlow()

    private val _slots = MutableStateFlow<List<MultiViewSlot>>(
        (0 until 4).map { MultiViewSlot(index = it, isAudioActive = it == 0, isFocused = it == 0) }
    )
    val slots: StateFlow<List<MultiViewSlot>> = _slots.asStateFlow()

    private val players = mutableMapOf<Int, ExoPlayer>()

    fun getOrCreatePlayer(slotIndex: Int): ExoPlayer {
        return players.getOrPut(slotIndex) {
            ExoPlayer.Builder(context).build().apply {
                playWhenReady = true
                volume = if (slotIndex == _activeAudioSlotIndex.value) 1.0f else 0.0f
            }
        }
    }

    fun assignChannel(slotIndex: Int, channel: ChannelEntity?) {
        if (channel == null) {
            players[slotIndex]?.stop()
            players[slotIndex]?.clearMediaItems()
            _slots.value = _slots.value.map { slot ->
                if (slot.index == slotIndex) slot.copy(channel = null) else slot
            }
            return
        }
        val player = getOrCreatePlayer(slotIndex)
        player.setMediaItem(MediaItem.fromUri(channel.streamUrl))
        player.prepare()
        player.play()
        // Ensure volume strictly matches whether this slot is the active audio slot
        players.forEach { (idx, p) ->
            p.volume = if (idx == _activeAudioSlotIndex.value) 1.0f else 0.0f
        }

        _slots.value = _slots.value.map { slot ->
            if (slot.index == slotIndex) slot.copy(channel = channel) else slot
        }
    }

    fun promoteToStage(slotIndex: Int) {
        if (slotIndex == 0) return
        val current0 = _slots.value.find { it.index == 0 }?.channel
        val currentTarget = _slots.value.find { it.index == slotIndex }?.channel
        assignChannel(0, currentTarget)
        assignChannel(slotIndex, current0)
        setActiveAudioSlot(0)
    }

    fun setActiveAudioSlot(activeSlotIndex: Int) {
        _activeAudioSlotIndex.value = activeSlotIndex
        players.forEach { (index, player) ->
            player.volume = if (index == activeSlotIndex) 1.0f else 0.0f
        }
        _slots.value = _slots.value.map { slot ->
            slot.copy(
                isAudioActive = slot.index == activeSlotIndex,
                isFocused = slot.index == activeSlotIndex
            )
        }
    }

    fun setLayout(newLayout: MultiViewLayout) {
        _layout.value = newLayout
        if (_activeAudioSlotIndex.value >= newLayout.slotCount) {
            setActiveAudioSlot(0)
        }
        players.forEach { (index, player) ->
            if (index >= newLayout.slotCount) {
                player.pause()
            } else if (_slots.value.getOrNull(index)?.channel != null) {
                player.play()
            }
        }
    }

    fun release() {
        players.values.forEach { it.release() }
        players.clear()
    }
}
