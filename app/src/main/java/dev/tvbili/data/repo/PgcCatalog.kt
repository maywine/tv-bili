package dev.tvbili.data.repo

enum class PgcOrder(val label: String, val tvSort: Int, val webOrder: Int) {
    RECOMMENDED("综合排序", 8, 2),
    UPDATED("最近更新", 0, 0),
}

enum class PgcSource { TV, WEB, RANK }

data class PgcPage(
    val cards: List<HomeCard.PgcSeason>,
    val hasMore: Boolean,
    val source: PgcSource,
)
