package org.getlume.android

import org.getlume.android.core.data.entities.ChannelEntity
import org.getlume.android.core.player.LiveChannelNavigator
import org.getlume.android.core.player.LiveSurfMode
import org.getlume.android.core.player.RemoteDirectionGate
import org.junit.Assert.*
import org.junit.Test

class LiveChannelNavigatorTest {

    private fun createChannel(id: String, number: Int, name: String): ChannelEntity {
        return ChannelEntity(
            id = id,
            playlistId = "p1",
            categoryId = "c1",
            streamId = id,
            name = name,
            streamUrl = "http://example.com/$id.m3u8",
            channelNumber = number,
            sortOrder = number
        )
    }

    @Test
    fun testRockerModeNextAndPrevious() {
        val ch1 = createChannel("1", 1, "Channel 1")
        val ch2 = createChannel("2", 2, "Channel 2")
        val ch3 = createChannel("3", 3, "Channel 3")
        val lineup = listOf(ch1, ch2, ch3)

        // UP moves to next higher channel in CHANNEL_UP_DOWN mode
        val next = LiveChannelNavigator.nextChannel(
            current = ch1,
            lineup = lineup,
            direction = RemoteDirectionGate.Direction.UP,
            mode = LiveSurfMode.CHANNEL_UP_DOWN
        )
        assertEquals(ch2.id, next.id)

        // DOWN moves to previous lower channel
        val prev = LiveChannelNavigator.nextChannel(
            current = ch2,
            lineup = lineup,
            direction = RemoteDirectionGate.Direction.DOWN,
            mode = LiveSurfMode.CHANNEL_UP_DOWN
        )
        assertEquals(ch1.id, prev.id)

        // DOWN from first wraps around to last
        val wrapped = LiveChannelNavigator.nextChannel(
            current = ch1,
            lineup = lineup,
            direction = RemoteDirectionGate.Direction.DOWN,
            mode = LiveSurfMode.CHANNEL_UP_DOWN
        )
        assertEquals(ch3.id, wrapped.id)
    }

    @Test
    fun testListOrderModeNavigation() {
        val ch1 = createChannel("1", 1, "Top Channel")
        val ch2 = createChannel("2", 2, "Middle Channel")
        val ch3 = createChannel("3", 3, "Bottom Channel")
        val lineup = listOf(ch1, ch2, ch3)

        // In LIST_ORDER mode, DOWN moves down the list (+1 index)
        val nextInList = LiveChannelNavigator.nextChannel(
            current = ch1,
            lineup = lineup,
            direction = RemoteDirectionGate.Direction.DOWN,
            mode = LiveSurfMode.LIST_ORDER
        )
        assertEquals(ch2.id, nextInList.id)

        // In LIST_ORDER mode, UP moves up the list (-1 index)
        val prevInList = LiveChannelNavigator.nextChannel(
            current = ch2,
            lineup = lineup,
            direction = RemoteDirectionGate.Direction.UP,
            mode = LiveSurfMode.LIST_ORDER
        )
        assertEquals(ch1.id, prevInList.id)
    }

    @Test
    fun testEmptyOrMissingChannelHandling() {
        val ch1 = createChannel("1", 1, "Channel 1")
        val nonExistent = createChannel("999", 999, "Alien")

        // Empty lineup returns current channel
        val resultEmpty = LiveChannelNavigator.nextChannel(
            current = ch1,
            lineup = emptyList(),
            direction = RemoteDirectionGate.Direction.UP
        )
        assertEquals(ch1.id, resultEmpty.id)

        // Missing channel in lineup falls back to first
        val resultFallback = LiveChannelNavigator.nextChannel(
            current = nonExistent,
            lineup = listOf(ch1),
            direction = RemoteDirectionGate.Direction.UP
        )
        assertEquals(ch1.id, resultFallback.id)
    }
}
