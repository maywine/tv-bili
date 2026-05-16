package dev.tvbili.data.repo

import androidx.compose.runtime.Immutable
import dev.tvbili.data.model.LiveRoom
import dev.tvbili.data.model.PopularItem
import dev.tvbili.data.model.RankingItem
import dev.tvbili.data.model.RecommendItem

/**
 * 统一的首页卡片 ViewModel —— 屏蔽推荐/热门视频与直播间的字段差异。
 * UI 层不再 import 任何 *Item / LiveRoom DTO，只见 HomeCard。
 *
 * 全部字段都是 `val + 原生不可变类型`，标 [@Immutable] 让 Compose 跳过相等检查、
 * 减少卡片网格滚动期间的无谓重组（Phase 7 性能基线）。
 */
@Immutable
sealed interface HomeCard {

    val stableKey: String
    val title: String
    val coverUrl: String

    @Immutable
    data class Video(
        val aid: Long,
        val bvid: String,
        override val title: String,
        override val coverUrl: String,
        val uploader: String,
        val durationSec: Int,
        val viewCount: Long,
    ) : HomeCard {
        override val stableKey: String get() = "video_$bvid"
    }

    @Immutable
    data class Live(
        val roomId: Long,
        override val title: String,
        override val coverUrl: String,
        val uploader: String,
        val areaName: String,
        val online: Int,
    ) : HomeCard {
        override val stableKey: String get() = "live_$roomId"
    }

    /**
     * PGC 季度卡（综艺 / 番剧 / 电影 等）。一条 = 一部节目。
     * 点击时调用方需要先解析 [seasonId] → 最新一集 bvid，再走 [Video] 的播放流程。
     */
    @Immutable
    data class PgcSeason(
        val seasonId: Long,
        override val title: String,
        override val coverUrl: String,
        /** 「更新至第 10 期」类提示；为空时卡片不展示。 */
        val indexShow: String,
        /** "会员" / "付费"；空字串表示无标签。 */
        val badge: String,
    ) : HomeCard {
        override val stableKey: String get() = "pgc_$seasonId"
    }
}

fun RecommendItem.toHomeCard(): HomeCard.Video = HomeCard.Video(
    aid = id,
    bvid = bvid.orEmpty(),
    title = title.orEmpty(),
    coverUrl = pic.orEmpty(),
    uploader = owner?.name.orEmpty(),
    durationSec = duration ?: 0,
    viewCount = stat?.view ?: 0L,
)

fun PopularItem.toHomeCard(): HomeCard.Video = HomeCard.Video(
    aid = aid,
    bvid = bvid,
    title = title,
    coverUrl = pic,
    uploader = owner.name,
    durationSec = duration,
    viewCount = stat.view.toLong(),
)

fun LiveRoom.toHomeCard(): HomeCard.Live = HomeCard.Live(
    roomId = roomid,
    title = title,
    coverUrl = displayCover(),
    uploader = uname,
    areaName = areaName,
    online = online,
)

fun RankingItem.toHomeCard(): HomeCard.Video = HomeCard.Video(
    aid = aid,
    bvid = bvid,
    title = title,
    coverUrl = pic,
    uploader = owner.name,
    durationSec = duration,
    viewCount = stat.view,
)
