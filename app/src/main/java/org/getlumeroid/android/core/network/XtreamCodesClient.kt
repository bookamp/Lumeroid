package org.getlumeroid.android.core.network

import android.util.Base64
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import org.getlumeroid.android.core.data.entities.*
import java.nio.charset.StandardCharsets

object LenientStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientString", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: String?) {
        if (value != null) encoder.encodeString(value) else encoder.encodeNull()
    }
    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder ?: return try { decoder.decodeString() } catch (e: Exception) { null }
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonNull -> null
            is JsonPrimitive -> element.content
            else -> element.toString()
        }
    }
}

@Serializable
data class XtreamCategory(
    val category_id: String,
    val category_name: String
)

@Serializable
data class XtreamLiveStream(
    val num: Int? = null,
    val name: String,
    val stream_type: String? = null,
    val stream_id: Long,
    val stream_icon: String? = null,
    val epg_channel_id: String? = null,
    @Serializable(with = LenientStringSerializer::class) val category_id: String? = null,
    val tv_archive: Int? = 0,
    val tv_archive_duration: Int? = 0
)

@Serializable
data class XtreamVodStream(
    val num: Int? = null,
    val name: String,
    val stream_id: Long,
    val stream_icon: String? = null,
    @Serializable(with = LenientStringSerializer::class) val category_id: String? = null,
    val container_extension: String? = "mp4",
    @Serializable(with = LenientStringSerializer::class) val rating: String? = null,
    @Serializable(with = LenientStringSerializer::class) val year: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val release_date: String? = null,
    val genre: String? = null,
    @Serializable(with = LenientStringSerializer::class) val tmdb_id: String? = null,
    @Serializable(with = LenientStringSerializer::class) val tmdb: String? = null,
    @Serializable(with = LenientStringSerializer::class) val added: String? = null
)

@Serializable
data class XtreamSeries(
    val series_id: Long,
    val name: String,
    val cover: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    @Serializable(with = LenientStringSerializer::class) val category_id: String? = null,
    @Serializable(with = LenientStringSerializer::class) val rating: String? = null,
    val genre: String? = null,
    @Serializable(with = LenientStringSerializer::class) val tmdb_id: String? = null,
    @Serializable(with = LenientStringSerializer::class) val tmdb: String? = null,
    @Serializable(with = LenientStringSerializer::class) val last_modified: String? = null
)

data class SeriesDetailsResult(
    val plot: String = "",
    val cast: String = "",
    val rating: Float = 0f,
    val seasons: List<Int> = emptyList(),
    val episodes: List<EpisodeEntity> = emptyList(),
    val backdropUrl: String = "",
    val genre: String = ""
)

data class VodDetailsResult(
    val plot: String = "",
    val cast: String = "",
    val director: String = "",
    val releaseDate: String = "",
    val durationMinutes: Int = 0,
    val rating: Float = 0f,
    val backdropUrl: String = "",
    val genre: String = ""
)

class XtreamCodesClient(
    private val serverUrl: String,
    private val username: String,
    private val password: String
) {
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(jsonParser)
        }
    }

    private val baseUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"

    private fun buildUrl(action: String, extraParams: String = ""): String {
        return "${baseUrl}player_api.php?username=$username&password=$password&action=$action$extraParams"
    }

    suspend fun fetchLiveCategories(playlistId: String): List<CategoryEntity> {
        return try {
            val response: List<XtreamCategory> = client.get(buildUrl("get_live_categories")).body()
            response.mapIndexed { index, cat ->
                CategoryEntity(
                    id = "${playlistId}_live_${cat.category_id}",
                    playlistId = playlistId,
                    type = "live",
                    name = cat.category_name,
                    sortOrder = index
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchLiveStreams(playlistId: String, categoryId: String? = null): List<ChannelEntity> {
        return try {
            val extra = if (!categoryId.isNullOrBlank()) "&category_id=$categoryId" else ""
            val response: List<XtreamLiveStream> = client.get(buildUrl("get_live_streams", extra)).body()
            response.mapIndexed { index, stream ->
                val streamUrl = "${baseUrl}live/$username/$password/${stream.stream_id}.ts"
                val catId = stream.category_id ?: "0"
                ChannelEntity(
                    id = "${playlistId}_stream_${stream.stream_id}",
                    playlistId = playlistId,
                    categoryId = "${playlistId}_live_$catId",
                    streamId = stream.stream_id.toString(),
                    name = stream.name,
                    logoUrl = stream.stream_icon ?: "",
                    streamUrl = streamUrl,
                    tvgId = stream.epg_channel_id ?: "",
                    channelNumber = stream.num ?: (index + 1),
                    catchupType = if ((stream.tv_archive ?: 0) == 1) "xc" else "",
                    catchupDays = stream.tv_archive_duration ?: 0,
                    sortOrder = index
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchVodCategories(playlistId: String): List<CategoryEntity> {
        return try {
            val response: List<XtreamCategory> = client.get(buildUrl("get_vod_categories")).body()
            response.mapIndexed { index, cat ->
                CategoryEntity(
                    id = "${playlistId}_movie_${cat.category_id}",
                    playlistId = playlistId,
                    type = "movie",
                    name = cat.category_name,
                    sortOrder = index
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchVodStreams(playlistId: String, categoryId: String? = null): List<MovieEntity> {
        return try {
            val extra = if (!categoryId.isNullOrBlank()) "&category_id=$categoryId" else ""
            val response: List<XtreamVodStream> = client.get(buildUrl("get_vod_streams", extra)).body()
            response.map { stream ->
                val ext = stream.container_extension ?: "mp4"
                val streamUrl = "${baseUrl}movie/$username/$password/${stream.stream_id}.$ext"
                val catId = stream.category_id ?: "0"
                val rawTmdb = stream.tmdb_id?.ifBlank { null } ?: stream.tmdb?.ifBlank { null }
                val tmdbId = rawTmdb?.trim()?.toIntOrNull()
                MovieEntity(
                    id = "${playlistId}_movie_${stream.stream_id}",
                    playlistId = playlistId,
                    categoryId = "${playlistId}_movie_$catId",
                    streamId = stream.stream_id.toString(),
                    name = stream.name,
                    posterUrl = stream.stream_icon ?: "",
                    streamUrl = streamUrl,
                    extension = ext,
                    plot = stream.plot ?: "",
                    cast = stream.cast ?: "",
                    director = stream.director ?: "",
                    releaseDate = stream.release_date ?: stream.year ?: "",
                    rating = stream.rating?.toFloatOrNull() ?: 0.0f,
                    genre = stream.genre ?: "",
                    tmdbId = tmdbId,
                    added = stream.added
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("XtreamCodesClient", "fetchVodStreams failed: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun fetchSeriesCategories(playlistId: String): List<CategoryEntity> {
        return try {
            val response: List<XtreamCategory> = client.get(buildUrl("get_series_categories")).body()
            response.mapIndexed { index, cat ->
                CategoryEntity(
                    id = "${playlistId}_series_${cat.category_id}",
                    playlistId = playlistId,
                    type = "series",
                    name = cat.category_name,
                    sortOrder = index
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("XtreamCodesClient", "fetchSeriesCategories failed: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun fetchSeries(playlistId: String, categoryId: String? = null): List<SeriesEntity> {
        return try {
            val extra = if (!categoryId.isNullOrBlank()) "&category_id=$categoryId" else ""
            val response: List<XtreamSeries> = client.get(buildUrl("get_series", extra)).body()
            response.map { series ->
                val catId = series.category_id ?: "0"
                val rawTmdb = series.tmdb_id?.ifBlank { null } ?: series.tmdb?.ifBlank { null }
                val tmdbId = rawTmdb?.trim()?.toIntOrNull()
                SeriesEntity(
                    id = "${playlistId}_series_${series.series_id}",
                    playlistId = playlistId,
                    categoryId = "${playlistId}_series_$catId",
                    seriesId = series.series_id.toString(),
                    name = series.name,
                    posterUrl = series.cover ?: "",
                    plot = series.plot ?: "",
                    cast = series.cast ?: "",
                    rating = series.rating?.toFloatOrNull() ?: 0.0f,
                    genre = series.genre ?: "",
                    tmdbId = tmdbId,
                    lastModified = series.last_modified
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("XtreamCodesClient", "fetchSeries failed: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun fetchSeriesDetails(seriesId: String): SeriesDetailsResult {
        return try {
            val rawJson: String = client.get(buildUrl("get_series_info", "&series_id=$seriesId")).body()
            val root = (jsonParser.parseToJsonElement(rawJson) as? JsonObject) ?: return SeriesDetailsResult()
            val info = root["info"] as? JsonObject
            val plot = info?.get("plot")?.jsonPrimitive?.contentOrNull ?: ""
            val cast = info?.get("cast")?.jsonPrimitive?.contentOrNull ?: ""
            val rating = info?.get("rating")?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0f

            val episodesMap = (root["episodes"] as? JsonObject) ?: JsonObject(emptyMap())
            val seasonsList = mutableListOf<Int>()
            val episodeEntities = mutableListOf<EpisodeEntity>()

            for ((seasonKey, epArrayJson) in episodesMap) {
                val seasonNum = seasonKey.toIntOrNull() ?: 1
                if (!seasonsList.contains(seasonNum)) seasonsList.add(seasonNum)

                val epArray = epArrayJson as? JsonArray ?: continue
                for (epElement in epArray) {
                    val epObj = epElement as? JsonObject ?: continue
                    val epId = epObj["id"]?.jsonPrimitive?.contentOrNull ?: continue
                    val epNum = epObj["episode_num"]?.jsonPrimitive?.intOrNull ?: 1
                    val epTitle = epObj["title"]?.jsonPrimitive?.contentOrNull ?: "Episode $epNum"
                    val ext = epObj["container_extension"]?.jsonPrimitive?.contentOrNull ?: "mp4"
                    val epInfo = epObj["info"] as? JsonObject
                    val epPlot = epInfo?.get("overview")?.jsonPrimitive?.contentOrNull ?: ""
                    val durationSecs = epInfo?.get("duration_secs")?.jsonPrimitive?.intOrNull ?: 0
                    val epThumb = epInfo?.get("movie_image")?.jsonPrimitive?.contentOrNull ?: ""
                    val epStreamUrl = "${baseUrl}series/$username/$password/$epId.$ext"

                    episodeEntities.add(
                        EpisodeEntity(
                            id = "ep_${seriesId}_${seasonNum}_${epId}",
                            seriesId = seriesId,
                            seasonNumber = seasonNum,
                            episodeNumber = epNum,
                            title = epTitle,
                            plot = epPlot,
                            thumbnail = epThumb,
                            streamId = epId,
                            streamUrl = epStreamUrl,
                            durationMinutes = durationSecs / 60
                        )
                    )
                }
            }

            val backdropUrl = info?.get("backdrop_path")?.let { elem ->
                when (elem) {
                    is JsonArray -> elem.firstOrNull()?.jsonPrimitive?.contentOrNull
                    is JsonPrimitive -> elem.contentOrNull
                    else -> null
                }
            } ?: ""
            val genre = info?.get("genre")?.jsonPrimitive?.contentOrNull ?: ""

            SeriesDetailsResult(
                plot = plot,
                cast = cast,
                rating = rating,
                seasons = seasonsList.sorted(),
                episodes = episodeEntities,
                backdropUrl = backdropUrl,
                genre = genre
            )
        } catch (e: Exception) {
            SeriesDetailsResult()
        }
    }

    suspend fun fetchVodDetails(vodId: String): VodDetailsResult {
        return try {
            val rawJson: String = client.get(buildUrl("get_vod_info", "&vod_id=$vodId")).body()
            val root = jsonParser.parseToJsonElement(rawJson).jsonObject
            val info = root["info"]?.jsonObject
            val plot = info?.get("plot")?.jsonPrimitive?.contentOrNull ?: ""
            val cast = info?.get("cast")?.jsonPrimitive?.contentOrNull ?: ""
            val director = info?.get("director")?.jsonPrimitive?.contentOrNull ?: ""
            val releaseDate = info?.get("releasedate")?.jsonPrimitive?.contentOrNull
                ?: info?.get("release_date")?.jsonPrimitive?.contentOrNull ?: ""
            val durationSecs = info?.get("duration_secs")?.jsonPrimitive?.intOrNull ?: 0
            val rating = info?.get("rating")?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0f
            val backdropUrl = info?.get("backdrop_path")?.let { elem ->
                when (elem) {
                    is JsonArray -> elem.firstOrNull()?.jsonPrimitive?.contentOrNull
                    is JsonPrimitive -> elem.contentOrNull
                    else -> null
                }
            } ?: ""
            val genre = info?.get("genre")?.jsonPrimitive?.contentOrNull ?: ""

            VodDetailsResult(
                plot = plot,
                cast = cast,
                director = director,
                releaseDate = releaseDate,
                durationMinutes = durationSecs / 60,
                rating = rating,
                backdropUrl = backdropUrl,
                genre = genre
            )
        } catch (e: Exception) {
            VodDetailsResult()
        }
    }

    suspend fun fetchShortEpg(streamId: String): List<EpgListingEntity> {
        return try {
            val rawJson: String = client.get(buildUrl("get_short_epg", "&stream_id=$streamId&limit=5")).body()
            val root = jsonParser.parseToJsonElement(rawJson).jsonObject
            val listingsArray = root["epg_listings"]?.jsonArray ?: return emptyList()

            listingsArray.mapNotNull { element ->
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val rawTitle = obj["title"]?.jsonPrimitive?.contentOrNull ?: ""
                val rawDesc = obj["description"]?.jsonPrimitive?.contentOrNull ?: ""
                val startTs = obj["start_timestamp"]?.jsonPrimitive?.longOrNull ?: 0L
                val stopTs = obj["stop_timestamp"]?.jsonPrimitive?.longOrNull ?: 0L

                val title = decodeBase64Safe(rawTitle)
                val desc = decodeBase64Safe(rawDesc)

                EpgListingEntity(
                    id = id,
                    channelTvgId = streamId,
                    title = title,
                    description = desc,
                    startTime = startTs * 1000L,
                    endTime = stopTs * 1000L
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun decodeBase64Safe(input: String): String {
        return try {
            if (input.isBlank()) return ""
            val decodedBytes = Base64.decode(input, Base64.DEFAULT)
            String(decodedBytes, StandardCharsets.UTF_8).trim()
        } catch (e: Exception) {
            input
        }
    }
}
