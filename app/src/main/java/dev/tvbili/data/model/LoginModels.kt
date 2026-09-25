package dev.tvbili.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * HD 客户端二维码登录响应模型。
 *
 * 申请二维码：`POST passport-tv-login/qrcode/auth_code`
 * 轮询登录态：`POST passport-tv-login/qrcode/poll`
 *
 * 关注的 [AppPollResponse.code]：
 * - 0 → 登录成功，[AppPollData] 含 accessToken / refreshToken / mid / cookieInfo
 * - 86039 → 尚未确认（等待用户在手机上确认）
 * - 86090 → 已扫码待确认
 * - 86038 → 二维码已过期
 */
@Serializable
data class AppQrCodeResponse(
    val code: Int = 0,
    val message: String = "",
    val data: AppQrData? = null,
)

@Serializable
data class AppQrData(
    val url: String? = null,
    @SerialName("auth_code") val authCode: String? = null,
)

@Serializable
data class AppPollResponse(
    val code: Int = 0,
    val message: String = "",
    val data: AppPollData? = null,
)

@Serializable
data class AppPollData(
    @SerialName("token_info") val tokenInfo: AppTokenInfo? = null,
    val mid: Long = 0,
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("expires_in") val expiresIn: Long = 0,
    @SerialName("cookie_info") val cookieInfo: AppCookieInfo? = null,
) {
    val sessionAccessToken: String get() = tokenInfo?.accessToken?.takeIf(String::isNotBlank) ?: accessToken
    val sessionRefreshToken: String get() = tokenInfo?.refreshToken?.takeIf(String::isNotBlank) ?: refreshToken
    val sessionMid: Long get() = tokenInfo?.mid?.takeIf { it > 0 } ?: mid
}

@Serializable
data class AppCookieInfo(
    val cookies: List<AppCookie> = emptyList(),
)

@Serializable
data class AppCookie(
    val name: String = "",
    val value: String = "",
)

/** Phase 4 access_token 刷新接口响应。 */
@Serializable
data class AppTokenRefreshResponse(
    val code: Int = 0,
    val message: String = "",
    val data: AppTokenRefreshData? = null,
)

@Serializable
data class AppTokenRefreshData(
    val mid: Long = 0,
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("expires_in") val expiresIn: Long = 0,
    @SerialName("cookie_info") val cookieInfo: AppCookieInfo? = null,
)

@Serializable
data class AppTokenInfo(
    val mid: Long = 0,
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
)
