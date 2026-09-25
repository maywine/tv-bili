package dev.tvbili.data.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class HdPgcModelsTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test fun `mobile search filters mixed film categories and duplicate seasons`() {
        val response = json.decodeFromString<HdPgcSearchResponse>("""
            {"code":0,"data":{"page":1,"pages":2,"items":[
              {"season_id":239101,"season_type":7,"title":"<em>天赐</em>的声音7","cover":"//example.com/7.jpg","label":"全12期","badges":[{"text":"会员"}]},
              {"season_id":239101,"season_type":7,"title":"重复"},
              {"season_id":44847,"season_type":2,"title":"电影"},
              {"season_id":0,"season_type":7,"title":"无效条目"}
            ]}}
        """)
        val data = requireNotNull(response.data)
        val card = data.items.toPgcCards(7).single()
        assertEquals(239101L, card.seasonId)
        assertEquals("天赐的声音7", card.title)
        assertEquals("https://example.com/7.jpg", card.coverUrl)
        assertEquals("会员", card.badge)
        assertEquals("", card.indexShow)
        assertEquals(2, data.pages)
        assertEquals(44847L, data.items.toPgcCards(2).single().seasonId)
    }

    @Test fun `HD detail uses positive episode module instead of trailers`() {
        val response = json.decodeFromString<HdSeasonResponse>("""
            {"code":0,"data":{"season_id":239101,"title":"天赐的声音7","modules":[
              {"style":"positive","data":{"episodes":[{"id":123,"bvid":"BVepisode","aid":3,"cid":456,"duration":10000,"title":"第1期"}]}},
              {"style":"section","data":{"episodes":[{"id":999,"bvid":"BVtrailer","cid":9999}]}},
              {"style":"season","data":{"seasons":[]}}
            ]}}
        """)
        val playback = requireNotNull(response.data).toSeasonDetail().latestPlayback()
        assertEquals(123L, playback.episodeId)
        assertEquals(456L, playback.detail.cid)
        assertEquals(10, playback.detail.duration)
    }

    @Test fun `mobile flat playurl result string does not hide media fields`() {
        val response = json.parseToJsonElement("""
            {"code":0,"message":"Success","result":"suee","quality":80,"is_preview":0,"dash":{"duration":10,"video":[{"id":80,"base_url":"https://example.com/video.m4s"}],"audio":[]}}
        """).jsonObject
        val data = decodeHdPlayUrl(response)
        assertEquals(80, data.quality)
        assertEquals(0, data.isPreview)
        assertEquals("https://example.com/video.m4s", data.dash!!.video.single().validUrl())
    }

    @Test fun `mobile preview and wrapped boolean preview are handled explicitly`() {
        val preview = decodeHdPlayUrl(json.parseToJsonElement("""
            {"code":0,"data":{"is_preview":true,"durl":[{"url":"https://example.com/preview.mp4","length":360000}]}}
        """).jsonObject)
        assertEquals(1, preview.isPreview)
        assertEquals(360000L, preview.durl.single().length)
        val full = decodeHdPlayUrl(json.parseToJsonElement("""{"code":0,"result":{"is_preview":false}}""").jsonObject)
        assertEquals(0, full.isPreview)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `permission errors remain errors rather than preview success`() {
        decodeHdPlayUrl(json.parseToJsonElement("""{"code":-10403,"message":"账号无权限"}""").jsonObject)
    }

    @Test fun `permission hint with explicit preview stream is not a transport failure`() {
        val data = decodeHdPlayUrl(json.parseToJsonElement("""
            {"code":0,"message":"Success","error_code":-10403,"is_preview":1,"durl":[{"url":"https://example.com/preview.mp4","length":360000}]}
        """).jsonObject)
        assertEquals(1, data.isPreview)
        assertEquals(360000L, data.durl.single().length)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `permission hint without explicit preview remains an error`() {
        decodeHdPlayUrl(json.parseToJsonElement("""
            {"code":0,"error_code":-10403,"is_preview":0,"durl":[{"url":"https://example.com/full.mp4"}]}
        """).jsonObject)
    }
}
