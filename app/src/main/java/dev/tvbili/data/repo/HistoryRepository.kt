package dev.tvbili.data.repo

import android.content.Context
import dev.tvbili.data.model.HistoryItem
import dev.tvbili.data.store.HistoryStore

/**
 * 历史记录读写薄包装。
 *
 * ViewModel 只依赖本类（不直接 import [HistoryStore]）——将来要换 Room 或别的
 * 存储时只改本文件。
 */
class HistoryRepository(private val context: Context) {

    /**
     * 进入视频详情页时调用：刷新元信息（标题 / 封面 / 时长）并顶到首位。
     *
     * **保留旧 lastPositionMs**：用户秒退（player.currentPosition 还是 0）时 recordProgress
     * 早返不写盘，本函数若把进度重置为 0 就会永久丢掉上次进度。仅当 cid 切换（用户在
     * 看不同 P）时才视为「新一轮观看」回到 0。
     */
    suspend fun recordView(card: HomeCard.Video, cid: Long = 0L) {
        val prev = HistoryStore.current.firstOrNull { it.bvid == card.bvid }
        val preservedPos = when {
            prev == null -> 0L
            // 双方都非 0 且 cid 不同 → 用户切到不同 P，旧进度无意义
            prev.cid != 0L && cid != 0L && prev.cid != cid -> 0L
            else -> prev.lastPositionMs
        }
        HistoryStore.upsert(
            context,
            HistoryItem(
                bvid = card.bvid,
                aid = card.aid,
                cid = cid,
                title = card.title,
                coverUrl = card.coverUrl,
                uploader = card.uploader,
                durationSec = card.durationSec,
                lastPositionMs = preservedPos,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** 离开视频页时调用：仅更新 lastPositionMs。bvid 不在历史里则 no-op。 */
    suspend fun recordProgress(bvid: String, positionMs: Long) {
        HistoryStore.updateProgress(context, bvid, positionMs)
    }

    /** HISTORY 分区数据源。返回按 updatedAt 倒序的 [HomeCard.Video] 列表。 */
    suspend fun getRecent(): List<HomeCard> =
        HistoryStore.current.map { it.toHomeCard() }

    /**
     * 计算视频可恢复进度（毫秒）。读 [HistoryStore.current] 内存缓存，O(1) 无 IO。
     *
     * @param bvid 视频 BV 号
     * @param cid 当前播放的分 P cid；与历史记录的 cid 不一致视为不同 P，**不**恢复。
     *            传 0 关闭 cid 校验（用于不知道 cid 的兜底场景）。
     * @param durationSec 视频总长度（秒）；传 0 表示未知，跳过末尾判定。
     *
     * @return >0 表示从该位置继续；0 表示从头开始（无历史 / < 5s / ≥ 末尾-5s / 数据异常 / cid 不匹配）。
     */
    fun getResumePositionMs(bvid: String, cid: Long, durationSec: Int): Long =
        computeResumePositionMs(HistoryStore.current, bvid, cid, durationSec)

    companion object {
        // < 5 s 的进度视为「没真正看」，不打扰用户重新进入流程
        const val THRESHOLD_RESUME_MS = 5_000L
        // ≥ 末尾 -5 s 视为「已看完」，回到 0 而非卡在结束帧
        const val THRESHOLD_END_MS = 5_000L

        /**
         * 纯函数版本——便于 JVM 单测。生产路径走实例方法 [getResumePositionMs]
         * 它从 [HistoryStore.current] 取列表。
         */
        internal fun computeResumePositionMs(
            items: List<HistoryItem>,
            bvid: String,
            cid: Long,
            durationSec: Int,
        ): Long {
            val item = items.firstOrNull { it.bvid == bvid } ?: return 0L
            // cid 双方都非 0 时严格匹配——多 P 视频里只恢复用户上次看的那一 P
            if (cid != 0L && item.cid != 0L && item.cid != cid) return 0L
            val pos = item.lastPositionMs
            if (pos < THRESHOLD_RESUME_MS) return 0L
            val totalMs = durationSec * 1000L
            if (totalMs > 0L && pos >= totalMs - THRESHOLD_END_MS) return 0L
            if (totalMs > 0L && pos > totalMs) return 0L
            return pos
        }
    }
}
