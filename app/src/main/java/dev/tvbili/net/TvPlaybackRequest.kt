package dev.tvbili.net

import dev.tvbili.data.model.PgcPlayback

internal fun tvPlaybackParams(pgc: PgcPlayback, quality: Int, accessToken: String?, timestamp: Long): Map<String, String> {
    require(pgc.seasonId > 0 && pgc.episodeId > 0 && pgc.detail.cid > 0) { "节目播放信息不完整" }
    return AppSignUtils.signForTvApi(buildMap {
        put("appkey", AppSignUtils.TV_APP_KEY)
        // 接口协议与云视听小电视 1.8.8 对齐；这不是 tv-bili 自身的版本号。
        put("build", "108800")
        put("mobi_app", "android_tv_yst")
        put("platform", "android")
        put("channel", "master")
        put("device_name", "android")
        put("ts", timestamp.toString())
        put("playurl_type", "2")
        put("object_id", pgc.episodeId.toString())
        put("season_id", pgc.seasonId.toString())
        put("cid", pgc.detail.cid.toString())
        put("ogv_aid", pgc.detail.aid.toString())
        put("qn", quality.toString())
        put("fnval", "4048")
        put("fnver", "0")
        put("fourk", "1")
        if (!accessToken.isNullOrBlank()) put("access_key", accessToken)
    })
}
