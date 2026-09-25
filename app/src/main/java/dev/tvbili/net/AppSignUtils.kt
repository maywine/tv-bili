package dev.tvbili.net

import java.security.MessageDigest
import java.net.URLEncoder

/**
 * APP 签名工具。
 * - 算法：sorted query string + appsec → MD5 → 作为 `sign` 字段加入参数。
 * - 用途：HD 扫码登录、片库、搜索与手机播放接口。
 *
 * 移植自 BiliPai `core/network/AppSignUtils.kt`。
 * 参考：https://socialsisteryi.github.io/bilibili-API-collect/docs/misc/sign/APPKey.html
 *
 * 注意：appkey/appsec 是 B 站公开的客户端凭据，不属于密钥。
 */
object AppSignUtils {

    /** 哔哩哔哩 HD 的公开客户端凭据，和用户登录令牌不是同一种凭据。 */
    const val HD_APP_KEY = "dfca71928277209b"
    private const val HD_APP_SEC = "b5475a8825547a4fc26c7d518eaaa02e"

    /** Android 客户端 appkey/appsec（playurl 等高画质接口用，Phase 4 引入）。 */
    const val ANDROID_APP_KEY = "1d8b6e7d45233436"
    private const val ANDROID_APP_SEC = "560c52ccd288fed045859ed18bffd973"

    fun signForHdApi(params: Map<String, String>): Map<String, String> {
        val sorted = params.toSortedMap()
        val query = sorted.entries.joinToString("&") {
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
        }
        return sorted + ("sign" to md5(query + HD_APP_SEC))
    }

    fun signForAndroidApi(params: Map<String, String>): Map<String, String> =
        sign(params, ANDROID_APP_SEC)

    fun getTimestamp(): Long = System.currentTimeMillis() / 1000

    private fun sign(params: Map<String, String>, appSec: String): Map<String, String> {
        val sorted = params.toSortedMap()
        val q = sorted.entries.joinToString("&") { "${it.key}=${it.value}" }
        val signature = md5(q + appSec)
        return sorted + ("sign" to signature)
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
