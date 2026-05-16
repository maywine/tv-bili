package dev.tvbili.data.repo

import dev.tvbili.data.model.PlayUrlData
import dev.tvbili.data.model.RelatedVideoItem
import dev.tvbili.data.model.VideoDetail
import dev.tvbili.net.NetworkModule
import dev.tvbili.net.WbiKeyManager
import dev.tvbili.net.WbiUtils
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/**
 * 视频详情 + 播放地址 + 弹幕 XML 拉取。
 *
 * - [loadDetail]：bvid → VideoDetail（含 cid）
 * - [loadPlayUrl]：cid + bvid + qn → PlayUrlData（含 DASH 视频/音频流）
 * - [loadDanmakuBytes]：cid → 解压后的 XML ByteArray（自动嗅探 raw deflate）
 *
 * 错误约定：所有方法返回 [Result]；code != 0 / 网络异常 / data 为 null 都 failure。
 */
class VideoRepository {

    suspend fun loadDetail(bvid: String): Result<VideoDetail> = runCatching {
        val resp = NetworkModule.videoApi.getVideoDetail(bvid)
        require(resp.code == 0) { "view code=${resp.code} msg=${resp.message}" }
        checkNotNull(resp.data) { "view data null for $bvid" }
    }

    /**
     * @param qn 目标清晰度，默认 80=1080P。B 站可能因登录态降级到 32/64。
     *           接口返回的 `data.quality` 是实际清晰度（可能 ≠ qn）。
     *
     * 大会员锁定的 PGC（番剧 / 综艺会员专享）会回 -10403 / -404；调用方据此走
     * [loadPgcPlayUrl] + `try_look=1` 拿试看流，与 B 站手机版「免费试看」一致。
     */
    suspend fun loadPlayUrl(bvid: String, cid: Long, qn: Int = 80): Result<PlayUrlData> = runCatching {
        val (img, sub) = WbiKeyManager.getKeys().getOrThrow()
        val raw = mapOf(
            "bvid" to bvid,
            "cid" to cid.toString(),
            "qn" to qn.toString(),
            "fnval" to "4048",  // 全 DASH 流
            "fnver" to "0",
            "fourk" to "1",
        )
        val signed = WbiUtils.sign(raw, img, sub)
        val resp = NetworkModule.videoApi.getPlayUrl(signed)
        require(resp.code == 0) { "playurl code=${resp.code} msg=${resp.message}" }
        checkNotNull(resp.data) { "playurl data null for $bvid cid=$cid qn=$qn" }
    }

    /**
     * PGC 视频播放地址（番剧 / 综艺 / 电影）。
     *
     * - **试看**：`try_look=1` 让非大会员拿前 5-15 分钟试看流。试看与正片的 DashStream
     *   结构相同，[dev.tvbili.player.StreamSelector] 直接复用
     * - **响应根字段是 `result`**：PGC 域走自己的 envelope，与 UGC 的 `data` 不一致
     * - 仍可能 -10403：账号被风控、地区受限、未上线试看（极少数会员专享）
     */
    suspend fun loadPgcPlayUrl(bvid: String, cid: Long, qn: Int = 80): Result<PlayUrlData> = runCatching {
        val params = mapOf(
            "bvid" to bvid,
            "cid" to cid.toString(),
            "qn" to qn.toString(),
            "fnval" to "4048",
            "fnver" to "0",
            "fourk" to "1",
            "try_look" to "1",
        )
        val resp = NetworkModule.pgcApi.getPgcPlayUrl(params)
        require(resp.code == 0) { "pgc playurl code=${resp.code} msg=${resp.message}" }
        checkNotNull(resp.result) { "pgc playurl result null for $bvid cid=$cid qn=$qn" }
    }

    /**
     * 拉 `comment.bilibili.com/{cid}.xml`：返回值是**解压后的明文 XML 字节**。
     *
     * 该端点返回 **raw deflate**（无 `Content-Encoding` 头，OkHttp 不会自动解压），
     * 首字节嗅探：
     * - `0x3C` (`<`) → 明文 XML
     * - 其它 → 走 [Inflater] (nowrap=true) 解 raw deflate
     *
     * 之前直接把字节流喂给 DanmakuFlameMaster 的 BILI loader 会被压缩头堵住，解析出 0 条
     * 弹幕——这是「弹幕开了也没有弹幕」的根因。
     */
    suspend fun loadDanmakuBytes(cid: Long): Result<ByteArray> = runCatching {
        val body = NetworkModule.videoApi.getDanmakuXml(cid)
        val raw = body.use { it.bytes() }
        require(raw.isNotEmpty()) { "danmaku empty cid=$cid" }
        if (raw[0] == 0x3C.toByte()) raw else inflateRawDeflate(raw)
    }

    /**
     * 拉取相关视频列表。失败 / 空返 failure；调用方据此跳过自动续播。
     * 过滤掉 bvid 为空 / 时长 0 的脏数据。
     */
    suspend fun loadRelated(bvid: String): Result<List<RelatedVideoItem>> = runCatching {
        val resp = NetworkModule.videoApi.getRelated(bvid)
        require(resp.code == 0) { "related code=${resp.code} msg=${resp.message}" }
        resp.data.filter { it.bvid.isNotBlank() && it.duration > 0 }
    }

    private fun inflateRawDeflate(bytes: ByteArray): ByteArray {
        val inflater = Inflater(true)
        inflater.setInput(bytes)
        val out = ByteArrayOutputStream(bytes.size * 3)
        val buf = ByteArray(4096)
        try {
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                }
                out.write(buf, 0, n)
            }
        } finally {
            inflater.end()
        }
        return out.toByteArray()
    }
}
