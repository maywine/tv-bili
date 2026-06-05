package dev.tvbili.player

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerBufferPolicyTest {

    @Test
    fun `mid-tier tv (3 GB) policy caps at 64 MB bytes`() {
        val p = resolvePlayerBufferPolicy(isTv = true, totalMemMb = 3072)
        assertEquals(64 * 1024 * 1024, p.targetBufferBytes)
        assertEquals(20_000, p.minBufferMs)
        assertEquals(40_000, p.maxBufferMs)
    }

    @Test
    fun `low-tier tv (1 GB) policy caps at 32 MB bytes`() {
        val p = resolvePlayerBufferPolicy(isTv = true, totalMemMb = 1024)
        assertEquals(32 * 1024 * 1024, p.targetBufferBytes)
        assertEquals(15_000, p.minBufferMs)
        assertEquals(30_000, p.maxBufferMs)
    }

    @Test
    fun `rebuffer-before-resume window is teens of seconds per tier`() {
        // 卡顿后续播阈值：中端 15 s / 老盒 12 s / 非 TV 12 s——别再 ~3 s 攒一点就续播又卡
        assertEquals(15_000, resolvePlayerBufferPolicy(isTv = true, totalMemMb = 3072).bufferForPlaybackAfterRebufferMs)
        assertEquals(12_000, resolvePlayerBufferPolicy(isTv = true, totalMemMb = 1024).bufferForPlaybackAfterRebufferMs)
        assertEquals(12_000, resolvePlayerBufferPolicy(isTv = false, totalMemMb = 4096).bufferForPlaybackAfterRebufferMs)
    }

    @Test
    fun `initial start stays fast so cold first play is not slowed`() {
        // 只拉长「卡顿后续播」，首播仍 ≤ 1.5 s 快起
        assertTrue(resolvePlayerBufferPolicy(isTv = true, totalMemMb = 3072).bufferForPlaybackMs <= 1_500)
        assertTrue(resolvePlayerBufferPolicy(isTv = true, totalMemMb = 1024).bufferForPlaybackMs <= 1_500)
        assertTrue(resolvePlayerBufferPolicy(isTv = false, totalMemMb = 4096).bufferForPlaybackMs <= 1_500)
    }

    @Test
    fun `every tier satisfies DefaultLoadControl ordering constraints`() {
        // DefaultLoadControl.Builder 强校验：0 <= forPlayback <= min <= max 且 0 <= afterRebuffer <= min
        // 违反会在 build() 抛 IllegalArgumentException —— 用此测试守住，避免改值后真机起播即崩
        val tiers = listOf(
            resolvePlayerBufferPolicy(isTv = true, totalMemMb = 3072),
            resolvePlayerBufferPolicy(isTv = true, totalMemMb = 1024),
            resolvePlayerBufferPolicy(isTv = false, totalMemMb = 4096),
        )
        for (p in tiers) {
            assertTrue("forPlayback >= 0", p.bufferForPlaybackMs >= 0)
            assertTrue("afterRebuffer >= 0", p.bufferForPlaybackAfterRebufferMs >= 0)
            assertTrue("min >= forPlayback", p.minBufferMs >= p.bufferForPlaybackMs)
            assertTrue("min >= afterRebuffer", p.minBufferMs >= p.bufferForPlaybackAfterRebufferMs)
            assertTrue("max >= min", p.maxBufferMs >= p.minBufferMs)
        }
    }

    @Test
    fun `tv threshold exactly at 2 GB picks mid tier`() {
        val p = resolvePlayerBufferPolicy(isTv = true, totalMemMb = 2048)
        assertEquals(64 * 1024 * 1024, p.targetBufferBytes)
    }

    @Test
    fun `non-tv policy uses unset bytes regardless of mem`() {
        val p = resolvePlayerBufferPolicy(isTv = false, totalMemMb = 16384)
        assertEquals(C.LENGTH_UNSET, p.targetBufferBytes)
    }

    @Test
    fun `tv policy prioritizes size over time`() {
        val p = resolvePlayerBufferPolicy(isTv = true, totalMemMb = 3072)
        assertFalse(p.prioritizeTimeOverSizeThresholds)
    }

    @Test
    fun `non-tv policy prioritizes time over size`() {
        val p = resolvePlayerBufferPolicy(isTv = false, totalMemMb = 4096)
        assertTrue(p.prioritizeTimeOverSizeThresholds)
    }
}
