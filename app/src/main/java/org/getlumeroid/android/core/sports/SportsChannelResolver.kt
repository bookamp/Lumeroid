package org.getlumeroid.android.core.sports

import org.getlumeroid.android.core.data.entities.ChannelEntity
import org.getlumeroid.android.core.network.SportsFixture

/**
 * Intelligent sports channel resolver matching Apple TV Lume's SportsChannelResolver.swift.
 * Matches live sports fixtures to channels in the viewer's active playlist.
 */
object SportsChannelResolver {

    private val leagueBroadcasterMap = mapOf(
        "soccer/eng.1" to listOf("sky sports", "tnt sports", "nbc", "peacock", "premier", "dazn", "canal+", "bein"),
        "soccer/esp.1" to listOf("dazn la liga", "laliga", "espn", "movistar", "bein"),
        "soccer/uefa.champions" to listOf("tnt sports", "paramount", "canal+ foot", "dazn", "cbs", "bein"),
        "basketball/nba" to listOf("espn", "tnt", "abc", "nba tv", "bally", "bein sports"),
        "football/nfl" to listOf("cbs", "fox", "nbc", "espn", "nfl redzone", "nfl"),
        "baseball/mlb" to listOf("mlb", "espn", "fox", "apple tv"),
        "hockey/nhl" to listOf("nhl", "espn", "tnt", "sportsnet")
    )

    fun resolveChannelsForFixture(
        fixture: SportsFixture,
        allChannels: List<ChannelEntity>,
        limit: Int = 4
    ): List<ChannelEntity> {
        if (allChannels.isEmpty()) return emptyList()

        val homeName = fixture.homeTeamName.lowercase()
        val awayName = fixture.awayTeamName.lowercase()
        val leagueKey = fixture.leagueCode.lowercase()
        val broadcasters = leagueBroadcasterMap[leagueKey] ?: listOf("sports", "sport", "dazn", "espn", "sky")

        val scoredChannels = mutableListOf<Pair<ChannelEntity, Int>>()

        for (channel in allChannels) {
            val name = channel.name.lowercase()
            var score = 0

            // 1. Direct team name match in channel name (highest confidence, e.g. "PPV: Arsenal vs Chelsea")
            if (homeName.length > 3 && name.contains(homeName)) {
                score += 100
            }
            if (awayName.length > 3 && name.contains(awayName)) {
                score += 100
            }

            // 2. Broadcaster keyword match
            for (kw in broadcasters) {
                if (name.contains(kw)) {
                    score += 25
                    // UHD / 4K channels get priority
                    if (name.contains("4k") || name.contains("uhd")) {
                        score += 10
                    }
                    break
                }
            }

            // 3. Category match
            val catId = channel.categoryId.lowercase()
            if (catId.contains("sport") || catId.contains("soccer") || catId.contains("football")) {
                score += 5
            }

            if (score >= 25) {
                scoredChannels.add(channel to score)
            }
        }

        return scoredChannels
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }
}
