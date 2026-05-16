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
 * 数值移植自 BiliPai `feature/video/state/VideoPlayerState.kt:88-105`，TV 分支验证过。
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
            bufferForPlaybackMs = 1_500,
            bufferForPlaybackAfterRebufferMs = 3_000,
            targetBufferBytes = 64 * 1024 * 1024, // 64 MB
        )
        isTv -> PlayerBufferPolicy(
            minBufferMs = 15_000,
            maxBufferMs = 30_000,
            bufferForPlaybackMs = 1_500,
            bufferForPlaybackAfterRebufferMs = 3_000,
            targetBufferBytes = 32 * 1024 * 1024, // 32 MB
        )
        else -> PlayerBufferPolicy(
            minBufferMs = 12_000,
            maxBufferMs = 45_000,
            bufferForPlaybackMs = 1_000,
            bufferForPlaybackAfterRebufferMs = 2_200,
        )
    }
