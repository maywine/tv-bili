package dev.tvbili.net

import dev.tvbili.data.model.PgcPlayback

internal fun hdApiParams(
    params: Map<String, String>,
    accessToken: String?,
    tokenAppKey: String?,
    timestamp: Long,
): Map<String, String> = AppSignUtils.signForHdApi(buildMap {
    putAll(params)
    put("appkey", AppSignUtils.HD_APP_KEY)
    // 对齐 HD 2.0.1 的客户端协议版本，与 tv-bili 的版本号无关。
    put("build", "2001100")
    put("mobi_app", "android_hd")
    put("platform", "android")
    put("channel", "master")
    put("ts", timestamp.toString())
    remove("access_key")
    // access_token 与签发它的 appkey 绑定，不能把旧 TV 令牌换个签名继续使用。
    if (isHdSession(accessToken, tokenAppKey)) put("access_key", requireNotNull(accessToken))
})

internal fun isHdSession(accessToken: String?, tokenAppKey: String?): Boolean =
    !accessToken.isNullOrBlank() && tokenAppKey == AppSignUtils.HD_APP_KEY

internal fun hdPlaybackParams(
    pgc: PgcPlayback,
    quality: Int,
    accessToken: String?,
    tokenAppKey: String?,
    timestamp: Long,
): Map<String, String> {
    require(pgc.seasonId > 0 && pgc.episodeId > 0 && pgc.detail.cid > 0) { "节目播放信息不完整" }
    return hdApiParams(
        mapOf(
            "ep_id" to pgc.episodeId.toString(),
            "cid" to pgc.detail.cid.toString(),
            "qn" to quality.toString(),
            "fnval" to "4048",
            "fnver" to "0",
            "fourk" to "1",
            "otype" to "json",
        ),
        accessToken, tokenAppKey, timestamp,
    )
}
