package dev.tvbili.data.repo

import dev.tvbili.data.model.LiveDanmuInfoData
import dev.tvbili.data.model.LiveRoomInfo
import dev.tvbili.data.model.LiveRoomPlayInfoData
import dev.tvbili.net.NetworkModule
import dev.tvbili.net.WbiKeyManager
import dev.tvbili.net.WbiUtils

/**
 * 直播间数据：room info → playurl → danmu auth。
 *
 * 调用顺序固定：先 [getRoomInfo] 解析短号→真 room_id，再用真 room_id 取
 * [getPlayInfo] 与 [getDanmuInfo]。短号直接拿去后两个接口会 -400。
 */
class LiveRoomRepository {

    suspend fun getRoomInfo(roomId: Long): Result<LiveRoomInfo> = runCatching {
        val resp = NetworkModule.liveApi.getRoomInfo(roomId)
        require(resp.code == 0) { "room/get_info code=${resp.code} msg=${resp.message}" }
        checkNotNull(resp.data) { "room/get_info data null roomId=$roomId" }
    }

    suspend fun getPlayInfo(realRoomId: Long, qn: Int = 10000): Result<LiveRoomPlayInfoData> = runCatching {
        val resp = NetworkModule.liveApi.getRoomPlayInfo(realRoomId, qn = qn)
        require(resp.code == 0) { "getRoomPlayInfo code=${resp.code} msg=${resp.message}" }
        checkNotNull(resp.data) { "getRoomPlayInfo data null roomId=$realRoomId" }
    }

    suspend fun getDanmuInfo(realRoomId: Long): Result<LiveDanmuInfoData> = runCatching {
        val (img, sub) = WbiKeyManager.getKeys().getOrThrow()
        val raw = mapOf(
            "id" to realRoomId.toString(),
            "type" to "0",
            "web_location" to "444.8",
        )
        val signed = WbiUtils.sign(raw, img, sub)
        val resp = NetworkModule.liveApi.getDanmuInfo(signed)
        require(resp.code == 0) { "getDanmuInfo code=${resp.code} msg=${resp.message}" }
        checkNotNull(resp.data) { "getDanmuInfo data null roomId=$realRoomId" }
    }
}
