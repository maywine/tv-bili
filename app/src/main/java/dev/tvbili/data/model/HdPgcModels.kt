package dev.tvbili.data.model

import dev.tvbili.data.repo.HomeCard
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull

@Serializable
data class HdPgcSearchResponse(
    val code: Int = 0,
    val message: String = "",
    val data: HdPgcSearchData? = null,
)

@Serializable
data class HdPgcSearchData(
    val items: List<HdPgcSearchItem> = emptyList(),
    val page: Int = 1,
    val pages: Int = 0,
)

@Serializable
data class HdPgcSearchItem(
    @SerialName("season_id") val seasonId: Long = 0,
    @SerialName("season_type") val seasonType: Int = 0,
    val title: String = "",
    val cover: String = "",
    val label: String = "",
    val badges: List<HdPgcBadge> = emptyList(),
)

@Serializable
data class HdPgcBadge(val text: String = "")

/** 移动端 type=8 为整个影视类目，电影和综艺仍须按 season_type 隔离。 */
internal fun List<HdPgcSearchItem>.toPgcCards(seasonType: Int): List<HomeCard.PgcSeason> =
    asSequence()
        .filter { it.seasonType == seasonType && it.seasonId > 0 && it.title.isNotBlank() }
        .distinctBy { it.seasonId }
        .map {
            HomeCard.PgcSeason(
                seasonId = it.seasonId,
                title = cleanSearchHtml(it.title),
                coverUrl = normalizeCoverUrl(it.cover),
                // 搜索的 label 是演员名单，不能作为更新提示铺满节目封面。
                indexShow = "",
                badge = it.badges.firstOrNull()?.text.orEmpty(),
            )
        }.toList()

@Serializable
data class HdSeasonResponse(
    val code: Int = 0,
    val message: String = "",
    val data: HdSeasonDetail? = null,
)

@Serializable
data class HdSeasonDetail(
    @SerialName("season_id") val seasonId: Long = 0,
    val title: String = "",
    val cover: String = "",
    val modules: List<HdSeasonModule> = emptyList(),
) {
    fun toSeasonDetail(): PgcSeasonDetail = PgcSeasonDetail(
        seasonId = seasonId,
        title = title,
        cover = cover,
        // section 中可能是花絮/预告；不能混入正片集表后再取“最新一集”。
        episodes = modules.filter { it.style == "positive" }.flatMap { it.data?.episodes.orEmpty() }
            .distinctBy { it.id },
    )
}

@Serializable
data class HdSeasonModule(val style: String = "", val data: HdSeasonModuleData? = null)

@Serializable
data class HdSeasonModuleData(val episodes: List<PgcEpisode> = emptyList())

private val hdPlaybackJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

/** 手机 playurl 返回平铺结构；兼容其他移动端响应的 data/result 包装，不能把 result 字符串当正文。 */
internal fun decodeHdPlayUrl(response: JsonObject): PlayUrlData {
    val code = (response["code"] as? JsonPrimitive)?.intOrNull ?: error("播放接口缺少状态码")
    val message = (response["message"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    require(code == 0) { "播放失败：$message ($code)" }
    val payload = response["data"] as? JsonObject ?: response["result"] as? JsonObject ?: response
    val errorCode = (payload["error_code"] as? JsonPrimitive)?.intOrNull ?: 0
    val normalized = payload.toMutableMap()
    (payload["is_preview"] as? JsonPrimitive)?.booleanOrNull?.let {
        normalized["is_preview"] = JsonPrimitive(if (it) 1 else 0)
    }
    val data = hdPlaybackJson.decodeFromJsonElement<PlayUrlData>(JsonObject(normalized))
    // 手机接口会用 error_code 表示正片权限不足，同时返回 is_preview=1 的合法试看流。
    val hasPreview = data.isPreview == 1 &&
        (data.dash?.video?.any { it.validUrl().isNotBlank() } == true || data.durl.any { it.validUrl().isNotBlank() })
    require(errorCode == 0 || hasPreview) { "当前账号无可用播放权限 ($errorCode)" }
    return data
}
