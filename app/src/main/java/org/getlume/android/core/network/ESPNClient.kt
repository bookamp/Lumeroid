package org.getlume.android.core.network

import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.*
import org.getlume.android.core.data.entities.ChannelEntity

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
        "hockey/nhl" to "NHL"
    )

    suspend fun fetchLiveScoreboards(): List<SportsFixture> {
        val fixtures = mutableListOf<SportsFixture>()
        for ((league, _) in supportedLeagues.take(4)) {
            fixtures.addAll(fetchScoreboard(league))
        }
        return fixtures
    }

    suspend fun fetchScoreboard(sportLeague: String): List<SportsFixture> {
        return try {
            val url = "https://site.api.espn.com/apis/site/v2/sports/$sportLeague/scoreboard"
            val responseText: String = client.get(url).bodyAsText()
            val root = json.parseToJsonElement(responseText).jsonObject

            val events = root["events"]?.jsonArray ?: return emptyList()
            val leagueName = root["leagues"]?.jsonArray?.firstOrNull()?.jsonObject?.get("name")?.jsonPrimitive?.content ?: "Sports"

            events.mapNotNull { eventElement ->
                val event = eventElement.jsonObject
                val id = event["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val competitions = event["competitions"]?.jsonArray?.firstOrNull()?.jsonObject ?: return@mapNotNull null
                val competitors = competitions["competitors"]?.jsonArray ?: return@mapNotNull null

                if (competitors.size < 2) return@mapNotNull null

                val home = competitors[0].jsonObject
                val away = competitors[1].jsonObject

                val homeName = home["team"]?.jsonObject?.get("displayName")?.jsonPrimitive?.content ?: "Home"
                val homeLogo = home["team"]?.jsonObject?.get("logo")?.jsonPrimitive?.content ?: ""
                val homeScore = home["score"]?.jsonPrimitive?.content ?: "-"

                val awayName = away["team"]?.jsonObject?.get("displayName")?.jsonPrimitive?.content ?: "Away"
                val awayLogo = away["team"]?.jsonObject?.get("logo")?.jsonPrimitive?.content ?: ""
                val awayScore = away["score"]?.jsonPrimitive?.content ?: "-"

                val status = event["status"]?.jsonObject
                val state = status?.get("type")?.jsonObject?.get("state")?.jsonPrimitive?.content ?: "pre"
                val statusDetail = status?.get("type")?.jsonObject?.get("shortDetail")?.jsonPrimitive?.content ?: ""
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
