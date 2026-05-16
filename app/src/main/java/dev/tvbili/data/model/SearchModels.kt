package dev.tvbili.data.model

import dev.tvbili.data.repo.HomeCard
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `x/web-interface/wbi/search/all/v2` 响应（slim 版）。
 *
 * tv-bili 只关心 `result_type == "video"` 的类目；其它（user / live_room /
 * bangumi）一律忽略。字段裁剪自 BiliPai `SearchModels.kt`。
 *
 * 注意：`duration` 字段是 **字符串** `"mm:ss"` 或 `"hh:mm:ss"`；
 * `pic` 经常返 `//i0.hdslb.com/...` 缺协议——见 [normalizeCoverUrl] / [parseSearchDuration]。
 */
@Serializable
data class SearchResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SearchData? = null,
)

@Serializable
data class SearchData(
    val page: Int = 1,
    val pagesize: Int = 20,
    @SerialName("numResults") val numResults: Int = 0,
    @SerialName("numPages") val numPages: Int = 0,
    val result: List<SearchResultCategory>? = null,
)

@Serializable
data class SearchResultCategory(
    @SerialName("result_type") val resultType: String = "",
    val data: List<SearchVideoItem>? = null,
)

@Serializable
data class SearchVideoItem(
    /** aid */
    val id: Long = 0,
    val bvid: String = "",
    /** HTML：含 `<em class="keyword">...</em>` 高亮，需 [cleanSearchHtml] 剥 */
    val title: String = "",
    /** 封面：常返 `//i0.hdslb.com/x.jpg` 缺协议 */
    val pic: String = "",
    val author: String = "",
    val play: Int = 0,
    /** `"05:32"` / `"01:05:32"` / `""`；非整数秒 */
    val duration: String = "",
    val mid: Long = 0,
)

fun SearchVideoItem.toHomeCardVideo(): HomeCard.Video = HomeCard.Video(
    aid = id,
    bvid = bvid,
    title = cleanSearchHtml(title),
    coverUrl = normalizeCoverUrl(pic),
    uploader = author,
    durationSec = parseSearchDuration(duration),
    viewCount = play.toLong(),
)

/**
 * 剥 `<em>` 高亮标签 + 4 个常见 HTML 实体反转义。移植自 BiliPai SearchModels:151-181。
 */
internal fun cleanSearchHtml(raw: String): String =
    raw.replace(Regex("<.*?>"), "")
        .replace("&quot;", "\"")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .trim()

/**
 * 补协议头：`//host/x` → `https://host/x`；`http://` → `https://`。
 */
internal fun normalizeCoverUrl(raw: String): String = when {
    raw.startsWith("//") -> "https:$raw"
    raw.startsWith("http://") -> raw.replaceFirst("http://", "https://")
    else -> raw
}

/**
 * `"mm:ss"` / `"hh:mm:ss"` / `"45"` / `""` → 秒数（解析失败返 0，不抛）。
 */
internal fun parseSearchDuration(raw: String): Int {
    if (raw.isBlank()) return 0
    if (raw.all { it.isDigit() }) return raw.toIntOrNull() ?: 0
    val parts = raw.split(":").mapNotNull { it.toIntOrNull() }
    return when (parts.size) {
        2 -> parts[0] * 60 + parts[1]
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        else -> 0
    }
}
