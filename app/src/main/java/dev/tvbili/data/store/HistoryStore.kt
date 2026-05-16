package dev.tvbili.data.store

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.tvbili.data.model.HistoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 本地视频观看历史。
 *
 * - 存储：DataStore Preferences，key `history_items_v1`，值是 JSON 数组字符串
 * - 上限：[MAX_ENTRIES] = 200；超出按 LRU 淘汰（按 `updatedAt` 倒序排在尾部的丢）
 * - dedupe：同 bvid 视为同一条，新值覆盖并顶到首位
 * - 解析失败：drop 全表（写日志），用户重新看就回来；好过持续 crash
 *
 * 启动期 bootstrap 同 [TokenStore] / [SectionConfigStore]：runBlocking 一次性
 * 灌进 [current] 内存缓存，供 ViewModel hot path 读取，避免每次 flow.first()。
 */
object HistoryStore {

    private const val MAX_ENTRIES = 200
    private const val TAG = "HistoryStore"
    private val KEY = stringPreferencesKey("history_items_v1")
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    // upsert / updateProgress 互斥：保证 read-compute-write 在并发协程下不丢更新，
    // 也保证「先写盘后改内存镜像」对所有写入路径生效。
    private val writeMutex = Mutex()

    @Volatile
    var current: List<HistoryItem> = emptyList()
        private set

    suspend fun bootstrap(context: Context) {
        val raw = context.tvBiliPrefs.data.first()[KEY]
        val parsed = parse(raw)
        if (parsed.isEmpty() && !raw.isNullOrBlank()) {
            Log.w(TAG, "parse failed, dropping all history (raw len=${raw.length})")
        }
        current = parsed
        Log.d(TAG, "bootstrap loaded ${parsed.size} entries")
    }

    fun flow(context: Context): Flow<List<HistoryItem>> =
        context.tvBiliPrefs.data.map { parse(it[KEY]) }

    /** 同 bvid 视为同一条：更新字段并把它顶到列表首位（LRU）。 */
    suspend fun upsert(context: Context, item: HistoryItem) = writeMutex.withLock {
        upsertLocked(context, item)
    }

    /**
     * 仅更新进度 + updatedAt；不存在则忽略（不补建条目，避免播放器空 seek 也产生历史）。
     */
    suspend fun updateProgress(context: Context, bvid: String, positionMs: Long) = writeMutex.withLock {
        val target = current.firstOrNull { it.bvid == bvid } ?: return@withLock
        upsertLocked(
            context,
            target.copy(
                lastPositionMs = positionMs,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    // 调用方必须已持 [writeMutex]。先写盘，写盘成功后再更新内存镜像——
    // 写盘抛异常时 `current` 不被污染，下次重试仍能从一致状态开始。
    private suspend fun upsertLocked(context: Context, item: HistoryItem) {
        val merged = (listOf(item) + current.filter { it.bvid != item.bvid }).take(MAX_ENTRIES)
        context.tvBiliPrefs.edit { it[KEY] = json.encodeToString(merged) }
        current = merged
    }

    // 纯函数（无 Log / 无 Context 依赖），便于 JVM 单元测试。
    // 失败诊断由调用方（[bootstrap]）做。
    internal fun parse(raw: String?): List<HistoryItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<HistoryItem>>(raw) }
            .getOrDefault(emptyList())
    }
}
