package dev.tvbili.data.model

import kotlinx.serialization.Serializable

/**
 * `x/web-interface/view?bvid=...` 响应（slim 版）。
 *
 * tv-bili 只关心：cid（playurl 必填）+ title + owner.name + duration + pic。
 * BiliPai 的 ViewInfo 含 30+ 字段——本期全部裁掉。
 */
@Serializable
data class VideoDetailResponse(
    val code: Int = 0,
    val message: String = "",
    val data: VideoDetail? = null,
)

@Serializable
data class VideoDetail(
    val bvid: String = "",
    val aid: Long = 0,
    val cid: Long = 0,
    val title: String = "",
    val pic: String = "",
    val duration: Int = 0,
    val owner: Owner = Owner(),
    /** 多 P 视频的分 P 列表；单 P 视频通常长度 1（cid == 顶层 cid）。 */
    val pages: List<VideoPage> = emptyList(),
)

/** `x/web-interface/view` 响应中的分 P 节点。tv-bili 只关心 cid + part 标题 + 时长。 */
@Serializable
data class VideoPage(
    val cid: Long = 0,
    /** 分 P 序号（1-based）。 */
    val page: Int = 0,
    /** 分 P 标题，如「第 1 集」。 */
    val part: String = "",
    /** 该分 P 时长，秒。 */
    val duration: Int = 0,
)

/**
 * `x/web-interface/archive/related?bvid=...` —— 当前视频的相关推荐。
 * 用于「单 P 看完 / 多 P 末 P 看完」时自动续播下一个视频。
 */
@Serializable
data class RelatedResponse(
    val code: Int = 0,
    val message: String = "",
    val data: List<RelatedVideoItem> = emptyList(),
)

@Serializable
data class RelatedVideoItem(
    val aid: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    val title: String = "",
    val pic: String = "",
    val duration: Int = 0,
    val owner: Owner = Owner(),
)
