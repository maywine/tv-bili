package dev.tvbili.data.repo

import dev.tvbili.data.model.LiveRoom
import dev.tvbili.data.model.Owner
import dev.tvbili.data.model.PopularItem
import dev.tvbili.data.model.PopularStat
import dev.tvbili.data.model.RankingItem
import dev.tvbili.data.model.RankingStat
import dev.tvbili.data.model.RecommendItem
import dev.tvbili.data.model.RecommendStat
import dev.tvbili.ui.home.components.formatCount
import dev.tvbili.ui.home.components.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeCardMappingTest {

    @Test
    fun `RecommendItem maps to Video card`() {
        val item = RecommendItem(
            id = 100L,
            bvid = "BV1abc",
            title = "T",
            pic = "http://example/p.jpg",
            duration = 65,
            owner = Owner(name = "UP"),
            stat = RecommendStat(view = 12345),
        )
        val card = item.toHomeCard()
        assertEquals(100L, card.aid)
        assertEquals("BV1abc", card.bvid)
        assertEquals("UP", card.uploader)
        assertEquals(65, card.durationSec)
        assertEquals(12345L, card.viewCount)
        assertEquals("video_BV1abc", card.stableKey)
    }

    @Test
    fun `RecommendItem with null fields gives empty defaults`() {
        val item = RecommendItem(id = 1)
        val card = item.toHomeCard()
        assertEquals("", card.bvid)
        assertEquals("", card.title)
        assertEquals("", card.uploader)
        assertEquals(0, card.durationSec)
    }

    @Test
    fun `PopularItem maps to Video card`() {
        val item = PopularItem(
            aid = 200L,
            bvid = "BV2xyz",
            title = "P",
            pic = "p.jpg",
            duration = 180,
            owner = Owner(name = "UP2"),
            stat = PopularStat(view = 999999),
        )
        val card = item.toHomeCard()
        assertEquals("video_BV2xyz", card.stableKey)
        assertEquals(999999L, card.viewCount)
    }

    @Test
    fun `LiveRoom maps to Live card with best cover fallback`() {
        val room = LiveRoom(
            roomid = 300L,
            title = "Live!",
            uname = "Host",
            cover = "",
            userCover = "https://user-cover.jpg",
            systemCover = "https://system-cover.jpg",
            areaName = "游戏",
            online = 50000,
        )
        val card = room.toHomeCard()
        assertEquals(300L, card.roomId)
        // cover blank → userCover 优先
        assertEquals("https://user-cover.jpg", card.coverUrl)
        assertEquals("游戏", card.areaName)
        assertEquals(50000, card.online)
        assertEquals("live_300", card.stableKey)
    }

    @Test
    fun `LiveRoom uses keyframe if all covers blank`() {
        val room = LiveRoom(roomid = 1, keyframe = "kf.jpg")
        assertEquals("kf.jpg", room.toHomeCard().coverUrl)
    }

    @Test
    fun `formatDuration zero gives empty`() {
        assertEquals("", formatDuration(0))
        assertEquals("", formatDuration(-1))
    }

    @Test
    fun `formatDuration under hour`() {
        assertEquals("1:05", formatDuration(65))
    }

    @Test
    fun `formatDuration hour plus`() {
        assertEquals("1:01:01", formatDuration(3661))
    }

    @Test
    fun `formatCount under 10k`() {
        assertEquals("999", formatCount(999))
    }

    @Test
    fun `formatCount in wan`() {
        assertEquals("1.2万", formatCount(12345))
    }

    @Test
    fun `formatCount in yi`() {
        assertEquals("2.5亿", formatCount(250_000_000))
    }

    @Test
    fun `RankingItem maps to Video card`() {
        val item = RankingItem(
            aid = 400L,
            bvid = "BV4rnk",
            title = "Top1",
            pic = "rnk.jpg",
            duration = 90,
            owner = Owner(name = "UP_RANK"),
            stat = RankingStat(view = 50_000_000L),
        )
        val card = item.toHomeCard()
        assertEquals(400L, card.aid)
        assertEquals("BV4rnk", card.bvid)
        assertEquals("UP_RANK", card.uploader)
        assertEquals(90, card.durationSec)
        assertEquals(50_000_000L, card.viewCount)
        assertEquals("video_BV4rnk", card.stableKey)
    }
}
