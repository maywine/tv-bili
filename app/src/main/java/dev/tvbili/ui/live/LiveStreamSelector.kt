package dev.tvbili.ui.live

import dev.tvbili.data.model.LiveRoomPlayInfoData

/**
 * 从 [LiveRoomPlayInfoData] 选出最佳拉流 URL，并把当前 qn / 可选 qn / qn 描述表
 * 一并回传给 UI，供清晰度切换 chip 使用。
 *
 * 优先级：
 * 1. **protocol**: `http_hls` > `http_stream`（HLS 更稳，盒子兼容好）
 * 2. **format**: `fmp4` > `ts` > `flv`
 * 3. **codec**: `avc` > `hevc`（盒子端 HEVC 软解吃不消）
 */
object LiveStreamSelector {

    fun select(data: LiveRoomPlayInfoData): Selection? {
        val playurl = data.playurlInfo?.playurl ?: return null
        val streams = playurl.stream
        if (streams.isEmpty()) return null

        val protocolRank = mapOf("http_hls" to 0, "http_stream" to 1)
        val formatRank = mapOf("fmp4" to 0, "ts" to 1, "flv" to 2)
        val codecRank = mapOf("avc" to 0, "hevc" to 1)

        val ranked = streams.flatMap { s ->
            s.format.flatMap { f ->
                f.codec.map { c -> Quad(s.protocolName, f.formatName, c, c.urlInfo.firstOrNull()) }
            }
        }
        .filter { it.urlInfo != null && it.codec.baseUrl.isNotBlank() }
        .sortedWith(
            compareBy(
                { protocolRank[it.protocol] ?: Int.MAX_VALUE },
                { formatRank[it.format] ?: Int.MAX_VALUE },
                { codecRank[it.codec.codecName] ?: Int.MAX_VALUE },
            )
        )

        val best = ranked.firstOrNull() ?: return null
        val host = best.urlInfo!!.host
        val url = host + best.codec.baseUrl + best.urlInfo.extra

        // qn 描述表：accept_qn 是「当前流支持」的列表，gQnDesc 是「全部 qn 字典」；
        // 取交集得到 UI 应该展示的清晰度按钮
        val descMap = playurl.gQnDesc.associate { it.qn to it.desc }
        val acceptQn = best.codec.acceptQn

        return Selection(
            url = url,
            format = best.format,
            protocol = best.protocol,
            currentQn = best.codec.currentQn,
            acceptQn = acceptQn,
            qnDesc = descMap,
        )
    }

    data class Selection(
        val url: String,
        val format: String,
        val protocol: String,
        val currentQn: Int,
        val acceptQn: List<Int>,
        /** qn → 人类可读描述（如 10000→"原画"） */
        val qnDesc: Map<Int, String>,
    ) {
        val isHls: Boolean get() = protocol == "http_hls"
    }

    private data class Quad(
        val protocol: String,
        val format: String,
        val codec: dev.tvbili.data.model.LiveStreamCodec,
        val urlInfo: dev.tvbili.data.model.LiveStreamUrlInfo?,
    )
}
