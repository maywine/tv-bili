package dev.tvbili.net

import java.security.MessageDigest

/**
 * APP 签名工具。
 * - 算法：sorted query string + appsec → MD5 → 作为 `sign` 字段加入参数。
 * - 用途：TV 端登录 (passport-tv-login/...) 与 Android APP 端 API（如 playurl，Phase 4）。
 *
 * 移植自 BiliPai `core/network/AppSignUtils.kt`。
 * 参考：https://socialsisteryi.github.io/bilibili-API-collect/docs/misc/sign/APPKey.html
 *
 * 注意：appkey/appsec 是 B 站公开的客户端凭据，不属于密钥。
 */
object AppSignUtils {

    /** TV 端 appkey/appsec（云视听小电视）。 */
    const val TV_APP_KEY = "4409e2ce8ffd12b8"
    private const val TV_APP_SEC = "59b43e04ad6965f34319062b478f83dd"

    /** Android 客户端 appkey/appsec（playurl 等高画质接口用，Phase 4 引入）。 */
    const val ANDROID_APP_KEY = "1d8b6e7d45233436"
    private const val ANDROID_APP_SEC = "560c52ccd288fed045859ed18bffd973"

    fun signForTvLogin(params: Map<String, String>): Map<String, String> =
        sign(params, TV_APP_SEC)

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
