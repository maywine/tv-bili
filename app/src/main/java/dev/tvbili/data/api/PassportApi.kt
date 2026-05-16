package dev.tvbili.data.api

import dev.tvbili.data.model.TvPollResponse
import dev.tvbili.data.model.TvQrCodeResponse
import dev.tvbili.data.model.TvTokenRefreshResponse
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * Passport（登录）接口。
 *
 * 所有路径写完整 URL —— TV 端登录走 `passport.bilibili.com`，与 baseUrl 无关。
 * `@FormUrlEncoded` + `@FieldMap` 组合发送 form body；缺 `@FormUrlEncoded` 会 400。
 *
 * 三个接口的 params 都需要先经过 [dev.tvbili.net.AppSignUtils.signForTvLogin] 签名。
 */
interface PassportApi {

    /** TV 端申请二维码，返回 url + auth_code。 */
    @FormUrlEncoded
    @POST("https://passport.bilibili.com/x/passport-tv-login/qrcode/auth_code")
    suspend fun generateTvQrCode(@FieldMap params: Map<String, String>): TvQrCodeResponse

    /** TV 端轮询登录态。code = 0 时返回 access_token + cookies。 */
    @FormUrlEncoded
    @POST("https://passport.bilibili.com/x/passport-tv-login/qrcode/poll")
    suspend fun pollTvQrCode(@FieldMap params: Map<String, String>): TvPollResponse

    /** Phase 4 access_token 失效时调用。 */
    @FormUrlEncoded
    @POST("https://passport.bilibili.com/x/passport-tv-login/h5/refresh")
    suspend fun refreshToken(@FieldMap params: Map<String, String>): TvTokenRefreshResponse
}
