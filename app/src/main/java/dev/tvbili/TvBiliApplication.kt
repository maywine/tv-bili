package dev.tvbili

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.bitmapConfig
import coil3.request.crossfade
import dev.tvbili.data.store.HistoryStore
import dev.tvbili.data.store.SectionConfigStore
import dev.tvbili.data.store.TokenStore
import dev.tvbili.net.NetworkModule
import dev.tvbili.net.WbiKeyManager
import dev.tvbili.tv.TvUtils
import kotlinx.coroutines.runBlocking

/**
 * 启动期初始化：
 * 1. NetworkModule.init —— 缓存 appContext，给后续 lazy OkHttp/Retrofit 使用
 * 2. TokenStore.bootstrap —— DataStore first() 同步读出 cookies/buvid3/tokens 到 @Volatile 内存缓存
 * 3. WbiKeyManager.bootstrap —— 恢复 WBI keys（Phase 2 推荐流首次调用免去一次 nav 网络往返）
 * 4. Coil ImageLoader —— 复用 NetworkModule.okHttpClient（cookies / UA 自动随之），TV 关 crossfade
 */
class TvBiliApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        NetworkModule.init(this)
        runBlocking {
            TokenStore.bootstrap(this@TvBiliApplication)
            WbiKeyManager.bootstrap(this@TvBiliApplication)
            SectionConfigStore.bootstrap(this@TvBiliApplication)
            HistoryStore.bootstrap(this@TvBiliApplication)
        }
        configureCoil()
        Log.d(
            "TvBiliApp",
            "bootstrap done, isLoggedIn=${TokenStore.isLoggedIn} buvid3=${TokenStore.buvid3?.take(8)}",
        )
    }

    private fun configureCoil() {
        // 按设备 RAM 分档：3 GB 中端 TV 走 6 % ≈ 180 MB；
        // 1 GB 老盒子走 5 % ≈ 50 MB，避免 OOM。Coil 默认 25 % 对仅播放页的应用过分。
        val totalMem = TvUtils.totalMemMb(this)
        val cachePct = if (totalMem >= 2048) 0.06 else 0.05
        val loader = ImageLoader.Builder(this)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { NetworkModule.okHttpClient }))
            }
            .crossfade(false) // TV 默认关 — 见 DESIGN.md §关键技术决策
            // 盒子端 16-bit 封面：每像素 4B → 2B 砍一半内存，肉眼 1080p 缩略图无可见色差
            .bitmapConfig(Bitmap.Config.RGB_565)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(this, cachePct)
                    .build()
            }
            .build()
        SingletonImageLoader.setSafe { loader }
        Log.d("TvBiliApp", "Coil cache=${(cachePct * 100).toInt()}% (RAM=${totalMem} MB)")
    }
}
