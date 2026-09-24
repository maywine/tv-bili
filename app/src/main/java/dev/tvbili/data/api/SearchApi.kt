package dev.tvbili.data.api

import dev.tvbili.data.model.SearchResponse
import dev.tvbili.data.model.TvPgcSearchResponse
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.QueryMap
import retrofit2.http.Query

/**
 * 搜索端点 —— 走 WBI 签名 + `search.bilibili.com` 域名特定 Header。
 *
 * 端点：`api.bilibili.com/x/web-interface/wbi/search/all/v2`
 * - WBI 签名（路径含 /wbi/，[dev.tvbili.net.HttpHeadersInterceptor] 自动 omit Referer）
 * - **Origin / Referer 必须指向 `search.bilibili.com`**：与 MainApi 共用的 Referer
 *   `https://www.bilibili.com` 在此接口会触发 -412 风控
 */
interface SearchApi {

    @GET("x/tv/search/v2")
    suspend fun searchTvPgc(
        @Query("keyword") keyword: String,
        @Query("page") page: Int = 1,
        @Query("category") category: Int,
        @Query("search_type") searchType: String = "tv_pgc",
        @Query("order") order: String = "totalrank",
        @Query("pagesize") pageSize: Int = 20,
    ): TvPgcSearchResponse

    @Headers(
        "Origin: https://search.bilibili.com",
        "Referer: https://search.bilibili.com/",
    )
    @GET("x/web-interface/wbi/search/all/v2")
    suspend fun searchAll(@QueryMap signed: Map<String, String>): SearchResponse
}
