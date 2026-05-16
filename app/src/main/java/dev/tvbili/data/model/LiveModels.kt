package dev.tvbili.data.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * 直播列表接口响应：`api.live.bilibili.com/room/v3/area/getRoomList`。
 *
 * 接口在不同入口返回字段名略有差异：
 * - `list` vs `list_by_area`：用 [LiveData.getAllRooms] 统一拿
 * - `roomid` vs `room_id`：用 [JsonNames] 同字段接受多名
 * - 封面字段：[LiveRoom.displayCover] 按 cover / user_cover / system_cover / keyframe 顺序兜底
 */
@Serializable
data class LiveResponse(
    val code: Int = 0,
    val message: String = "",
    val data: LiveData? = null,
)

@Serializable
data class LiveData(
    val list: List<LiveRoom>? = null,
    @SerialName("list_by_area") val listByArea: List<LiveRoom>? = null,
    val count: Int = 0,
    @SerialName("has_more") val hasMore: Int = 0,
) {
    fun getAllRooms(): List<LiveRoom> = list ?: listByArea ?: emptyList()
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class LiveRoom(
    @JsonNames("roomid", "room_id")
    val roomid: Long = 0,
    val uid: Long = 0,
    val title: String = "",
    val uname: String = "",
    val face: String = "",
    val cover: String = "",
    @SerialName("user_cover") val userCover: String = "",
    @SerialName("system_cover") val systemCover: String = "",
    val keyframe: String = "",
    @JsonNames("area_name", "area_v2_name")
    @SerialName("area_name")
    val areaName: String = "",
    val online: Int = 0,
) {
    fun displayCover(): String =
        listOf(cover, userCover, systemCover, keyframe)
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
}
