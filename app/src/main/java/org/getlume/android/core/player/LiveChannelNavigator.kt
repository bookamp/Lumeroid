package org.getlume.android.core.player

import org.getlume.android.core.data.entities.ChannelEntity

enum class LiveSurfMode(val displayName: String) {
    CHANNEL_UP_DOWN("Channel Up/Down"),
    LIST_ORDER("List Order")
}

/**
 * Resolves the channel to surf to when pressing Up/Down on TV remote.
 * Port of Lume Apple's LiveChannelNavigator.swift.
 *
 * Honors category sequence boundaries:
 * - If first in category, channel up is disabled (returns null).
 * - If last channel in category, channel down is disabled (returns null).
 */
object LiveChannelNavigator {

    fun nextChannel(
        current: ChannelEntity,
        lineup: List<ChannelEntity>,
        direction: RemoteDirectionGate.Direction,
        mode: LiveSurfMode = LiveSurfMode.LIST_ORDER
    ): ChannelEntity? {
        if (lineup.isEmpty()) return null

        val currentIndex = lineup.indexOfFirst { it.id == current.id || it.streamUrl == current.streamUrl }
        if (currentIndex == -1) return lineup.firstOrNull()

        return when (direction) {
            RemoteDirectionGate.Direction.UP -> {
                // If first in category then channel up should be disabled
                if (currentIndex <= 0) {
                    null
                } else {
                    lineup[currentIndex - 1]
                }
            }
            RemoteDirectionGate.Direction.DOWN -> {
                // If last channel in category then channel down should be disabled
                if (currentIndex >= lineup.size - 1) {
                    null
                } else {
                    lineup[currentIndex + 1]
                }
            }
            else -> null
        }
    }

    fun canChannelUp(current: ChannelEntity, lineup: List<ChannelEntity>): Boolean {
        if (lineup.isEmpty()) return false
        val index = lineup.indexOfFirst { it.id == current.id || it.streamUrl == current.streamUrl }
        return index > 0
    }

    fun canChannelDown(current: ChannelEntity, lineup: List<ChannelEntity>): Boolean {
        if (lineup.isEmpty()) return false
        val index = lineup.indexOfFirst { it.id == current.id || it.streamUrl == current.streamUrl }
        return index in 0 until (lineup.size - 1)
    }
}
