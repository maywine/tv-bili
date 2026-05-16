package dev.tvbili.data.api

import dev.tvbili.data.model.LiveDanmuInfoResponse
import dev.tvbili.data.model.LiveResponse
import dev.tvbili.data.model.LiveRoomInfoResponse
import dev.tvbili.data.model.LiveRoomPlayInfoResponse
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.QueryMap

/**
 * 直播相关接口（baseUrl 不重要——所有端点都写全 URL）。
 */
interface LiveApi {

    /** 全站直播间列表（按 online 排序）。 */
    @GET("https://api.live.bilibili.com/room/v3/area/getRoomList")
    suspend fun getLiveList(
        @Query("parent_area_id") parentAreaId: Int = 0,
        @Query("area_id") areaId: Int = 0,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 30,
        @Query("sort_type") sortType: String = "online",
    ): LiveResponse

    /**
     * 房间基本信息（标题、主播 UID、真实 room_id——短号会回 [LiveRoomInfo.roomId]）。
     */
    @GET("https://api.live.bilibili.com/room/v1/Room/get_info")
    suspend fun getRoomInfo(
        @Query("room_id") roomId: Long,
    ): LiveRoomInfoResponse

    /**
     * 直播拉流地址。
     *
     * @param protocol `0` = HTTP-FLV，`1` = HTTP-HLS。tv-bili 同时请求两种，优先用 HLS（更
     *   稳，盒子支持好）；HLS 不返时降级到 FLV。
     * @param format `0` = flv，`1` = ts，`2` = fmp4。
     * @param codec `0` = AVC，`1` = HEVC。盒子优先 AVC。
     * @param qn 清晰度（10000=原画，400=蓝光，250=超清，150=高清，80=流畅）。
     * @param platform 必须 `web` 或 `h5`，给 tv 端用 `web` 兼容性最好。
     */
    @GET("https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo")
    suspend fun getRoomPlayInfo(
        @Query("room_id") roomId: Long,
        @Query("protocol") protocol: String = "0,1",
        @Query("format") format: String = "0,1,2",
        @Query("codec") codec: String = "0,1",
        @Query("qn") qn: Int = 10000,
        @Query("platform") platform: String = "web",
        @Query("ptype") ptype: Int = 8,
    ): LiveRoomPlayInfoResponse

    /**
     * 弹幕 WebSocket 接入信息：token + 备用 host 列表。
     *
     * 自 2024 起该端点已纳入 WBI 风控；不签 `wts`/`w_rid` 直接返回 -352。
     * 调用方必须先用 [dev.tvbili.net.WbiUtils.sign] 拼好参数（至少包含
     * `id`、`type`、`web_location`），再走这里。
     */
    @GET("https://api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo")
    suspend fun getDanmuInfo(
        @QueryMap signed: Map<String, String>,
    ): LiveDanmuInfoResponse
}
