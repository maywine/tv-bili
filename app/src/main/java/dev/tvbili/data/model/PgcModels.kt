package dev.tvbili.data.model

import dev.tvbili.data.repo.HomeCard
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * `pgc/season/index/result?season_type={N}&page={p}&pagesize=20&order=2&...`
 *
 * season_type 取值：1=番剧 / 2=电影 / 3=纪录片 / 4=国创 / 5=电视剧 / 7=综艺
 * order：2=按热度（人气）/ 0=按更新时间 / 3=按追番人数
 *
 * 注意：返回的是 **季度（season）** 维度，不是单集——一部综艺 = 一个 season_id；
 * 点开后需要再请求 `pgc/view/web/season?season_id=...` 拿剧集列表里的 bvid 才能播。
 *
 * **响应根字段是 `result` 不是 `data`**——B 站 PGC 域的接口大多数用 `result`，与
 * UGC 域的 `data` 不一致。之前用 `data` 反序列化拿不到值导致首页综艺分区报错。
 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class PgcIndexResponse(
    val code: Int = 0,
    val message: String = "",
    /**
     * 主字段 `result`；少数 PGC 兜底端点返回 `data`——靠 [JsonNames] 同时认两个名字。
     */
    @JsonNames("result", "data")
    val result: PgcIndexData? = null,
)

@Serializable
data class PgcIndexData(
    val list: List<PgcSeasonItem> = emptyList(),
    val num: Int = 0,
    val page: Int = 0,
    val total: Int = 0,
    @SerialName("has_next")
    val hasNext: Int = 0,
)

@Serializable
data class PgcSeasonItem(
    @SerialName("season_id")
    val seasonId: Long = 0,
    @SerialName("media_id")
    val mediaId: Long = 0,
    val title: String = "",
    val cover: String = "",
    /** 「更新至第 10 期」类提示串；空串表示无更新信息。 */
    @SerialName("index_show")
    val indexShow: String = "",
    /** 大会员标签（"会员"）/ 付费标签（"付费"）；空表示免费。 */
    val badge: String = "",
)

/**
 * `pgc/web/rank/list?season_type={N}&day=3` —— B 站「热门 PGC 排行」专用接口。
 * 比 `pgc/season/index/result` 更稳：参数简单、响应结构明确，是综艺频道 web 端使用的同款。
 *
 * - `day` 可选 3 / 7：3 = 3 天榜（更新快），7 = 周榜
 * - 不分页，固定返回 top ~30 条
 * - 注意：`index_show` 嵌在 `new_ep` 节点下，不在顶层
 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class PgcRankResponse(
    val code: Int = 0,
    val message: String = "",
    @JsonNames("result", "data")
    val result: PgcRankData? = null,
)

@Serializable
data class PgcRankData(
    val list: List<PgcRankItem> = emptyList(),
)

@Serializable
data class PgcRankItem(
    @SerialName("season_id")
    val seasonId: Long = 0,
    val title: String = "",
    val cover: String = "",
    val badge: String = "",
    @SerialName("new_ep")
    val newEp: PgcRankNewEp? = null,
)

@Serializable
data class PgcRankNewEp(
    @SerialName("index_show")
    val indexShow: String = "",
    val cover: String = "",
)

fun PgcRankItem.toHomeCard(): HomeCard.PgcSeason = HomeCard.PgcSeason(
    seasonId = seasonId,
    title = title,
    coverUrl = cover,
    indexShow = newEp?.indexShow.orEmpty(),
    badge = badge,
)

/**
 * `pgc/view/web/season?season_id=...` —— PGC 季度详情，含剧集列表。
 * 只取最少字段：episodes[*].bvid 用于点 PgcSeason 卡后跳 VideoDetail。
 */
@Serializable
data class PgcSeasonDetailResponse(
    val code: Int = 0,
    val message: String = "",
    val result: PgcSeasonDetail? = null,
)

@Serializable
data class PgcSeasonDetail(
    @SerialName("season_id")
    val seasonId: Long = 0,
    val title: String = "",
    val cover: String = "",
    val episodes: List<PgcEpisode> = emptyList(),
)

@Serializable
data class PgcEpisode(
    /** ep_id；与 bvid 不同体系，仅 PGC 内部用。 */
    val id: Long = 0,
    val aid: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    /** 标题，如「第 10 期 2026.05.16」。 */
    val title: String = "",
    /** 长标题，如「向往的生活 第10期」。 */
    @SerialName("long_title")
    val longTitle: String = "",
    val cover: String = "",
)

/**
 * `pgc/player/web/playurl?bvid=...&cid=...&qn=...&fnval=4048&try_look=1` —— PGC 视频
 * 播放地址。**响应根字段是 `result`**，与 UGC 域的 `data` 不一致。
 *
 * `try_look=1` 让没大会员的用户也能拿到「试看片段」（前 5-15 分钟）；与 B 站手机版
 * 的「免费试看」体验一致。试看流的 DashStream 字段结构与正片完全相同——可复用
 * [PlayUrlData] / [StreamSelector]，UI 层不需要特殊适配。
 */
@Serializable
data class PgcPlayUrlResponse(
    val code: Int = 0,
    val message: String = "",
    val result: PlayUrlData? = null,
)

fun PgcSeasonItem.toHomeCard(): HomeCard.PgcSeason = HomeCard.PgcSeason(
    seasonId = seasonId,
    title = title,
    coverUrl = cover,
    indexShow = indexShow,
    badge = badge,
)
