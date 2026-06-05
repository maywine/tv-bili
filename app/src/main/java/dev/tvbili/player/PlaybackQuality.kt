package dev.tvbili.player

/**
 * 解码失败后的清晰度降级目标。纯函数，集中 qn 降级策略，便于单测。
 *
 * 背景：B 站 4K（qn≥112）只发 HEVC/AV1，没有 H.264；播放器用 `EXTENSION_RENDERER_MODE_OFF`
 * 不带软解，必须硬解。万一某设备/某片源硬解 4K HEVC 失败（`DECODING_FAILED` 等），
 * 重拉同档没用（解码器 fallback 只换同 MIME 的解码器），只能降到有 H.264 的档兜底。
 * 默认仍上 4K（[dev.tvbili.ui.video.VideoDetailViewModel] 的 `_selectedQn`）——此降级只在真·解码报错时触发。
 */

/** 最高的、稳定含 H.264(codecid=7) 的清晰度档；弱盒子能硬解。 */
const val H264_SAFE_QN = 80

/**
 * 解码器扛不住（HEVC/AV1 硬解失败）时的降级目标：
 * - > 80（4K / 1080P60 / 1080P+，B 站只给 HEVC/AV1）：**一步**降到 80=1080P(H.264)，
 *   而不是逐档降——逐档会在撞满 `MAX_AUTO_RECOVER` 前还到不了能解的档
 * - ≤ 80：再沿 720/480/360 逐级降
 * - 已是最低档：返回 null（无可降，调用方翻 Error）
 */
fun qnAfterDecoderError(qn: Int): Int? = when {
    qn > H264_SAFE_QN -> H264_SAFE_QN
    qn > 64 -> 64
    qn > 32 -> 32
    qn > 16 -> 16
    else -> null
}
