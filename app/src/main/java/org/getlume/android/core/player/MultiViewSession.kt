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
    SPOTLIGHT(4) // 1 stage tile + 3 rail tiles
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

    private val _slots = MutableStateFlow<List<MultiViewSlot>>(
        (0 until 4).map { MultiViewSlot(index = it, isAudioActive = it == 0, isFocused = it == 0) }
    )
    val slots: StateFlow<List<MultiViewSlot>> = _slots.asStateFlow()

    private val players = mutableMapOf<Int, ExoPlayer>()

    fun getOrCreatePlayer(slotIndex: Int): ExoPlayer {
        return players.getOrPut(slotIndex) {
            ExoPlayer.Builder(context).build().apply {
                playWhenReady = true
                volume = if (slotIndex == 0) 1.0f else 0.0f
            }
        }
    }

    fun assignChannel(slotIndex: Int, channel: ChannelEntity) {
        val player = getOrCreatePlayer(slotIndex)
        player.setMediaItem(MediaItem.fromUri(channel.streamUrl))
        player.prepare()

        _slots.value = _slots.value.map { slot ->
            if (slot.index == slotIndex) slot.copy(channel = channel) else slot
        }
    }

    fun setActiveAudioSlot(activeSlotIndex: Int) {
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
    }

    fun release() {
        players.values.forEach { it.release() }
        players.clear()
    }
}
