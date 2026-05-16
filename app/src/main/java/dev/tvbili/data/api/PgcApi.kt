package dev.tvbili.data.api

import dev.tvbili.data.model.PgcIndexResponse
import dev.tvbili.data.model.PgcPlayUrlResponse
import dev.tvbili.data.model.PgcRankResponse
import dev.tvbili.data.model.PgcSeasonDetailResponse
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.QueryMap

/**
 * PGC（番剧 / 电影 / 综艺 / 国创 / 电视剧 / 纪录片）接口。
 *
 * - [getSeasonIndex]：按 season_type 拉某类 PGC 的季度排行；用于首页 VARIETY 等分区列表
 * - [getSeasonDetail]：拉单个 season 的剧集 bvid 列表；点 PgcSeason 卡时解析最新一集 bvid
 */
interface PgcApi {

    /**
     * 参数集对齐 B 站 web 端实际请求——`pagesize` 而非 `page_size`，且需要 `st`、
     * `sort`、`season_status`、`type` 等参数齐全，否则可能返回空 list / 接口报错。
     * 默认值都是 -1 / 0 表示「不筛选」，等价于「全部综艺按热度」。
     */
    @GET("pgc/season/index/result")
    suspend fun getSeasonIndex(
        @Query("season_type") seasonType: Int,
        /** st 是 season_type 的别名，B 站两个字段都要带。 */
        @Query("st") st: Int = seasonType,
        @Query("page") page: Int = 1,
        @Query("pagesize") pageSize: Int = 20,
        /** 2=按热度（默认）/ 0=按更新时间 / 3=按追番人数。 */
        @Query("order") order: Int = 2,
        /** 0=desc 1=asc。 */
        @Query("sort") sort: Int = 0,
        /** 1=按单字段排序；type=0 是按 ep_id 排，不适合 grid。 */
        @Query("type") type: Int = 1,
        @Query("style_id") styleId: Int = -1,
        @Query("season_status") seasonStatus: Int = -1,
        @Query("copyright") copyright: Int = -1,
        @Query("season_version") seasonVersion: Int = -1,
        @Query("spoken_language_type") spokenLanguageType: Int = -1,
        @Query("area") area: Int = -1,
        @Query("is_finish") isFinish: Int = -1,
        @Query("year") year: Int = -1,
    ): PgcIndexResponse

    /**
     * 热门 PGC 排行（综艺/番剧/电影各 season_type 通用）。新版 web 端口。
     * @param day 3 = 3 天榜（默认） / 7 = 周榜
     */
    @GET("pgc/web/rank/list")
    suspend fun getRank(
        @Query("season_type") seasonType: Int,
        @Query("day") day: Int = 3,
    ): PgcRankResponse

    /**
     * 老版排行端点——比 [getRank] 历史更久、对匿名 / 弱 Cookie 请求更宽容；
     * 当 [getRank] 没数据时降级用它再试一次。
     */
    @GET("pgc/season/rank/list")
    suspend fun getLegacyRank(
        @Query("season_type") seasonType: Int,
        @Query("day") day: Int = 3,
    ): PgcRankResponse

    @GET("pgc/view/web/season")
    suspend fun getSeasonDetail(@Query("season_id") seasonId: Long): PgcSeasonDetailResponse

    /**
     * PGC 播放地址。**没大会员时配合 `try_look=1` 拿试看片段**——B 站手机版即此体验。
     * 不走 WBI 签名；调用方按 [dev.tvbili.data.repo.VideoRepository.loadPgcPlayUrl] 拼参数。
     */
    @GET("pgc/player/web/playurl")
    suspend fun getPgcPlayUrl(@QueryMap params: Map<String, String>): PgcPlayUrlResponse
}
