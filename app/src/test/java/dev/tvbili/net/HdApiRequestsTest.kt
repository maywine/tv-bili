package dev.tvbili.net

import dev.tvbili.data.model.PgcPlayback
import dev.tvbili.data.model.VideoDetail
import org.junit.Assert.*
import org.junit.Test

class HdApiRequestsTest {
    private val pgc = PgcPlayback(239101, 6055241, VideoDetail(bvid = "BVexample", aid = 117245409822048, cid = 41752985788))

    @Test fun `mobile playback preserves episode and HD account identity`() {
        val params = hdPlaybackParams(pgc, 80, "test-token", AppSignUtils.HD_APP_KEY, 1700000000)
        assertEquals("6055241", params["ep_id"])
        assertEquals("41752985788", params["cid"])
        assertEquals(AppSignUtils.HD_APP_KEY, params["appkey"])
        assertEquals("android_hd", params["mobi_app"])
        assertEquals("test-token", params["access_key"])
        assertEquals("80", params["qn"])
        assertEquals(AppSignUtils.signForHdApi(params - "sign"), params)
        assertFalse(params.containsKey("object_id"))
        assertFalse(params.containsKey("playurl_type"))
        assertFalse(params.containsKey("try_look"))
        assertFalse(params.containsKey("preview"))
    }

    @Test fun `legacy tokens are never sent under a different client signature`() {
        val params = hdApiParams(mapOf("access_key" to "injected"), "legacy-token", null, 1700000000)
        assertFalse(params.containsKey("access_key"))
        assertFalse(isHdSession("legacy-token", "4409e2ce8ffd12b8"))
        assertFalse(isHdSession("legacy-token", null))
        assertFalse(isHdSession("", AppSignUtils.HD_APP_KEY))
        assertTrue(isHdSession("hd-token", AppSignUtils.HD_APP_KEY))
    }

    @Test fun `guest requests omit access key and signature covers quality`() {
        val low = hdPlaybackParams(pgc, 64, null, null, 1700000000)
        val high = hdPlaybackParams(pgc, 80, null, null, 1700000000)
        assertFalse(low.containsKey("access_key"))
        assertNotEquals(low["sign"], high["sign"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid episode cannot fall back to a BV request`() {
        hdPlaybackParams(pgc.copy(episodeId = 0), 80, null, null, 1700000000)
    }
}
