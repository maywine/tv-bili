package dev.tvbili.data.repo

import dev.tvbili.net.NetworkModule
import dev.tvbili.net.WbiKeyManager
import dev.tvbili.net.WbiUtils

/**
 * 首页 3 分区数据加载。
 *
 * - 推荐流走 WBI 签名；[freshIdx] 必须每次 +1，否则 B 站可能返回缓存空响应
 * - 热门 / 直播 无签名
 * - 所有方法返回 [Result]：网络异常 / code != 0 都包成 failure
 */
class HomeRepository {

    suspend fun loadRecommend(freshIdx: Int): Result<List<HomeCard>> = runCatching {
        val (img, sub) = WbiKeyManager.getKeys().getOrThrow()
        val raw = mapOf(
            "y_num" to "4",
            "fresh_type" to "4",
            "feed_version" to "V8",
            "fresh_idx" to freshIdx.toString(),
            "fresh_idx_1h" to freshIdx.toString(),
            "fetch_row" to "1",
            "ps" to "12",
            "homepage_ver" to "1",
            "screen" to "0-0",
            "web_location" to "1430650",
        )
        val signed = WbiUtils.sign(raw, img, sub)
        val resp = NetworkModule.mainApi.getRecommend(signed)
        require(resp.code == 0) { "recommend code=${resp.code} msg=${resp.message}" }
        resp.data?.item.orEmpty()
            .filter { it.goto == null || it.goto == "av" }
            .map { it.toHomeCard() }
    }

    suspend fun loadPopular(page: Int = 1): Result<List<HomeCard>> = runCatching {
        val resp = NetworkModule.mainApi.getPopular(pn = page, ps = 20)
        require(resp.code == 0) { "popular code=${resp.code} msg=${resp.message}" }
        resp.data?.list.orEmpty().map { it.toHomeCard() }
    }

    suspend fun loadLive(page: Int = 1): Result<List<HomeCard>> = runCatching {
        val resp = NetworkModule.liveApi.getLiveList(page = page)
        require(resp.code == 0) { "live code=${resp.code} msg=${resp.message}" }
        resp.data?.getAllRooms().orEmpty().map { it.toHomeCard() }
    }

    suspend fun loadRanking(rid: Int): Result<List<HomeCard>> = runCatching {
        val resp = NetworkModule.mainApi.getRanking(rid = rid)
        require(resp.code == 0) { "ranking(rid=$rid) code=${resp.code} msg=${resp.message}" }
        resp.data?.list.orEmpty().map { it.toHomeCard() }
    }
}
