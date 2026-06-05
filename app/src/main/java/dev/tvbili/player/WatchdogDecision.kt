package dev.tvbili.player

import androidx.media3.common.Player

/**
 * 看门狗「卡死判定」纯函数 —— 把一次 ExoPlayer 状态采样翻成一个裁决，便于单测。
 *
 * **核心修正（电影卡顿→无法播放的根因）**：正常的网络重缓冲（[Player.STATE_BUFFERING]）
 * **不算卡死**。重缓冲期间 ExoPlayer 会保持 `playWhenReady=true`、把媒体时钟冻结，
 * `currentPosition` 自然不前进——这是健康行为。旧逻辑只看 `playWhenReady` + 位置不动，
 * 把一次 4K 重缓冲误判成「黑屏卡死」，触发整流重拉 → 重拉又从头缓冲 → 越拉越卡 →
 * 撞满 `MAX_AUTO_RECOVER` 翻 Error，表现为「卡顿然后无法播放」。
 *
 * 真正的「无声卡死」是：[Player.STATE_READY]（已就绪、本应推进）但位置**连续多个**
 * 轮询不动。只有这种情况才触发自动恢复。
 */
enum class WatchdogVerdict {
    /**
     * 重置卡死基线（caller：`lastPos = pos; stallStreak = 0`）。
     * 缓冲中 / 用户暂停 / 已播完 / 起播未就绪 / 接近末尾 / 恢复进行中 —— 都不算卡死。
     */
    RESET_BASELINE,

    /**
     * 位置在健康推进（caller：重置基线**并**把 `autoRecoverAttempts` 清零）。
     * 这是「恢复确实生效 / 播放正常」的唯一可信信号——据此把连续失败计数归零，
     * 避免一部长电影里几次互不相关的瞬时卡顿累计撞死 Error。
     */
    PROGRESS,

    /** 累计一次卡死采样（caller：`stallStreak++`，保留 `lastPos`），尚未到触发阈值。 */
    STUCK_TICK,

    /** 连续卡死达阈值：触发 `refreshPlayUrlAfterError`。 */
    RECOVER,
}

/**
 * 触发自动恢复前需要的连续「READY 但位置不动」轮询数。
 * 含首个建立基线的轮询，实际窗口 ≈ (N+1) × [WATCHDOG_POLL_MS]，3 档约 10 s——
 * 远超任何健康重缓冲，只会命中真正的无声卡死。
 */
const val STUCK_POLLS_BEFORE_RECOVER = 3

/**
 * @param playbackState [Player] 的 playbackState（IDLE/BUFFERING/READY/ENDED）
 * @param playWhenReady 用户是否意图播放（暂停时 false）
 * @param pos 本次采样的 currentPosition
 * @param dur 当前时长（≤0 表示未知）
 * @param lastPos 上次基线位置；-1 表示无基线（首次采样 / 刚重置）
 * @param stallStreak 当前已累计的连续卡死采样数
 * @param recovering 是否有恢复协程在飞行中——飞行期间一律跳过，取代旧的脆弱「只压一个轮询」写法
 */
fun decideWatchdog(
    playbackState: Int,
    playWhenReady: Boolean,
    pos: Long,
    dur: Long,
    lastPos: Long,
    stallStreak: Int,
    recovering: Boolean,
    stuckPollsBeforeRecover: Int = STUCK_POLLS_BEFORE_RECOVER,
    nearEndMs: Long = 1_000L,
): WatchdogVerdict {
    // 恢复进行中：别采样、别再触发（覆盖异步重拉+re-prepare 的整个窗口）
    if (recovering) return WatchdogVerdict.RESET_BASELINE
    // 正常重缓冲：位置冻结是健康的，绝不计入卡死
    if (playbackState == Player.STATE_BUFFERING) return WatchdogVerdict.RESET_BASELINE
    // 用户暂停 / 已播完 / 起播未就绪：本来就不该推进
    if (!playWhenReady ||
        playbackState == Player.STATE_ENDED ||
        playbackState == Player.STATE_IDLE
    ) {
        return WatchdogVerdict.RESET_BASELINE
    }
    // 接近末尾，最后一秒数值抖动不算卡死
    if (dur > 0L && pos >= dur - nearEndMs) return WatchdogVerdict.RESET_BASELINE

    // 到这里一定是 STATE_READY 且本应推进
    if (lastPos != -1L && pos == lastPos) {
        return if (stallStreak + 1 >= stuckPollsBeforeRecover) {
            WatchdogVerdict.RECOVER
        } else {
            WatchdogVerdict.STUCK_TICK
        }
    }
    // 位置确实在前进 → 健康；否则（首次采样无基线 / seek 回退）只重置基线
    return if (lastPos != -1L && pos > lastPos) {
        WatchdogVerdict.PROGRESS
    } else {
        WatchdogVerdict.RESET_BASELINE
    }
}
