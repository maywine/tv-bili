package dev.tvbili.player

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchdogDecisionTest {

    private fun decide(
        state: Int = Player.STATE_READY,
        playWhenReady: Boolean = true,
        pos: Long = 5_000L,
        dur: Long = 600_000L,
        lastPos: Long = 5_000L,
        stallStreak: Int = 0,
        recovering: Boolean = false,
    ): WatchdogVerdict = decideWatchdog(
        playbackState = state,
        playWhenReady = playWhenReady,
        pos = pos,
        dur = dur,
        lastPos = lastPos,
        stallStreak = stallStreak,
        recovering = recovering,
    )

    @Test
    fun `buffering with frozen position is never stuck`() {
        // 正常重缓冲：位置冻结也只重置基线，绝不计入卡死（电影卡顿误判的根因）
        assertEquals(
            WatchdogVerdict.RESET_BASELINE,
            decide(state = Player.STATE_BUFFERING, pos = 5_000L, lastPos = 5_000L, stallStreak = 2),
        )
    }

    @Test
    fun `ready and frozen below threshold only ticks`() {
        assertEquals(
            WatchdogVerdict.STUCK_TICK,
            decide(state = Player.STATE_READY, pos = 5_000L, lastPos = 5_000L, stallStreak = 0),
        )
        assertEquals(
            WatchdogVerdict.STUCK_TICK,
            decide(state = Player.STATE_READY, pos = 5_000L, lastPos = 5_000L, stallStreak = 1),
        )
    }

    @Test
    fun `ready and frozen reaching threshold recovers`() {
        // stallStreak=2, +1 = 3 = STUCK_POLLS_BEFORE_RECOVER → RECOVER
        assertEquals(
            WatchdogVerdict.RECOVER,
            decide(state = Player.STATE_READY, pos = 5_000L, lastPos = 5_000L, stallStreak = 2),
        )
    }

    @Test
    fun `user pause never recovers`() {
        assertEquals(
            WatchdogVerdict.RESET_BASELINE,
            decide(playWhenReady = false, pos = 5_000L, lastPos = 5_000L, stallStreak = 2),
        )
    }

    @Test
    fun `ended never recovers`() {
        assertEquals(
            WatchdogVerdict.RESET_BASELINE,
            decide(state = Player.STATE_ENDED, pos = 5_000L, lastPos = 5_000L, stallStreak = 2),
        )
    }

    @Test
    fun `idle (re-preparing) never recovers`() {
        assertEquals(
            WatchdogVerdict.RESET_BASELINE,
            decide(state = Player.STATE_IDLE, pos = 5_000L, lastPos = 5_000L, stallStreak = 2),
        )
    }

    @Test
    fun `near end is not stuck`() {
        assertEquals(
            WatchdogVerdict.RESET_BASELINE,
            decide(pos = 599_500L, dur = 600_000L, lastPos = 599_500L, stallStreak = 2),
        )
    }

    @Test
    fun `recovery in flight is skipped even when ready and frozen`() {
        assertEquals(
            WatchdogVerdict.RESET_BASELINE,
            decide(recovering = true, pos = 5_000L, lastPos = 5_000L, stallStreak = 2),
        )
    }

    @Test
    fun `forward progress reports PROGRESS so the failure counter can reset`() {
        assertEquals(
            WatchdogVerdict.PROGRESS,
            decide(pos = 7_500L, lastPos = 5_000L, stallStreak = 1),
        )
    }

    @Test
    fun `first sample with no baseline only resets`() {
        assertEquals(
            WatchdogVerdict.RESET_BASELINE,
            decide(pos = 5_000L, lastPos = -1L, stallStreak = 0),
        )
    }

    @Test
    fun `backward seek is not progress and not stuck`() {
        assertEquals(
            WatchdogVerdict.RESET_BASELINE,
            decide(pos = 2_000L, lastPos = 5_000L, stallStreak = 1),
        )
    }
}
