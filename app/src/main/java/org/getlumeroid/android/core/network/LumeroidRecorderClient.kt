package org.getlumeroid.android.core.network

import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.getlumeroid.android.core.data.entities.RecordingEntity

@Serializable
data class PairRequest(val code: String)

@Serializable
data class ScheduleRecordingRequest(
    val channelStreamUrl: String,
    val channelName: String,
    val programmeTitle: String,
    val durationMinutes: Int
)

class LumeroidRecorderClient(
    private val host: String = "192.168.1.235",
    private val port: Int = 8090
) {
    private val client = HttpClient(Android)
    private val json = Json { ignoreUnknownKeys = true }
    private val baseUrl = "http://$host:$port"
    var bearerToken: String? = null

    suspend fun pairWithCode(sixDigitCode: String): Boolean {
        return try {
            val response = client.post("$baseUrl/api/pair") {
                contentType(ContentType.Application.Json)
                setBody("""{"code":"$sixDigitCode"}""")
            }
            if (response.status == HttpStatusCode.OK) {
                val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
                bearerToken = body["token"]?.jsonPrimitive?.content
                bearerToken != null
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getRecordings(): List<RecordingEntity> {
        return try {
            val response = client.get("$baseUrl/api/recordings") {
                bearerToken?.let { header("Authorization", "Bearer $it") }
            }
            if (response.status != HttpStatusCode.OK) return emptyList()

            val array = json.parseToJsonElement(response.bodyAsText()).jsonArray
            array.mapNotNull { element ->
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val channel = obj["channelName"]?.jsonPrimitive?.content ?: "Live TV"
                val title = obj["programmeTitle"]?.jsonPrimitive?.content ?: "Recording"
                val status = obj["status"]?.jsonPrimitive?.content ?: "completed"
                val start = obj["startTime"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()
                val duration = obj["durationMinutes"]?.jsonPrimitive?.intOrNull ?: 60
                val playbackPath = obj["playbackPath"]?.jsonPrimitive?.content ?: ""

                RecordingEntity(
                    id = id,
                    serverId = "$host:$port",
                    channelName = channel,
                    programmeTitle = title,
                    status = status,
                    startTime = start,
                    durationMinutes = duration,
                    playbackUrl = "$baseUrl$playbackPath"
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun scheduleRecording(
        channelStreamUrl: String,
        channelName: String,
        programmeTitle: String,
        durationMinutes: Int
    ): Boolean {
        return try {
            val response = client.post("$baseUrl/api/recordings") {
                contentType(ContentType.Application.Json)
                bearerToken?.let { header("Authorization", "Bearer $it") }
                setBody(
                    """
                    {
                        "channelStreamUrl": "$channelStreamUrl",
                        "channelName": "$channelName",
                        "programmeTitle": "$programmeTitle",
                        "durationMinutes": $durationMinutes
                    }
                    """.trimIndent()
                )
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            false
        }
    }
}
