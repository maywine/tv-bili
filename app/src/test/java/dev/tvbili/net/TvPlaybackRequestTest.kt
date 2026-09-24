package dev.tvbili.net

import dev.tvbili.data.model.PgcPlayback
import dev.tvbili.data.model.VideoDetail
import org.junit.Assert.*
import org.junit.Test

class TvPlaybackRequestTest {
    private val pgc = PgcPlayback(239101, 6055241, VideoDetail(bvid = "BVexample", aid = 117245409822048, cid = 41752985788))

    @Test fun `TV playback uses episode ID and current account token`() {
        val params = tvPlaybackParams(pgc, 80, "test-token", 1700000000)
        assertEquals("2", params["playurl_type"])
        assertEquals("6055241", params["object_id"])
        assertEquals("41752985788", params["cid"])
        assertEquals("117245409822048", params["ogv_aid"])
        assertEquals("239101", params["season_id"])
        assertEquals("test-token", params["access_key"])
        assertEquals("80", params["qn"])
        assertEquals(AppSignUtils.signForTvApi(params - "sign"), params)
        assertFalse(params.containsKey("bvid"))
        assertFalse(params.containsKey("preview"))
    }

    @Test fun `guest requests omit access key and signature covers quality`() {
        val low = tvPlaybackParams(pgc, 64, null, 1700000000)
        val high = tvPlaybackParams(pgc, 80, null, 1700000000)
        assertFalse(low.containsKey("access_key"))
        assertNotEquals(low["sign"], high["sign"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid episode cannot fall back to a BV request`() {
        tvPlaybackParams(pgc.copy(episodeId = 0), 80, null, 1700000000)
    }
}
