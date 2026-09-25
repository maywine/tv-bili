package dev.tvbili.data.repo

enum class PgcOrder(val label: String, val hdOrder: Int, val webOrder: Int) {
    RECOMMENDED("综合排序", 8, 2),
    UPDATED("最近更新", 0, 0),
}

enum class PgcSource { HD, WEB, RANK }

data class PgcPage(
    val cards: List<HomeCard.PgcSeason>,
    val hasMore: Boolean,
    val source: PgcSource,
)
