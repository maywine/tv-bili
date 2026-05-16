package dev.tvbili.data.api

import dev.tvbili.data.model.PlayUrlResponse
import dev.tvbili.data.model.VideoDetailResponse
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap
import retrofit2.http.Streaming

/**
 * 视频详情 + 播放地址 + 弹幕 XML。
 *
 * - [getVideoDetail]：`bvid` 唯一参数，无签名
 * - [getPlayUrl]：WBI 签名（path 含 /wbi/，HttpHeadersInterceptor 自动 omit Referer）
 * - [getDanmakuXml]：comment.bilibili.com 上的 XML 弹幕；OkHttp 自动解 Content-Encoding: deflate
 *   返回 [ResponseBody] 让调用方拿 byteStream() 直接喂 DanmakuFlameMaster
 *   @Streaming 注解：让 Retrofit 不要把整个 body 缓到内存
 */
interface VideoApi {

    @GET("x/web-interface/view")
    suspend fun getVideoDetail(@Query("bvid") bvid: String): VideoDetailResponse

    /** WBI 签名端点；调用前先 `WbiUtils.sign(params, imgKey, subKey)`。 */
    @GET("x/player/wbi/playurl")
    suspend fun getPlayUrl(@QueryMap signed: Map<String, String>): PlayUrlResponse

    /** 弹幕 XML 端点；返回 deflate 压缩 XML，OkHttp 自动解压。 */
    @Streaming
    @GET("https://comment.bilibili.com/{cid}.xml")
    suspend fun getDanmakuXml(@Path("cid") cid: Long): ResponseBody
}
