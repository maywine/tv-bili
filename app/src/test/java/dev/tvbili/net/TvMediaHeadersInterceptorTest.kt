package dev.tvbili.net

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class TvMediaHeadersInterceptorTest {
    @Test fun `TV headers override both player defaults and generic interceptor`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("video"))
            val client = OkHttpClient.Builder()
                .addInterceptor(HttpHeadersInterceptor())
                .addInterceptor(TvMediaHeadersInterceptor())
                .build()
            val request = Request.Builder().url(server.url("/preview.mp4"))
                .header("Referer", "https://www.bilibili.com")
                .header("Origin", "https://www.bilibili.com")
                .header("User-Agent", "desktop")
                .header("Range", "bytes=0-1023")
                .build()
            client.newCall(request).execute().use { assertEquals(200, it.code) }
            val recorded = server.takeRequest()
            assertEquals("Mozilla/5.0 BiliTV/1.8.8", recorded.getHeader("User-Agent"))
            assertNull(recorded.getHeader("Referer"))
            assertNull(recorded.getHeader("Origin"))
            assertEquals("bytes=0-1023", recorded.getHeader("Range"))
        }
    }

    @Test fun `deriving TV client does not change normal video client`() {
        MockWebServer().use { server ->
            repeat(2) { server.enqueue(MockResponse().setBody("ok")) }
            val web = OkHttpClient.Builder().addInterceptor(HttpHeadersInterceptor()).build()
            val tv = web.newBuilder().addInterceptor(TvMediaHeadersInterceptor()).build()
            tv.newCall(Request.Builder().url(server.url("/tv.mp4")).build()).execute().close()
            web.newCall(Request.Builder().url(server.url("/web.m4s")).build()).execute().close()
            assertNull(server.takeRequest().getHeader("Referer"))
            val normal = server.takeRequest()
            assertEquals("https://www.bilibili.com", normal.getHeader("Referer"))
            assertTrue(normal.getHeader("User-Agent")!!.contains("Chrome"))
        }
    }
}
