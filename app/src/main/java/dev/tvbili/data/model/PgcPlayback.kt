package dev.tvbili.data.model

import kotlinx.serialization.Serializable

/** 节目播放必须保留 ep_id，不能仅传 BV 号后再按普通投稿请求详情。 */
@Serializable
data class PgcPlayback(
    val seasonId: Long,
    val episodeId: Long,
    val detail: VideoDetail,
)

internal fun PgcSeasonDetail.latestPlayback(): PgcPlayback {
    val episode = episodes.lastOrNull { it.id > 0 && it.cid > 0 && it.bvid.isNotBlank() }
        ?: error("本节目暂无可播放剧集")
    return PgcPlayback(
        seasonId = seasonId,
        episodeId = episode.id,
        detail = VideoDetail(
            bvid = episode.bvid,
            aid = episode.aid,
            cid = episode.cid,
            title = listOf(title, episode.title, episode.longTitle).filter(String::isNotBlank).joinToString(" · "),
            pic = episode.cover.ifBlank { cover },
            duration = (episode.duration / 1000L).coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
        ),
    )
}
