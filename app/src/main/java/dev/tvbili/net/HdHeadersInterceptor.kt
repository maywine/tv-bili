package dev.tvbili.net

import okhttp3.Interceptor
import okhttp3.Response

/** HD API 和媒体请求使用移动端 UA，不能被通用网页拦截器覆盖。 */
class HdHeadersInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(
        chain.request().newBuilder()
            .header("User-Agent", "Mozilla/5.0 BiliDroid/2.0.1")
            .removeHeader("Referer")
            .removeHeader("Origin")
            .build(),
    )
}
