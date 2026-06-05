package dev.tvbili.player

import dev.tvbili.data.model.Dash
import dev.tvbili.data.model.DashStream
import dev.tvbili.data.model.PlayUrlData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StreamSelectorTest {

    private fun video(id: Int, codecid: Int = 7, url: String = "http://v$id"): DashStream =
        DashStream(id = id, baseUrl = url, codecid = codecid, bandwidth = id * 1000)

    private fun audio(id: Int, bw: Int): DashStream =
        DashStream(id = id, baseUrl = "http://a$id", bandwidth = bw)

    private fun playUrl(videos: List<DashStream>, audios: List<DashStream> = emptyList()): PlayUrlData =
        PlayUrlData(dash = Dash(video = videos, audio = audios))

    @Test
    fun `select exact qn when available`() {
        val data = playUrl(videos = listOf(video(32), video(64), video(80)))
        val sel = StreamSelector.select(data, targetQn = 80)
        assertNotNull(sel)
        assertEquals(80, sel!!.video.id)
    }

    @Test
    fun `select downgrades to highest below target`() {
        val data = playUrl(videos = listOf(video(32), video(64)))
        val sel = StreamSelector.select(data, targetQn = 80)
        assertEquals(64, sel!!.video.id)
    }

    @Test
    fun `select returns lowest when nothing below or equal`() {
        val data = playUrl(videos = listOf(video(116), video(120)))
        val sel = StreamSelector.select(data, targetQn = 64)
        // 没有 <= 64 的，落到最低（116）
        assertEquals(116, sel!!.video.id)
    }

    @Test
    fun `select prefers H264 over HEVC at same qn`() {
        val data = playUrl(
            videos = listOf(
                video(80, codecid = 12),
                video(80, codecid = 7),
            ),
        )
        val sel = StreamSelector.select(data, targetQn = 80)
        assertEquals(7, sel!!.video.codecid)
    }

    @Test
    fun `select returns null for empty video list`() {
        val data = playUrl(videos = emptyList())
        assertNull(StreamSelector.select(data, targetQn = 80))
    }

    @Test
    fun `select picks highest bandwidth audio`() {
        val data = playUrl(
            videos = listOf(video(80)),
            audios = listOf(audio(30216, 64_000), audio(30232, 128_000), audio(30280, 192_000)),
        )
        val sel = StreamSelector.select(data, targetQn = 80)
        assertEquals(30280, sel!!.audio!!.id)
    }

    @Test
    fun `select audio null when none returned`() {
        val data = playUrl(videos = listOf(video(80)), audios = emptyList())
        val sel = StreamSelector.select(data, targetQn = 80)
        assertNull(sel!!.audio)
    }

    @Test
    fun `codecRank falls back to codecs string`() {
        val s = DashStream(codecid = 0, codecs = "hev1.1.6.L120")
        assertEquals(1, StreamSelector.codecRank(s))
    }

    @Test
    fun `at 4K only HEVC and AV1 exist so HEVC is chosen`() {
        // 4K（qn=120）没有 H.264：选 HEVC（codecid=12，优于 AV1）。
        // 这正是弱盒子硬解扛不住的档——只能靠「降 qn」而非「同档换 codec」逃生，
        // 见 qnAfterDecoderError / onPlayerError 的解码降级分支。
        val data = playUrl(videos = listOf(video(120, codecid = 13), video(120, codecid = 12)))
        val sel = StreamSelector.select(data, targetQn = 120)
        assertEquals(12, sel!!.video.codecid)
    }

    @Test
    fun `decoder downgrade target lands on H264 when present`() {
        // 解码降级到 80：StreamSelector 在同档优先 H.264，弱盒子能解
        val data = playUrl(videos = listOf(video(80, codecid = 12), video(80, codecid = 7), video(120, codecid = 12)))
        val sel = StreamSelector.select(data, targetQn = 80)
        assertEquals(80, sel!!.video.id)
        assertEquals(7, sel.video.codecid)
    }
}
