package dev.tvbili.player

import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.IDisplayer
import master.flame.danmaku.danmaku.model.android.DanmakuContext

/**
 * TV 友好的 DanmakuFlameMaster 配置。
 *
 * 与默认相比的关键差异：
 * - 字号 +20%（scaleTextSize = 1.2f）—— 10 ft 距离看不清默认尺寸
 * - 最大行数限制：滚动 5 行 + 顶 3 + 底 3，避免遮字幕
 * - 防止重叠（同 lane 同时段）
 * - 全局透明度 0.85（保留对比 + 不刺眼）
 */
object DanmakuConfig {

    fun create(): DanmakuContext = DanmakuContext.create().apply {
        setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 3f)
        setScaleTextSize(1.2f)
        setDuplicateMergingEnabled(false)
        setScrollSpeedFactor(1.2f)
        setMaximumLines(
            mapOf(
                BaseDanmaku.TYPE_SCROLL_RL to 5,
                BaseDanmaku.TYPE_SCROLL_LR to 5,
                BaseDanmaku.TYPE_FIX_TOP to 3,
                BaseDanmaku.TYPE_FIX_BOTTOM to 3,
            ),
        )
        preventOverlapping(
            mapOf(
                BaseDanmaku.TYPE_SCROLL_RL to true,
                BaseDanmaku.TYPE_SCROLL_LR to true,
                BaseDanmaku.TYPE_FIX_TOP to true,
                BaseDanmaku.TYPE_FIX_BOTTOM to true,
            ),
        )
        setDanmakuTransparency(0.85f)
    }
}
