package dev.tvbili.data.model

import kotlinx.serialization.Serializable

/**
 * `x/web-interface/nav` 响应。
 * - 用于验证登录态 ([NavData.isLogin]、[NavData.mid]、[NavData.uname])。
 * - WBI 签名 key 来源：[NavData.wbi_img]（[dev.tvbili.net.WbiKeyManager] 解析）。
 */
@Serializable
data class NavResponse(
    val code: Int = 0,
    val message: String = "",
    val data: NavData? = null,
)

@Serializable
data class NavData(
    val isLogin: Boolean = false,
    val uname: String = "",
    val face: String = "",
    val mid: Long = 0,
    /** 大会员状态：0 = 非大会员，1 = 月度，2 = 年度。字段缺失视为 0。 */
    val vipStatus: Int = 0,
    /** 硬币数；服务端可能返回 Double，缺失视为 0。 */
    val money: Double = 0.0,
    val level_info: LevelInfo? = null,
    val wbi_img: WbiImg? = null,
)

@Serializable
data class LevelInfo(
    val current_level: Int = 0,
    val current_exp: Long = 0,
    /** 升下一级需要的经验阈值；满级 6 时服务端返字符串 "--"，这里 nullable 字符串。 */
    val next_exp: kotlinx.serialization.json.JsonElement? = null,
)

@Serializable
data class WbiImg(
    val img_url: String = "",
    val sub_url: String = "",
)
