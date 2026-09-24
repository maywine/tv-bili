package dev.tvbili.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PgcPlaybackTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private fun season() = json.decodeFromString<PgcSeasonDetail>("""
        {"season_id":239101,"title":"天赐的声音7","cover":"https://example.com/season.jpg","episodes":[
          {"id":4280680,"aid":116736372247973,"bvid":"BV1bKE268ELY","cid":39180764643,
           "duration":5111903,"title":"第1期","long_title":"实力歌手组队竞演"},
          {"id":6055241,"aid":117245409822048,"bvid":"BV14fYT6QEH7","cid":41752985788,
           "duration":5131000,"title":"第13期精编版","long_title":"实力唱将舞台合集"},
          {"id":0,"bvid":"","cid":0,"title":"即将上线"}
        ]}
    """)

    @Test fun `preserve latest valid episode identifiers and convert milliseconds`() {
        val target = season().latestPlayback()
        assertEquals(239101L, target.seasonId)
        assertEquals(6055241L, target.episodeId)
        assertEquals("BV14fYT6QEH7", target.detail.bvid)
        assertEquals(41752985788L, target.detail.cid)
        assertEquals(117245409822048L, target.detail.aid)
        assertEquals(5131, target.detail.duration)
        assertEquals("天赐的声音7 · 第13期精编版 · 实力唱将舞台合集", target.detail.title)
        assertEquals("https://example.com/season.jpg", target.detail.pic)
        assertTrue(target.detail.pages.isEmpty())
    }

    @Test(expected = IllegalStateException::class)
    fun `missing episode metadata fails before navigation`() {
        PgcSeasonDetail(seasonId = 1).latestPlayback()
    }

    @Test fun `history retains PGC routing after serialization`() {
        val pgc = season().latestPlayback()
        val item = HistoryItem(pgc.detail.bvid, title = pgc.detail.title, coverUrl = "", pgc = pgc)
        val restored = json.decodeFromString<HistoryItem>(json.encodeToString(item))
        assertEquals(pgc, restored.toHomeCard().pgc)
    }

    @Test fun `old history entries remain readable without PGC metadata`() {
        val item = json.decodeFromString<HistoryItem>("""{"bvid":"BVold","title":"旧视频","coverUrl":""}""")
        assertNull(item.pgc)
        assertNull(item.toHomeCard().pgc)
    }
}
