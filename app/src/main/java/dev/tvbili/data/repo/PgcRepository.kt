package dev.tvbili.data.repo

import android.util.Log
import dev.tvbili.data.model.toHomeCard
import dev.tvbili.net.NetworkModule

/**
 * PGC 内容（综艺 / 番剧 / 电影 / 国创 等）拉取。
 *
 * - [loadSeasonIndex]：按 season_type 拉 season 列表，转 [HomeCard.PgcSeason]
 *   优先走 `pgc/web/rank/list`（热门榜，结构稳）；为空时降级到 `pgc/season/index/result`
 * - [resolveLatestEpisodeBvid]：根据 season_id 取剧集列表，返回最新一集的 bvid——
 *   用于「点综艺卡 → 跳到最新一集」流程
 *
 * season_type：1=番剧 / 2=电影 / 3=纪录片 / 4=国创 / 5=电视剧 / 7=综艺
 */
class PgcRepository {

    /**
     * 拉 PGC 季度列表。三级 fallback——任一拿到 ≥ 1 条即返回：
     *
     * 1. `pgc/web/rank/list?season_type=7&day=3`——新版 web 排行接口，结构简单
     * 2. `pgc/season/rank/list?season_type=7&day=3`——老版排行接口，对匿名请求更宽容
     * 3. `pgc/season/index/result?...`——索引接口，参数集对齐 B 站 web 实际请求
     *
     * 每步失败/为空都打 Log.w("PgcRepository", ...)，方便用 `adb logcat -s PgcRepository`
     * 抓到具体哪步返回了什么（code / message / size）。
     */
    suspend fun loadSeasonIndex(seasonType: Int, page: Int = 1): Result<List<HomeCard.PgcSeason>> = runCatching {
        val tryRank = tryFetch("rank/web") {
            val r = NetworkModule.pgcApi.getRank(seasonType = seasonType, day = 3)
            r.code to (r.result?.list.orEmpty().mapNotNull { it.takeIf { it.seasonId > 0 && it.title.isNotBlank() }?.toHomeCard() })
        }
        if (tryRank.isNotEmpty()) return@runCatching tryRank

        val tryLegacy = tryFetch("rank/legacy") {
            val r = NetworkModule.pgcApi.getLegacyRank(seasonType = seasonType, day = 3)
            r.code to (r.result?.list.orEmpty().mapNotNull { it.takeIf { it.seasonId > 0 && it.title.isNotBlank() }?.toHomeCard() })
        }
        if (tryLegacy.isNotEmpty()) return@runCatching tryLegacy

        val tryIndex = tryFetch("index/result") {
            val r = NetworkModule.pgcApi.getSeasonIndex(seasonType = seasonType, page = page)
            r.code to (r.result?.list.orEmpty().mapNotNull { it.takeIf { it.seasonId > 0 && it.title.isNotBlank() }?.toHomeCard() })
        }
        tryIndex // 即便为空也作 success 返回——UI 显示「空空如也」而不是 Error
    }

    /** 调一个 PGC 端点，记录 code/数量；任何异常都吞掉返回空列表（由调用方继续尝试下一步）。 */
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
