package dev.tvbili.ui.live

import org.json.JSONArray
import org.json.JSONObject

/**
 * 直播弹幕消息（仅 DANMU_MSG 的文本部分）。
 *
 * 礼物 / 进场 / 上舰 / Super Chat 暂不渲染——TV 端追求清爽，先把最常见的滚动弹幕做透。
 */
data class LiveDanmakuMessage(
    val text: String,
    /** 24-bit RGB；服务端给 0 表示默认白 */
    val color: Int,
    val uname: String,
)

/**
 * 把 OP_MESSAGE 包体 (UTF-8 JSON) 解析成 [LiveDanmakuMessage]，非 DANMU_MSG 返回 null。
 *
 * DANMU_MSG payload 形状：
 * ```json
 * {
 *   "cmd": "DANMU_MSG",
 *   "info": [
 *     [_, _, _, _, _, _, _, "<color hex>"...],   // info[0] 元信息，[3] 是颜色 int
 *     "弹幕文字",                                 // info[1] = 文本
 *     [uid, "uname", ...]                         // info[2][1] = 用户名
 *     ...
 *   ]
 * }
 * ```
 *
 * 颜色字段位置版本差异较大；为稳健起见 fallback 到白色。
 */
object LiveDanmakuMessageParser {

    fun parse(jsonBytes: ByteArray): LiveDanmakuMessage? = runCatching {
        val root = JSONObject(String(jsonBytes, Charsets.UTF_8))
        if (root.optString("cmd") != "DANMU_MSG") return null
        val info: JSONArray = root.optJSONArray("info") ?: return null
        if (info.length() < 3) return null

        val text = info.optString(1).ifBlank { return null }
        val color = runCatching {
            val meta = info.optJSONArray(0)
            // info[0][3] 历史上是颜色 (int)；防御性兜底 0xFFFFFF
            meta?.optInt(3, 0xFFFFFF) ?: 0xFFFFFF
        }.getOrDefault(0xFFFFFF).let { if (it == 0) 0xFFFFFF else it }

        val uname = runCatching {
            info.optJSONArray(2)?.optString(1).orEmpty()
        }.getOrDefault("")

        LiveDanmakuMessage(text = text, color = color, uname = uname)
    }.getOrNull()
}
