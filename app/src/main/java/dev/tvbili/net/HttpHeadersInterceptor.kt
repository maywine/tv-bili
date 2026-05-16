package dev.tvbili.net

import okhttp3.Interceptor
import okhttp3.Response

/**
 * 给所有请求加 User-Agent / Origin / Referer。
 *
 * - 默认走 www.bilibili.com / origin
 * - api.live.bilibili.com 走 live.bilibili.com
 * - **WBI 端点（path 含 `/wbi/`）必须 omit Referer**——否则 B 站会返回 412
 *   （参考 BiliPai 注释 + bilibili-API-collect wbi 文档）
 */
class HttpHeadersInterceptor : Interceptor {

    private companion object {
        const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val url = original.url

        val isLive = url.host == "api.live.bilibili.com"
        val referer = if (isLive) "https://live.bilibili.com" else "https://www.bilibili.com"
        val origin = if (isLive) "https://live.bilibili.com" else "https://www.bilibili.com"

        val builder = original.newBuilder()
            .header("User-Agent", UA)
            .header("Origin", origin)

        val isWbiEndpoint = url.encodedPath.contains("/wbi/")
        if (!isWbiEndpoint) {
            builder.header("Referer", referer)
        }

        return chain.proceed(builder.build())
    }
}
