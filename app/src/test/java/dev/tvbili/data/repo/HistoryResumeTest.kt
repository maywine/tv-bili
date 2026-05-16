package dev.tvbili.data.repo

import dev.tvbili.data.model.HistoryItem
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 覆盖 [HistoryRepository.computeResumePositionMs]——「按上次进度继续播放」的核心逻辑。
 * 纯函数无 IO，直接传 items 列表测各边界。
 */
class HistoryResumeTest {

    private fun item(
        bvid: String = "BV1",
        cid: Long = 0L,
        pos: Long = 0L,
        durationSec: Int = 600,
    ) = HistoryItem(
        bvid = bvid,
        cid = cid,
        title = "t",
        coverUrl = "c",
        durationSec = durationSec,
        lastPositionMs = pos,
        updatedAt = 1L,
    )

    @Test fun `no history returns 0`() {
        val result = HistoryRepository.computeResumePositionMs(
            items = emptyList(),
            bvid = "BV1",
            cid = 100L,
            durationSec = 600,
        )
        assertEquals(0L, result)
    }

    @Test fun `mid-position returns position`() {
        val result = HistoryRepository.computeResumePositionMs(
            items = listOf(item(pos = 300_000L)),
            bvid = "BV1",
            cid = 0L,
            durationSec = 600,
        )
        assertEquals(300_000L, result)
    }

    @Test fun `position under 5s threshold returns 0`() {
        val result = HistoryRepository.computeResumePositionMs(
            items = listOf(item(pos = 3_000L)),
            bvid = "BV1",
            cid = 0L,
            durationSec = 600,
        )
        assertEquals(0L, result)
    }

    @Test fun `position within 5s of end returns 0`() {
        // duration = 600s = 600_000 ms；末尾 5s 内（≥ 595_000）视为看完
        val result = HistoryRepository.computeResumePositionMs(
            items = listOf(item(pos = 596_000L)),
            bvid = "BV1",
            cid = 0L,
            durationSec = 600,
        )
        assertEquals(0L, result)
    }

    @Test fun `position equals end returns 0`() {
        val result = HistoryRepository.computeResumePositionMs(
            items = listOf(item(pos = 600_000L)),
            bvid = "BV1",
            cid = 0L,
            durationSec = 600,
        )
        assertEquals(0L, result)
    }

    @Test fun `position greater than duration returns 0`() {
        // 数据异常：position 比总长还大
        val result = HistoryRepository.computeResumePositionMs(
            items = listOf(item(pos = 700_000L)),
            bvid = "BV1",
            cid = 0L,
            durationSec = 600,
        )
        assertEquals(0L, result)
    }

    @Test fun `duration zero skips end check`() {
        // duration 未知（API 没返回）→ 跳过末尾判定，仍尝试恢复
        val result = HistoryRepository.computeResumePositionMs(
            items = listOf(item(pos = 300_000L, durationSec = 0)),
            bvid = "BV1",
            cid = 0L,
            durationSec = 0,
        )
        assertEquals(300_000L, result)
    }

    @Test fun `wrong bvid returns 0`() {
        val result = HistoryRepository.computeResumePositionMs(
            items = listOf(item(bvid = "BV1", pos = 300_000L)),
            bvid = "BV2",
            cid = 0L,
            durationSec = 600,
        )
        assertEquals(0L, result)
    }

    @Test fun `cid mismatch returns 0 - different P`() {
        // 历史记录是 P1 的 cid，现在请求 P2 的 cid → 不恢复
        val result = HistoryRepository.computeResumePositionMs(
            items = listOf(item(cid = 100L, pos = 300_000L)),
            bvid = "BV1",
            cid = 200L,
            durationSec = 600,
        )
        assertEquals(0L, result)
    }

    @Test fun `cid matches returns position`() {
        val result = HistoryRepository.computeResumePositionMs(
            items = listOf(item(cid = 100L, pos = 300_000L)),
            bvid = "BV1",
            cid = 100L,
            durationSec = 600,
        )
        assertEquals(300_000L, result)
    }

    @Test fun `cid zero in request matches any stored cid`() {
        // 调用方不知道 cid（传 0）→ 兜底走 bvid 单键匹配
        val result = HistoryRepository.computeResumePositionMs(
            items = listOf(item(cid = 100L, pos = 300_000L)),
            bvid = "BV1",
            cid = 0L,
            durationSec = 600,
        )
        assertEquals(300_000L, result)
    }

    @Test fun `cid zero in stored matches any requested cid - legacy record`() {
        // 老版 v1 记录没 cid（=0）→ 仍能用于新版恢复（向前兼容）
        val result = HistoryRepository.computeResumePositionMs(
            items = listOf(item(cid = 0L, pos = 300_000L)),
            bvid = "BV1",
            cid = 100L,
            durationSec = 600,
        )
        assertEquals(300_000L, result)
    }

    @Test fun `picks first matching bvid in list`() {
        // 同 bvid 只该出现一次（dedupe by upsert），但写测验证 firstOrNull 语义
        val result = HistoryRepository.computeResumePositionMs(
            items = listOf(
                item(bvid = "BVx", pos = 100_000L),
                item(bvid = "BV1", pos = 300_000L),
            ),
            bvid = "BV1",
            cid = 0L,
            durationSec = 600,
        )
        assertEquals(300_000L, result)
    }
}
