package dev.tvbili.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackQualityTest {

    @Test
    fun `decoder error jumps 4K and 1080p-plus tiers straight to H264 safe qn`() {
        assertEquals(H264_SAFE_QN, qnAfterDecoderError(120))
        assertEquals(H264_SAFE_QN, qnAfterDecoderError(116))
        assertEquals(H264_SAFE_QN, qnAfterDecoderError(112))
    }

    @Test
    fun `decoder error steps down within decodable tiers`() {
        assertEquals(64, qnAfterDecoderError(80))
        assertEquals(32, qnAfterDecoderError(64))
        assertEquals(16, qnAfterDecoderError(32))
    }

    @Test
    fun `decoder error at floor has nowhere to go`() {
        assertNull(qnAfterDecoderError(16))
        assertNull(qnAfterDecoderError(6))
    }
}
