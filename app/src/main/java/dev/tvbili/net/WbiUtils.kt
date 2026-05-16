package dev.tvbili.net

import java.net.URLEncoder
import java.security.MessageDigest

/**
 * WBI 签名工具。算法移植自 BiliPai `core/network/WbiUtils.kt`。
 *
 * 用法：
 * ```
 * val (img, sub) = WbiKeyManager.getKeys().getOrThrow()
 * val signed = WbiUtils.sign(rawParams, img, sub)
 * api.someWbiEndpoint(signed)  // signed map 含 wts + w_rid
 * ```
 *
 * 参考：https://socialsisteryi.github.io/bilibili-API-collect/docs/misc/sign/wbi.html
 */
object WbiUtils {

    private val mixinKeyEncTab = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
        33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40,
        61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11,
        36, 20, 34, 44, 52,
    )

    private fun getMixinKey(orig: String): String {
        val sb = StringBuilder()
        for (i in mixinKeyEncTab) {
            if (i < orig.length) sb.append(orig[i])
        }
        return sb.toString().substring(0, 32)
    }

    /** 过滤 Bilibili 不允许的字符（与官方 demo 一致）。 */
    private fun filterIllegalChars(value: String): String {
        return value.replace(Regex("[!'()*]"), "")
    }

    /**
     * 标准化 URL 编码（仅用于计算签名，最终请求时由 Retrofit/OkHttp 再编码）。
     */
    private fun encodeURIComponent(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")
    }

    private fun md5(str: String): String {
        return MessageDigest.getInstance("MD5").digest(str.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * 计算 WBI 签名。
     *
     * 返回的 Map 中 value 保持原始状态（仅做 [filterIllegalChars] 过滤），未做 URL 编码，
     * 让 Retrofit 去做最终编码。签名计算时使用 URL 编码后的值。
     */
    fun sign(
        params: Map<String, String>,
        imgKey: String,
        subKey: String,
    ): Map<String, String> {
        val mixinKey = getMixinKey(imgKey + subKey)
        val wts = System.currentTimeMillis() / 1000

        val raw = mutableMapOf<String, String>()
        for ((k, v) in params) {
            raw[k] = filterIllegalChars(v)
        }
        raw["wts"] = wts.toString()

        val sortedKeys = raw.keys.sorted()
        val q = StringBuilder()
        for (k in sortedKeys) {
            val ev = encodeURIComponent(raw.getValue(k))
            if (q.isNotEmpty()) q.append("&")
            q.append(k).append("=").append(ev)
        }
        raw["w_rid"] = md5(q.toString() + mixinKey)
        return raw
    }
}
