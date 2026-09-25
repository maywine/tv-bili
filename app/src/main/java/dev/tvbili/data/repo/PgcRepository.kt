package dev.tvbili.data.repo

import dev.tvbili.data.model.PgcPlayback
import dev.tvbili.data.model.latestPlayback
import dev.tvbili.data.model.toHomeCard
import dev.tvbili.data.store.TokenStore
import dev.tvbili.net.AppSignUtils
import dev.tvbili.net.NetworkModule
import dev.tvbili.net.hdApiParams
import kotlinx.coroutines.CancellationException
import dev.tvbili.data.api.PgcApi

/** 电影和综艺统一使用 HD 客户端的片库及节目详情。 */
class PgcRepository(private val api: () -> PgcApi = { NetworkModule.hdPgcApi }) {
    private fun params(values: Map<String, String>): Map<String, String> = hdApiParams(
        values, TokenStore.accessToken, TokenStore.tokenAppKey, AppSignUtils.getTimestamp(),
    )

    suspend fun resolveLatestEpisode(seasonId: Long): Result<PgcPlayback> = runCatching {
        val response = api().getHdSeasonDetail(params(mapOf("season_id" to seasonId.toString())))
        require(response.code == 0) { "节目详情：${response.message} (${response.code})" }
        val season = requireNotNull(response.data) { "节目详情为空" }
        season.toSeasonDetail().copy(seasonId = seasonId).latestPlayback()
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun loadSeasonIndex(
        seasonType: Int,
        page: Int = 1,
        order: PgcOrder = PgcOrder.RECOMMENDED,
        source: PgcSource? = null,
    ): Result<PgcPage> = runCatching {
        require(source == null || source == PgcSource.HD) { "片库来源已变更，请刷新列表" }
        val response = api().getHdSeasonIndex(params(mapOf(
            "season_type" to seasonType.toString(), "st" to seasonType.toString(),
            "type" to "1", "page" to page.toString(), "pagesize" to "20",
            "order" to order.hdOrder.toString(), "sort" to "0",
        )))
        require(response.code == 0) { "片库加载失败：${response.message} (${response.code})" }
        val data = requireNotNull(response.result) { "片库未返回内容" }
        val cards = data.list.filter { it.seasonId > 0 && it.title.isNotBlank() }
            .distinctBy { it.seasonId }.map { it.toHomeCard() }
        PgcPage(cards, data.hasNext == 1, PgcSource.HD)
    }.onFailure { if (it is CancellationException) throw it }

    /**
     * 取季度详情，返回**最新一集**的 bvid。
     *
     * - 综艺通常按更新顺序 append，episodes.last() 就是最新；首集（episodes.first()）虽然合理
     *   但 TV 体验里用户更倾向看「最新一期」
     * - 集表为空（即将开播 / 接口异常）→ failure，调用方提示并按返回处理
     * - bvid 为空（极少数 PGC ep 只有 ep_id 没回 bvid）→ failure
     */
    suspend fun resolveLatestEpisodeBvid(seasonId: Long): Result<String> =
        resolveLatestEpisode(seasonId).map { it.detail.bvid }

    private companion object {
        const val TAG = "PgcRepository"
    }
}
