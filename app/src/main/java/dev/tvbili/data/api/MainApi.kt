package dev.tvbili.data.api

import dev.tvbili.data.model.NavResponse
import dev.tvbili.data.model.PopularResponse
import dev.tvbili.data.model.RankingResponse
import dev.tvbili.data.model.RecommendResponse
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.QueryMap

/**
 * 主站接口（baseUrl = https://api.bilibili.com/）。
 *
 * - [getNavInfo]：验证登录态 + 取 wbi_img keys
 * - [getRecommend]：推荐流（WBI 签名；path 含 /wbi/，HttpHeadersInterceptor 自动 omit Referer）
 * - [getPopular]：热门列表（无签名）
 * - [getRanking]：排行榜（无签名；rid=0 全站，其他按分区 ID）
 */
interface MainApi {

    @GET("x/web-interface/nav")
    suspend fun getNavInfo(): NavResponse

    /** WBI 签名端点；调用前先 `WbiUtils.sign(params, imgKey, subKey)`。 */
    @GET("x/web-interface/wbi/index/top/feed/rcmd")
    suspend fun getRecommend(@QueryMap signed: Map<String, String>): RecommendResponse

    @GET("x/web-interface/popular")
    suspend fun getPopular(
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 20,
    ): PopularResponse

    /** 排行榜：rid=0 全站；其他参考 video_zone 文档（13=番剧 / 23=电影 / 1=动画 / 4=游戏 / 3=音乐 / 36=科技）。 */
    @GET("x/web-interface/ranking/v2")
    suspend fun getRanking(
        @Query("rid") rid: Int = 0,
        @Query("type") type: String = "all",
    ): RankingResponse
}
