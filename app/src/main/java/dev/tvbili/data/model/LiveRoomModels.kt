package dev.tvbili.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `room/v1/Room/get_info`：房间基本信息。短号会被解析为真实 room_id。
 */
@Serializable
data class LiveRoomInfoResponse(
    val code: Int = 0,
    val message: String = "",
    val data: LiveRoomInfo? = null,
)

@Serializable
data class LiveRoomInfo(
    /** 真实 room_id；短号 → 长号 */
    @SerialName("room_id") val roomId: Long = 0,
    @SerialName("short_id") val shortId: Long = 0,
    val uid: Long = 0,
    val title: String = "",
    @SerialName("area_name") val areaName: String = "",
    @SerialName("parent_area_name") val parentAreaName: String = "",
    /** `0` = 未开播，`1` = 直播中，`2` = 轮播 */
    @SerialName("live_status") val liveStatus: Int = 0,
    val online: Int = 0,
    @SerialName("user_cover") val userCover: String = "",
    val keyframe: String = "",
) {
    val isLiving: Boolean get() = liveStatus == 1
}

/**
 * `xlive/web-room/v2/index/getRoomPlayInfo`：拉流地址 + 当前直播状态。
 *
 * playurl_info.playurl.stream → [].format → [].codec → [].url_info[] + [].base_url
 * url_info[].host 与 base_url 拼接成最终播放 URL。
 *
 * tv-bili 偏好策略：HLS protocol → fmp4 format → AVC codec。降级链：
 *   HLS-fmp4-AVC → HLS-ts-AVC → FLV-AVC → 第一条可用
 */
@Serializable
data class LiveRoomPlayInfoResponse(
    val code: Int = 0,
    val message: String = "",
    val data: LiveRoomPlayInfoData? = null,
)

@Serializable
data class LiveRoomPlayInfoData(
    @SerialName("room_id") val roomId: Long = 0,
    @SerialName("short_id") val shortId: Long = 0,
    val uid: Long = 0,
    /** 0=未开播，1=直播中，2=轮播 */
    @SerialName("live_status") val liveStatus: Int = 0,
    @SerialName("playurl_info") val playurlInfo: LivePlayurlInfo? = null,
)

@Serializable
data class LivePlayurlInfo(
    @SerialName("playurl") val playurl: LivePlayurl? = null,
)

@Serializable
data class LivePlayurl(
    /** 全量 qn 描述表：{qn=10000,"原画"} 等；与 codec.accept_qn 取交集后才是当前可选清晰度。 */
    @SerialName("g_qn_desc") val gQnDesc: List<LiveQnDesc> = emptyList(),
    val stream: List<LiveStream> = emptyList(),
)

@Serializable
data class LiveQnDesc(
    val qn: Int = 0,
    val desc: String = "",
)

@Serializable
data class LiveStream(
    /** "http_hls" / "http_stream" */
    @SerialName("protocol_name") val protocolName: String = "",
    val format: List<LiveStreamFormat> = emptyList(),
)

@Serializable
data class LiveStreamFormat(
    /** "flv" / "ts" / "fmp4" */
    @SerialName("format_name") val formatName: String = "",
    val codec: List<LiveStreamCodec> = emptyList(),
)

@Serializable
data class LiveStreamCodec(
    /** "avc" / "hevc" */
    @SerialName("codec_name") val codecName: String = "",
    /** 当前清晰度 qn */
    @SerialName("current_qn") val currentQn: Int = 0,
    /** 本流支持的全部 qn；UI 切换清晰度时展示的就是这串。 */
    @SerialName("accept_qn") val acceptQn: List<Int> = emptyList(),
    /** 拼接到 baseUrl 前的路径 (含 host) */
    @SerialName("base_url") val baseUrl: String = "",
    @SerialName("url_info") val urlInfo: List<LiveStreamUrlInfo> = emptyList(),
)

@Serializable
data class LiveStreamUrlInfo(
    val host: String = "",
    /** query string，包含 token，必须原样拼上 */
    val extra: String = "",
)

/**
 * `xlive/web-room/v1/index/getDanmuInfo`：弹幕 WebSocket 接入参数。
 */
@Serializable
data class LiveDanmuInfoResponse(
    val code: Int = 0,
    val message: String = "",
    val data: LiveDanmuInfoData? = null,
)

@Serializable
data class LiveDanmuInfoData(
    /** WebSocket 认证 token，原样塞进 auth 包 `key` 字段 */
    val token: String = "",
    @SerialName("host_list") val hostList: List<LiveDanmuHost> = emptyList(),
)

@Serializable
data class LiveDanmuHost(
    val host: String = "",
    /** TCP 端口（不用） */
    val port: Int = 0,
    /** WebSocket 明文端口（不用） */
    @SerialName("ws_port") val wsPort: Int = 0,
    /** WebSocket TLS 端口（用这个） */
    @SerialName("wss_port") val wssPort: Int = 443,
) {
    fun wssUrl(): String = "wss://$host:$wssPort/sub"
}
