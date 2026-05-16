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
