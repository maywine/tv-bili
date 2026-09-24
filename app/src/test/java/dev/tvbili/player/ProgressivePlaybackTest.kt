package dev.tvbili.player

import dev.tvbili.data.model.PlayUrlResponse
import dev.tvbili.data.model.PlayUrlData
import dev.tvbili.data.model.ProgressiveStream
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ProgressivePlaybackTest {
    @Test fun `TV preview MP4 is playable when DASH is null`() {
        val response = Json { ignoreUnknownKeys = true }.decodeFromString<PlayUrlResponse>("""
            {"code":0,"data":{"quality":80,"format":"mp4","timelength":5130807,
            "is_preview":1,"is_drm":false,"dash":null,
            "durl":[{"order":1,"length":360100,"url":"https://example.com/preview.mp4"}]}}
        """)
        val data = requireNotNull(response.data)
        assertNull(StreamSelector.select(data, 80))
        assertEquals(listOf("https://example.com/preview.mp4"), StreamSelector.progressiveUrls(data))
        assertEquals(1, data.isPreview)
        // 试看实际只有 6 分钟，不能把节目总长当作当前流的播放时长。
        assertEquals(360100L, data.durl.single().length)
    }

    @Test fun `preserve segment order and use backup for missing primary URL`() {
        val data = PlayUrlData(durl = listOf(
            ProgressiveStream(order = 2, url = "https://example.com/2.mp4"),
            ProgressiveStream(order = 1, backupUrl = listOf("", "https://example.com/1.mp4")),
        ))
        assertEquals(listOf("https://example.com/1.mp4", "https://example.com/2.mp4"), StreamSelector.progressiveUrls(data))
    }

    @Test fun `incomplete segment lists and DRM sources are rejected`() {
        val valid = ProgressiveStream(url = "https://example.com/1.mp4")
        assertTrue(StreamSelector.progressiveUrls(PlayUrlData(durl = listOf(valid, ProgressiveStream()))).isEmpty())
        assertTrue(StreamSelector.progressiveUrls(PlayUrlData(durl = listOf(valid), isDrm = true)).isEmpty())
    }
}
