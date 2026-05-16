package dev.tvbili.data.store

import dev.tvbili.data.model.HistoryItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 仅测 [HistoryStore] 的纯函数解析。upsert / updateProgress 涉及 DataStore IO，
 * 需 instrumentation 测试；放 androidTest 模块；本期只测 parse 健壮性。
 */
class HistoryStoreParseTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test fun `parse null returns empty`() {
        assertEquals(emptyList<HistoryItem>(), HistoryStore.parse(null))
    }

    @Test fun `parse blank returns empty`() {
        assertEquals(emptyList<HistoryItem>(), HistoryStore.parse("  "))
    }

    @Test fun `parse valid json roundtrip`() {
        val items = listOf(
            HistoryItem(
                bvid = "BV1aaa",
                title = "测试",
                coverUrl = "https://x.jpg",
                durationSec = 60,
                lastPositionMs = 30_000,
                updatedAt = 1_700_000_000_000,
            ),
        )
        val raw = json.encodeToString(items)
        val parsed = HistoryStore.parse(raw)
        assertEquals(1, parsed.size)
        assertEquals("BV1aaa", parsed[0].bvid)
        assertEquals(30_000L, parsed[0].lastPositionMs)
    }

    @Test fun `parse garbage returns empty (no crash)`() {
        val parsed = HistoryStore.parse("{this is not json}")
        assertTrue("must not crash on bad json", parsed.isEmpty())
    }

    @Test fun `parse unknown fields are ignored`() {
        val raw = """[{
            "bvid": "BV1xx",
            "title": "old",
            "coverUrl": "u",
            "future_field_v2": "ignored"
        }]""".trimIndent()
        val parsed = HistoryStore.parse(raw)
        assertEquals(1, parsed.size)
        assertEquals("BV1xx", parsed[0].bvid)
    }
}
