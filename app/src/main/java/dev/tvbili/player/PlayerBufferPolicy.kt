package dev.tvbili.player

import androidx.media3.common.C

/**
 * Media3 LoadControl 数值集中点。
 *
 * 三档：
 * - **中端 TV（4×A73 / 3 GB RAM / 4K HEVC HW）**：64 MB target + 40 s 上限
 *   4K HEVC 平均 8-12 Mbps → 30 s 约 30-45 MB，加余量定到 64 MB；可避免 4K seek 后频繁
 *   再缓冲。
 * - **老 TV / 1 GB 盒子**：32 MB 硬封顶 + 30 s 上限，防止 native heap 打爆
 * - **桌面 / 非 TV**：不字节封顶，按时间走
 *
 * **[bufferForPlaybackAfterRebufferMs]（卡顿后续播阈值）是本策略的关键**：网络卡顿进入
 * 重缓冲后，ExoPlayer 要先攒够这么多媒体才恢复播放。旧值 ~3 s 太小——攒一点点就续播、
 * 马上又卡，反复抖动。现拉到 ~十几秒（中端 15 s / 老盒 12 s），一次卡顿攒够再连续播。
 * media3 1.5.1 `DefaultLoadControl.shouldStartPlayback` 实测：续播时机 = min(本时间阈值,
 * 字节封顶对应秒数)；上述带宽下字节封顶都填不满 ≤ 该时长，故恒由时间阈值兜底生效。
 * 与看门狗（[decideWatchdog] 把 STATE_BUFFERING 视为健康、不误判卡死）配套，长重缓冲不会触发误恢复。
 *
 * 约束（DefaultLoadControl.Builder 强校验，违反抛 IllegalArgumentException）：
 * `0 ≤ bufferForPlaybackMs ≤ minBufferMs ≤ maxBufferMs` 且 `0 ≤ bufferForPlaybackAfterRebufferMs ≤ minBufferMs`。
 *
 * 纯 data class + 纯函数 —— 便于单元测试。
 */
data class PlayerBufferPolicy(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int,
    /** [C.LENGTH_UNSET] 表示不字节封顶（默认非 TV） */
    val targetBufferBytes: Int = C.LENGTH_UNSET,
) {
    val prioritizeTimeOverSizeThresholds: Boolean
        get() = targetBufferBytes == C.LENGTH_UNSET
}

/** 中端与老 TV 分档阈值：≥ 2 GB 视为中端。 */
private const val MID_TIER_TV_MEM_MB = 2048L

/**
 * @param isTv 由 [dev.tvbili.tv.TvUtils.isTv] 判定
 * @param totalMemMb 由 [dev.tvbili.tv.TvUtils.totalMemMb] 判定
 */
fun resolvePlayerBufferPolicy(isTv: Boolean, totalMemMb: Long): PlayerBufferPolicy =
    when {
        isTv && totalMemMb >= MID_TIER_TV_MEM_MB -> PlayerBufferPolicy(
            minBufferMs = 20_000,
            maxBufferMs = 40_000,
            bufferForPlaybackMs = 1_500,         // 首播仍快起（1.5 s），只拉长卡顿后续播
            bufferForPlaybackAfterRebufferMs = 15_000, // 卡顿后攒 15 s 再续播
            targetBufferBytes = 64 * 1024 * 1024, // 64 MB
        )
        isTv -> PlayerBufferPolicy(
            minBufferMs = 15_000,
            maxBufferMs = 30_000,
            bufferForPlaybackMs = 1_500,
            bufferForPlaybackAfterRebufferMs = 12_000, // 老盒攒 12 s 再续播（25 Mbps 峰值时 32 MB 封顶约 10.7 s 兜底）
            targetBufferBytes = 32 * 1024 * 1024, // 32 MB
        )
        else -> PlayerBufferPolicy(
            minBufferMs = 15_000,               // 抬到 15 s 以容纳 12 s 的卡顿后续播阈值
            maxBufferMs = 45_000,
            bufferForPlaybackMs = 1_000,
            bufferForPlaybackAfterRebufferMs = 12_000,
        )
    }
