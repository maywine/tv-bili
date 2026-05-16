package dev.tvbili.player

import dev.tvbili.data.model.DashStream
import dev.tvbili.data.model.PlayUrlData

/**
 * 从 DASH 列表选择「目标清晰度 + 兼容 codec」的视频流，与最高 bandwidth 音频流。
 *
 * 优先级（视频）：
 * 1. id == targetQn 的流
 * 2. id ≤ targetQn 的最高一档（降级）
 * 3. 整个 list 第一个（fallback）
 *
 * 同档清晰度多个流时：H.264（codecid=7）> HEVC（codecid=12）> AV1（codecid=13）
 * —— 盒子端低端 SoC HEVC 软解 OOM 风险大（PRD §"Technical Risks"）。
 *
 * 音频：选最高 bandwidth 的一条。
 *
 * 纯函数 —— 完全可单元测试。
 */
object StreamSelector {

    data class Selection(
        val video: DashStream,
        val audio: DashStream?,
    )

    /** @return null 表示 list 完全空，调用方需要兜底（显示错误） */
    fun select(data: PlayUrlData, targetQn: Int): Selection? {
        val videos = data.dash?.video.orEmpty()
        if (videos.isEmpty()) return null
        val video = pickVideo(videos, targetQn) ?: return null
        val audio = data.dash?.audio.orEmpty().maxByOrNull { it.bandwidth }
        return Selection(video, audio)
    }

    internal fun pickVideo(videos: List<DashStream>, targetQn: Int): DashStream? {
        if (videos.isEmpty()) return null
        val byId = videos.groupBy { it.id }
        val exact = byId[targetQn]?.let(::pickByCodec)
        if (exact != null) return exact
        // 降级：找 ≤ targetQn 的最高一档；若无再升到最低一档
        val downgrade = byId.keys.filter { it <= targetQn }.maxOrNull()
            ?: byId.keys.minOrNull()
            ?: return null
        return byId[downgrade]?.let(::pickByCodec)
    }

    private fun pickByCodec(streams: List<DashStream>): DashStream? {
        if (streams.isEmpty()) return null
        val byCodec = streams.sortedBy { codecRank(it) }
        return byCodec.firstOrNull { it.validUrl().isNotEmpty() } ?: byCodec.firstOrNull()
    }

    /** 越小越优先 */
    internal fun codecRank(s: DashStream): Int = when {
        s.codecid == 7 -> 0
        s.codecs.startsWith("avc", ignoreCase = true) -> 0
        s.codecid == 12 -> 1
        s.codecs.startsWith("hev", ignoreCase = true) -> 1
        s.codecs.startsWith("hvc", ignoreCase = true) -> 1
        s.codecid == 13 -> 2
        s.codecs.startsWith("av0", ignoreCase = true) -> 2
        else -> 3
    }
}
