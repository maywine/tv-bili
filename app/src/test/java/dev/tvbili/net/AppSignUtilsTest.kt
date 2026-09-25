package dev.tvbili.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSignUtilsTest {

    @Test
    fun `signForHdApi appends sign field`() {
        val params = mapOf(
            "appkey" to AppSignUtils.HD_APP_KEY,
            "local_id" to "0",
            "ts" to "1700000000",
        )
        val signed = AppSignUtils.signForHdApi(params)
        assertTrue(signed.containsKey("sign"))
        assertEquals(32, signed["sign"]!!.length)
        assertEquals(AppSignUtils.HD_APP_KEY, signed["appkey"])
        assertEquals("0", signed["local_id"])
        assertEquals("1700000000", signed["ts"])
    }

    @Test
    fun `signForHdApi is deterministic for same input`() {
        val params = mapOf(
            "appkey" to AppSignUtils.HD_APP_KEY,
            "local_id" to "0",
            "ts" to "1700000000",
        )
        val a = AppSignUtils.signForHdApi(params)
        val b = AppSignUtils.signForHdApi(params)
        assertEquals(a["sign"], b["sign"])
    }

    @Test
    fun `sign is order-independent`() {
        val a = AppSignUtils.signForHdApi(
            linkedMapOf(
                "appkey" to AppSignUtils.HD_APP_KEY,
                "ts" to "1700000000",
                "local_id" to "0",
            ),
        )
        val b = AppSignUtils.signForHdApi(
            linkedMapOf(
                "local_id" to "0",
                "appkey" to AppSignUtils.HD_APP_KEY,
                "ts" to "1700000000",
            ),
        )
        assertEquals(a["sign"], b["sign"])
    }
}
