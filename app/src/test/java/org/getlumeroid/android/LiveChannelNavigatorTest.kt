package org.getlumeroid.android

import org.getlumeroid.android.core.data.entities.ChannelEntity
import org.getlumeroid.android.core.player.LiveChannelNavigator
import org.getlumeroid.android.core.player.LiveSurfMode
import org.getlumeroid.android.core.player.RemoteDirectionGate
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
    fun testNavigationWithBoundaryClamping() {
        val ch1 = createChannel("1", 1, "Channel 1")
        val ch2 = createChannel("2", 2, "Channel 2")
        val ch3 = createChannel("3", 3, "Channel 3")
        val lineup = listOf(ch1, ch2, ch3)

        // UP from first channel is disabled (returns null)
        val upFromFirst = LiveChannelNavigator.nextChannel(
            current = ch1,
            lineup = lineup,
            direction = RemoteDirectionGate.Direction.UP
        )
        assertNull(upFromFirst)
        assertFalse(LiveChannelNavigator.canChannelUp(ch1, lineup))

        // DOWN from first channel moves to second
        val downFromFirst = LiveChannelNavigator.nextChannel(
            current = ch1,
            lineup = lineup,
            direction = RemoteDirectionGate.Direction.DOWN
        )
        assertEquals(ch2.id, downFromFirst?.id)
        assertTrue(LiveChannelNavigator.canChannelDown(ch1, lineup))

        // UP from second channel moves to first
        val upFromSecond = LiveChannelNavigator.nextChannel(
            current = ch2,
            lineup = lineup,
            direction = RemoteDirectionGate.Direction.UP
        )
        assertEquals(ch1.id, upFromSecond?.id)
        assertTrue(LiveChannelNavigator.canChannelUp(ch2, lineup))

        // DOWN from last channel is disabled (returns null)
        val downFromLast = LiveChannelNavigator.nextChannel(
            current = ch3,
            lineup = lineup,
            direction = RemoteDirectionGate.Direction.DOWN
        )
        assertNull(downFromLast)
        assertFalse(LiveChannelNavigator.canChannelDown(ch3, lineup))
    }

    @Test
    fun testEmptyOrMissingChannelHandling() {
        val ch1 = createChannel("1", 1, "Channel 1")
        val nonExistent = createChannel("999", 999, "Alien")

        // Empty lineup returns null
        val resultEmpty = LiveChannelNavigator.nextChannel(
            current = ch1,
            lineup = emptyList(),
            direction = RemoteDirectionGate.Direction.UP
        )
        assertNull(resultEmpty)

        // Missing channel in lineup falls back to first
        val resultFallback = LiveChannelNavigator.nextChannel(
            current = nonExistent,
            lineup = listOf(ch1),
            direction = RemoteDirectionGate.Direction.UP
        )
        assertEquals(ch1.id, resultFallback?.id)
    }
}
