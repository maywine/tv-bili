package dev.tvbili.ui.search

import dev.tvbili.data.repo.HomeCard

/** 搜索屏状态机：空 → 查询中 → 有结果 / 空结果 / 错误。 */
sealed interface SearchState {
    data object Idle : SearchState
    data class Loading(val keyword: String) : SearchState
    data class Loaded(val keyword: String, val cards: List<HomeCard.Video>) : SearchState
    data class Empty(val keyword: String) : SearchState
    data class Error(val keyword: String, val message: String) : SearchState
}
