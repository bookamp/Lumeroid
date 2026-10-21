package org.getlume.android.core.player

import org.getlume.android.core.data.entities.ChannelEntity

enum class LiveSurfMode(val displayName: String) {
    CHANNEL_UP_DOWN("Channel Up/Down"),
    LIST_ORDER("List Order")
}

/**
 * Resolves the channel to surf to when pressing Up/Down on TV remote.
 * Port of Lume Apple's LiveChannelNavigator.swift.
 */
object LiveChannelNavigator {

    fun nextChannel(
        current: ChannelEntity,
        lineup: List<ChannelEntity>,
        direction: RemoteDirectionGate.Direction,
        mode: LiveSurfMode = LiveSurfMode.CHANNEL_UP_DOWN
    ): ChannelEntity {
        if (lineup.isEmpty()) return current

        val currentIndex = lineup.indexOfFirst { it.id == current.id }
        if (currentIndex == -1) return lineup.first()

        val delta = when (mode) {
            LiveSurfMode.CHANNEL_UP_DOWN -> {
                // Rocker: Up moves to next channel (higher number), Down moves to previous
                if (direction == RemoteDirectionGate.Direction.UP) 1 else -1
            }
            LiveSurfMode.LIST_ORDER -> {
                // List order: Up moves up the list (-1), Down moves down the list (+1)
                if (direction == RemoteDirectionGate.Direction.UP) -1 else 1
            }
            else -> 1
        }

        val targetIndex = (currentIndex + delta + lineup.size) % lineup.size
        return lineup[targetIndex]
    }
}
