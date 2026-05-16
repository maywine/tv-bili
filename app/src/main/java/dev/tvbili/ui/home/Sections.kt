package dev.tvbili.ui.home

import dev.tvbili.data.repo.HomeCard

/**
 * 首页分区候选池。Phase 3：写死 12 项；用户通过 Settings 选 + 排序。
 *
 * @property label SideBar 显示名（≤ 2 个汉字以避免溢出）
 * @property rid   分区 ID（用于 ranking/v2 调用；负值代表非 ranking 类）
 * @property kind  数据获取方式
 */
enum class SectionId(
    val label: String,
    val rid: Int,
    val kind: Kind,
) {
    RCMD("推荐", -1, Kind.RECOMMEND),
    HOT("热门", -1, Kind.POPULAR),
    LIVE("直播", -1, Kind.LIVE),
    RANKING("排行", 0, Kind.RANKING),
    BANGUMI("番剧", 13, Kind.RANKING),
    CINEMA("电影", 23, Kind.RANKING),
    /**
     * 综艺：走 PGC index 接口（season_type=7），返回真正的综艺节目（向往的生活/奔跑吧 等），
     * 而不是 ranking/v2 rid=5 那种「娱乐分区 UGC 短视频」。
     * [rid] 字段在 Kind.PGC 下重用为 `season_type` 值。
     */
    VARIETY("综艺", 7, Kind.PGC),
    ANIME_AREA("动画", 1, Kind.RANKING),
    GAME_AREA("游戏", 4, Kind.RANKING),
    MUSIC_AREA("音乐", 3, Kind.RANKING),
    TECH_AREA("科技", 36, Kind.RANKING),
    HISTORY("历史", -1, Kind.HISTORY),
    FAVORITE("收藏", -1, Kind.PLACEHOLDER);

    enum class Kind { RECOMMEND, POPULAR, LIVE, RANKING, PGC, HISTORY, PLACEHOLDER }
}

/** 分区数据加载的 5 态机。 */
sealed interface SectionState {
    data object Idle : SectionState
    data object Loading : SectionState
    /**
     * @property loadedAtMs 本次拉取完成的墙钟时间戳，用于过期判定（隔夜/隔小时回到 app
     * 时强制重拉，避免一直看缓存）。
     * @property appending true 表示底部分页加载中（仅 [SectionId.Kind.RECOMMEND] 用），
     * 用于在 grid 末尾画 spinner、防止重复触发分页。
     */
    data class Loaded(
        val cards: List<HomeCard>,
        val loadedAtMs: Long = System.currentTimeMillis(),
        val appending: Boolean = false,
    ) : SectionState
    data class Error(val message: String) : SectionState
    /** 分区无数据时的占位（如 FAVORITE 尚未实装）。 */
    data class Empty(val hint: String) : SectionState
}
