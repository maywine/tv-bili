package dev.tvbili.data.api

import dev.tvbili.data.model.AppPollResponse
import dev.tvbili.data.model.AppQrCodeResponse
import dev.tvbili.data.model.AppTokenRefreshResponse
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * Passport（登录）接口。
 *
 * 所有路径写完整 URL —— HD 客户端登录走 `passport.bilibili.com`，与 baseUrl 无关。
 * `@FormUrlEncoded` + `@FieldMap` 组合发送 form body；缺 `@FormUrlEncoded` 会 400。
 *
 * 三个接口的 params 都需要先经过 [dev.tvbili.net.AppSignUtils.signForHdApi] 签名。
 */
interface PassportApi {

    /** HD 客户端申请二维码，返回 url + auth_code。 */
    @FormUrlEncoded
    @POST("https://passport.bilibili.com/x/passport-tv-login/qrcode/auth_code")
    suspend fun generateAppQrCode(@FieldMap params: Map<String, String>): AppQrCodeResponse

    /** HD 客户端轮询登录态。code = 0 时返回 access_token + cookies。 */
    @FormUrlEncoded
    @POST("https://passport.bilibili.com/x/passport-tv-login/qrcode/poll")
    suspend fun pollAppQrCode(@FieldMap params: Map<String, String>): AppPollResponse

    /** Phase 4 access_token 失效时调用。 */
    @FormUrlEncoded
    @POST("https://passport.bilibili.com/x/passport-login/oauth2/refresh_token")
    suspend fun refreshToken(@FieldMap params: Map<String, String>): AppTokenRefreshResponse
}
