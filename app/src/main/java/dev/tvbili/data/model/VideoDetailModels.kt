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
)
