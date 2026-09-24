package dev.tvbili.net

import okhttp3.Interceptor
import okhttp3.Response

/** 电视 CDN 会拒绝网页版请求头；必须在通用拦截器之后应用。 */
class TvMediaHeadersInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(
        chain.request().newBuilder()
            .header("User-Agent", "Mozilla/5.0 BiliTV/1.8.8")
            .removeHeader("Referer")
            .removeHeader("Origin")
            .build(),
    )
}
