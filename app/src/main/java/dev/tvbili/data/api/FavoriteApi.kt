package dev.tvbili.data.api

import dev.tvbili.data.model.FavFolderListResponse
import dev.tvbili.data.model.FavResourceListResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 用户收藏夹接口（需登录态：CookieJar 自动带 SESSDATA）。
 *
 * - [getCreatedFolders]：当前用户创建的全部收藏夹列表
 * - [getFolderResources]：单个收藏夹的资源（视频）分页列表
 *
 * 字段最小化：只解析播放本期所需的 id / title / bvid / cover / duration / upper。
 */
interface FavoriteApi {

    @GET("x/v3/fav/folder/created/list-all")
    suspend fun getCreatedFolders(
        @Query("up_mid") upMid: Long,
        /** type=2 = 视频收藏夹（与 list 接口 type 同义）。 */
        @Query("type") type: Int = 2,
    ): FavFolderListResponse

    @GET("x/v3/fav/resource/list")
    suspend fun getFolderResources(
        @Query("media_id") mediaId: Long,
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 20,
        /** order=mtime 按收藏时间倒序（最新收藏排前）。 */
        @Query("order") order: String = "mtime",
        @Query("type") type: Int = 0,
        @Query("platform") platform: String = "web",
    ): FavResourceListResponse
}
