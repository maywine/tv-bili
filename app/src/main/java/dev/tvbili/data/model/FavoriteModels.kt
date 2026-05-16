package dev.tvbili.data.model

import dev.tvbili.data.repo.HomeCard
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `x/v3/fav/folder/created/list-all` —— 当前用户创建的全部收藏夹（默认+自建）。
 * 接口要求 `up_mid` = 当前用户 mid（来自 [dev.tvbili.data.store.TokenStore]）。
 */
@Serializable
data class FavFolderListResponse(
    val code: Int = 0,
    val message: String = "",
    val data: FavFolderListData? = null,
)

@Serializable
data class FavFolderListData(
    val count: Int = 0,
    val list: List<FavFolder> = emptyList(),
)

@Serializable
data class FavFolder(
    /** 真正的收藏夹 ID（用于 list 接口的 media_id），后端字段名 `id`。 */
    val id: Long = 0,
    /** 默认收藏夹标志：0=自建，1=默认。 */
    val fid: Long = 0,
    val mid: Long = 0,
    val attr: Int = 0,
    val title: String = "",
    /** 视频数量。 */
    @SerialName("media_count")
    val mediaCount: Int = 0,
)

/**
 * `x/v3/fav/resource/list?media_id={fid}&ps=20&pn={n}` —— 收藏夹下的资源列表。
 * tv-bili 只关心可播 av 类型（type=2）。
 */
@Serializable
data class FavResourceListResponse(
    val code: Int = 0,
    val message: String = "",
    val data: FavResourceListData? = null,
)

@Serializable
data class FavResourceListData(
    val info: FavResourceInfo = FavResourceInfo(),
    val medias: List<FavMedia>? = emptyList(),
    @SerialName("has_more")
    val hasMore: Boolean = false,
)

@Serializable
data class FavResourceInfo(
    val id: Long = 0,
    val title: String = "",
    @SerialName("media_count")
    val mediaCount: Int = 0,
)

@Serializable
data class FavMedia(
    /** 类型：2=普通视频；其它跳过（番剧/音乐/专栏等本期不支持播放）。 */
    val type: Int = 0,
    /** 视频 aid 或番剧 sid。 */
    val id: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val cover: String = "",
    val duration: Int = 0,
    val upper: FavUpper = FavUpper(),
    @SerialName("fav_time")
    val favTime: Long = 0,
)

@Serializable
data class FavUpper(
    val mid: Long = 0,
    val name: String = "",
)

fun FavMedia.toHomeCard(): HomeCard.Video = HomeCard.Video(
    aid = id,
    bvid = bvid,
    title = title,
    coverUrl = cover,
    uploader = upper.name,
    durationSec = duration,
    viewCount = 0,
)
