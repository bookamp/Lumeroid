package org.getlumeroid.android.core.network

import org.getlumeroid.android.core.data.entities.EpgListingEntity
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object XMLTVParser {

    private val DATE_FORMAT = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val SHORT_DATE_FORMAT = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun parseTimestamp(raw: String?): Long {
        if (raw.isNullOrEmpty()) return 0L
        return try {
            val trimmed = raw.trim()
            if (trimmed.length > 14 && (trimmed.contains("+") || trimmed.contains("-"))) {
                DATE_FORMAT.parse(trimmed)?.time ?: 0L
            } else {
                SHORT_DATE_FORMAT.parse(trimmed.take(14))?.time ?: 0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    fun parse(inputStream: InputStream): List<EpgListingEntity> {
        val listings = mutableListOf<EpgListingEntity>()
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        var eventType = parser.eventType
        var currentTvgId = ""
        var currentStart = 0L
        var currentStop = 0L
        var currentTitle = ""
        var currentDesc = ""
        var currentPoster = ""
        var index = 0

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name.lowercase(Locale.ROOT)) {
                        "programme" -> {
                            currentTvgId = parser.getAttributeValue(null, "channel") ?: ""
                            currentStart = parseTimestamp(parser.getAttributeValue(null, "start"))
                            currentStop = parseTimestamp(parser.getAttributeValue(null, "stop"))
                            currentTitle = ""
                            currentDesc = ""
                            currentPoster = ""
                        }
                        "title" -> currentTitle = parser.nextText().trim()
                        "desc" -> currentDesc = parser.nextText().trim()
                        "icon" -> currentPoster = parser.getAttributeValue(null, "src") ?: ""
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("programme", ignoreCase = true)) {
                        if (currentTvgId.isNotEmpty() && currentStart > 0 && currentStop > currentStart) {
                            listings.add(
                                EpgListingEntity(
                                    id = "${currentTvgId}_${currentStart}",
                                    channelTvgId = currentTvgId,
                                    title = currentTitle.ifEmpty { "No Title" },
                                    description = currentDesc,
                                    startTime = currentStart,
                                    endTime = currentStop,
                                    posterUrl = currentPoster
                                )
                            )
                            index++
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return listings
    }
}
