package org.getlume.android.core.network

import org.getlume.android.core.data.entities.CategoryEntity
import org.getlume.android.core.data.entities.ChannelEntity
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.UUID

data class M3UParseResult(
    val categories: List<CategoryEntity>,
    val channels: List<ChannelEntity>
)

object M3UParser {

    private val TVG_ID_REGEX = """tvg-id="([^"]*)"""".toRegex()
    private val TVG_NAME_REGEX = """tvg-name="([^"]*)"""".toRegex()
    private val TVG_LOGO_REGEX = """tvg-logo="([^"]*)"""".toRegex()
    private val GROUP_TITLE_REGEX = """group-title="([^"]*)"""".toRegex()
    private val CATCHUP_REGEX = """catchup="([^"]*)"""".toRegex()
    private val CATCHUP_DAYS_REGEX = """catchup-days="([^"]*)"""".toRegex()

    fun parse(inputStream: InputStream, playlistId: String): M3UParseResult {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val categoriesMap = mutableMapOf<String, CategoryEntity>()
        val channels = mutableListOf<ChannelEntity>()

        var currentExtInf: String? = null
        var line: String? = reader.readLine()
        var channelIndex = 0

        while (line != null) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF:")) {
                currentExtInf = trimmed
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                val extInf = currentExtInf
                if (extInf != null) {
                    val tvgId = TVG_ID_REGEX.find(extInf)?.groupValues?.get(1) ?: ""
                    val tvgName = TVG_NAME_REGEX.find(extInf)?.groupValues?.get(1)
                    val tvgLogo = TVG_LOGO_REGEX.find(extInf)?.groupValues?.get(1) ?: ""
                    val groupTitle = GROUP_TITLE_REGEX.find(extInf)?.groupValues?.get(1)?.trim() ?: "General"
                    val catchupType = CATCHUP_REGEX.find(extInf)?.groupValues?.get(1) ?: ""
                    val catchupDays = CATCHUP_DAYS_REGEX.find(extInf)?.groupValues?.get(1)?.toIntOrNull() ?: 0

                    val displayName = extInf.substringAfterLast(",").trim().ifEmpty {
                        tvgName ?: "Channel ${channelIndex + 1}"
                    }

                    val catKey = "${playlistId}_${groupTitle.lowercase()}"
                    val category = categoriesMap.getOrPut(catKey) {
                        CategoryEntity(
                            id = catKey,
                            playlistId = playlistId,
                            type = "live",
                            name = groupTitle,
                            sortOrder = categoriesMap.size
                        )
                    }

                    channels.add(
                        ChannelEntity(
                            id = "${playlistId}_chan_${channelIndex}",
                            playlistId = playlistId,
                            categoryId = category.id,
                            streamId = channelIndex.toString(),
                            name = displayName,
                            logoUrl = tvgLogo,
                            streamUrl = trimmed,
                            tvgId = tvgId,
                            channelNumber = channelIndex + 1,
                            catchupType = catchupType,
                            catchupDays = catchupDays,
                            sortOrder = channelIndex
                        )
                    )
                    channelIndex++
                    currentExtInf = null
                }
            }
            line = reader.readLine()
        }

        return M3UParseResult(
            categories = categoriesMap.values.toList(),
            channels = channels
        )
    }
}
