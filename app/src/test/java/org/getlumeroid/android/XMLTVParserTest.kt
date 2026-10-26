package org.getlumeroid.android

import org.getlumeroid.android.core.network.XMLTVParser
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class XMLTVParserTest {

    @Test
    fun testParseXmltvProgrammes() {
        val sampleXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <tv>
                <channel id="espn.us">
                    <display-name>ESPN</display-name>
                </channel>
                <programme start="20261020120000 +0000" stop="20261020130000 +0000" channel="espn.us">
                    <title>SportsCenter</title>
                    <desc>Daily sports news and highlights.</desc>
                    <icon src="https://example.com/poster.jpg" />
                </programme>
                <programme start="20261020130000 +0000" stop="20261020140000 +0000" channel="espn.us">
                    <title>NBA Today</title>
                    <desc>In-depth look at NBA storylines.</desc>
                </programme>
            </tv>
        """.trimIndent()

        val inputStream = ByteArrayInputStream(sampleXml.toByteArray())
        val listings = XMLTVParser.parse(inputStream)

        assertEquals(2, listings.size)

        val first = listings[0]
        assertEquals("espn.us", first.channelTvgId)
        assertEquals("SportsCenter", first.title)
        assertEquals("Daily sports news and highlights.", first.description)
        assertEquals("https://example.com/poster.jpg", first.posterUrl)
        assertTrue(first.endTime > first.startTime)

        val second = listings[1]
        assertEquals("espn.us", second.channelTvgId)
        assertEquals("NBA Today", second.title)
    }
}
