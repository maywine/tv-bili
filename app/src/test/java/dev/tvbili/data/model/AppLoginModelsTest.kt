package dev.tvbili.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class AppLoginModelsTest {
    private val json = Json { ignoreUnknownKeys = true }
    @Test fun `HD nested credentials are read with cookies`() {
        val response = json.decodeFromString<AppPollResponse>("""
            {"code":0,"data":{"token_info":{"mid":123,"access_token":"hd-token","refresh_token":"hd-refresh"},
              "cookie_info":{"cookies":[{"name":"SESSDATA","value":"cookie"}]}}}
        """)
        val data = requireNotNull(response.data)
        assertEquals("hd-token", data.sessionAccessToken)
        assertEquals("hd-refresh", data.sessionRefreshToken)
        assertEquals(123L, data.sessionMid)
        assertEquals("cookie", data.cookieInfo!!.cookies.single().value)
    }

    @Test fun `flat credentials from shared QR endpoint also work`() {
        val response = json.decodeFromString<AppPollResponse>("""
            {"code":0,"data":{"mid":456,"access_token":"flat-token","refresh_token":"flat-refresh"}}
        """)
        val data = requireNotNull(response.data)
        assertEquals("flat-token", data.sessionAccessToken)
        assertEquals("flat-refresh", data.sessionRefreshToken)
        assertEquals(456L, data.sessionMid)
    }
}
