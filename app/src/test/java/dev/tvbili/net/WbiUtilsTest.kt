package dev.tvbili.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WbiUtilsTest {

    private val imgKey = "7cd084941338484aae1ad9425b84077c"
    private val subKey = "4932caff0ff746eab6f01bf08b70ac45"

    @Test
    fun `sign appends wts and w_rid`() {
        val signed = WbiUtils.sign(
            params = mapOf("foo" to "bar"),
            imgKey = imgKey,
            subKey = subKey,
        )
        assertTrue("wts must be added", signed.containsKey("wts"))
        assertTrue("w_rid must be added", signed.containsKey("w_rid"))
        assertEquals(32, signed["w_rid"]!!.length)
        assertTrue(signed["wts"]!!.toLong() > 1_700_000_000L)
    }

    @Test
    fun `sign result keeps original keys`() {
        val signed = WbiUtils.sign(
            params = mapOf("foo" to "bar", "baz" to "1"),
            imgKey = imgKey,
            subKey = subKey,
        )
        assertEquals("bar", signed["foo"])
        assertEquals("1", signed["baz"])
    }

    @Test
    fun `sign strips illegal chars from values`() {
        val signed = WbiUtils.sign(
            params = mapOf("q" to "hello!'world(*)"),
            imgKey = imgKey,
            subKey = subKey,
        )
        // !'()* 都会被过滤掉
        assertEquals("helloworld", signed["q"])
    }
}
