package org.getlumeroid.android

import org.getlumeroid.android.core.network.M3UParser
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class M3UParserTest {

    @Test
    fun testParseM3UWithStandardMetadata() {
        val sampleM3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="espn.us" tvg-name="ESPN HD" tvg-logo="https://example.com/espn.png" group-title="Sports",ESPN HD
            http://example.com/live/espn.m3u8
            #EXTINF:-1 tvg-id="cnn.us" tvg-name="CNN HD" tvg-logo="https://example.com/cnn.png" group-title="News" catchup="append" catchup-days="7",CNN USA
            http://example.com/live/cnn.m3u8
        """.trimIndent()

        val inputStream = ByteArrayInputStream(sampleM3u.toByteArray())
        val result = M3UParser.parse(inputStream, playlistId = "playlist_1")

        assertEquals(2, result.channels.size)
        assertEquals(2, result.categories.size)

        val espn = result.channels[0]
        assertEquals("ESPN HD", espn.name)
        assertEquals("espn.us", espn.tvgId)
        assertEquals("https://example.com/espn.png", espn.logoUrl)
        assertEquals("http://example.com/live/espn.m3u8", espn.streamUrl)
        assertEquals(1, espn.channelNumber)

        val cnn = result.channels[1]
        assertEquals("CNN USA", cnn.name)
        assertEquals("cnn.us", cnn.tvgId)
        assertEquals("append", cnn.catchupType)
        assertEquals(7, cnn.catchupDays)
        assertEquals(2, cnn.channelNumber)
    }

    @Test
    fun testParseM3UWithBlankLinesAndComments() {
        val sampleM3u = """
            #EXTM3U
            
            # This is a comment
            #EXTINF:-1 group-title="General",My Channel
            
            http://example.com/stream.ts
        """.trimIndent()

        val inputStream = ByteArrayInputStream(sampleM3u.toByteArray())
        val result = M3UParser.parse(inputStream, playlistId = "playlist_2")

        assertEquals(1, result.channels.size)
        assertEquals("My Channel", result.channels[0].name)
        assertEquals("General", result.categories[0].name)
        assertEquals("http://example.com/stream.ts", result.channels[0].streamUrl)
    }
}
