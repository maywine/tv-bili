package dev.tvbili.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tvbili.data.repo.HomeCard
import dev.tvbili.data.repo.PgcRepository
import dev.tvbili.data.repo.SearchPage
import dev.tvbili.data.repo.SearchRepository
import dev.tvbili.data.repo.SearchScope
import dev.tvbili.data.model.PgcPlayback
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 每个搜索分类使用独立实例；换关键词立即取消旧请求，IME 提交会跳过防抖。 */
class SearchViewModel(
    val scope: SearchScope = SearchScope.VIDEO,
    private val search: suspend (String, SearchScope, Int) -> Result<SearchPage> = SearchRepository()::search,
    private val resolveSeason: suspend (Long) -> Result<PgcPlayback> = PgcRepository()::resolveLatestEpisode,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()
    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state = _state.asStateFlow()
    private var searchJob: Job? = null
    private var openJob: Job? = null
    private val _resolvingKey = MutableStateFlow<String?>(null)
    val resolvingKey = _resolvingKey.asStateFlow()
    private val _openError = MutableStateFlow<String?>(null)
    val openError = _openError.asStateFlow()
    private val _navigateEvent = MutableSharedFlow<PgcPlayback>(extraBufferCapacity = 1)
    val navigateEvent = _navigateEvent.asSharedFlow()

    fun onQueryChange(q: String) {
        if (_query.value == q) return
        _query.value = q
        startSearch(300)
    }

    fun submit() = startSearch(0)

    private fun startSearch(delayMs: Long) {
        searchJob?.cancel()
        openJob?.cancel()
        _resolvingKey.value = null
        _openError.value = null
        val keyword = _query.value.trim()
        _state.value = if (keyword.isEmpty()) SearchState.Idle else SearchState.Loading(keyword)
        if (keyword.isEmpty()) return
        searchJob = viewModelScope.launch {
            delay(delayMs)
            val result = search(keyword, scope, 1)
            ensureActive()
            _state.value = result.fold(
                onSuccess = {
                    if (it.cards.isEmpty()) SearchState.Empty(keyword)
                    else SearchState.Loaded(keyword, it.cards.distinctBy(HomeCard::stableKey), it.nextPage)
                },
                onFailure = { SearchState.Error(keyword, it.message ?: "搜索失败") },
            )
        }
    }

    fun loadMore() {
        val current = _state.value as? SearchState.Loaded ?: return
        val page = current.nextPage ?: return
        if (current.appending) return
        val loading = current.copy(appending = true, appendError = null)
        _state.value = loading
        searchJob = viewModelScope.launch {
            val result = search(current.keyword, scope, page)
            ensureActive()
            if (_state.value !== loading) return@launch
            _state.value = result.fold(
                onSuccess = {
                    current.copy(
                        cards = (current.cards + it.cards).distinctBy(HomeCard::stableKey),
                        nextPage = it.nextPage,
                        appendError = null,
                    )
                },
                onFailure = { current.copy(appendError = it.message ?: "加载失败，请重试") },
            )
        }
    }

    fun openSeason(card: HomeCard.PgcSeason) {
        if (_resolvingKey.value != null) return
        _resolvingKey.value = card.stableKey
        _openError.value = null
        openJob = viewModelScope.launch {
            val result = resolveSeason(card.seasonId)
            ensureActive()
            result.fold(
                onSuccess = { _navigateEvent.emit(it) },
                onFailure = { _openError.value = it.message ?: "节目暂时无法打开" },
            )
            _resolvingKey.value = null
        }
    }
}
