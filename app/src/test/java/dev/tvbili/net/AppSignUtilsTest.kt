package dev.tvbili.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSignUtilsTest {

    @Test
    fun `signForTvLogin appends sign field`() {
        val params = mapOf(
            "appkey" to AppSignUtils.TV_APP_KEY,
            "local_id" to "0",
            "ts" to "1700000000",
        )
        val signed = AppSignUtils.signForTvLogin(params)
        assertTrue(signed.containsKey("sign"))
        assertEquals(32, signed["sign"]!!.length)
        assertEquals(AppSignUtils.TV_APP_KEY, signed["appkey"])
        assertEquals("0", signed["local_id"])
        assertEquals("1700000000", signed["ts"])
    }

    @Test
    fun `signForTvLogin is deterministic for same input`() {
        val params = mapOf(
            "appkey" to AppSignUtils.TV_APP_KEY,
            "local_id" to "0",
            "ts" to "1700000000",
        )
        val a = AppSignUtils.signForTvLogin(params)
        val b = AppSignUtils.signForTvLogin(params)
        assertEquals(a["sign"], b["sign"])
    }

    @Test
    fun `sign is order-independent`() {
        val a = AppSignUtils.signForTvLogin(
            linkedMapOf(
                "appkey" to AppSignUtils.TV_APP_KEY,
                "ts" to "1700000000",
                "local_id" to "0",
            ),
        )
        val b = AppSignUtils.signForTvLogin(
            linkedMapOf(
                "local_id" to "0",
                "appkey" to AppSignUtils.TV_APP_KEY,
                "ts" to "1700000000",
            ),
        )
        assertEquals(a["sign"], b["sign"])
    }
}
