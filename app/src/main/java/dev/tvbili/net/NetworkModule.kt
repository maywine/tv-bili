package dev.tvbili.net

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dev.tvbili.data.api.LiveApi
import dev.tvbili.data.api.MainApi
import dev.tvbili.data.api.PassportApi
import dev.tvbili.data.api.SearchApi
import dev.tvbili.data.api.VideoApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * 单例 HTTP 客户端 + Retrofit api 工厂。
 *
 * 调用顺序：
 * 1. [TvBiliApplication.onCreate] 调 [init] 注入 appContext
 * 2. 首次访问 [okHttpClient] / [mainApi] / [passportApi] 时按需 lazy build
 *
 * Phase 1 简化点（对比 BiliPai）：
 * - 不开 OkHttp Cache（Phase 7 加）
 * - 不引 logging-interceptor（Phase 7 加，且仅 debug 启用）
 * - 不做 DNS hardcoded fallback / trust-all SSL
 */
object NetworkModule {

    internal var appContext: Context? = null
        private set

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    val cookieJar: TvBiliCookieJar by lazy { TvBiliCookieJar() }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .cookieJar(cookieJar)
            .addInterceptor(HttpHeadersInterceptor())
            .build()
    }

    val mainApi: MainApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.bilibili.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MainApi::class.java)
    }

    val passportApi: PassportApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://passport.bilibili.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PassportApi::class.java)
    }

    val liveApi: LiveApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.live.bilibili.com/") // baseUrl 仅占位——所有端点写全 URL
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(LiveApi::class.java)
    }

    val videoApi: VideoApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.bilibili.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(VideoApi::class.java)
    }

    val searchApi: SearchApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.bilibili.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SearchApi::class.java)
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
