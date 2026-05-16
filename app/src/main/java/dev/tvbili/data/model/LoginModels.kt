package dev.tvbili.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * TV 端二维码登录响应模型。
 *
 * 申请二维码：`POST passport-tv-login/qrcode/auth_code`
 * 轮询登录态：`POST passport-tv-login/qrcode/poll`
 *
 * 关注的 [TvPollResponse.code]：
 * - 0 → 登录成功，[TvPollData] 含 accessToken / refreshToken / mid / cookieInfo
 * - 86039 → 尚未确认（等待用户在手机上确认）
 * - 86090 → 已扫码待确认
 * - 86038 → 二维码已过期
 */
@Serializable
data class TvQrCodeResponse(
    val code: Int = 0,
    val message: String = "",
    val data: TvQrData? = null,
)

@Serializable
data class TvQrData(
    val url: String? = null,
    @SerialName("auth_code") val authCode: String? = null,
)

@Serializable
data class TvPollResponse(
    val code: Int = 0,
    val message: String = "",
    val data: TvPollData? = null,
)

@Serializable
data class TvPollData(
    val mid: Long = 0,
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("expires_in") val expiresIn: Long = 0,
    @SerialName("cookie_info") val cookieInfo: TvCookieInfo? = null,
)

@Serializable
data class TvCookieInfo(
    val cookies: List<TvCookie> = emptyList(),
)

@Serializable
data class TvCookie(
    val name: String = "",
    val value: String = "",
)

/** Phase 4 access_token 刷新接口响应。 */
@Serializable
data class TvTokenRefreshResponse(
    val code: Int = 0,
    val message: String = "",
    val data: TvTokenRefreshData? = null,
)

@Serializable
data class TvTokenRefreshData(
    val mid: Long = 0,
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("expires_in") val expiresIn: Long = 0,
    @SerialName("cookie_info") val cookieInfo: TvCookieInfo? = null,
)
