package org.getlumeroid.android.core.network

import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.*
import org.getlumeroid.android.core.data.entities.ChannelEntity

data class SportsFixture(
    val id: String,
    val leagueCode: String = "",
    val leagueName: String,
    val homeTeamName: String,
    val homeTeamLogo: String,
    val homeTeamScore: String,
    val awayTeamName: String,
    val awayTeamLogo: String,
    val awayTeamScore: String,
    val statusText: String, // "LIVE", "FT", "15:00"
    val isLive: Boolean,
    val startTimeEpochMs: Long,
    val matchedChannel: ChannelEntity? = null
) {
    val competition: String get() = leagueName
    val homeTeam: String get() = homeTeamName
    val awayTeam: String get() = awayTeamName
    val homeScore: String? get() = homeTeamScore
    val awayScore: String? get() = awayTeamScore
    val status: String get() = statusText
    val broadcaster: String? get() = matchedChannel?.name
}

class ESPNClient {
    private val client = HttpClient(Android)
    private val json = Json { ignoreUnknownKeys = true }

    // Popular default leagues
    val supportedLeagues = listOf(
        "soccer/eng.1" to "Premier League",
        "soccer/uefa.champions" to "Champions League",
        "soccer/esp.1" to "La Liga",
        "basketball/nba" to "NBA",
        "football/nfl" to "NFL",
        "baseball/mlb" to "MLB",
        "hockey/nhl" to "NHL",
        "tennis/atp" to "Tennis",
        "cricket" to "Cricket",
        "racing/f1" to "Formula 1"
    )

    suspend fun fetchLiveScoreboards(): List<SportsFixture> {
        val fixtures = mutableListOf<SportsFixture>()
        for ((league, _) in supportedLeagues.take(4)) {
            fixtures.addAll(fetchScoreboard(league))
        }
        return fixtures
    }

    suspend fun fetchScoreboard(sportLeague: String): List<SportsFixture> {
        return when (sportLeague) {
            "cricket" -> {
                val results = mutableListOf<SportsFixture>()
                for (leagueId in listOf("cricket/8043", "cricket/8048", "cricket/8604")) {
                    results.addAll(fetchSingleScoreboard(leagueId))
                }
                results
            }
            "tennis/atp", "tennis" -> {
                val results = mutableListOf<SportsFixture>()
                results.addAll(fetchSingleScoreboard("tennis/atp"))
                results.addAll(fetchSingleScoreboard("tennis/wta"))
                results
            }
            else -> fetchSingleScoreboard(sportLeague)
        }
    }

    private suspend fun fetchSingleScoreboard(sportLeague: String): List<SportsFixture> {
        return try {
            val url = "https://site.api.espn.com/apis/site/v2/sports/$sportLeague/scoreboard"
            val responseText: String = client.get(url).bodyAsText()
            val root = json.parseToJsonElement(responseText).jsonObject

            val events = root["events"]?.jsonArray ?: return emptyList()
            val leagueName = root["leagues"]?.jsonArray?.firstOrNull()?.jsonObject?.get("name")?.jsonPrimitive?.content ?: "Sports"

            events.mapNotNull { eventElement ->
                val event = eventElement.jsonObject
                val id = event["id"]?.jsonPrimitive?.content ?: return@mapNotNull null

                val competitionsList = mutableListOf<JsonObject>()
                event["competitions"]?.jsonArray?.forEach { if (it is JsonObject) competitionsList.add(it) }
                if (competitionsList.isEmpty()) {
                    event["groupings"]?.jsonArray?.forEach { grouping ->
                        if (grouping is JsonObject) {
                            grouping["competitions"]?.jsonArray?.forEach { comp ->
                                if (comp is JsonObject) competitionsList.add(comp)
                            }
                        }
                    }
                }

                val competition = competitionsList.firstOrNull() ?: return@mapNotNull null
                val competitors = competition["competitors"]?.jsonArray ?: JsonArray(emptyList())

                val (homeName, homeLogo, homeScore) = if (competitors.size >= 1) {
                    val c = competitors[0].jsonObject
                    val name = c["team"]?.jsonObject?.get("displayName")?.jsonPrimitive?.content
                        ?: c["athlete"]?.jsonObject?.get("displayName")?.jsonPrimitive?.content
                        ?: c["athlete"]?.jsonObject?.get("fullName")?.jsonPrimitive?.content
                        ?: "Home"
                    val logo = c["team"]?.jsonObject?.get("logo")?.jsonPrimitive?.content
                        ?: c["athlete"]?.jsonObject?.get("flag")?.jsonObject?.get("href")?.jsonPrimitive?.content
                        ?: c["athlete"]?.jsonObject?.get("headshot")?.jsonObject?.get("href")?.jsonPrimitive?.content
                        ?: ""
                    val score = c["score"]?.jsonPrimitive?.content ?: ""
                    Triple(name, logo, score)
                } else {
                    val eventName = event["name"]?.jsonPrimitive?.content ?: "Event"
                    Triple(eventName, "", "")
                }

                val (awayName, awayLogo, awayScore) = if (competitors.size >= 2) {
                    val c = competitors[1].jsonObject
                    val name = c["team"]?.jsonObject?.get("displayName")?.jsonPrimitive?.content
                        ?: c["athlete"]?.jsonObject?.get("displayName")?.jsonPrimitive?.content
                        ?: c["athlete"]?.jsonObject?.get("fullName")?.jsonPrimitive?.content
                        ?: "Away"
                    val logo = c["team"]?.jsonObject?.get("logo")?.jsonPrimitive?.content
                        ?: c["athlete"]?.jsonObject?.get("flag")?.jsonObject?.get("href")?.jsonPrimitive?.content
                        ?: c["athlete"]?.jsonObject?.get("headshot")?.jsonObject?.get("href")?.jsonPrimitive?.content
                        ?: ""
                    val score = c["score"]?.jsonPrimitive?.content ?: ""
                    Triple(name, logo, score)
                } else {
                    val compType = competition["type"]?.jsonObject?.get("abbreviation")?.jsonPrimitive?.content
                        ?: competition["description"]?.jsonPrimitive?.content
                        ?: "Session"
                    Triple(compType, "", "")
                }

                val status = event["status"]?.jsonObject ?: competition["status"]?.jsonObject
                val state = status?.get("type")?.jsonObject?.get("state")?.jsonPrimitive?.content ?: "pre"
                val statusDetail = status?.get("type")?.jsonObject?.get("shortDetail")?.jsonPrimitive?.content
                    ?: status?.get("type")?.jsonObject?.get("description")?.jsonPrimitive?.content
                    ?: ""
                val isLive = state == "in"

                SportsFixture(
                    id = id,
                    leagueCode = sportLeague,
                    leagueName = leagueName,
                    homeTeamName = homeName,
                    homeTeamLogo = homeLogo,
                    homeTeamScore = homeScore,
                    awayTeamName = awayName,
                    awayTeamLogo = awayLogo,
                    awayTeamScore = awayScore,
                    statusText = if (isLive) "LIVE $statusDetail" else statusDetail,
                    isLive = isLive,
                    startTimeEpochMs = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Matches a fixture's team names against user's channels & EPG data
     */
    fun matchFixtureToChannel(fixture: SportsFixture, allChannels: List<ChannelEntity>): ChannelEntity? {
        val homeTokens = fixture.homeTeamName.lowercase().split(" ")
        val awayTokens = fixture.awayTeamName.lowercase().split(" ")

        return allChannels.firstOrNull { channel ->
            val name = channel.name.lowercase()
            homeTokens.any { token -> token.length > 3 && name.contains(token) } ||
            awayTokens.any { token -> token.length > 3 && name.contains(token) }
        }
    }
}
