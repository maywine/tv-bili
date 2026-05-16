package dev.tvbili.data.model

import kotlinx.serialization.Serializable

/** 共享 UP 主结构（推荐 / 热门 / 视频详情等均使用）。 */
@Serializable
data class Owner(
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
)

// --- 推荐流：x/web-interface/wbi/index/top/feed/rcmd （WBI 签名） ---

@Serializable
data class RecommendResponse(
    val code: Int = 0,
    val message: String = "",
    val data: RecommendData? = null,
)

@Serializable
data class RecommendData(
    val item: List<RecommendItem>? = null,
)

@Serializable
data class RecommendItem(
    val id: Long = 0,
    val bvid: String? = null,
    val cid: Long? = null,
    val goto: String? = null, // "av" 普通视频；其他（ad/picture/live）过滤
    val pic: String? = null,
    val title: String? = null,
    val duration: Int? = null,
    val owner: Owner? = null,
    val stat: RecommendStat? = null,
)

@Serializable
data class RecommendStat(
    val view: Long = 0,
    val like: Long = 0,
    val danmaku: Long = 0,
)

// --- 热门：x/web-interface/popular ---

@Serializable
data class PopularResponse(
    val code: Int = 0,
    val message: String = "",
    val data: PopularData? = null,
)

@Serializable
data class PopularData(
    val list: List<PopularItem>? = null,
    val no_more: Boolean = false,
)

@Serializable
data class PopularItem(
    val aid: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    val pic: String = "",
    val title: String = "",
    val duration: Int = 0,
    val owner: Owner = Owner(),
    val stat: PopularStat = PopularStat(),
)

@Serializable
data class PopularStat(
    val view: Int = 0,
    val like: Int = 0,
    val danmaku: Int = 0,
)

// --- 排行榜：x/web-interface/ranking/v2 ---

@Serializable
data class RankingResponse(
    val code: Int = 0,
    val message: String = "",
    val data: RankingData? = null,
)

@Serializable
data class RankingData(
    val list: List<RankingItem>? = null,
    val note: String = "",
)

/**
 * ranking/v2 单项；字段集与 PopularItem 接近，但 stat.view 是 Long（不是 Int），
 * 单独建一份避免改动 PopularStat 影响 Phase 2。
 */
@Serializable
data class RankingItem(
    val aid: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    val pic: String = "",
    val title: String = "",
    val duration: Int = 0,
    val owner: Owner = Owner(),
    val stat: RankingStat = RankingStat(),
)

@Serializable
data class RankingStat(
    val view: Long = 0,
    val like: Long = 0,
    val danmaku: Long = 0,
)
