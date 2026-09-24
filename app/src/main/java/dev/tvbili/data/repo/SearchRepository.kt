package dev.tvbili.data.repo

import dev.tvbili.data.model.toUniqueHomeCardVideos
import dev.tvbili.data.model.toPgcCards
import dev.tvbili.data.api.SearchApi
import dev.tvbili.net.NetworkModule
import dev.tvbili.net.WbiKeyManager
import dev.tvbili.net.WbiUtils
import kotlinx.coroutines.CancellationException

/**
 * 视频沿用 WBI 综合搜索；电影、综艺使用电视影视搜索并按 catalog_id 过滤。
 *
 * 流程：WbiKeyManager.getKeys() → WbiUtils.sign(params, img, sub) → SearchApi.searchAll
 *
 * 失败模式：
 * - keyword 空 → require 抛 IllegalArgumentException
 * - WBI key 获取失败 → WbiKeyManager.getOrThrow 抛
 * - 服务端 code != 0 → require 抛（错误信息透传给上层 State.Error）
 * - data.result 全空或没 video 类目 → 返回 emptyList()（视为「无结果」非错误）
 */
class SearchRepository(private val tvApi: () -> SearchApi = { NetworkModule.searchApi }) {

    suspend fun search(keyword: String, scope: SearchScope, page: Int = 1): Result<SearchPage> {
        if (scope == SearchScope.VIDEO) return searchVideos(keyword, page).map { SearchPage(it) }
        return runCatching {
            require(keyword.isNotBlank()) { "请输入搜索关键词" }
            val seasonType = requireNotNull(scope.seasonType)
            var currentPage = page
            var result: SearchPage
            // TV 接口返回混合影视结果；当前页没有目标分类时继续翻页，避免误报“没找到”。
            do {
                val response = tvApi().searchTvPgc(keyword, currentPage, seasonType)
                require(response.code == 0) { "搜索失败：${response.message} (${response.code})" }
                val data = requireNotNull(response.data) { "搜索接口未返回内容" }
                val cards = data.modules.flatMap { it.list }.toPgcCards(seasonType)
                val nextPage = (currentPage + 1).takeIf { currentPage < (data.pageInfo?.tvpgc?.pages ?: 0) }
                result = SearchPage(cards, nextPage)
                currentPage++
            } while (result.cards.isEmpty() && result.nextPage != null)
            result
        }.onFailure { if (it is CancellationException) throw it }
    }

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
            .toUniqueHomeCardVideos()
    }
}
