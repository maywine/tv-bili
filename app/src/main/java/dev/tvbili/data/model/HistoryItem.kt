package dev.tvbili.data.model

import dev.tvbili.data.repo.HomeCard
import kotlinx.serialization.Serializable

/**
 * 本地历史记录条目。DataStore JSON 序列化目标。
 *
 * 字段一旦上线 **不可重命名 / 删除**——新字段都用默认值，老 JSON 仍能反序列化。
 * 真正破坏性变更才升 key（`history_items_v1` → `_v2`）并并行存储。
 *
 * `equals` / `hashCode` 由 kotlinx.serialization 默认 data class 实现（比较所有字段），
 * dedupe by bvid 必须显式 `it.bvid == bvid`，不要依赖 `==`。
 */
@Serializable
data class HistoryItem(
    val bvid: String,
    val aid: Long = 0,
    val cid: Long = 0,
    val title: String,
    val coverUrl: String,
    val uploader: String = "",
    val durationSec: Int = 0,
    /** 0 = 仅打开过未播放，或不可恢复的早返调用 */
    val lastPositionMs: Long = 0,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun toHomeCard(): HomeCard.Video = HomeCard.Video(
        aid = aid,
        bvid = bvid,
        title = title,
        coverUrl = coverUrl,
        uploader = uploader,
        durationSec = durationSec,
        viewCount = 0, // 历史卡片不展示播放量
    )
}
