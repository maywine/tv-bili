package dev.tvbili.data.repo

import android.util.Log
import dev.tvbili.data.model.toHomeCard
import dev.tvbili.data.model.toPgcCards
import dev.tvbili.data.model.PgcPlayback
import dev.tvbili.data.model.latestPlayback
import dev.tvbili.net.NetworkModule
import kotlinx.coroutines.CancellationException

/** 电影、综艺优先使用电视片库；网页版索引和排行榜只在首屏失败时兜底。 */
class PgcRepository {

    suspend fun resolveLatestEpisode(seasonId: Long): Result<PgcPlayback> = runCatching {
        val response = NetworkModule.pgcApi.getSeasonDetail(seasonId)
        require(response.code == 0) { "节目详情：${response.message} (${response.code})" }
        val season = requireNotNull(response.result) { "节目详情为空" }
        season.copy(seasonId = seasonId).latestPlayback()
    }.onFailure { if (it is CancellationException) throw it }

    /** 固定后续页的数据源，避免电视片库和网页版排序不同导致漏项或重复。 */
    suspend fun loadSeasonIndex(
        seasonType: Int,
        page: Int = 1,
        order: PgcOrder = PgcOrder.RECOMMENDED,
        source: PgcSource? = null,
    ): Result<PgcPage> = runCatching {
        if (source == PgcSource.RANK) return@runCatching PgcPage(emptyList(), false, source)
        if (source == null || source == PgcSource.TV) {
            try {
                val response = NetworkModule.pgcApi.getTvSeasonIndex(seasonType, page, sort = order.tvSort)
                require(response.code == 0) { "电视片库：${response.message} (${response.code})" }
                val data = requireNotNull(response.data) { "电视片库未返回内容" }
                return@runCatching PgcPage(data.result.toPgcCards(seasonType), data.hasNext(page), PgcSource.TV)
            } catch (e: Exception) {
                if (e is CancellationException || source == PgcSource.TV) throw e
                Log.w(TAG, "TV catalog unavailable; trying web index", e)
            }
        }
        try {
            val response = NetworkModule.pgcApi.getSeasonIndex(seasonType = seasonType, page = page, order = order.webOrder)
            require(response.code == 0) { "片库加载失败：${response.message} (${response.code})" }
            val data = requireNotNull(response.result) { "片库未返回内容" }
            val cards = data.list.filter { it.seasonId > 0 && it.title.isNotBlank() }
                .distinctBy { it.seasonId }.map { it.toHomeCard() }
            PgcPage(cards, data.hasNext == 1, PgcSource.WEB)
        } catch (e: Exception) {
            if (e is CancellationException || source == PgcSource.WEB || page > 1) throw e
            val rank = tryFetch("rank/web") {
                val r = NetworkModule.pgcApi.getRank(seasonType = seasonType, day = 3)
                r.code to r.result?.list.orEmpty().filter { it.seasonId > 0 && it.title.isNotBlank() }.map { it.toHomeCard() }
            }.ifEmpty {
                tryFetch("rank/legacy") {
                    val r = NetworkModule.pgcApi.getLegacyRank(seasonType = seasonType, day = 3)
                    r.code to r.result?.list.orEmpty().filter { it.seasonId > 0 && it.title.isNotBlank() }.map { it.toHomeCard() }
                }
            }
            if (rank.isEmpty()) throw e
            PgcPage(rank.distinctBy { it.seasonId }, false, PgcSource.RANK)
        }
    }.onFailure { if (it is CancellationException) throw it }

    /** 排行端点失败时尝试下一个兜底；协程取消必须向上传递。 */
    private inline fun tryFetch(
        tag: String,
        block: () -> Pair<Int, List<HomeCard.PgcSeason>>,
    ): List<HomeCard.PgcSeason> = runCatching(block).fold(
        onSuccess = { (code, cards) ->
            Log.d(TAG, "pgc[$tag] code=$code size=${cards.size}")
            if (code != 0) {
                Log.w(TAG, "pgc[$tag] non-zero code=$code → fallthrough")
                emptyList()
            } else cards
        },
        onFailure = { e ->
            if (e is CancellationException) throw e
            Log.w(TAG, "pgc[$tag] threw: ${e.message}")
            emptyList()
        },
    )

    /**
     * 取季度详情，返回**最新一集**的 bvid。
     *
     * - 综艺通常按更新顺序 append，episodes.last() 就是最新；首集（episodes.first()）虽然合理
     *   但 TV 体验里用户更倾向看「最新一期」
     * - 集表为空（即将开播 / 接口异常）→ failure，调用方提示并按返回处理
     * - bvid 为空（极少数 PGC ep 只有 ep_id 没回 bvid）→ failure
     */
    suspend fun resolveLatestEpisodeBvid(seasonId: Long): Result<String> = runCatching {
        val resp = NetworkModule.pgcApi.getSeasonDetail(seasonId)
        require(resp.code == 0) { "pgc season code=${resp.code} msg=${resp.message}" }
        val episodes = resp.result?.episodes.orEmpty()
        require(episodes.isNotEmpty()) { "本节目暂无可播放剧集" }
        val latest = episodes.lastOrNull { it.bvid.isNotBlank() }
            ?: error("剧集无 bvid，暂不支持播放")
        latest.bvid
    }

    private companion object {
        const val TAG = "PgcRepository"
    }
}
