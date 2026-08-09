package dev.tvbili.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchModelsTest {

    // ── cleanSearchHtml ────────────────────────────────────────
    @Test fun `cleanSearchHtml strips em tags`() {
        assertEquals(
            "鬼畜名场面",
            cleanSearchHtml("<em class='keyword'>鬼畜</em>名场面"),
        )
    }

    @Test fun `cleanSearchHtml decodes entities`() {
        assertEquals(
            "Hello \"world\" & <bro>",
            cleanSearchHtml("Hello &quot;world&quot; &amp; &lt;bro&gt;"),
        )
    }

    @Test fun `cleanSearchHtml trims whitespace`() {
        assertEquals("a", cleanSearchHtml("  a  "))
    }

    // ── parseSearchDuration ────────────────────────────────────
    @Test fun `parseSearchDuration mmss`() {
        assertEquals(332, parseSearchDuration("05:32"))
    }

    @Test fun `parseSearchDuration hhmmss`() {
        assertEquals(3932, parseSearchDuration("01:05:32"))
    }

    @Test fun `parseSearchDuration empty`() {
        assertEquals(0, parseSearchDuration(""))
    }

    @Test fun `parseSearchDuration pure seconds`() {
        assertEquals(45, parseSearchDuration("45"))
    }

    @Test fun `parseSearchDuration garbage`() {
        assertEquals(0, parseSearchDuration("abc"))
    }

    // ── normalizeCoverUrl ──────────────────────────────────────
    @Test fun `normalizeCoverUrl prepends https for protocol-relative`() {
        assertEquals(
            "https://i0.hdslb.com/x.jpg",
            normalizeCoverUrl("//i0.hdslb.com/x.jpg"),
        )
    }

    @Test fun `normalizeCoverUrl upgrades http to https`() {
        assertEquals(
            "https://i0.hdslb.com/x.jpg",
            normalizeCoverUrl("http://i0.hdslb.com/x.jpg"),
        )
    }

    @Test fun `normalizeCoverUrl keeps https intact`() {
        assertEquals(
            "https://i0.hdslb.com/x.jpg",
            normalizeCoverUrl("https://i0.hdslb.com/x.jpg"),
        )
    }

    // ── LazyGrid key safety ─────────────────────────────────────
    @Test fun `toUniqueHomeCardVideos drops blank bvids and duplicate keys`() {
        val items = listOf(
            SearchVideoItem(id = 1, bvid = "", title = "推广占位"),
            SearchVideoItem(id = 2, bvid = "BV1same", title = "第一条"),
            SearchVideoItem(id = 3, bvid = "BV1same", title = "重复条目"),
            SearchVideoItem(id = 4, bvid = "BV1other", title = "另一条"),
        )

        val cards = items.toUniqueHomeCardVideos()

        assertEquals(listOf("BV1same", "BV1other"), cards.map { it.bvid })
        assertEquals(listOf("video_BV1same", "video_BV1other"), cards.map { it.stableKey })
        assertEquals("第一条", cards.first().title)
    }
}
