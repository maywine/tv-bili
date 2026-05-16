package dev.tvbili.data.repo

import dev.tvbili.data.model.toHomeCardVideo
import dev.tvbili.net.NetworkModule
import dev.tvbili.net.WbiKeyManager
import dev.tvbili.net.WbiUtils

/**
 * 综合搜索 —— 仅取 video 类目，其它 result_type（user / live_room / bangumi）忽略。
 *
 * 流程：WbiKeyManager.getKeys() → WbiUtils.sign(params, img, sub) → SearchApi.searchAll
 *
 * 失败模式：
 * - keyword 空 → require 抛 IllegalArgumentException
 * - WBI key 获取失败 → WbiKeyManager.getOrThrow 抛
 * - 服务端 code != 0 → require 抛（错误信息透传给上层 State.Error）
 * - data.result 全空或没 video 类目 → 返回 emptyList()（视为「无结果」非错误）
 */
class SearchRepository {

    suspend fun searchVideos(keyword: String, page: Int = 1): Result<List<HomeCard.Video>> = runCatching {
        require(keyword.isNotBlank()) { "keyword 不能为空" }
        val (img, sub) = WbiKeyManager.getKeys().getOrThrow()
        val raw = mapOf(
            "keyword" to keyword,
            "search_type" to "video",
            "page" to page.toString(),
            "page_size" to "20",
            "order" to "totalrank",
            "platform" to "pc",
        )
        val signed = WbiUtils.sign(raw, img, sub)
        val resp = NetworkModule.searchApi.searchAll(signed)
        require(resp.code == 0) { "search code=${resp.code} msg=${resp.message}" }
        resp.data?.result.orEmpty()
            .firstOrNull { it.resultType == "video" }
            ?.data.orEmpty()
            .map { it.toHomeCardVideo() }
    }
}
