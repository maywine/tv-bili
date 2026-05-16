package dev.tvbili.data.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * `x/player/wbi/playurl` 响应。
 *
 * - `accept_quality` + `accept_description` 提供清晰度 picker 数据源
 * - `dash.video[]` + `dash.audio[]` 是真正的播放流（MP4 segmented，可 ProgressiveMediaSource 拉）
 * - 不解析 `durl` / `dolby` / `flac`（YAGNI；只走 DASH）
 */
@Serializable
data class PlayUrlResponse(
    val code: Int = 0,
    val message: String = "",
    val data: PlayUrlData? = null,
)

@Serializable
data class PlayUrlData(
    val quality: Int = 0,
    val timelength: Long = 0,
    @SerialName("accept_quality")
    val acceptQuality: List<Int> = emptyList(),
    @SerialName("accept_description")
    val acceptDescription: List<String> = emptyList(),
    val dash: Dash? = null,
)

@Serializable
data class Dash(
    val duration: Int = 0,
    val video: List<DashStream> = emptyList(),
    val audio: List<DashStream>? = null,
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class DashStream(
    val id: Int = 0, // 视频流：等于 qn (如 80=1080P)；音频流：30216/30232/30280 等
    @JsonNames("baseUrl", "base_url")
    val baseUrl: String = "",
    @JsonNames("backupUrl", "backup_url")
    val backupUrl: List<String>? = null,
    val bandwidth: Int = 0,
    val codecs: String = "",
    val width: Int = 0,
    val height: Int = 0,
    @JsonNames("mimeType", "mime_type")
    val mimeType: String = "",
    val codecid: Int = 0, // 7=AVC/H.264, 12=HEVC/H.265, 13=AV1
) {
    fun validUrl(): String =
        baseUrl.takeIf { it.isNotBlank() }
            ?: backupUrl?.firstOrNull { it.isNotBlank() }
            ?: ""
}
