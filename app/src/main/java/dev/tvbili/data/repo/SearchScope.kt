package dev.tvbili.data.repo

enum class SearchScope(val label: String, val seasonType: Int?) {
    VIDEO("视频", null),
    CINEMA("电影", 2),
    VARIETY("综艺", 7),
}

data class SearchPage(val cards: List<HomeCard>, val nextPage: Int? = null)
