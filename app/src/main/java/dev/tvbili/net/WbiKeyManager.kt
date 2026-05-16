package dev.tvbili.net

import android.content.Context
import android.util.Log
import dev.tvbili.data.store.WbiKeysStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * WBI 签名 key 管理器：24h 缓存 + 互斥刷新 + DataStore 持久化。
 *
 * 调用流程：
 * - [bootstrap] 启动时从 DataStore 恢复
 * - [getKeys] 首选内存缓存，缺失/过期时去 `mainApi.getNavInfo()` 拉
 * - 多协程并发触发刷新时由 [mutex] 保证只发一次网络请求
 */
object WbiKeyManager {

    private const val TAG = "WbiKeyManager"
    private const val CACHE_MS = 24L * 60 * 60 * 1000

    @Volatile private var cached: Pair<String, String>? = null
    @Volatile private var ts: Long = 0
    private val mutex = Mutex()

    /** 启动时调用：从 DataStore 恢复持久化的 keys 到内存。 */
    suspend fun bootstrap(context: Context) {
        val (img, sub, savedAt) = WbiKeysStore.load(context)
        if (!img.isNullOrEmpty() && !sub.isNullOrEmpty() && savedAt > 0) {
            cached = img to sub
            ts = savedAt
        }
    }

    suspend fun getKeys(): Result<Pair<String, String>> {
        cached?.takeIf { isValid() }?.let { return Result.success(it) }
        return mutex.withLock {
            cached?.takeIf { isValid() }?.let { return@withLock Result.success(it) }
            refresh()
        }
    }

    private suspend fun refresh(): Result<Pair<String, String>> = runCatching {
        val nav = NetworkModule.mainApi.getNavInfo()
        val wbi = nav.data?.wbi_img ?: error("nav response missing wbi_img")
        val img = wbi.img_url.substringAfterLast('/').substringBefore('.')
        val sub = wbi.sub_url.substringAfterLast('/').substringBefore('.')
        cached = img to sub
        ts = System.currentTimeMillis()
        NetworkModule.appContext?.let { WbiKeysStore.save(it, img, sub, ts) }
        Log.d(TAG, "wbi keys refreshed: imgKey=${img.take(12)}...")
        img to sub
    }

    private fun isValid(): Boolean = System.currentTimeMillis() - ts < CACHE_MS
}
